package eu.kanade.tachiyomi.data.backup.restore.kotatsu

import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlinx.serialization.decodeFromString
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Converts a Kotatsu / Kotatsu Redo `.bk.zip` backup into the backup model used by the restore
 * pipeline, so the existing restore flow handles the import.
 *
 * Entries whose source has no equivalent here are not imported; they are reported through
 * [Backup.unmatchedEntries] so the restore dialog can list them before anything is written.
 */
class KotatsuBackupConverter(
    private val resolver: KotatsuSourceResolver,
) {

    fun convert(archive: InputStream): Backup {
        val sections = readSections(archive)
        val categories: List<KotatsuCategory> = decodeSection(sections, "categories")
        val favourites: List<KotatsuFavourite> = decodeSection(sections, "favourites")
        val index = decodeSection<KotatsuIndexEntry>(sections, "index").associateBy { it.mangaId }
        val history = decodeSection<KotatsuHistory>(sections, "history").associateBy { it.mangaId }

        val unmatched = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val sortedCategories = categories.sortedWith(compareBy({ it.sortKey }, { it.createdAt }))
        val categoryIndex = sortedCategories
            .mapIndexed { index, category -> category.categoryId to index }
            .toMap()
        val backupCategories = sortedCategories.map { BackupCategory(name = it.title.ifBlank { "Category" }, order = it.sortKey) }

        val sources = linkedMapOf<Long, BackupSource>()
        val mangas = mutableListOf<BackupManga>()

        // A manga appears once per category it is in, so entries are merged by manga id.
        favourites
            .groupBy { it.mangaId }
            .forEach { (_, rows) ->
                val favourite = rows.first()
                val manga = rows.firstNotNullOfOrNull { it.manga }
                if (manga == null) {
                    warnings.add("Skipped a favourite without manga data")
                    return@forEach
                }

                val candidate = resolver.resolve(manga)
                if (candidate == null) {
                    unmatched.add("${manga.title} (${manga.source.ifBlank { "unknown source" }})")
                    return@forEach
                }

                sources.getOrPut(candidate.id) { BackupSource(name = candidate.name, sourceId = candidate.id) }

                val chapters = index[manga.id]?.chapters.orEmpty()
                val historyEntry = history[manga.id]
                val lastReadChapter = chapters.firstOrNull { it.id == historyEntry?.chapterId }
                val lastReadChapterDate = lastReadChapter?.uploadDate ?: 0L

                val backupChapters = chapters.mapIndexed { order, chapter ->
                    val isLastRead = chapter.id == historyEntry?.chapterId
                    BackupChapter(
                        url = chapter.url.ifBlank { chapter.id.toString() },
                        name = chapter.name.ifBlank { chapter.url },
                        scanlator = chapter.scanlator?.takeIf { it.isNotBlank() },
                        read = isLastRead ||
                            (lastReadChapterDate > 0 && chapter.uploadDate in 1 until lastReadChapterDate),
                        bookmark = false,
                        lastPageRead = if (isLastRead) historyEntry?.page ?: 0 else 0,
                        dateFetch = chapter.uploadDate * 1000L,
                        dateUpload = chapter.uploadDate * 1000L,
                        chapterNumber = chapter.number,
                        sourceOrder = order,
                    )
                }

                val backupHistory = if (lastReadChapter != null && historyEntry != null) {
                    listOf(
                        BackupHistory(
                            url = lastReadChapter.url.ifBlank { lastReadChapter.id.toString() },
                            lastRead = historyEntry.updatedAt * 1000L,
                        ),
                    )
                } else {
                    emptyList()
                }

                if (chapters.isEmpty()) {
                    warnings.add("${manga.title}: no chapters in the Kotatsu index, only the series was imported")
                }

                mangas.add(
                    BackupManga(
                        source = candidate.id,
                        url = manga.url.ifBlank { manga.publicUrl.ifBlank { manga.title } },
                        title = manga.title.ifBlank { manga.url },
                        artist = null,
                        author = manga.author?.takeIf { it.isNotBlank() },
                        description = null,
                        genre = manga.tagTitles,
                        status = statusOf(manga.state),
                        thumbnailUrl = (manga.coverUrl ?: manga.largeCoverUrl)?.takeIf { it.isNotBlank() },
                        dateAdded = (favourite.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis() / 1000L) * 1000L,
                        viewer = 0,
                        chapters = backupChapters,
                        categories = rows.mapNotNull { categoryIndex[it.categoryId] }.distinct().sorted(),
                        favorite = true,
                        history = backupHistory,
                    ),
                )
            }

        return Backup(
            backupManga = mangas,
            backupCategories = backupCategories,
            backupSources = sources.values.toList(),
        ).also {
            it.warnings = warnings
            it.unmatchedEntries = unmatched
        }
    }

    private fun readSections(archive: InputStream): Map<String, String> {
        val sections = mutableMapOf<String, String>()
        ZipInputStream(archive.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name.substringAfterLast('/').substringBeforeLast('.')
                sections[name] = zip.readBytes().decodeToString()
            }
        }
        return sections
    }

    private inline fun <reified T> decodeSection(sections: Map<String, String>, name: String): List<T> {
        val raw = sections[name] ?: return emptyList()
        return try {
            kotatsuJson.decodeFromString<List<T>>(raw)
        } catch (e: Exception) {
            Logger.e(e) { "Unable to read the Kotatsu '$name' section" }
            emptyList()
        }
    }

    private fun statusOf(state: String?): Int = when (state?.uppercase()) {
        "ONGOING" -> SManga.ONGOING
        "FINISHED" -> SManga.COMPLETED
        "ABANDONED", "CANCELLED" -> SManga.CANCELLED
        "HIATUS", "PAUSED" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    companion object {
        fun create(): KotatsuBackupConverter {
            val sourceManager = Injekt.get<SourceManager>()
            val extensionManager = Injekt.get<ExtensionManager>()
            return KotatsuBackupConverter(
                KotatsuSourceResolver(
                    installedSources = sourceManager.getCatalogueSources(),
                    availableExtensions = runCatching { extensionManager.availableExtensionsFlow.value }
                        .getOrDefault(emptyList()),
                ),
            )
        }
    }
}
