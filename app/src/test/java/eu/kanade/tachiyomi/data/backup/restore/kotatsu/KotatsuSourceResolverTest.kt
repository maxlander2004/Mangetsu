package eu.kanade.tachiyomi.data.backup.restore.kotatsu

import eu.kanade.tachiyomi.extension.model.Extension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KotatsuSourceResolverTest {

    @Test
    fun `exact source name wins over a host match`() {
        val resolver = resolver(
            extension("MangaDex", 1, "https://mangadex.org"),
            extension("Vortex Scans", 2, "https://vortexscans.org"),
        )

        val match = resolver.resolveWithScore(
            manga(source = "MangaDexParser", url = "https://vortexscans.org/series/1"),
        )

        assertEquals("MangaDex", match?.candidate?.name)
        assertEquals(100, match?.score)
    }

    @Test
    fun `base url host matches when the parser name does not`() {
        val resolver = resolver(extension("Vortex Scans", 2, "https://vortexscans.org"))

        val match = resolver.resolveWithScore(
            manga(source = "UnknownParser", url = "https://vortexscans.org/series/1"),
        )

        assertEquals("Vortex Scans", match?.candidate?.name)
        assertEquals(90, match?.score)
    }

    @Test
    fun `subdomain of the base url host matches`() {
        val resolver = resolver(extension("Vortex Scans", 2, "https://vortexscans.org"))

        val match = resolver.resolveWithScore(
            manga(source = "UnknownParser", url = "https://m.vortexscans.org/series/1"),
        )

        assertEquals("Vortex Scans", match?.candidate?.name)
        assertEquals(80, match?.score)
    }

    @Test
    fun `a partial name still matches its source`() {
        val resolver = resolver(extension("Vortex", 2, "https://vortexscans.org"))

        val match = resolver.resolveWithScore(manga(source = "VortexScansParser", url = "/series/1"))

        assertEquals(70, match?.score)
    }

    @Test
    fun `an unrelated source resolves nothing`() {
        val resolver = resolver(extension("MangaDex", 1, "https://mangadex.org"))

        assertNull(resolver.resolve(manga(source = "SomethingElseParser", url = "https://example.org/series/1")))
    }

    private fun resolver(vararg extensions: Extension.Available): KotatsuSourceResolver {
        return KotatsuSourceResolver(
            installedSources = emptyList(),
            availableExtensions = extensions.toList(),
        )
    }

    private fun extension(name: String, id: Long, baseUrl: String): Extension.Available {
        return Extension.Available(
            name = name,
            pkgName = "eu.kanade.tachiyomi.extension.en.${name.lowercase().replace(" ", "")}",
            versionName = "1.0.0",
            versionCode = 1,
            libVersion = 1.4,
            lang = "en",
            isNsfw = false,
            apkName = "$name.apk",
            iconUrl = "",
            sources = listOf(
                Extension.AvailableSource(name = name, id = id, lang = "en", baseUrl = baseUrl),
            ),
        )
    }

    private fun manga(source: String, url: String = ""): KotatsuManga {
        return KotatsuManga(
            id = 1,
            title = "A Grimoire Changed My Fate",
            url = url,
            source = source,
        )
    }
}
