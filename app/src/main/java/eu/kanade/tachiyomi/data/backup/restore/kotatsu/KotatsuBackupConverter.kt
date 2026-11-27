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
import eu.kanade.tachiyomi.util.manga.mangaUrlPath
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlinx.serialization.decodeFromString
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Converts a Kotatsu / Kotatsu Redo `.bk.zip` backup into the model used by the restore pipeline,
 * so the existing restore flow handles the import.
 *
 * Entries whose source has no equivalent here are not imported; they are reported through
 * [Backup.unmatchedEntries] so the restore dialog can list them before anything is written.
 *
 * A Kotatsu archive holds no chapter lists and no descriptions, so the imported entries need a
 * fetch from their source; that is flagged with [Backup.requiresSourceRefresh].
 */
class KotatsuBackupConverter(
    private val resolver: KotatsuSourceResolver,
) {

    fun convert(archive: InputStream): Backup {
        val sections = readSections(archive)
        val categories = decodeSection<KotatsuCategory>(sections, "categories").sortedBy { it.sortKey }
        val favourites = decodeSection<KotatsuFavourite>(sections, "favourites")
        val index = decodeSection<KotatsuIndexEntry>(sections, "index").associateBy { it.mangaId }
        val history = decodeSection<KotatsuHistory>(sections, "history").associateBy { it.mangaId }

        // A manga is stored once per category it is in, so the favourites are merged by manga id.
        // Restore matches a manga's categories by a category's order, hence the list position.
        val categoryOrders = categories.mapIndexed { position, category -> category.categoryId to position }.toMap()

        val unmatched = mutableListOf<String>()
        val sources = linkedMapOf<Long, BackupSource>()
        val mangas = mutableListOf<BackupManga>()

        favourites.groupBy { it.mangaId }.values.forEach { rows ->
            val manga = rows.firstNotNullOfOrNull { it.manga } ?: return@forEach
            val candidate = resolver.resolve(manga)
            if (candidate == null) {
                unmatched.add("${manga.title} (${manga.source.ifBlank { "unknown source" }})")
                return@forEach
            }

            sources.getOrPut(candidate.id) { BackupSource(name = candidate.name, sourceId = candidate.id) }

            val url = sourceUrl(candidate, manga)
            if (url == null) {
                // Without a usable url nothing can be requested from the source, and storing the
                // title as a path would only produce an entry that can never be opened.
                unmatched.add("${manga.title} (${manga.source.ifBlank { "unknown source" }}: no usable url)")
                return@forEach
            }

            val chapters = index[manga.id]?.chapters.orEmpty()
            val historyEntry = history[manga.id]
            val lastRead = chapters.firstOrNull { it.id == historyEntry?.chapterId }
            val lastReadDate = lastRead?.uploadDate ?: 0L

            mangas.add(
                BackupManga(
                    source = candidate.id,
                    url = url,
                    title = manga.title.ifBlank { manga.url },
                    author = manga.author?.takeIf { it.isNotBlank() },
                    genre = manga.tagTitles,
                    status = statusOf(manga.state),
                    thumbnailUrl = (manga.coverUrl ?: manga.largeCoverUrl)?.takeIf { it.isNotBlank() },
                    dateAdded = (rows.first().createdAt.takeIf { it > 0 } ?: System.currentTimeMillis() / 1000L) * 1000L,
                    chapters = chapters.mapIndexed { order, chapter ->
                        val url = chapter.url.ifBlank { chapter.id.toString() }
                        val isLastRead = chapter.id == historyEntry?.chapterId
                        BackupChapter(
                            url = url,
                            name = chapter.name.ifBlank { url },
                            scanlator = chapter.scanlator?.takeIf { it.isNotBlank() },
                            // Everything before the last read chapter was read as well.
                            read = isLastRead || (lastReadDate > 0 && chapter.uploadDate > 0 && chapter.uploadDate < lastReadDate),
                            lastPageRead = if (isLastRead) historyEntry?.page ?: 0 else 0,
                            dateUpload = chapter.uploadDate * 1000L,
                            chapterNumber = chapter.number,
                            sourceOrder = order,
                        )
                    },
                    categories = rows.mapNotNull { categoryOrders[it.categoryId] }.distinct().sorted(),
                    history = if (historyEntry != null && lastRead != null) {
                        listOf(
                            BackupHistory(
                                url = lastRead.url.ifBlank { lastRead.id.toString() },
                                lastRead = historyEntry.updatedAt * 1000L,
                                readDuration = historyEntry.page.toLong(),
                            ),
                        )
                    } else {
                        emptyList()
                    },
                ),
            )
        }

        return Backup(
            backupManga = mangas,
            backupCategories = categories.mapIndexed { position, category ->
                BackupCategory(name = category.title.ifBlank { "Category $position" }, order = position)
            },
            backupSources = sources.values.toList(),
        ).also {
            it.unmatchedEntries = unmatched
            it.requiresSourceRefresh = true
        }
    }

    /**
     * The url the matched source expects. Sources here build their requests as
     * `baseUrl + manga.url`, while Kotatsu stores either such a path or a whole url, so an absolute
     * url is reduced to its path and a path always gets a single leading slash. Without this the
     * details and chapter requests go to a malformed url.
     */
    private fun sourceUrl(candidate: KotatsuSourceResolver.Candidate, manga: KotatsuManga): String? {
        return sequenceOf(manga.url, manga.publicUrl)
            .map { sourcePath(it, candidate.baseUrl) }
            .firstOrNull { it.isNotEmpty() }
    }

    private fun sourcePath(raw: String, baseUrl: String?): String {
        val path = mangaUrlPath(raw)
        if (path.isEmpty()) return ""

        // A source whose base url has a path expects the manga url to be relative to it.
        val basePath = mangaUrlPath(baseUrl.orEmpty()).trimEnd('/')
        return if (basePath.isNotEmpty() && (path == basePath || path.startsWith("$basePath/"))) {
            path.removePrefix(basePath)
        } else {
            path
        }
    }

    /** Reads the sections the import needs and leaves the rest (settings, covers, ...) in the zip. */
    private fun readSections(archive: InputStream): Map<String, String> {
        val sections = mutableMapOf<String, String>()
        ZipInputStream(archive.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name.substringAfterLast('/').substringBeforeLast('.')
                if (name in SECTIONS) sections[name] = zip.readBytes().decodeToString()
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
        private val SECTIONS = setOf("categories", "favourites", "history", "index")

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
