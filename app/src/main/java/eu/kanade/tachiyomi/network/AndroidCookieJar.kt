package eu.kanade.tachiyomi.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Keeps OkHttp's cookies in WebView's cookie store, so both see the same session. */
class AndroidCookieJar : CookieJar {

    private val manager: CookieManager by lazy { CookieManager.getInstance() }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val key = url.toString()
        cookies.forEach { manager.setCookie(key, it.toString()) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = get(url)

    fun get(url: HttpUrl): List<Cookie> {
        val raw = runCatching { manager.getCookie(url.toString()) }.getOrNull() ?: return emptyList()
        return raw.split(";").mapNotNull { Cookie.parse(url, it.trim()) }
    }

    fun remove(url: HttpUrl, names: List<String>? = null) {
        val domain = url.host
        manager.getCookie(url.toString())?.split(";")
            ?.map { it.substringBefore("=").trim() }
            ?.filter { names == null || it in names }
            ?.forEach { manager.setCookie(url.toString(), "$it=;Max-Age=-1;Domain=$domain") }
    }

    fun clear() = manager.removeAllCookies(null)
}
