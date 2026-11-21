package dev.pridgey.folio.work

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.pridgey.folio.R
import dev.pridgey.folio.ui.MainActivity

object Notifications {
    const val CHANNEL_UPDATES = "new_chapters"
    const val CHANNEL_DOWNLOADS = "downloads"
    const val CHANNEL_SYSTEM = "system"
    const val DOWNLOAD_ID = 2001
    const val LIBRARY_ID = 2002
    const val SYNC_ID = 2003
    const val TITLE_SYNC_ID = 2004
    private const val CHANNEL_TITLE_SYNC = "title_sync"
    private const val CHANNEL_TITLE_READY = "title_ready"
    private const val TITLE_READY_BASE = 3000

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_UPDATES, "New chapters", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When titles in your library get new chapters"
                },
                NotificationChannel(CHANNEL_DOWNLOADS, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Progress of chapters downloading for offline reading"
                },
                NotificationChannel(CHANNEL_SYSTEM, "Library sync", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Shown while Folio checks your library for new chapters"
                },
                NotificationChannel(CHANNEL_TITLE_SYNC, "Title sync", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while a title syncs in the background"
                },
                NotificationChannel(CHANNEL_TITLE_READY, "Title ready", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When a title you synced is ready to read"
                },
            ),
        )
    }

    private fun openApp(context: Context, tab: String? = null): PendingIntent =
        PendingIntent.getActivity(
            context, tab.hashCode(),
            Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply { tab?.let { putExtra(MainActivity.EXTRA_TAB, it) } },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun downloadProgress(context: Context, text: String, done: Int, total: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_stat_folio)
            .setContentTitle("Downloading")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(total, done, total == 0)
            .setContentIntent(openApp(context))
            .build()

    fun syncProgress(context: Context, done: Int, total: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_SYSTEM)
            .setSmallIcon(R.drawable.ic_stat_folio)
            .setContentTitle("Checking for new chapters")
            .setProgress(total, done, total == 0)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .build()

    fun newChapters(context: Context, titles: List<Pair<String, Int>>) {
        if (titles.isEmpty() || !canNotify(context)) return
        val total = titles.sumOf { it.second }
        val text = titles.take(5).joinToString("\n") { (t, n) -> if (n == 1) "$t · 1 new chapter" else "$t · $n new chapters" }
        val n = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_folio)
            .setContentTitle(if (total == 1) "1 new chapter" else "$total new chapters")
            .setContentText(titles.joinToString(", ") { it.first })
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, MainActivity.TAB_UPDATES))
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(LIBRARY_ID, n)
    }

    fun titleSyncProgress(context: Context, title: String?, waiting: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_TITLE_SYNC)
            .setSmallIcon(R.drawable.ic_stat_folio)
            .setContentTitle(title?.let { "Syncing $it" } ?: "Syncing")
            .apply { if (waiting > 0) setContentText("$waiting more in queue") }
            .setProgress(0, 0, true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setContentIntent(openApp(context))
            .build()

    fun titleReady(context: Context, entry: TitleSync.Entry) =
        titleResult(context, entry, "${entry.title} is ready", "Tap to start reading")

    fun titleSyncFailed(context: Context, entry: TitleSync.Entry, reason: String) =
        titleResult(context, entry, "Couldn't sync ${entry.title}", "$reason Tap to try again.")

    private fun titleResult(context: Context, entry: TitleSync.Entry, title: String, text: String) {
        if (!canNotify(context)) return
        val open = PendingIntent.getActivity(
            context, TITLE_READY_BASE + entry.mangaId.toInt(),
            MainActivity.titleIntent(context, entry.mangaId, entry.sourceId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_TITLE_READY)
            .setSmallIcon(R.drawable.ic_stat_folio)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(TITLE_READY_BASE + entry.mangaId.toInt(), n)
    }

    fun canNotify(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
