package eu.kanade.tachiyomi.data.library

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MangaIdBatchesTest {

    @Test
    fun `an update without ids queues nothing extra`() {
        assertTrue(mangaIdBatches(emptyList()).isEmpty())
    }

    @Test
    fun `ids that fit stay in one request`() {
        val batches = mangaIdBatches(listOf(3, 1, 2))

        assertEquals(1, batches.size)
        assertArrayEquals(longArrayOf(3, 1, 2), batches.single())
    }

    @Test
    fun `a large import is split into ordered batches that fit the work data`() {
        val ids = (1L..2_001L).toList()

        val batches = mangaIdBatches(ids)

        assertEquals(3, batches.size)
        assertEquals(ids, batches.flatMap { it.toList() })
        assertTrue(batches.all { it.size <= MAX_MANGA_IDS_PER_REQUEST })
        // A Long takes 8 bytes and WorkManager caps a request's data at 10 KB.
        assertTrue(MAX_MANGA_IDS_PER_REQUEST * 8 <= 10 * 1024)
    }
}
