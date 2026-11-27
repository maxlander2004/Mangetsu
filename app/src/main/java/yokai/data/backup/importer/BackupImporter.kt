package yokai.data.backup.importer

import android.content.Context
import android.net.Uri
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.domain.manga.models.Manga
import yokai.data.backup.importers.PendingHistoryItem
import yokai.domain.chapter.interactor.GetChapter
import yokai.domain.chapter.interactor.UpdateChapter
import yokai.domain.chapter.models.ChapterUpdate
import yokai.domain.history.interactor.PendingHistoryRepository
import yokai.domain.history.interactor.ReconcilePendingHistory
import yokai.domain.history.interactor.UpsertHistory
import yokai.domain.manga.interactor.GetManga
import eu.kanade.tachiyomi.util.manga.mangaUrlPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Two-pass backup importer that bridges Kotatsu (.bk.zip) read history into Mangetsu's pending-history
 * layer so reading progress is preserved even when the chapters are not yet synced locally.
 *
 * Pass 1 builds a normalized lookup index (source + url / title fallbacks) from the backup manga.
 * Pass 2 resolves each backup manga to its local counterpart via [GetManga], then for each history
 * entry either updates the matching local chapter + history or inserts a [PendingHistoryItem] when
 * the chapter is not present yet. Tachiyomi/Mihon protobuf backups are handled by the existing
 * restorer, so this importer only acts on Kotatsu archives (detected via [Backup.requiresSourceRefresh]).
 *
 * After chapters are synced for a manga, call [reconcilePending] to promote pending items into real
 * [Chapter] and [History] rows.
 */
class BackupImporter(
    private val context: Context,
) {
    /** Decode the backup and run the Kotatsu history bridge (no-op for non-Kotatsu archives). */
    suspend fun import(uri: Uri): Unit = importFrom(
        eu.kanade.tachiyomi.util.BackupUtil.decodeBackup(context, uri)
    )

    /**
     * Run the Kotatsu history bridge on an already-decoded backup. No-op for non-Kotatsu archives
     * (those whose [Backup.requiresSourceRefresh] is false); Tachiyomi/Mihon protobuf backups are
     * handled by the existing restorer.
     *
     * This is the reusable entry point so the archive is decoded once: [import] decodes and delegates
     * here, and the restorer calls this with the backup it already decoded.
     */
    suspend fun importFrom(backup: Backup) = withContext(Dispatchers.IO) {
        if (!backup.requiresSourceRefresh) return@withContext

        val index = buildLookupIndex(backup)
        backup.backupManga.forEach { backupManga ->
            if (backupManga.history.isNotEmpty()) {
                handleHistory(backupManga, index)
            }
        }
    }

    private val getManga: GetManga by lazy { Injekt.get() }
    private val getChapter: GetChapter by lazy { Injekt.get() }
    private val updateChapter: UpdateChapter by lazy { Injekt.get() }
    private val upsertHistory: UpsertHistory by lazy { Injekt.get() }
    private val pendingRepository: PendingHistoryRepository by lazy { Injekt.get() }
    private val reconcilePendingHistory: ReconcilePendingHistory by lazy { Injekt.get() }

    /** Call after chapters for [mangaId] have been synced so pending progress can be promoted. */
    suspend fun reconcilePending(mangaId: Long) {
        try {
            reconcilePendingHistory.reconcile(listOf(mangaId))
        } catch (e: Throwable) {
            Logger.e(e) { "BackupImporter.reconcilePending failed for $mangaId" }
        }
    }

    companion object {
        private const val CHAPTER_NUMBER_TOLERANCE = 0.001
    }

    /** Pass 1: build the normalized lookup index for backup-manga resolution. */
    private fun buildLookupIndex(backup: Backup): MangaLookupIndex {
        val sourceMapping = backup.backupSources.associate { it.sourceId to it.name }
        val index = MangaLookupIndex(sourceMapping)

        backup.backupManga.forEach { backupManga ->
            val normalizedUrl = normalizedUrl(backupManga.url)
            val normalizedTitle = normalizedTitle(backupManga.title)
            val sourceKey = sourceKey(backupManga.source)

            if (normalizedUrl.isNotEmpty()) {
                index.bySourceAndUrl["$sourceKey:$normalizedUrl"] = backupManga
                index.byUrl[normalizedUrl] = backupManga
            }

            if (normalizedTitle.isNotEmpty()) {
                index.bySourceAndTitle["$sourceKey:$normalizedTitle"] = backupManga
                index.byTitle[normalizedTitle] = backupManga
            }
        }

        return index
    }

    /**
     * Resolve a backup manga to its local counterpart and bridge each history entry.
     */
    private suspend fun handleHistory(backupManga: BackupManga, index: MangaLookupIndex) {
        // Resolve via the lookup index to confirm the entry is recognised at all;
        // the actual local lookup is done by source + url below.
        val resolved = index.resolve(backupManga)
        if (resolved == null) {
            Logger.d { "No manga match for Kotatsu history entry: ${backupManga.title}" }
            return
        }

        val localManga = getManga.awaitByUrlAndSource(resolved.url, resolved.source)
        if (localManga == null) {
            Logger.d { "No local manga for Kotatsu history: ${resolved.title} (${resolved.source})" }
            return
        }

        val normalizedUrl = normalizedUrl(resolved.url)
        val mangaKey = if (normalizedUrl.isNotEmpty()) {
            "${sourceKey(resolved.source)}:$normalizedUrl"
        } else {
            rawSourceKey(resolved.source)
        }

        resolved.history.forEach { history ->
            handleHistoryEntry(localManga, mangaKey, history)
        }
    }

    private suspend fun handleHistoryEntry(
        localManga: Manga,
        mangaKey: String,
        history: BackupHistory,
    ) {
        val page = history.readDuration.toInt().coerceAtLeast(0)
        val pending = PendingHistoryItem(
            mangaKey = mangaKey,
            chapterUrl = history.url.ifBlank { null },
            chapterNumber = chapterNumberFrom(localManga, history.url),
            lastReadAt = history.lastRead,
            pageOrProgress = page,
            completedChapter = page > 0,
        )

        val (dbChapter, foundByUrl) = findChapter(localManga.id!!, history.url, pending.chapterNumber)
        if (dbChapter != null) {
            applyToChapter(dbChapter, pending, foundByUrl)
        } else {
            pendingRepository.insertPending(pending)
        }
    }

    private suspend fun findChapter(
        mangaId: Long,
        historyUrl: String,
        chapterNumber: Double?,
    ): Pair<Chapter?, Boolean> {
        // URL match is the restorer-candidate path: the restorer already placed those chapters
        // (and, for URL-matched ones, already wrote their history). Treat those as "found by URL"
        // so the importer only promotes reading progress there and does not re-write history.
        if (historyUrl.isNotBlank()) {
            val byUrl = getChapter.awaitByUrlAndMangaId(historyUrl, mangaId, false)
            if (byUrl != null) return byUrl to true
        }

        chapterNumber?.let { number ->
            val chapters = getChapter.awaitAll(mangaId, false)
            return chapters.firstOrNull { chapter ->
                chapter.isRecognizedNumber && abs(chapter.chapter_number - number) < CHAPTER_NUMBER_TOLERANCE
            } to false
        }

        return null to false
    }

    private suspend fun applyToChapter(
        chapter: Chapter,
        pending: PendingHistoryItem,
        foundByUrl: Boolean,
    ) {
        // Always restore/keep the larger page so an existing manga's reading progress is not
        // clobbered by a no-op import pass, and so a fresh import carries the real Kotatsu page.
        val nextRead = (chapter.read || pending.completedChapter)
        val nextBookmark = chapter.bookmark
        val nextLastPageRead = maxOf(
            chapter.last_page_read.toLong(),
            pending.pageOrProgress.toLong(),
        )

        updateChapter.await(
            ChapterUpdate(
                id = chapter.id!!,
                read = nextRead,
                bookmark = nextBookmark,
                lastPageRead = nextLastPageRead,
                pagesLeft = chapter.pages_left.toLong(),
            ),
        )

        // Write history only as the fallback for chapters the restorer did not place by url.
        // Those are the cases where the importer itself located the chapter, so it is the one
        // responsible for the history row. The restorer already wrote history for URL-matched
        // chapters, and the history upsert is additive on time_read, so re-upserting there would
        // inflate time_read with a guess.
        if (!foundByUrl) {
            upsertHistory.await(
                chapter.id!!,
                pending.lastReadAt,
                pending.pageOrProgress.toLong(),
            )
        }
    }

    private suspend fun chapterNumberFrom(manga: Manga, historyUrl: String): Double? {
        // Try to infer chapter number from the manga's local chapters by matching url.
        val chapters = getChapter.awaitAll(manga.id!!, false)
        val matched = chapters.firstOrNull {
            it.url == historyUrl || it.url == historyUrl.substringAfterLast('/')
        }
        return matched?.chapter_number?.toDouble()
    }

    private fun normalizedUrl(raw: String): String {
        val path = mangaUrlPath(raw)
        return path.trimEnd('/').lowercase()
    }

    private fun normalizedTitle(title: String): String {
        return title.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").takeIf { it.isNotEmpty() }
            ?: "untitled"
    }

    private fun sourceKey(sourceId: Long): String = "source:$sourceId"

    private fun rawSourceKey(sourceId: Long): String = "raw_source:$sourceId"
}

/** Normalized lookup index for backup-manga resolution in Pass 2. */
class MangaLookupIndex(
    private val sourceMapping: Map<Long, String>,
) {
    val bySourceAndUrl = mutableMapOf<String, BackupManga>()
    val byUrl = mutableMapOf<String, BackupManga>()
    val bySourceAndTitle = mutableMapOf<String, BackupManga>()
    val byTitle = mutableMapOf<String, BackupManga>()

    /** Resolve a backup manga in priority order, preserving raw-source fallbacks. */
    fun resolve(backupManga: BackupManga): BackupManga? {
        val normalizedUrl = normalizedUrl(backupManga.url)
        val normalizedTitle = normalizedTitle(backupManga.title)
        val sourceKey = sourceKey(backupManga.source)

        if (normalizedUrl.isNotEmpty()) {
            bySourceAndUrl["$sourceKey:$normalizedUrl"]?.let { return it }
            byUrl[normalizedUrl]?.let { return it }
        }

        if (normalizedTitle.isNotEmpty()) {
            bySourceAndTitle["$sourceKey:$normalizedTitle"]?.let { return it }
            byTitle[normalizedTitle]?.let { return it }
        }

        sourceMapping[backupManga.source]?.let { name ->
            if (name.isNotEmpty()) return backupManga
        }

        return null
    }

    private fun normalizedUrl(raw: String): String {
        val path = eu.kanade.tachiyomi.util.manga.mangaUrlPath(raw)
        return path.trimEnd('/').lowercase()
    }

    private fun normalizedTitle(title: String): String {
        return title.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").takeIf { it.isNotEmpty() }
            ?: "untitled"
    }

    private fun sourceKey(sourceId: Long): String = "source:$sourceId"
}
