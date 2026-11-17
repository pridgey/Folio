package dev.pridgey.folio.source

import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.Response

/** Everything Folio needs from a place to read manga, whether it's an extension or built in. */
interface FolioSource {
    val id: Long
    val name: String
    val lang: String
    val supportsLatest: Boolean
    val homeUrl: String

    /** Set for extension sources: the package that provides it. */
    val packageName: String? get() = null

    /** Headers for loading covers (some sites check the Referer). */
    val imageHeaders: Headers

    fun filters(): FilterList
    suspend fun popular(page: Int): MangasPage
    suspend fun latest(page: Int): MangasPage
    suspend fun search(page: Int, query: String, filters: FilterList): MangasPage
    suspend fun update(manga: SManga, chapters: List<SChapter>, details: Boolean, fetchChapters: Boolean): SMangaUpdate
    suspend fun pages(chapter: SChapter): List<Page>
    suspend fun image(page: Page): Response
    fun webUrl(manga: SManga): String?
    fun chapterWebUrl(chapter: SChapter): String?

    /** Loads a cover through the source's client (cookies, Cloudflare clearance, rate limits). */
    suspend fun cover(url: String): Response

    /** The extension's own settings, if it has any. */
    val configurable: ConfigurableSource? get() = null
}

/** Adapts an extension's [HttpSource]. */
class ExtensionSource(val source: HttpSource, override val packageName: String) : FolioSource {
    override val id get() = source.id
    override val name: String get() = source.name
    override val lang: String get() = source.lang
    override val supportsLatest get() = source.supportsLatest
    override val homeUrl: String get() = source.getHomeUrl()
    override val imageHeaders: Headers get() = source.headers
    override val configurable get() = source as? ConfigurableSource

    override fun filters() = source.getFilterList()
    override suspend fun popular(page: Int) = source.getPopularManga(page)
    override suspend fun latest(page: Int) = source.getLatestUpdates(page)
    override suspend fun search(page: Int, query: String, filters: FilterList) = source.getSearchManga(page, query, filters)
    override suspend fun update(manga: SManga, chapters: List<SChapter>, details: Boolean, fetchChapters: Boolean) =
        source.getMangaUpdate(manga, chapters, details, fetchChapters)

    override suspend fun pages(chapter: SChapter) = source.getPageList(chapter)
    override suspend fun image(page: Page) = source.fetchImage(page)
    override fun webUrl(manga: SManga) = runCatching { source.getMangaUrl(manga) }.getOrNull()
    override fun chapterWebUrl(chapter: SChapter) = runCatching { source.getChapterUrl(chapter) }.getOrNull()

    override suspend fun cover(url: String): Response = source.fetchImage(Page(0, imageUrl = url))
}
