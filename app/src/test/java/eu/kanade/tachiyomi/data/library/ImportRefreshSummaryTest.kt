package eu.kanade.tachiyomi.data.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImportRefreshSummaryTest {

    @Test
    fun `a fully refreshed import says so`() {
        assertEquals(
            "2/2 entries refreshed",
            importRefreshSummary(requested = listOf(1, 2), refreshed = listOf(1, 2), failed = emptyList()),
        )
    }

    @Test
    fun `failures are named`() {
        assertEquals(
            "1/2 entries refreshed, 1 failed: A Grimoire Changed My Fate",
            importRefreshSummary(
                requested = listOf(1, 2),
                refreshed = listOf(1),
                failed = listOf("A Grimoire Changed My Fate"),
            ),
        )
    }

    @Test
    fun `an entry counted twice is counted once`() {
        assertEquals(
            "2/2 entries refreshed",
            importRefreshSummary(requested = listOf(1, 1, 2), refreshed = listOf(1, 2, 2), failed = emptyList()),
        )
    }
}
