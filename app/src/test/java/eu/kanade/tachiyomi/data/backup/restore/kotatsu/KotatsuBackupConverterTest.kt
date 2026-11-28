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

    @Test
    fun `millisecond timestamps are stored unchanged`() {
        val backup = converted(
            createdAt = 1_700_000_000_000,
            chapterUploadDates = listOf(1_699_000_000_000, 1_699_500_000_000),
            lastReadAt = 1_699_999_999_000,
        )

        val manga = backup.backupManga.single()
        assertEquals(1_700_000_000_000, manga.dateAdded)
        assertEquals(listOf(1_699_000_000_000, 1_699_500_000_000), manga.chapters.map { it.dateUpload })
        assertEquals(1_699_999_999_000, manga.history.single().lastRead)
    }

    @Test
    fun `second timestamps from older backups are scaled to milliseconds`() {
        val backup = converted(
            createdAt = 1_700_000_000,
            chapterUploadDates = listOf(1_699_000_000, 1_699_500_000),
            lastReadAt = 1_699_999_999,
        )

        val manga = backup.backupManga.single()
        assertEquals(1_700_000_000_000, manga.dateAdded)
        assertEquals(listOf(1_699_000_000_000, 1_699_500_000_000), manga.chapters.map { it.dateUpload })
        assertEquals(1_699_999_999_000, manga.history.single().lastRead)
    }

    @Test
    fun `a missing added date falls back to now instead of 1970`() {
        val before = System.currentTimeMillis()
        val backup = converted(createdAt = 0, chapterUploadDates = listOf(0), lastReadAt = 0)
        val after = System.currentTimeMillis()

        val manga = backup.backupManga.single()
        assertTrue(manga.dateAdded in before..after)
        // An unknown chapter date stays unknown; scaling 0 would make it 1970.
        assertEquals(0, manga.chapters.single().dateUpload)
    }

    /** Runs a one-entry Kotatsu archive through [KotatsuBackupConverter.convert] and returns its url. */
    private fun restoredUrl(
        url: String,
        publicUrl: String = "",
        baseUrl: String = "https://vortexscans.org",
    ): String? = converted(url, publicUrl, baseUrl).backupManga.single().url

    private fun converted(
        url: String = "/series/a-grimoire-changed-my-fate",
        publicUrl: String = "",
        baseUrl: String = "https://vortexscans.org",
        createdAt: Long = 1_700_000_000_000,
        chapterUploadDates: List<Long> = emptyList(),
        lastReadAt: Long = 0,
    ): Backup {
        val favourites = """
            [
              {
                "manga_id": 1,
                "category_id": 0,
                "created_at": $createdAt,
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

        val sections = mutableMapOf("favourites" to favourites)
        if (chapterUploadDates.isNotEmpty()) {
            val chapters = chapterUploadDates.mapIndexed { index, uploadDate ->
                val id = CHAPTER_ID + index
                "{ \"id\": $id, \"name\": \"Chapter ${index + 1}\", \"url\": \"/chapter/$id\", \"upload_date\": $uploadDate }"
            }
            sections["index"] = """[{ "manga_id": 1, "chapters": [${chapters.joinToString(",")}] }]"""
        }
        if (lastReadAt != 0L) {
            // History always points at the last chapter, so it is read and the ones before it too.
            sections["history"] = """
                [{ "manga_id": 1, "updated_at": $lastReadAt, "chapter_id": ${CHAPTER_ID + chapterUploadDates.lastIndex}, "page": 5 }]
            """.trimIndent()
        }

        return KotatsuBackupConverter(resolver(baseUrl)).convert(archive(sections))
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

    private fun archive(sections: Map<String, String>): InputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            sections.forEach { (name, json) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(json.toByteArray())
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }

    private companion object {
        const val CHAPTER_ID = 11L
    }
}
