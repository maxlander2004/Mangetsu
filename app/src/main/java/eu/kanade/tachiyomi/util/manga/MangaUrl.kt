package eu.kanade.tachiyomi.util.manga

/**
 * The path a source expects for a stored manga url.
 *
 * Sources build their requests as `baseUrl + manga.url`. A stored url that is a whole url, or that
 * carries a query or fragment, therefore produces a malformed request; the Kotatsu importer used to
 * write exactly that, so stored urls have to be compared in path form.
 */
internal fun mangaUrlPath(url: String): String {
    val cleaned = url.trim().substringBefore('#').substringBefore('?')
    if (cleaned.isEmpty()) return ""

    val path = if (cleaned.contains("://")) {
        val afterScheme = cleaned.substringAfter("://")
        val start = afterScheme.indexOf('/')
        if (start < 0) "" else afterScheme.substring(start)
    } else {
        cleaned
    }

    val trimmed = path.trim('/')
    return if (trimmed.isEmpty()) "" else "/$trimmed"
}
