package dev.pridgey.folio.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.pridgey.folio.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

/** Checks every title in the library for new chapters. */
class LibraryUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val n = Notifications.syncProgress(applicationContext, 0, 0)
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(Notifications.SYNC_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(Notifications.SYNC_ID, n)
    }

    override suspend fun doWork(): Result {
        val repo = Graph.library
        val settings = Graph.settings.current
        val titles = Graph.db.manga().favorites()
        if (titles.isEmpty()) return Result.success()
        LibrarySync.start(titles.size)
        val found = mutableListOf<Pair<String, Int>>()
        try {
            titles.forEachIndexed { i, m ->
                runCatching { repo.refresh(m.id, details = false) }.onSuccess { n ->
                    if (n > 0) {
                        found += m.title to n
                        if (settings.autoDownload) {
                            val newest = repo.chaptersOf(m.id).filter { !it.read }.sortedBy { it.sourceOrder }.take(n).map { it.id }
                            Graph.downloads.enqueue(m.id, newest)
                        }
                    }
                }
                LibrarySync.progress(i + 1)
            }
        } finally {
            LibrarySync.finish()
        }
        if (settings.notifyNewChapters) Notifications.newChapters(applicationContext, found)
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "library_update"
        private const val NOW = "library_update_now"

        fun schedule(context: Context) {
            val s = Graph.settings.current
            val wm = WorkManager.getInstance(context)
            if (s.updateIntervalHours <= 0) {
                wm.cancelUniqueWork(PERIODIC)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (s.updateOnlyOnWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            wm.enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<LibraryUpdateWorker>(s.updateIntervalHours.toLong(), TimeUnit.HOURS, 1, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build(),
            )
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<LibraryUpdateWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        }
    }
}

/** Progress of the running library update, for the UI. */
object LibrarySync {
    data class State(val done: Int, val total: Int)

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    fun start(total: Int) { _state.value = State(0, total) }
    fun progress(done: Int) { _state.value = _state.value?.copy(done = done) }
    fun finish() { _state.value = null }
}

/** Keeps extensions current, so a site's changes are usually handled before you notice. */
class ExtensionUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (Graph.extensions.checkForUpdates(install = true).isSuccess) Result.success() else Result.retry()

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "extension_update",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ExtensionUpdateWorker>(6, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        }
    }
}
