package eu.kanade.tachiyomi.network

import android.content.Context
import android.webkit.WebSettings
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The HTTP client every source builds on. It shares cookies with WebView (so a challenge solved in a
 * WebView carries over), caches responses, and clears Cloudflare checks on its own when it can.
 */
class NetworkHelper(context: Context) {

    private val app = context.applicationContext

    val cookieJar = AndroidCookieJar()

    /** The WebView's own User-Agent minus the WebView marker, so requests look like Chrome on this phone. */
    val defaultUserAgent: String by lazy {
        runCatching { WebSettings.getDefaultUserAgent(app) }.getOrNull()
            ?.replace("; wv)", ")")
            ?.replace(Regex("""Version/\d+\.\d+ """), "")
            ?: FALLBACK_USER_AGENT
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .cache(Cache(File(app.cacheDir, "network_cache"), 64L * 1024 * 1024))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        .addInterceptor(UncaughtExceptionInterceptor())
        .addInterceptor(UserAgentInterceptor { defaultUserAgent })
        .addInterceptor(CloudflareInterceptor(app, cookieJar) { defaultUserAgent })
        .build()

    companion object {
        const val FALLBACK_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"
    }
}
