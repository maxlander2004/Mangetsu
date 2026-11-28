package eu.kanade.tachiyomi.util.manga

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MangaUrlTest {

    @Test
    fun `an absolute url becomes its path`() {
        assertEquals("/series/x", mangaUrlPath("https://vortexscans.org/series/x"))
    }

    @Test
    fun `query and fragment are dropped`() {
        assertEquals("/series/x", mangaUrlPath("https://vortexscans.org/series/x?page=2#chapters"))
        assertEquals("/series/x", mangaUrlPath("series/x?page=2"))
    }

    @Test
    fun `slashes are normalised and a url without a path normalises to nothing`() {
        assertEquals("/series/x", mangaUrlPath("///series/x/"))
        assertEquals("", mangaUrlPath("https://vortexscans.org"))
        assertEquals("", mangaUrlPath(""))
    }

    @Test
    fun `an entry a previous build wrote has the same path the importer writes`() {
        assertEquals(mangaUrlPath("/series/x"), mangaUrlPath("https://vortexscans.org/series/x"))
        assertEquals(mangaUrlPath("/series/x"), mangaUrlPath("https://vortexscans.org/series/x/"))
    }
}
