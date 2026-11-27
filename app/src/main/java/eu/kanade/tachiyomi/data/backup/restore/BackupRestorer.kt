package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.restore.restorers.CategoriesBackupRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaBackupRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceBackupRestorer
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.util.BackupUtil
import eu.kanade.tachiyomi.util.manga.mangaUrlPath
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.data.backup.importer.BackupImporter
import yokai.domain.manga.interactor.GetManga
import yokai.i18n.MR
import yokai.util.lang.getString
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/** How many restored entries are written at the same time. */
private const val RESTORE_BATCH_SIZE = 4

class BackupRestorer(
    val context: Context,
    val notifier: BackupNotifier,
    private val categoriesBackupRestorer: CategoriesBackupRestorer = CategoriesBackupRestorer(),
    private val mangaBackupRestorer: MangaBackupRestorer = MangaBackupRestorer(),
    private val preferenceBackupRestorer: PreferenceBackupRestorer = PreferenceBackupRestorer(context),
    private val getManga: GetManga = Injekt.get(),
    private val backupImporter: BackupImporter = Injekt.get(),
) {
    private var restoreAmount = 0

    @Volatile
    private var restoreProgress = 0

    /**
     * Mapping of source ID to source name from backup data
     */
    private var sourceMapping: Map<Long, String> = emptyMap()

    private val errors = Collections.synchronizedList(mutableListOf<Pair<Date, String>>())

    suspend fun restore(uri: Uri) {
        val startTime = System.currentTimeMillis()
        restoreProgress = 0
        errors.clear()

        performRestore(uri)

        val endTime = System.currentTimeMillis()
        val time = endTime - startTime

        val logFile = writeErrorLog()

        notifier.showRestoreComplete(time, errors.size, logFile.parent, logFile.name)
    }

    private suspend fun performRestore(uri: Uri) {
        val backup = BackupUtil.decodeBackup(context, uri)

        backup.unmatchedEntries.forEach { errors.add(Date() to "Not imported, no matching source: $it") }

        restoreAmount = backup.backupManga.size + 3 // +3 for categories, app prefs, source prefs

        sourceMapping = backup.backupSources.associate { it.sourceId to it.name }

        val importedMangaIds = Collections.synchronizedList(mutableListOf<Long>())

        coroutineScope {
            // Restore categories
            if (backup.backupCategories.isNotEmpty()) {
                ensureActive()
                categoriesBackupRestorer.restoreCategories(backup.backupCategories) {
                    restoreProgress += 1
                    showRestoreProgress(restoreProgress, restoreAmount, context.getString(MR.strings.categories))
                }
            }

            ensureActive()
            preferenceBackupRestorer.restoreAppPreferences(backup.backupPreferences) {
                restoreProgress += 1
                showRestoreProgress(restoreProgress, restoreAmount, context.getString(MR.strings.app_settings))
            }
            ensureActive()
            preferenceBackupRestorer.restoreSourcePreferences(backup.backupSourcePreferences) {
                restoreProgress += 1
                showRestoreProgress(restoreProgress, restoreAmount, context.getString(MR.strings.source_settings))
            }

            // The library can already hold entries from an earlier import of the same archive,
            // whose stored url is the malformed one a previous build wrote. They are indexed by the
            // path their source builds requests from, so this import repairs them in place instead
            // of adding a duplicate entry.
            val existingBySource = runCatching { getManga.awaitFavorites() }
                .getOrDefault(emptyList())
                .groupBy { it.source }
                .mapValues { (_, mangas) ->
                    mangas.mapNotNull { manga ->
                        mangaUrlPath(manga.url).takeIf { it.isNotEmpty() }?.let { path -> path to manga }
                    }.toMap()
                }

            // Restore individual manga a batch at a time: a database round trip per entry, one
            // after the other, made a large archive take a very long time to import.
            backup.backupManga.chunked(RESTORE_BATCH_SIZE).forEach { batch ->
                coroutineScope {
                    batch.map { backupManga ->
                        async {
                            ensureActive()
                            mangaBackupRestorer.restoreManga(
                                backupManga,
                                backup.backupCategories,
                                existing = existingBySource[backupManga.source].orEmpty(),
                                onComplete = { manga ->
                                    manga.id?.let { importedMangaIds.add(it) }
                                    showRestoreProgress(advanceProgress(), restoreAmount, manga.title)
                                },
                                onError = { manga, e ->
                                    val sourceName = sourceMapping[manga.source] ?: manga.source.toString()
                                    errors.add(Date() to "${manga.title} [$sourceName]: ${e.message}")
                                },
                            )
                        }
                    }.awaitAll()
                }
            }
        }

        // Bridge Kotatsu read history into the local library. The converter now writes the page
        // number into BackupHistory.readDuration, and this import turns each history entry either
        // into a real chapter+history update (when the chapter is already present) or into a
        // pending item (when the chapter has not been synced yet). That is what lets a user restore
        // a Kotatsu backup and open the reader right where they stopped in the old app.
        if (backup.requiresSourceRefresh) {
            currentCoroutineContext().ensureActive()
            backupImporter.importFrom(backup)
        }

        if (backup.requiresSourceRefresh && importedMangaIds.isNotEmpty()) {
            // A converted archive holds no chapters and no descriptions, so fetch them from the
            // sources now instead of leaving every entry empty until it is opened. Only the entries
            // this restore wrote are updated, not the whole library.
            LibraryUpdateJob.startNow(
                context,
                mangaIds = importedMangaIds.toList().distinct(),
                // The archive has no descriptions or covers either, so this pass fetches details
                // as well as chapters and reports what it could not fetch.
                fetchDetails = true,
                fromImport = true,
            )
        }
        // TODO: optionally trigger online library + tracker update
    }

    /**
     * Called to update dialog in [BackupConst]
     *
     * @param progress restore progress
     * @param amount total restoreAmount of manga
     * @param title title of restored manga
     */
    private fun showRestoreProgress(
        progress: Int,
        amount: Int,
        title: String,
    ) {
        notifier.showRestoreProgress(title, progress, amount)
    }

    /** Progress is advanced by the parallel batch workers. */
    @Synchronized
    private fun advanceProgress(): Int = ++restoreProgress

    internal fun writeErrorLog(): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("yokai_restore.txt")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

                file.bufferedWriter().use { out ->
                    errors.forEach { (date, message) ->
                        out.write("[${sdf.format(date)}] $message\n")
                    }
                }
                return file
            }
        } catch (e: Exception) {
            // Empty
        }
        return File("")
    }
}
