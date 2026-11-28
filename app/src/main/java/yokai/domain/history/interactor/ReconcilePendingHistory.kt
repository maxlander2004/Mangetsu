package yokai.domain.history.interactor

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.util.manga.mangaUrlPath
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yokai.data.backup.importers.PendingHistoryItem
import yokai.domain.chapter.ChapterRepository
import yokai.domain.chapter.interactor.UpdateChapter
import yokai.domain.chapter.models.ChapterUpdate
import yokai.domain.manga.interactor.GetManga

/**
 * Promotes pending read-progress / history records for a manga into real [Chapter] + history
 * entries once the importer (or a refresh) has synced chapters for that manga. Pending records whose
 * chapters are still absent keep their pending state, so the UI can still surface "continue reading"
 * for them.
 */
class ReconcilePendingHistory(
    private val chapterRepository: ChapterRepository,
    private val updateChapter: UpdateChapter,
    private val upsertHistory: UpsertHistory,
    private val pendingRepository: PendingHistoryRepository,
    private val getManga: GetManga,
) {
    companion object {
        private const val TAG = "ReconcilePendingHistory"
        private const val CHAPTER_NUMBER_TOLERANCE = 0.001

        /** Must match the key [yokai.data.backup.importer.BackupImporter] stores pending items under. */
        fun mangaKey(source: Long, url: String) = "source:$source:${normalizedUrl(url)}"

        private fun normalizedUrl(raw: String): String = mangaUrlPath(raw).trimEnd('/').lowercase()
    }

    suspend fun reconcile(mangaIds: List<Long>) = withContext(Dispatchers.IO) {
        mangaIds.forEach { mangaId ->
            try {
                reconcileOne(mangaId)
            } catch (e: Throwable) {
                co.touchlab.kermit.Logger.e(e) { TAG }
            }
        }
    }

    private suspend fun reconcileOne(mangaId: Long) {
        val manga = getManga.awaitById(mangaId) ?: return
        val chapters = chapterRepository.getChapters(mangaId, filterScanlators = false)
        if (chapters.isEmpty()) return

        val pending = pendingRepository.selectPendingByMangaKey(mangaKey(manga.source, manga.url))
        if (pending.isEmpty()) return

        val chapterByUrl = chapters.associateBy { it.url }
        val chapterByNumber = chapters.groupBy { it.chapter_number }

        pending.forEach { item ->
            val chapter = matchChapter(item, chapterByUrl, chapterByNumber) ?: return@forEach
            applyPendingToChapter(item, chapter)
            pendingRepository.deletePendingForMangaKey(item.mangaKey)
        }
    }

    private fun matchChapter(
        item: PendingHistoryItem,
        chapterByUrl: Map<String, Chapter>,
        chapterByNumber: Map<Float, List<Chapter>>,
    ): Chapter? {
        val byUrl = item.chapterUrl?.let { chapterByUrl[it] }
        if (byUrl != null) return byUrl

        val number = item.chapterNumber?.toFloat() ?: return null
        return chapterByNumber.entries
            .firstOrNull { (chapterNumber, _) -> abs(chapterNumber - number) <= CHAPTER_NUMBER_TOLERANCE }
            ?.value
            ?.firstOrNull()
    }

    private suspend fun applyPendingToChapter(item: PendingHistoryItem, chapter: Chapter) {
        val chapterId = chapter.id ?: return

        // The history upsert is additive on time_read, and the importer no longer writes history
        // for chapters the restorer placed by url, so this only fills in the entries the importer
        // itself left pending.
        upsertHistory.await(chapterId, item.lastReadAt, item.pageOrProgress.toLong())

        if (item.completedChapter) {
            updateChapter.await(
                ChapterUpdate(
                    id = chapterId,
                    read = true,
                    bookmark = chapter.bookmark,
                    lastPageRead = item.pageOrProgress.toLong(),
                    pagesLeft = chapter.pages_left.toLong(),
                ),
            )
        } else if (item.pageOrProgress > 0) {
            updateChapter.await(
                ChapterUpdate(
                    id = chapterId,
                    lastPageRead = item.pageOrProgress.toLong(),
                ),
            )
        }
    }
}
