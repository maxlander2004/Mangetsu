package yokai.domain.history.interactor

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yokai.data.DatabaseHandler
import yokai.data.backup.importers.PendingHistoryItem

/**
 * Read progress imported from a Kotatsu backup whose chapter has not been synced locally yet.
 * Items are promoted into real chapter + history rows by [ReconcilePendingHistory].
 */
interface PendingHistoryRepository {
    suspend fun insertPending(pending: PendingHistoryItem)
    suspend fun deletePendingForMangaKey(mangaKey: String)
    suspend fun selectPendingByMangaKey(mangaKey: String): List<PendingHistoryItem>
}

class PendingHistoryRepositoryImpl(
    private val handler: DatabaseHandler,
) : PendingHistoryRepository {

    override suspend fun insertPending(pending: PendingHistoryItem): Unit = withContext(Dispatchers.IO) {
        try {
            handler.await(inTransaction = true) {
                pending_historyQueries.INSERT_PENDING(
                    mangaKey = pending.mangaKey,
                    chapterUrl = pending.chapterUrl,
                    chapterNumber = pending.chapterNumber,
                    lastReadAt = pending.lastReadAt,
                    pageOrProgress = pending.pageOrProgress.toLong(),
                    completedChapter = if (pending.completedChapter) 1L else 0L,
                )
                Unit
            }
        } catch (e: Throwable) {
            Logger.e(e) { "PendingHistoryRepositoryImpl.insertPending failed for ${pending.mangaKey}" }
        }
    }

    override suspend fun deletePendingForMangaKey(mangaKey: String): Unit = withContext(Dispatchers.IO) {
        try {
            handler.await(inTransaction = true) {
                pending_historyQueries.DELETE_PENDING_FOR_MANGA(mangaKey)
                Unit
            }
        } catch (e: Throwable) {
            Logger.e(e) { "PendingHistoryRepositoryImpl.deletePendingForMangaKey failed for $mangaKey" }
        }
    }

    override suspend fun selectPendingByMangaKey(mangaKey: String) = withContext(Dispatchers.IO) {
        try {
            handler.awaitList {
                pending_historyQueries.SELECT_PENDING_BY_MANGA_KEY(mangaKey)
            }.map { row ->
                PendingHistoryItem(
                    id = row.pending_id,
                    mangaKey = row.manga_key,
                    chapterUrl = row.chapter_url,
                    chapterNumber = row.chapter_number,
                    lastReadAt = row.last_read_at,
                    pageOrProgress = row.page_or_progress.toInt(),
                    completedChapter = row.completed_chapter != 0L,
                )
            }
        } catch (e: Throwable) {
            Logger.e(e) { "PendingHistoryRepositoryImpl.selectPendingByMangaKey failed for $mangaKey" }
            emptyList()
        }
    }
}
