package eu.kanade.tachiyomi.data.library

import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MangaDetailsUpdateTest {

    @Test
    fun `details from the source are written onto the entry`() {
        val network = SManga.create().apply {
            title = "A Grimoire Changed My Fate"
            author = "Someone"
            artist = "Someone Else"
            description = "A description"
            genre = "Action, Fantasy"
            status = SManga.ONGOING
            thumbnail_url = "https://vortexscans.org/covers/1.jpg"
            update_strategy = UpdateStrategy.ALWAYS_UPDATE
            initialized = true
        }
        val manga = MangaImpl(source = 42, url = "/series/a-grimoire-changed-my-fate").apply {
            id = 7
            favorite = false
            ogTitle = "A Grimoire Changed My Fate"
        }

        val update = manga.applyFetchedDetails(network)

        assertEquals("A description", update.description)
        assertEquals(listOf("Action", "Fantasy"), update.genres)
        assertEquals(SManga.ONGOING, update.status)
        assertEquals("https://vortexscans.org/covers/1.jpg", update.thumbnailUrl)
        assertEquals(true, update.initialized)
        assertEquals("/series/a-grimoire-changed-my-fate", update.url)
        assertEquals(42L, update.source)
        assertEquals(7L, update.id)
    }

    @Test
    fun `a source that returns nothing does not wipe the stored entry`() {
        val manga = MangaImpl(source = 1, url = "/series/x").apply {
            id = 1
            favorite = false
            ogTitle = "A Grimoire Changed My Fate"
            description = "stored description"
            author = "stored author"
            ogGenre = "Drama"
        }

        val update = manga.applyFetchedDetails(SManga.create())

        assertEquals("stored description", update.description)
        assertEquals("stored author", update.author)
        assertEquals(listOf("Drama"), update.genres)
        assertEquals(true, update.initialized)
    }
}
