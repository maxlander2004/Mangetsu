package yokai.data.backup.importers

/**
 * A read-progress / history item that has been imported but whose local chapter is not yet present.
 * It is stored in [yokai.data.PendingHistoryQueries] and promoted into real history/chapter rows by
 * [yokai.domain.history.interactor.ReconcilePendingHistory] once chapters are synced.
 */
data class PendingHistoryItem(
    val id: Long = 0,
    val mangaKey: String,
    val chapterUrl: String? = null,
    val chapterNumber: Double? = null,
    val lastReadAt: Long = 0L,
    val pageOrProgress: Int = 0,
    val completedChapter: Boolean = false,
)
