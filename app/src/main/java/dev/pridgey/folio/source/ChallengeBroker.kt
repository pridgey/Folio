package dev.pridgey.folio.source

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * When a site wants a human to pass a check (Cloudflare and friends), the network layer asks here and
 * waits. The app shows [current] as a sheet with the page in a WebView; once the check is passed the
 * waiting request carries on by itself.
 */
object ChallengeBroker {

    class Challenge(val url: String, val userAgent: String, internal val isSolved: () -> Boolean) {
        internal val cancelled = AtomicBoolean(false)
        val host: String get() = runCatching { java.net.URI(url).host }.getOrNull() ?: url
        fun solvedNow() = isSolved()
    }

    private val _current = MutableStateFlow<Challenge?>(null)
    val current: StateFlow<Challenge?> = _current.asStateFlow()

    private val lock = Any()

    private val inForeground: Boolean
        get() = runCatching { ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }.getOrDefault(false)

    /** Blocks (on a network thread) until the user passes the check, dismisses it, or two minutes pass. */
    fun solveInteractively(url: String, userAgent: String, isSolved: () -> Boolean): Boolean {
        if (!inForeground) return false
        synchronized(lock) {
            if (isSolved()) return true
            val challenge = Challenge(url, userAgent, isSolved)
            _current.value = challenge
            try {
                val deadline = System.currentTimeMillis() + 120_000
                while (System.currentTimeMillis() < deadline && !challenge.cancelled.get()) {
                    if (isSolved()) return true
                    Thread.sleep(400)
                }
                return isSolved()
            } finally {
                _current.compareAndSet(challenge, null)
            }
        }
    }

    fun dismiss(challenge: Challenge) {
        challenge.cancelled.set(true)
        _current.compareAndSet(challenge, null)
    }
}
