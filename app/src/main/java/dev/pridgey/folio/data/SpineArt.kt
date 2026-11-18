package dev.pridgey.folio.data

import android.content.Context
import dev.pridgey.folio.source.MangaDexSource
import dev.pridgey.folio.source.TitleMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Better art for the bookcase's spines. A source usually gives one thumbnail; MangaDex keeps a cover
 * for every volume. Each library title is matched on MangaDex by name once, in the background, and its
 * volume covers are remembered (or that there were none, rechecked after a week).
 */
class SpineArt(context: Context, private val dex: MangaDexSource, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("spine_art", Context.MODE_PRIVATE)
    private val _covers = MutableStateFlow(load())

    /** Volume covers by [key]; an empty list means MangaDex has none for that title. */
    val covers: StateFlow<Map<String, List<String>>> = _covers.asStateFlow()

    private val pending = HashSet<String>()
    // MangaDex asks clients to stay well under five requests a second.
    private val gate = Semaphore(2)

    /** Looks [title] up if it hasn't been, or if it found nothing over a week ago. */
    fun request(title: String) {
        val key = key(title)
        if (key.isEmpty()) return
        val known = prefs.getString(key, null)
        if (known != null) {
            val (at, urls) = parse(known)
            if (urls.isNotEmpty() || System.currentTimeMillis() - at < WEEK) return
        }
        synchronized(pending) { if (!pending.add(key)) return }
        scope.launch(Dispatchers.IO) {
            try {
                val urls = gate.withPermit {
                    val found = runCatching { find(title) }.getOrNull()
                    delay(500)
                    found
                } ?: return@launch // a failed lookup isn't remembered, so it's tried again later
                prefs.edit().putString(key, "${System.currentTimeMillis()}|${urls.joinToString("\n")}").apply()
                _covers.update { it + (key to urls) }
            } finally {
                synchronized(pending) { pending.remove(key) }
            }
        }
    }

    private suspend fun find(title: String): List<String> {
        val match = TitleMatcher.best(title, dex.search(1, title, dex.filters()).mangas) ?: return emptyList()
        return dex.volumeCovers(match.url.substringAfterLast('/'))
    }

    private fun load(): Map<String, List<String>> =
        prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to parse(it).second } }.toMap()

    private fun parse(value: String): Pair<Long, List<String>> {
        val at = value.substringBefore('|').toLongOrNull() ?: 0L
        val urls = value.substringAfter('|', "").split('\n').filter { it.isNotBlank() }
        return at to urls
    }

    companion object {
        private const val WEEK = 7L * 24 * 3600 * 1000
        fun key(title: String) = TitleMatcher.normalize(title)
    }
}
