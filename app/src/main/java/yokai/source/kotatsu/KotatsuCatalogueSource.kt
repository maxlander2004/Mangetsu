package yokai.source.kotatsu

import android.util.LruCache
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.ParserMangaSource
import org.koitharu.kotatsu.parsers.model.SortOrder

/**
 * A Kotatsu-Redo parser presented as a source, exactly like an installed extension: it shows up in
 * the same source list, is browsed and searched by the same screens, and its chapters are read by
 * the same reader. Nothing in the UI knows which engine a source came from.
 */
class KotatsuCatalogueSource(
    private val parserSource: ParserMangaSource,
    private val loaderContext: MangaLoaderContext,
) : CatalogueSource {

    private val parser: MangaParser by lazy { loaderContext.newParserInstance(parserSource) }

    override val id: Long = stableId("kotatsu/${parserSource.name}")

    override val name: String = parserSource.title

    override val lang: String = parserSource.locale.ifBlank { "all" }

    override val supportsLatest: Boolean
        get() = runCatching { SortOrder.UPDATED in parser.availableSortOrders }.getOrDefault(false)

    /**
     * Kotatsu pages the list by offset and remembers how a page maps once it has seen its size.
     * These keep that mapping for the page index the app asks for, so paging stays sequential
     * even for parsers that return fewer items than they claim.
     */
    private val offsets = ConcurrentHashMap<String, Int>()

    private val mangaCache = LruCache<String, Manga>(CACHE_SIZE)

    private val chapterCache = LruCache<String, MangaChapter>(CACHE_SIZE)

    override suspend fun getPopularManga(page: Int): MangasPage =
        fetchList(page, "popular", popularOrder(), MangaListFilter.EMPTY)

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        fetchList(page, "latest", latestOrder(), MangaListFilter.EMPTY)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        fetchList(
            page,
            "search/$query",
            searchOrder(),
            MangaListFilter(query = query.takeIf { it.isNotBlank() }),
        )

    override suspend fun getMangaDetails(manga: SManga): SManga {
        val details = parser.getDetails(manga.toManga())
        mangaCache.put(details.url, details)
        return details.toSManga()
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        val details = parser.getDetails(manga.toManga())
        mangaCache.put(details.url, details)
        return details.chapters.orEmpty().map { chapter ->
            chapterCache.put(chapter.url, chapter)
            chapter.toSChapter()
        }
    }

    override fun getFilterList(): FilterList = FilterList()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pages = parser.getPages(chapter.toMangaChapter())
        return pages.mapIndexed { index, page ->
            val imageUrl = runCatching { parser.getPageUrl(page) }.getOrDefault(page.url)
            Page(index = index, url = page.url, imageUrl = imageUrl)
        }
    }

    private suspend fun fetchList(
        page: Int,
        key: String,
        order: SortOrder,
        filter: MangaListFilter,
    ): MangasPage {
        val offset = if (page <= 1) 0 else offsets[key] ?: ((page - 1) * DEFAULT_PAGE_SIZE)
        val result = parser.getList(offset, order, filter)
        offsets[key] = offset + result.size
        result.forEach { mangaCache.put(it.url, it) }
        return MangasPage(result.map { it.toSManga() }, hasNextPage = result.isNotEmpty())
    }

    private fun popularOrder(): SortOrder =
        SortOrder.POPULARITY.takeIf { it in parser.availableSortOrders } ?: firstOrder()

    private fun latestOrder(): SortOrder =
        SortOrder.UPDATED.takeIf { it in parser.availableSortOrders } ?: firstOrder()

    private fun searchOrder(): SortOrder =
        SortOrder.RELEVANCE.takeIf { it in parser.availableSortOrders } ?: firstOrder()

    private fun firstOrder(): SortOrder = parser.availableSortOrders.firstOrNull() ?: SortOrder.POPULARITY

    private fun SManga.toManga(): Manga = mangaCache[url] ?: Manga(
        id = stableId(url),
        title = title,
        altTitles = emptySet(),
        url = url,
        publicUrl = url,
        rating = 0f,
        contentRating = null,
        coverUrl = thumbnail_url,
        tags = emptySet(),
        state = null,
        authors = emptySet(),
        source = parserSource,
    )

    private fun SChapter.toMangaChapter(): MangaChapter = chapterCache[url] ?: MangaChapter(
        id = stableId(url),
        title = name,
        number = chapter_number,
        volume = 0,
        url = url,
        scanlator = scanlator,
        uploadDate = date_upload,
        branch = null,
        source = parserSource,
    )

    private fun Manga.toSManga(): SManga = SManga.create().apply {
        url = this@toSManga.url
        title = this@toSManga.title
        thumbnail_url = this@toSManga.coverUrl ?: this@toSManga.largeCoverUrl
        author = this@toSManga.authors.joinToString(", ").ifBlank { null }
        artist = this@toSManga.authors.firstOrNull()
        genre = this@toSManga.tags.joinToString(", ") { it.title }.ifBlank { null }
        description = this@toSManga.description
        status = when (this@toSManga.state) {
            MangaState.ONGOING -> SManga.ONGOING
            MangaState.FINISHED -> SManga.COMPLETED
            MangaState.ABANDONED -> SManga.CANCELLED
            MangaState.PAUSED -> SManga.ON_HIATUS
            MangaState.UPCOMING -> SManga.PUBLISHING_FINISHED
            MangaState.RESTRICTED -> SManga.LICENSED
            null -> SManga.UNKNOWN
        }
        initialized = true
    }

    private fun MangaChapter.toSChapter(): SChapter = SChapter.create().apply {
        url = this@toSChapter.url
        name = this@toSChapter.title?.takeIf { it.isNotBlank() }
            ?: this@toSChapter.numberString()
            ?: "Chapter"
        chapter_number = this@toSChapter.number
        scanlator = this@toSChapter.scanlator
        date_upload = this@toSChapter.uploadDate
    }

    override fun toString(): String = name

    private companion object {
        const val CACHE_SIZE = 300

        /** Only used when a page is skipped to; sequential paging learns the real size. */
        const val DEFAULT_PAGE_SIZE = 30

        fun stableId(key: String): Long {
            val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
            var result = 0L
            for (index in 0..7) {
                result = (result shl 8) or (bytes[index].toLong() and 0xFF)
            }
            return result and Long.MAX_VALUE
        }
    }
}

/** Novels have no pages to read, so only image content is offered. */
internal val ParserMangaSource.hasPages: Boolean
    get() = contentType != ContentType.NOVEL
