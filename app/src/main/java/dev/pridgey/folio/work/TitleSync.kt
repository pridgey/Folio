package dev.pridgey.folio.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.pridgey.folio.Graph
import dev.pridgey.folio.source.ExtensionManager
import dev.pridgey.folio.source.SourceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * The queue of titles waiting on their first sync. Some sources take minutes to list a title's chapters,
 * so this runs in the background, one title at a time: the source paces its requests across all of them,
 * so running several together wouldn't finish any sooner.
 */
object TitleSync {

    @Serializable
    data class Entry(val mangaId: Long, val sourceId: Long, val title: String)

    data class State(val current: Entry? = null, val waiting: List<Entry> = emptyList()) {
        fun contains(mangaId: Long) = current?.mangaId == mangaId || waiting.any { it.mangaId == mangaId }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Why a title's last sync failed, until it's tried again. */
    private val _failures = MutableStateFlow<Map<Long, String>>(emptyMap())
    val failures: StateFlow<Map<Long, String>> = _failures.asStateFlow()

    private val lock = Any()
    private var running: Job? = null
    private val prefs by lazy { Graph.app.getSharedPreferences("title_sync", Context.MODE_PRIVATE) }
    private val listSerializer = ListSerializer(Entry.serializer())

    /** Whether a title has to go through the queue before it can be read. */
    fun needsSync(sourceId: Long, initialized: Boolean, chapterCount: Int): Boolean =
        !initialized && chapterCount == 0 && Graph.sources.fromPackage(ExtensionManager.COMIX)?.id == sourceId

    /** Picks the queue back up after the process was stopped. */
    fun restore(context: Context) {
        val saved = prefs.getString(KEY, null)?.let { runCatching { Graph.json.decodeFromString(listSerializer, it) }.getOrNull() }.orEmpty()
        if (saved.isEmpty()) return
        synchronized(lock) { _state.value = State(waiting = saved) }
        start(context)
    }

    fun enqueue(context: Context, entry: Entry, next: Boolean = false) {
        synchronized(lock) {
            _failures.value -= entry.mangaId
            val s = _state.value
            if (s.current?.mangaId == entry.mangaId) return
            val others = s.waiting.filter { it.mangaId != entry.mangaId }
            val existing = s.waiting.firstOrNull { it.mangaId == entry.mangaId } ?: entry
            update(s.copy(waiting = if (next) listOf(existing) + others else if (others.size == s.waiting.size) others + entry else s.waiting))
        }
        start(context)
    }

    /** Leaves the queue. A title mid-sync stops, and what it had fetched is lost. */
    fun cancel(mangaId: Long) {
        val job = synchronized(lock) {
            val s = _state.value
            update(s.copy(waiting = s.waiting.filter { it.mangaId != mangaId }))
            if (s.current?.mangaId == mangaId) running else null
        }
        job?.cancel()
    }

    fun clearFailure(mangaId: Long) {
        _failures.value -= mangaId
    }

    internal fun takeNext(): Entry? = synchronized(lock) {
        val s = _state.value
        val next = s.waiting.firstOrNull()
        update(State(current = next, waiting = s.waiting.drop(1)))
        next
    }

    internal fun attach(job: Job) = synchronized(lock) { running = job }

    internal fun finished(entry: Entry, error: Throwable?) = synchronized(lock) {
        running = null
        if (error != null) _failures.value += entry.mangaId to SourceManager.describe(error)
        val s = _state.value
        if (s.current?.mangaId == entry.mangaId) update(s.copy(current = null))
    }

    /** The system stopped the work: the title goes back to the front to start over. */
    internal fun interrupted() = synchronized(lock) {
        running = null
        val s = _state.value
        update(State(waiting = listOfNotNull(s.current) + s.waiting))
    }

    private fun update(next: State) {
        _state.value = next
        val all = listOfNotNull(next.current) + next.waiting
        prefs.edit().putString(KEY, Graph.json.encodeToString(listSerializer, all)).apply()
    }

    private fun start(context: Context) {
        val app = context.applicationContext
        launchWork(app)
        // A worker that's just emptied the queue still counts as running and would swallow this request.
        Graph.scope.launch {
            delay(RESTART_CHECK_MS)
            val s = _state.value
            if (s.current == null && s.waiting.isNotEmpty()) launchWork(app)
        }
    }

    private fun launchWork(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<TitleSyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    private const val KEY = "queue"
    private const val WORK = "title_sync"
    private const val RESTART_CHECK_MS = 3_000L
}

/** Works through [TitleSync]'s queue with a notification up, so it carries on outside the app. */
class TitleSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = info(TitleSync.state.value)

    private fun info(state: TitleSync.State): ForegroundInfo {
        val n = Notifications.titleSyncProgress(applicationContext, state.current?.title, state.waiting.size)
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(Notifications.TITLE_SYNC_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(Notifications.TITLE_SYNC_ID, n)
        }
    }

    override suspend fun doWork(): Result {
        runCatching { setForeground(getForegroundInfo()) }
        try {
            while (true) {
                val entry = TitleSync.takeNext() ?: break
                runCatching { setForeground(info(TitleSync.state.value)) }
                // Cleared while it was queued, say by the app's clean-up.
                if (Graph.library.get(entry.mangaId) == null) {
                    TitleSync.finished(entry, null)
                    continue
                }
                var failure: Throwable? = null
                var cancelled = false
                coroutineScope {
                    val job = launch {
                        try {
                            Graph.library.refresh(entry.mangaId)
                            // Asking to read it is what puts it in the library.
                            if (Graph.library.get(entry.mangaId)?.favorite == false) Graph.library.setFavorite(entry.mangaId, true)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failure = e
                        }
                    }
                    TitleSync.attach(job)
                    job.invokeOnCompletion { cancelled = it is CancellationException }
                }
                val error = failure
                TitleSync.finished(entry, error)
                when {
                    cancelled -> Unit
                    error != null -> Notifications.titleSyncFailed(applicationContext, entry, SourceManager.describe(error))
                    else -> Notifications.titleReady(applicationContext, entry)
                }
            }
        } catch (e: CancellationException) {
            TitleSync.interrupted()
            throw e
        }
        return Result.success()
    }
}
