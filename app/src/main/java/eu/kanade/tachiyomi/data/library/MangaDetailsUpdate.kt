package eu.kanade.tachiyomi.data.library

import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.model.SManga
import yokai.domain.manga.models.MangaUpdate

/**
 * Applies a details fetch onto the stored manga and returns the row to persist.
 *
 * A converted Kotatsu archive holds no descriptions, authors or covers, and the library update
 * only asks its source for chapters, so the refresh that follows an import has to fetch details
 * too — otherwise every restored entry stays blank until it is opened.
 */
internal fun Manga.applyFetchedDetails(network: SManga): MangaUpdate {
    copyFrom(network)
    initialized = true
    return toMangaUpdate()
}
