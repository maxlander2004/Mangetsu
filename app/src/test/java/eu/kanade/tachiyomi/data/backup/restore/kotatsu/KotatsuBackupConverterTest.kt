package eu.kanade.tachiyomi.data.backup.restore.kotatsu

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.extension.model.Extension
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The url a Kotatsu import writes has to be the path `baseUrl + manga.url` expects: an absolute
 * url, a path carrying a query, or a url of a source that lives under a base path all produced a
 * malformed request and left the restored entry with no details and no chapters.
 */
class KotatsuBackupConverterTest {

    @Test
    fun `absolute url is reduced to its path`() {
        assertEquals(
            "/series/a-grimoire-changed-my-fate",
            restoredUrl("https://vortexscans.org/series/a-grimoire-changed-my-fate"),
        )
    }

    @Test
    fun `trailing slash is not kept`() {
        assertEquals(
            "/series/a-grimoire-changed-my-fate",
            restoredUrl("https://vortexscans.org/series/a-grimoire-changed-my-fate/"),
        )
    }

    @Test
    fun `query string and fragment are dropped`() {
        assertEquals("/series/a-grimoire-changed-my-fate", restoredUrl("series/a-grimoire-changed-my-fate?page=2"))
        assertEquals("/series/a-grimoire-changed-my-fate", restoredUrl("https://vortexscans.org/series/a-grimoire-changed-my-fate?utm_source=kotatsu#chapters"))
    }

    @Test
    fun `path without a leading slash gets exactly one`() {
        assertEquals("/series/a-grimoire-changed-my-fate", restoredUrl("series/a-grimoire-changed-my-fate"))
        assertEquals("/series/a-grimoire-changed-my-fate", restoredUrl("///series/a-grimoire-changed-my-fate"))
    }

    @Test
    fun `source base path is stripped from the path`() {
        assertEquals(
            "/series/a-grimoire-changed-my-fate",
            restoredUrl("https://example.org/manga/series/a-grimoire-changed-my-fate", baseUrl = "https://example.org/manga"),
        )
        assertEquals(
            "/series/a-grimoire-changed-my-fate",
            restoredUrl("manga/series/a-grimoire-changed-my-fate", baseUrl = "https://example.org/manga/"),
        )
    }

    @Test
    fun `blank url falls back to the public url`() {
        assertEquals(
            "/series/a-grimoire-changed-my-fate",
            restoredUrl(url = "", publicUrl = "https://vortexscans.org/series/a-grimoire-changed-my-fate"),
        )
    }

    @Test
    fun `url with no path at all falls back to the public url`() {
        assertEquals(
            "/series/a-grimoire-changed-my-fate",
            restoredUrl(url = "https://vortexscans.org", publicUrl = "https://vortexscans.org/series/a-grimoire-changed-my-fate"),
        )
    }

    @Test
    fun `an entry with no usable url is reported instead of stored`() {
        val backup = converted(url = "", publicUrl = "")

        assertTrue(backup.backupManga.isEmpty())
        assertEquals(1, backup.unmatchedEntries.size)
        assertTrue(backup.unmatchedEntries.single().contains("A Grimoire Changed My Fate"))
        assertTrue(backup.unmatchedEntries.single().contains("no usable url"))
    }

    /** Runs a one-entry Kotatsu archive through [KotatsuBackupConverter.convert] and returns its url. */
    private fun restoredUrl(
        url: String,
        publicUrl: String = "",
        baseUrl: String = "https://vortexscans.org",
    ): String? = converted(url, publicUrl, baseUrl).backupManga.single().url

    private fun converted(
        url: String,
        publicUrl: String = "",
        baseUrl: String = "https://vortexscans.org",
    ): Backup {
        val favourites = """
            [
              {
                "manga_id": 1,
                "category_id": 0,
                "created_at": 1700000000,
                "manga": {
                  "id": 1,
                  "title": "A Grimoire Changed My Fate",
                  "url": "$url",
                  "public_url": "$publicUrl",
                  "source": "VortexScansParser"
                }
              }
            ]
        """.trimIndent()

        return KotatsuBackupConverter(resolver(baseUrl)).convert(archive(favourites))
    }

    private fun resolver(baseUrl: String): KotatsuSourceResolver {
        return KotatsuSourceResolver(
            installedSources = emptyList(),
            availableExtensions = listOf(
                Extension.Available(
                    name = "Vortex Scans",
                    pkgName = "eu.kanade.tachiyomi.extension.en.vortexscans",
                    versionName = "1.4.0",
                    versionCode = 1,
                    libVersion = 1.4,
                    lang = "en",
                    isNsfw = false,
                    apkName = "vortexscans.apk",
                    iconUrl = "",
                    sources = listOf(
                        Extension.AvailableSource(
                            name = "Vortex Scans",
                            id = 42,
                            lang = "en",
                            baseUrl = baseUrl,
                        ),
                    ),
                ),
            ),
        )
    }

    private fun archive(favourites: String): InputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("favourites"))
            zip.write(favourites.toByteArray())
            zip.closeEntry()
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }
}
