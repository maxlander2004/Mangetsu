package eu.kanade.tachiyomi.data.library

/**
 * WorkManager caps a worker's input data at 10 KB, which is roughly 1200 manga ids.
 *
 * The ids of a limited update used to travel as "first one in the work data, the rest in a static
 * list", which silently drops everything past the first id whenever the process that queued the
 * work is not the one that runs it — the normal case for a deferred WorkManager job.
 */
internal const val MAX_MANGA_IDS_PER_REQUEST = 900

/** Splits [ids] into ordered batches that each fit in one request's work data. */
internal fun mangaIdBatches(ids: List<Long>): List<LongArray> {
    return ids.chunked(MAX_MANGA_IDS_PER_REQUEST) { batch -> batch.toLongArray() }
}
