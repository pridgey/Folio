package eu.kanade.tachiyomi.network

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Evaluates JavaScript for sources. Runs in a throwaway WebView, so it supports everything Chrome does;
 * the result comes back as a primitive (String, Int, Long, Double, Boolean) or null.
 */
@Suppress("Unused")
class JavaScriptEngine(context: Context) {

    private val app = context.applicationContext

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> evaluate(script: String): T {
        val result = CompletableDeferred<String?>()
        val main = Handler(Looper.getMainLooper())
        var webView: WebView? = null
        main.post {
            runCatching {
                webView = WebView(app).apply {
                    settings.javaScriptEnabled = true
                    evaluateJavascript("(function(){ return ($script); })()") { result.complete(it) }
                }
            }.onFailure { result.completeExceptionally(it) }
        }
        val raw = try {
            withTimeout(30_000) { result.await() }
        } finally {
            main.post { webView?.destroy() }
        }
        return decode(raw) as T
    }

    private fun decode(raw: String?): Any? {
        if (raw == null || raw == "null" || raw == "undefined") return null
        val element = runCatching { Json.parseToJsonElement(raw) }.getOrNull() ?: return raw
        if (element is JsonNull) return null
        if (element !is JsonPrimitive) return element.toString()
        if (element.isString) return element.content
        element.booleanOrNull?.let { return it }
        element.longOrNull?.let { return if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it }
        element.doubleOrNull?.let { return it }
        return element.content
    }
}
