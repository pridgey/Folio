package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.pridgey.folio.source.ChallengeBroker
import eu.kanade.tachiyomi.network.AndroidCookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Gets past Cloudflare's browser check. The page is loaded in an invisible WebView with the same
 * User-Agent, which usually clears it on its own; when Cloudflare wants a tap, Folio shows the check to
 * the user ([ChallengeBroker]) and carries on once it's passed. The clearance cookie lands in the
 * shared cookie jar, so the retried request goes through.
 */
class CloudflareInterceptor(
    private val context: Context,
    private val cookieJar: AndroidCookieJar,
    private val defaultUserAgent: () -> String,
) : Interceptor {

    private val locks = ConcurrentHashMap<String, Any>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!isChallenge(response)) return response
        response.close()

        val url = request.url
        synchronized(locks.getOrPut(url.host) { Any() }) {
            // Another request may have cleared it while this one waited.
            val before = clearance(url)
            val retry = chain.proceed(request)
            if (!isChallenge(retry)) return retry
            retry.close()

            cookieJar.remove(url, listOf(CLEARANCE))
            val userAgent = request.header("User-Agent") ?: defaultUserAgent()
            val solved = solveHeadless(request, userAgent, before) ||
                ChallengeBroker.solveInteractively(url.toString(), userAgent) { clearance(url) != null && clearance(url) != before }
            if (!solved) throw IOException("${url.host} is checking that you're human. Open it in the browser to continue.")
        }
        return chain.proceed(request)
    }

    private fun clearance(url: HttpUrl) = cookieJar.get(url).firstOrNull { it.name == CLEARANCE }?.value

    @SuppressLint("SetJavaScriptEnabled")
    private fun solveHeadless(request: Request, userAgent: String, before: String?): Boolean {
        val latch = CountDownLatch(1)
        val main = Handler(Looper.getMainLooper())
        var webView: WebView? = null
        val url = request.url
        val headers = request.headers.toMultimap()
            .filterKeys { !it.equals("cookie", true) && !it.equals("user-agent", true) }
            .mapValues { it.value.last() }

        main.post {
            runCatching {
                webView = WebView(context).apply {
                    with(settings) {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        userAgentString = userAgent
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, pageUrl: String) {
                            val now = clearance(url)
                            if (now != null && now != before) latch.countDown()
                        }
                    }
                    loadUrl(url.toString(), headers)
                }
            }.onFailure { latch.countDown() }
        }

        // The cookie can arrive without a page finishing, so check it on a timer too.
        val deadline = System.currentTimeMillis() + HEADLESS_TIMEOUT_MS
        var solved = false
        while (System.currentTimeMillis() < deadline) {
            if (latch.await(500, TimeUnit.MILLISECONDS)) Unit
            val now = clearance(url)
            if (now != null && now != before) {
                solved = true
                break
            }
            if (latch.count == 0L && webView == null) break
        }
        main.post {
            webView?.stopLoading()
            webView?.destroy()
        }
        return solved
    }

    companion object {
        private const val CLEARANCE = "cf_clearance"
        private const val HEADLESS_TIMEOUT_MS = 20_000L
        private val CHALLENGE_CODES = setOf(403, 503)
        private val SERVERS = setOf("cloudflare-nginx", "cloudflare")

        fun isChallenge(response: Response): Boolean =
            response.code in CHALLENGE_CODES &&
                response.header("Server") in SERVERS &&
                (
                    response.header("cf-mitigated") == "challenge" ||
                        runCatching { response.peekBody(64 * 1024).string() }.getOrDefault("")
                            .let { "challenge-platform" in it || "cf-chl" in it || "Just a moment" in it }
                    )
    }
}
