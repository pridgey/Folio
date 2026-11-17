package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest

/** A source backed by a website (extension API 1.6). */
@Suppress("Unused", "MemberVisibilityCanBePrivate")
abstract class HttpSource : CatalogueSource {

    protected val network: NetworkHelper by lazy { Injekt.get<NetworkHelper>() }

    /** Base url of the site without a trailing slash. */
    abstract val baseUrl: String

    open fun getHomeUrl(): String = baseUrl

    open val versionId: Int = 1

    override val id: Long by lazy { generateId(name, lang, versionId) }

    // Extensions replace this delegate by reflection (the field must stay "headers$delegate").
    val headers: Headers by lazy { headersBuilder().build() }

    open val client: OkHttpClient get() = network.client

    protected fun generateId(name: String, lang: String, versionId: Int): Long {
        val key = "${name.lowercase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    protected open fun headersBuilder(): Headers.Builder = Headers.Builder().add("User-Agent", network.defaultUserAgent)

    override fun toString(): String = "$name (${lang.uppercase()})"

    /** Only called when [Page.imageUrl] is null. */
    open suspend fun getImageUrl(page: Page): String =
        throw UnsupportedOperationException("This source doesn't resolve image urls")

    protected open fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers)

    /** Folio: fetches a page's image the way the source wants it requested. */
    suspend fun fetchImage(page: Page): Response {
        if (page.imageUrl.isNullOrEmpty()) page.imageUrl = getImageUrl(page)
        return client.newCall(imageRequest(page)).awaitSuccess()
    }

    fun SChapter.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    fun SManga.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    private fun getUrlWithoutDomain(orig: String): String = try {
        val uri = URI(orig.replace(" ", "%20"))
        buildString {
            append(uri.path)
            uri.query?.let { append('?').append(it) }
            uri.fragment?.let { append('#').append(it) }
        }
    } catch (_: URISyntaxException) {
        orig
    }

    abstract fun getMangaUrl(manga: SManga): String

    abstract fun getChapterUrl(chapter: SChapter): String
}
