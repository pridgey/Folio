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

    fun canNotify(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
