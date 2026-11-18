package dev.pridgey.folio.download

import dev.pridgey.folio.Graph
import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Spaces out download requests per source. Starts at the pace chosen in settings, doubles the gap when
 * a site pushes back (rate limits, Cloudflare), and eases back down as requests succeed.
 */
object Pacer {
    private const val MAX_GAP = 8_000L

    /** Extra slowdown per source, on top of the chosen pace, in milliseconds. */
    private val _backoff = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val backoff: StateFlow<Map<Long, Long>> = _backoff.asStateFlow()

    fun gap(sourceId: Long): Long = Graph.settings.current.downloadPace.gapMs + (_backoff.value[sourceId] ?: 0)

    suspend fun before(sourceId: Long) {
        val g = gap(sourceId)
        if (g > 0) delay(g)
    }

    fun ok(sourceId: Long) = _backoff.update { m ->
        val b = m[sourceId] ?: return@update m
        val next = b * 3 / 4
        if (next < 100) m - sourceId else m + (sourceId to next)
    }

    fun pushBack(sourceId: Long) = _backoff.update { m ->
        val b = m[sourceId] ?: 0
        m + (sourceId to (if (b == 0L) 1_000L else b * 2).coerceAtMost(MAX_GAP))
    }

    /** True when an error means "slow down" rather than "this is broken". */
    fun isPushBack(e: Throwable): Boolean = when {
        e is HttpException -> e.code == 429 || e.code == 503 || e.code == 403
        e.message?.contains("cloudflare", ignoreCase = true) == true -> true
        e.message?.contains("too many", ignoreCase = true) == true -> true
        else -> false
    }

    /** Runs [block], retrying with a longer gap when the site pushes back. */
    suspend fun <T> paced(sourceId: Long, block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            before(sourceId)
            try {
                return block().also { ok(sourceId) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (!isPushBack(e) || ++attempt >= 4) throw e
                pushBack(sourceId)
            }
        }
    }
}
