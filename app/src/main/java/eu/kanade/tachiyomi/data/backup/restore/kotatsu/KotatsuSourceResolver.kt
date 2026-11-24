package eu.kanade.tachiyomi.data.backup.restore.kotatsu

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.online.HttpSource
import java.net.URI

/**
 * Maps a Kotatsu parser/source onto a source available here.
 *
 * Kotatsu stores its own parser name (e.g. `MangaDexParser`) and the manga URL, while the same
 * content is served by a Keiyoushi extension with a different name and id, so matching is done on
 * the normalised name first and then on the base URL host. Candidates come from the installed
 * extensions and from the cached extension index, so a source that exists in a repository but is
 * not installed yet can still be recognised.
 */
class KotatsuSourceResolver(
    installedSources: List<CatalogueSource>,
    availableExtensions: List<Extension.Available> = emptyList(),
) {

    data class Candidate(
        val id: Long,
        val name: String,
        val baseUrl: String?,
        val installed: Boolean,
    )

    private val installedIds = installedSources.map { it.id }.toSet()

    private val candidates: List<Candidate> = buildList {
        installedSources.forEach { source ->
            add(
                Candidate(
                    id = source.id,
                    name = source.name,
                    baseUrl = (source as? HttpSource)?.baseUrl,
                    installed = true,
                ),
            )
        }
        availableExtensions
            .flatMap { it.sources }
            .filterNot { it.id in installedIds }
            .forEach { source ->
                add(
                    Candidate(
                        id = source.id,
                        name = source.name,
                        baseUrl = source.baseUrl,
                        installed = false,
                    ),
                )
            }
    }

    private val byName: Map<String, Candidate> = candidates
        .sortedByDescending { it.installed }
        .associateBy { keyOf(it.name) }

    private val byHost: Map<String, Candidate> = candidates
        .mapNotNull { candidate -> candidate.baseUrl?.let(::hostOf)?.let { it to candidate } }
        .sortedBy { it.second.installed }
        .toMap()

    fun resolve(manga: KotatsuManga): Candidate? {
        val parserKey = keyOf(manga.source)

        byName[parserKey]?.let { return it }

        // "MangaDex" vs "MangaDex (unoriginal)": accept a contained key when it is specific enough.
        if (parserKey.length >= 4) {
            candidates
                .sortedByDescending { it.installed }
                .firstOrNull { candidate ->
                    val nameKey = keyOf(candidate.name)
                    nameKey == parserKey || nameKey.contains(parserKey) || parserKey.contains(nameKey)
                }
                ?.let { return it }
        }

        val mangaHost = hostOf(manga.url).takeIf { it.isNotBlank() }
            ?: hostOf(manga.publicUrl).takeIf { it.isNotBlank() }

        if (mangaHost != null) {
            byHost[mangaHost]?.let { return it }
            byHost.entries
                .firstOrNull { (host, _) -> host.endsWith(".$mangaHost") || mangaHost.endsWith(".$host") }
                ?.let { return it.value }
        }

        if (parserKey.length >= 4) {
            candidates
                .sortedByDescending { it.installed }
                .firstOrNull { candidate ->
                    val host = candidate.baseUrl?.let(::hostOf).orEmpty()
                    host.isNotEmpty() && (host.contains(parserKey) || keyOf(host).contains(parserKey))
                }
                ?.let { return it }
        }

        return null
    }

    private fun keyOf(raw: String): String = raw
        .lowercase()
        .removeSuffix("parser")
        .filter { it.isLetterOrDigit() }

    private fun hostOf(url: String): String {
        if (url.isBlank()) return ""
        return runCatching {
            val normalised = if (url.contains("://")) url else "https://$url"
            URI(normalised).host?.removePrefix("www.")?.lowercase().orEmpty()
        }.getOrDefault("")
    }
}
