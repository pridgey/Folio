package dev.pridgey.folio.source

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * A record of how each source has behaved: calls that worked and failed per day, and notable moments
 * (failures, recoveries, self-repairs). Kept small and saved in the background.
 */
class SourceLog(context: Context, private val json: Json, private val scope: CoroutineScope) {

    enum class Kind { FAILED, RECOVERED, HEALING, HEALED, HEAL_FAILED }

    @Serializable
    data class Event(val sourceId: Long, val at: Long, val kind: Kind, val detail: String? = null)

    /** Calls on one day (epoch day) for one source. */
    @Serializable
    data class Day(val day: Long, val ok: Int = 0, val failed: Int = 0)

    @Serializable
    data class Snapshot(
        val events: List<Event> = emptyList(),
        val days: Map<Long, List<Day>> = emptyMap(),
        val lastOk: Map<Long, Long> = emptyMap(),
    ) {
        /** Share of calls that worked over the last [window] days, or null without enough calls. */
        fun uptime(sourceId: Long, window: Int = 7): Float? {
            val since = LocalDate.now().toEpochDay() - window + 1
            val d = days[sourceId].orEmpty().filter { it.day >= since }
            val total = d.sumOf { it.ok + it.failed }
            return if (total < 3) null else d.sumOf { it.ok } / total.toFloat()
        }

        fun lastFailure(sourceId: Long) = events.lastOrNull { it.sourceId == sourceId && it.kind == Kind.FAILED }
    }

    private val prefs = context.getSharedPreferences("source_log", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Snapshot> = _state.asStateFlow()
    private var saveJob: Job? = null
    private val failing = HashSet<Long>()

    fun ok(sourceId: Long) {
        val recovered = synchronized(failing) { failing.remove(sourceId) }
        update {
            val s = count(it, sourceId, ok = true).copy(lastOk = it.lastOk + (sourceId to System.currentTimeMillis()))
            if (recovered) s.add(Event(sourceId, System.currentTimeMillis(), Kind.RECOVERED)) else s
        }
    }

    fun failed(sourceId: Long, message: String) {
        val first = synchronized(failing) { failing.add(sourceId) }
        update {
            val s = count(it, sourceId, ok = false)
            // One event per outage, not one per failed call.
            if (first) s.add(Event(sourceId, System.currentTimeMillis(), Kind.FAILED, message)) else s
        }
    }

    fun event(sourceId: Long, kind: Kind, detail: String? = null) = update { it.add(Event(sourceId, System.currentTimeMillis(), kind, detail)) }

    private fun Snapshot.add(e: Event) = copy(events = (events + e).takeLast(MAX_EVENTS))

    private fun count(s: Snapshot, sourceId: Long, ok: Boolean): Snapshot {
        val today = LocalDate.now().toEpochDay()
        val list = s.days[sourceId].orEmpty().filter { it.day > today - KEEP_DAYS }
        val current = list.lastOrNull()?.takeIf { it.day == today } ?: Day(today)
        val next = if (ok) current.copy(ok = current.ok + 1) else current.copy(failed = current.failed + 1)
        return s.copy(days = s.days + (sourceId to (list.filter { it.day != today } + next)))
    }

    private fun update(transform: (Snapshot) -> Snapshot) {
        _state.update(transform)
        // Calls come in bursts (a chapter is dozens of images), so save a little later, once.
        if (saveJob?.isActive != true) {
            saveJob = scope.launch {
                delay(5_000)
                prefs.edit().putString("log", json.encodeToString(Snapshot.serializer(), _state.value)).apply()
            }
        }
    }

    private fun load(): Snapshot =
        prefs.getString("log", null)?.let { runCatching { json.decodeFromString(Snapshot.serializer(), it) }.getOrNull() } ?: Snapshot()

    private companion object {
        const val MAX_EVENTS = 120
        const val KEEP_DAYS = 14
    }
}
