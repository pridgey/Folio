package dev.pridgey.folio.source

import dev.pridgey.folio.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.time.OffsetDateTime

/**
 * MangaDex, built in. It talks to MangaDex's official public API, so it keeps working when scraped
 * sites don't. That makes it Folio's fallback.
 */
class MangaDexSource(baseClient: OkHttpClient, private val json: Json, private val language: () -> String) : FolioSource {

    override val id = ID
    override val name = "MangaDex"
    override val lang: String get() = language()
    override val supportsLatest = true
    override val homeUrl = "https://mangadex.org"

    private val userAgent = "Folio/${BuildConfig.VERSION_NAME} (Android)"
    override val imageHeaders: Headers = Headers.Builder().add("User-Agent", userAgent).add("Referer", "https://mangadex.org/").build()

    private val client = baseClient.newBuilder()
        .addInterceptor(Interceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
        })
        .addInterceptor(RateLimit(permitsPerSecond = 4, host = API_HOST))
        .build()

    // ------------------------------------------------------------------ Catalog

    override suspend fun popular(page: Int) = list(page) { addQueryParameter("order[followedCount]", "desc") }

    override suspend fun latest(page: Int) = list(page) { addQueryParameter("order[latestUploadedChapter]", "desc") }

    override suspend fun search(page: Int, query: String, filters: FilterList): MangasPage {
        uuid(query)?.let { id ->
            return MangasPage(listOf(details(id)), false)
        }
        return list(page) {
            if (query.isNotBlank()) addQueryParameter("title", query.trim())
            var sorted = false
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> filter.state?.let {
                        sorted = true
                        addQueryParameter("order[${SortFilter.KEYS[it.index]}]", if (it.ascending) "asc" else "desc")
                    }
                    is StatusFilter -> filter.state.filter { it.state }.forEach { addQueryParameter("status[]", it.key) }
                    is DemographicFilter -> filter.state.filter { it.state }.forEach { addQueryParameter("publicationDemographic[]", it.key) }
                    is RatingFilter -> filter.state.filter { it.state }.forEach { addQueryParameter("contentRating[]", it.key) }
                    else -> Unit
                }
            }
            if (!sorted && query.isNotBlank()) addQueryParameter("order[relevance]", "desc")
        }
    }

    // ------------------------------------------------------------------ Discovery: tags and people

    /** A MangaDex tag. [group] is genre, theme, format or content. */
    data class TagInfo(val id: String, val name: String, val group: String)

    data class Person(val id: String, val name: String)

    @Volatile private var tagCache: List<TagInfo>? = null

    /** Every tag MangaDex knows, fetched once per run. */
    suspend fun tags(): List<TagInfo> {
        tagCache?.let { return it }
        val body = get<TagList>("$API/manga/tag".toHttpUrl())
        return body.data.mapNotNull { t ->
            val name = t.attributes.name["en"] ?: t.attributes.name.values.firstOrNull() ?: return@mapNotNull null
            TagInfo(t.id, name, t.attributes.group)
        }.sortedBy { it.name }.also { tagCache = it }
    }

    /** Matches tag names from any source ("Action", "slice of life") to MangaDex tags. */
    suspend fun tagsNamed(names: Collection<String>): List<TagInfo> {
        val wanted = names.map { norm(it) }.toSet()
        return tags().filter { norm(it.name) in wanted }
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

    /** Titles carrying every one of [include], most followed first. */
    suspend fun byTags(include: List<String>, page: Int, exclude: List<String> = emptyList()): MangasPage = list(page) {
        include.forEach { addQueryParameter("includedTags[]", it) }
        exclude.forEach { addQueryParameter("excludedTags[]", it) }
        addQueryParameter("includedTagsMode", "AND")
        addQueryParameter("order[followedCount]", "desc")
    }

    /** Authors and artists whose name matches [name]. */
    suspend fun people(name: String): List<Person> {
        if (name.isBlank()) return emptyList()
        val url = "$API/author".toHttpUrl().newBuilder()
            .addQueryParameter("name", name.trim())
            .addQueryParameter("limit", "20")
            .build()
        return get<PersonList>(url).data.mapNotNull { p -> p.attributes.name?.let { Person(p.id, it) } }
    }

    /** Everything an author or artist worked on, most followed first. */
    suspend fun byPerson(personId: String, page: Int): MangasPage = list(page) {
        addQueryParameter("authorOrArtist", personId)
        addQueryParameter("order[followedCount]", "desc")
    }

    @Serializable private class TagList(val data: List<TagData> = emptyList())

    @Serializable private class TagData(val id: String, val attributes: TagDataAttributes)

    @Serializable private class TagDataAttributes(val name: Map<String, String> = emptyMap(), val group: String = "genre")

    @Serializable private class PersonList(val data: List<PersonData> = emptyList())

    @Serializable private class PersonData(val id: String, val attributes: PersonAttributes)

    @Serializable private class PersonAttributes(val name: String? = null)

    private suspend fun list(page: Int, configure: HttpUrl.Builder.() -> Unit): MangasPage {
        val limit = 30
        val url = "$API/manga".toHttpUrl().newBuilder().apply {
            addQueryParameter("limit", limit.toString())
            addQueryParameter("offset", ((page - 1) * limit).toString())
            addQueryParameter("includes[]", "cover_art")
            addQueryParameter("availableTranslatedLanguage[]", lang)
            addQueryParameter("hasAvailableChapters", "true")
            configure()
            if (build().queryParameterValues("contentRating[]").isEmpty()) {
                SAFE_RATINGS.forEach { addQueryParameter("contentRating[]", it) }
            }
        }.build()
        val body = get<MangaList>(url)
        val mangas = body.data.map { it.toSManga() }
        return MangasPage(mangas, body.offset + body.data.size < body.total)
    }

    override fun filters() = FilterList(
        SortFilter(),
        StatusFilter(),
        DemographicFilter(),
        RatingFilter(),
    )

    // ------------------------------------------------------------------ Title

    override suspend fun update(manga: SManga, chapters: List<SChapter>, details: Boolean, fetchChapters: Boolean): SMangaUpdate {
        val id = manga.url.substringAfterLast('/')
        val updated = if (details) details(id) else manga
        val list = if (fetchChapters) chapters(id) else chapters
        return SMangaUpdate(updated, list)
    }

    private suspend fun details(id: String): SManga {
        val url = "$API/manga/$id".toHttpUrl().newBuilder()
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .addQueryParameter("includes[]", "artist")
            .build()
        return get<MangaOne>(url).data.toSManga(full = true)
    }

    private suspend fun chapters(id: String): List<SChapter> {
        val out = mutableListOf<SChapter>()
        var offset = 0
        while (true) {
            val url = "$API/manga/$id/feed".toHttpUrl().newBuilder()
                .addQueryParameter("limit", "500")
                .addQueryParameter("offset", offset.toString())
                .addQueryParameter("translatedLanguage[]", lang)
                .addQueryParameter("order[volume]", "desc")
                .addQueryParameter("order[chapter]", "desc")
                .addQueryParameter("includes[]", "scanlation_group")
                .addQueryParameter("includeExternalUrl", "0")
                .addQueryParameter("includeEmptyPages", "0")
                .apply { ALL_RATINGS.forEach { addQueryParameter("contentRating[]", it) } }
                .build()
            val page = get<ChapterList>(url)
            page.data.forEach { c ->
                out += SChapter.create().apply {
                    this.url = "/chapter/${c.id}"
                    val a = c.attributes
                    name = buildString {
                        a.volume?.let { append("Vol. ").append(it).append(' ') }
                        a.chapter?.let { append("Ch. ").append(it) }
                        if (!a.title.isNullOrBlank()) {
                            if (isNotEmpty()) append(" – ")
                            append(a.title)
                        }
                        if (isEmpty()) append("Oneshot")
                    }
                    chapter_number = a.chapter?.toFloatOrNull() ?: -1f
                    scanlator = c.relationships.filter { it.type == "scanlation_group" }
                        .mapNotNull { it.attributes?.get("name")?.jsonPrimitive?.content }
                        .joinToString(" & ").ifEmpty { null }
                    date_upload = runCatching { OffsetDateTime.parse(a.publishAt).toInstant().toEpochMilli() }.getOrDefault(0)
                }
            }
            offset += page.data.size
            if (page.data.isEmpty() || offset >= page.total) break
        }
        return out
    }

    override suspend fun pages(chapter: SChapter): List<Page> {
        val id = chapter.url.substringAfterLast('/')
        val server = get<AtHome>("$API/at-home/server/$id".toHttpUrl())
        val base = "${server.baseUrl}/data/${server.chapter.hash}"
        return server.chapter.data.mapIndexed { i, file -> Page(i, imageUrl = "$base/$file") }
    }

    override suspend fun image(page: Page): Response =
        client.newCall(GET(page.imageUrl!!, imageHeaders)).awaitSuccess()

    override suspend fun cover(url: String): Response = client.newCall(GET(url, imageHeaders)).awaitSuccess()

    override fun webUrl(manga: SManga) = "https://mangadex.org" + manga.url

    override fun chapterWebUrl(chapter: SChapter) = "https://mangadex.org" + chapter.url

    // ------------------------------------------------------------------ JSON

    private suspend inline fun <reified T> get(url: HttpUrl): T =
        client.newCall(GET(url, imageHeaders)).awaitSuccess().use { json.decodeFromString<T>(it.body.string()) }

    @Serializable private class MangaList(val data: List<MangaData> = emptyList(), val offset: Int = 0, val total: Int = 0)

    @Serializable private class MangaOne(val data: MangaData)

    @Serializable
    private class MangaData(val id: String, val attributes: MangaAttributes, val relationships: List<Relationship> = emptyList())

    @Serializable
    private class MangaAttributes(
        val title: Map<String, String> = emptyMap(),
        val altTitles: List<Map<String, String>> = emptyList(),
        val description: Map<String, String> = emptyMap(),
        val status: String? = null,
        val publicationDemographic: String? = null,
        val originalLanguage: String? = null,
        val tags: List<Tag> = emptyList(),
    )

    @Serializable private class Tag(val attributes: TagAttributes)

    @Serializable private class TagAttributes(val name: Map<String, String> = emptyMap())

    @Serializable
    private class Relationship(val id: String, val type: String, val attributes: JsonObject? = null)

    @Serializable private class ChapterList(val data: List<ChapterData> = emptyList(), val total: Int = 0)

    @Serializable
    private class ChapterData(val id: String, val attributes: ChapterAttributes, val relationships: List<Relationship> = emptyList())

    @Serializable
    private class ChapterAttributes(
        val volume: String? = null,
        val chapter: String? = null,
        val title: String? = null,
        val publishAt: String? = null,
    )

    @Serializable private class AtHome(val baseUrl: String, val chapter: AtHomeChapter)

    @Serializable private class AtHomeChapter(val hash: String, val data: List<String>)

    private fun MangaData.toSManga(full: Boolean = false): SManga = SManga.create().also { m ->
        val a = attributes
        m.url = "/title/$id"
        m.title = a.title[lang] ?: a.title["en"]
            ?: a.altTitles.firstNotNullOfOrNull { it[lang] } ?: a.altTitles.firstNotNullOfOrNull { it["en"] }
            ?: a.title.values.firstOrNull() ?: "Untitled"
        relationships.firstOrNull { it.type == "cover_art" }?.attributes?.get("fileName")?.jsonPrimitive?.content?.let {
            m.thumbnail_url = "https://uploads.mangadex.org/covers/$id/$it.512.jpg"
        }
        m.author = relationships.filter { it.type == "author" }.names()
        m.artist = relationships.filter { it.type == "artist" }.names()
        m.description = (a.description[lang] ?: a.description["en"])?.let(::cleanDescription)
        m.status = when (a.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        val type = when (a.originalLanguage) {
            "ko" -> "Manhwa"
            "zh", "zh-hk" -> "Manhua"
            "ja" -> "Manga"
            else -> null
        }
        m.genre = (listOfNotNull(type, a.publicationDemographic?.replaceFirstChar { it.uppercase() }) +
            a.tags.mapNotNull { it.attributes.name["en"] }).joinToString(", ")
        m.initialized = full
    }

    private fun List<Relationship>.names() =
        mapNotNull { it.attributes?.get("name")?.jsonPrimitive?.content }.distinct().joinToString(", ").ifEmpty { null }

    /** MangaDex descriptions are markdown with a links footer; keep the prose. */
    private fun cleanDescription(text: String) = text
        .substringBefore("\n---")
        .replace(Regex("""\[([^\]]+)]\([^)]+\)"""), "$1")
        .replace(Regex("""[*_]{1,3}([^*_]+)[*_]{1,3}"""), "$1")
        .trim()

    private fun uuid(query: String): String? =
        Regex("""[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""").find(query.trim())?.value
            ?.takeIf { query.contains("mangadex") || query.trim().length == 36 }

    // ------------------------------------------------------------------ Filters

    class Option(name: String, val key: String) : Filter.CheckBox(name)

    class SortFilter : Filter.Sort("Sort", LABELS, Selection(0, false)) {
        companion object {
            val LABELS = arrayOf("Popularity", "Latest chapter", "Rating", "Recently added", "Title", "Relevance")
            val KEYS = arrayOf("followedCount", "latestUploadedChapter", "rating", "createdAt", "title", "relevance")
        }
    }

    class StatusFilter : Filter.Group<Option>(
        "Status",
        listOf(Option("Ongoing", "ongoing"), Option("Completed", "completed"), Option("Hiatus", "hiatus"), Option("Cancelled", "cancelled")),
    )

    class DemographicFilter : Filter.Group<Option>(
        "Demographic",
        listOf(Option("Shounen", "shounen"), Option("Shoujo", "shoujo"), Option("Seinen", "seinen"), Option("Josei", "josei")),
    )

    class RatingFilter : Filter.Group<Option>(
        "Content rating",
        listOf(Option("Safe", "safe"), Option("Suggestive", "suggestive"), Option("Erotica", "erotica")),
    )

    /** Spaces requests to one host out so MangaDex's rate limit is never hit. */
    private class RateLimit(permitsPerSecond: Int, private val host: String) : Interceptor {
        private val gap = 1000L / permitsPerSecond
        private var next = 0L

        override fun intercept(chain: Interceptor.Chain): Response {
            if (chain.request().url.host == host) {
                val wait = synchronized(this) {
                    val now = System.currentTimeMillis()
                    val at = maxOf(now, next)
                    next = at + gap
                    at - now
                }
                if (wait > 0) Thread.sleep(wait)
            }
            return chain.proceed(chain.request())
        }
    }

    companion object {
        /** Fixed, so library entries survive language changes. */
        const val ID = 7_313_045_190_117_000_001L
        private const val API_HOST = "api.mangadex.org"
        private const val API = "https://$API_HOST"
        private val SAFE_RATINGS = listOf("safe", "suggestive")
        private val ALL_RATINGS = listOf("safe", "suggestive", "erotica", "pornographic")
    }
}


internal fun JsonElement.stringOrNull() = runCatching { jsonPrimitive.content }.getOrNull()
