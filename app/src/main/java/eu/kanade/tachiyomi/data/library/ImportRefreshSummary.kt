package eu.kanade.tachiyomi.data.library

/**
 * What the refresh that follows a Kotatsu import achieved, as one line for the log.
 *
 * The import itself only writes the entries; their chapters and details come from this refresh, so
 * a silent failure here leaves entries that cannot be opened. Reporting it is what turns that into
 * something the user can act on.
 */
internal fun importRefreshSummary(
    requested: Collection<Long>,
    refreshed: Collection<Long>,
    failed: List<String>,
): String {
    val requestedCount = requested.distinct().size
    val refreshedCount = refreshed.toSet().size
    val failures = failed.distinct()

    return buildString {
        append("$refreshedCount/$requestedCount entries refreshed")
        if (failures.isNotEmpty()) {
            append(", ${failures.size} failed: ")
            append(failures.joinToString(", "))
        }
    }
}
