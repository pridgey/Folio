package dev.pridgey.folio.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dev.pridgey.folio.Graph
import dev.pridgey.folio.work.Notifications

/** Works through the download queue, showing progress in a notification. */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = info("Preparing downloads…", 0, 0)

    private fun info(text: String, done: Int, total: Int): ForegroundInfo {
        val n = Notifications.downloadProgress(applicationContext, text, done, total)
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(Notifications.DOWNLOAD_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(Notifications.DOWNLOAD_ID, n)
        }
    }

    override suspend fun doWork(): Result {
        runCatching { setForeground(getForegroundInfo()) }
        var failures = 0
        while (true) {
            val more = try {
                Graph.downloads.downloadNext { p, label ->
                    runCatching { setForegroundAsync(info(label, p.done, p.total)) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (++failures >= 3) return Result.retry()
                true
            }
            if (!more) break
        }
        return Result.success()
    }
}
