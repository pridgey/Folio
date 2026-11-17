package dev.pridgey.folio.source

import dev.pridgey.folio.data.Settings
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.net.UnknownHostException

/** How a source has been doing lately. */
sealed interface Health {
    data object Unknown : Health
    data object Ok : Health
    /** Failing; [healing] while Folio fetches a fixed extension. */
    data class Failing(val message: String, val healing: Boolean = false) : Health
}

class SourceUnavailableException(message: String) : IOException(message)

/**
 * Finds sources by id and runs calls against them with self-healing: when an extension source fails
 * in a way that looks like the site changed, Folio installs the newest extension build and retries.
 */
class SourceManager(
    private val extensions: ExtensionManager,
    val mangaDex: MangaDexSource,
    private val settings: Settings,
) {
    private val _health = MutableStateFlow<Map<Long, Health>>(emptyMap())
    val health: StateFlow<Map<Long, Health>> = _health.asStateFlow()

    /** Every source Folio can use right now. */
    fun all(): List<FolioSource> = extensionSources() + mangaDex

    private fun extensionSources(): List<ExtensionSource> =
        extensions.status.value.values.flatMap { it.installed?.sources.orEmpty() }

    fun get(id: Long): FolioSource? = if (id == MangaDexSource.ID) mangaDex else extensionSources().firstOrNull { it.id == id }

    /** The primary source in the reading language (MangaFire), if installed. */
    fun primary(): FolioSource? {
        val lang = settings.current.language
        val all = extensions.status.value[ExtensionManager.MANGAFIRE]?.installed?.sources.orEmpty()
        return all.firstOrNull { it.lang == lang } ?: all.firstOrNull { it.lang == "en" } ?: all.firstOrNull()
    }

    /** Languages the primary source offers. */
    fun primaryLanguages(): List<String> =
        extensions.status.value[ExtensionManager.MANGAFIRE]?.installed?.sources.orEmpty().map { it.lang }.ifEmpty { listOf("en") }

    fun nameOf(id: Long): String = get(id)?.name ?: when (id) {
        MangaDexSource.ID -> "MangaDex"
        else -> ExtensionManager.titleFor(ExtensionManager.MANGAFIRE)
    }

    /**
     * Runs [block] against source [id]. On a failure that a newer extension might fix, installs it and
     * tries once more with the reloaded source.
     */
    suspend fun <T> call(id: Long, block: suspend (FolioSource) -> T): T {
        val source = get(id) ?: throw SourceUnavailableException("${nameOf(id)} isn't installed yet")
        return try {
            block(source).also { mark(id, Health.Ok) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val pkg = source.packageName
            if (pkg == null || !worthHealing(e)) {
                mark(id, Health.Failing(describe(e)))
                throw e
            }
            mark(id, Health.Failing(describe(e), healing = true))
            val healed = runCatching { extensions.heal(pkg) }.getOrDefault(false)
            val fresh = get(id)
            if (!healed || fresh == null) {
                mark(id, Health.Failing(describe(e)))
                throw e
            }
            try {
                block(fresh).also { mark(id, Health.Ok) }
            } catch (e2: CancellationException) {
                throw e2
            } catch (e2: Exception) {
                mark(id, Health.Failing(describe(e2)))
                throw e2
            }
        }
    }

    /** Search one source, or every source when [sourceId] is null. Failures come back per source. */
    suspend fun search(sourceId: Long, query: String, page: Int = 1, filters: FilterList? = null): MangasPage =
        call(sourceId) { it.search(page, query, filters ?: it.filters()) }

    private fun mark(id: Long, health: Health) = _health.update { it + (id to health) }

    private fun worthHealing(e: Exception): Boolean = when {
        e is UnknownHostException -> false
        e is HttpException && e.code == 404 -> false
        e is SourceUnavailableException -> false
        e.message?.contains("captcha", true) == true -> false
        e.message?.contains("checking that you're human") == true -> false
        else -> true
    }

    companion object {
        fun describe(e: Throwable): String = when (e) {
            is UnknownHostException -> "You're offline"
            is HttpException -> when (e.code) {
                403 -> "The site refused the request (403)"
                429 -> "Too many requests; try again in a minute"
                in 500..599 -> "The site is having trouble (${e.code})"
                else -> "The site said ${e.code}"
            }
            is java.net.SocketTimeoutException -> "The site took too long to answer"
            else -> e.message?.takeIf { it.isNotBlank() && it.length < 160 } ?: e.javaClass.simpleName
        }
    }
}

/** Finds the same title on another source by name. */
object TitleMatcher {
    fun normalize(title: String): String = title.lowercase()
        .replace(Regex("""\(.*?\)|\[.*?]"""), " ")
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .replace(Regex("""\b(the|a|an)\b"""), " ")
        .trim().replace(Regex("""\s+"""), " ")

    /** 0..1, how alike two titles are (Sørensen–Dice over character bigrams). */
    fun similarity(a: String, b: String): Double {
        val x = normalize(a)
        val y = normalize(b)
        if (x == y) return 1.0
        if (x.length < 2 || y.length < 2) return 0.0
        val bx = x.windowed(2).groupingBy { it }.eachCount()
        val by = y.windowed(2).groupingBy { it }.eachCount()
        val shared = bx.entries.sumOf { (k, v) -> minOf(v, by[k] ?: 0) }
        return 2.0 * shared / (x.length - 1 + y.length - 1)
    }

    fun best(title: String, candidates: List<SManga>, threshold: Double = 0.72): SManga? =
        candidates.map { it to similarity(title, it.title) }.filter { it.second >= threshold }.maxByOrNull { it.second }?.first
}
