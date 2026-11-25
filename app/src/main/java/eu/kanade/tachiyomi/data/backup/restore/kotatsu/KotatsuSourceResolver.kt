package eu.kanade.tachiyomi.data.backup.restore.kotatsu

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.online.HttpSource
import java.net.URI

/**
 * Maps a Kotatsu parser onto a source available here.
 *
 * Kotatsu stores its own parser name (e.g. `MangaDexParser`) and the manga URL, while the same
 * content is served by a Keiyoushi extension with a different name and id, so candidates are
 * scored on the normalised name and on the base URL host. Candidates come from the installed
 * extensions and from the extension index, so a source that is known but not installed can still
 * be recognised.
 */
class KotatsuSourceResolver(
    installedSources: List<CatalogueSource>,
    availableExtensions: List<Extension.Available> = emptyList(),
) {

    data class Candidate(val id: Long, val name: String, val baseUrl: String?)

    private val installedIds: Set<Long> = installedSources.mapTo(HashSet()) { it.id }

    private val candidates: List<Candidate> =
        installedSources.map { Candidate(it.id, it.name, (it as? HttpSource)?.baseUrl) } +
            availableExtensions
                .flatMap { it.sources }
                .filterNot { it.id in installedIds }
                .map { Candidate(it.id, it.name, it.baseUrl) }

    fun resolve(manga: KotatsuManga): Candidate? {
        val parserKey = keyOf(manga.source)
        val hosts = listOf(manga.url, manga.publicUrl).map(::hostOf).filter { it.isNotEmpty() }

        return candidates
            .map { it to matchScore(it, parserKey, hosts) }
            .filter { (_, score) -> score > 0 }
            .maxWithOrNull(compareBy({ it.second }, { it.first.id in installedIds }))
            ?.first
    }

    /**
     * How likely [candidate] is the source [parserKey] and [hosts] describe, higher being better
     * and 0 meaning "not the same source". An installed source wins ties.
     */
    private fun matchScore(candidate: Candidate, parserKey: String, hosts: List<String>): Int {
        val nameKey = keyOf(candidate.name)
        val host = hostOf(candidate.baseUrl)
        val fuzzy = parserKey.length >= 4

        return when {
            nameKey.isNotEmpty() && nameKey == parserKey -> 100
            host.isNotEmpty() && hosts.any { it == host } -> 90
            host.isNotEmpty() && hosts.any { it.endsWith(".$host") || host.endsWith(".$it") } -> 80
            fuzzy && nameKey.contains(parserKey) -> 70
            fuzzy && nameKey.length >= 4 && parserKey.contains(nameKey) -> 70
            fuzzy && keyOf(host).contains(parserKey) -> 40
            else -> 0
        }
    }

    /** `MangaDexParser` and `MangaDex` both become `mangadex`. */
    private fun keyOf(raw: String): String = raw
        .lowercase()
        .removeSuffix("parser")
        .filter { it.isLetterOrDigit() }

    private fun hostOf(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return runCatching {
            val normalised = if (url.contains("://")) url else "https://$url"
            URI(normalised).host?.removePrefix("www.")?.lowercase().orEmpty()
        }.getOrDefault("")
    }
}
