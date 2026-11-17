package dev.pridgey.folio

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import dev.pridgey.folio.ui.CrashActivity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.system.exitProcess

/**
 * Catches crashes and shows them in [CrashActivity], which runs in its own process and doesn't
 * start any of the app. That way a report can be read and shared even when Folio crashes on launch.
 *
 * Some exits never reach the handler (native crashes, the system killing the app). For those, the
 * next launch reads Android's record of why the last process ended and shows that instead.
 */
object CrashReporter {
    const val PROCESS_SUFFIX = ":crash"
    const val EXTRA_REPORT = "report"
    const val EXTRA_PREVIOUS = "previous"

    fun isCrashProcess(): Boolean = Application.getProcessName().endsWith(PROCESS_SUFFIX)

    private fun reportFile(context: Context) = File(context.filesDir, "last-crash.txt")
    private fun pendingMarker(context: Context) = File(context.filesDir, "crash-pending")
    private fun prefs(context: Context) = context.getSharedPreferences("crash", Context.MODE_PRIVATE)

    fun install(app: Application) {
        if (isCrashProcess()) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val report = report(thread, error)
                // Saved first: if the report screen can't open, the next launch shows it.
                runCatching {
                    reportFile(app).writeText(report)
                    pendingMarker(app).createNewFile()
                }
                app.startActivity(
                    Intent(app, CrashActivity::class.java)
                        .putExtra(EXTRA_REPORT, report)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                )
                Process.killProcess(Process.myPid())
                exitProcess(10)
            } catch (_: Throwable) {
                previous?.uncaughtException(thread, error)
            }
        }
    }

    /** The report screen calls this once a report has been shown. */
    fun markSeen(context: Context) {
        pendingMarker(context).delete()
        // The crashed process is ending right about now; don't report its exit again next launch.
        prefs(context).edit().putLong("seenUntil", System.currentTimeMillis() + 15_000).commit()
    }

    /**
     * A report about how the last run ended, if it ended badly and hasn't been shown yet. Call once at
     * launch, off the main thread.
     */
    fun previousRunReport(context: Context): String? {
        if (pendingMarker(context).exists()) {
            markSeen(context)
            return reportFile(context).takeIf { it.exists() }?.readText()
        }
        return if (Build.VERSION.SDK_INT >= 30) lastAbnormalExit(context) else null
    }

    @RequiresApi(30)
    private fun lastAbnormalExit(context: Context): String? {
        val prefs = prefs(context)
        val seenUntil = prefs.getLong("seenUntil", 0)
        val exits = runCatching {
            context.getSystemService(ActivityManager::class.java)
                .getHistoricalProcessExitReasons(context.packageName, 0, 8)
                .filter { it.processName == context.packageName }
        }.getOrDefault(emptyList())
        exits.maxOfOrNull { it.timestamp }?.let { prefs.edit().putLong("seenUntil", maxOf(it, seenUntil)).apply() }
        // Exits not shown yet (the first time, anything in the last few hours). Updating the app is an
        // exit too, so look past it for the most recent abnormal one.
        val since = if (seenUntil == 0L) System.currentTimeMillis() - FIRST_RUN_LOOKBACK_MS else seenUntil
        val bad = exits.filter { it.timestamp > since && it.reason in ABNORMAL }.maxByOrNull { it.timestamp } ?: return null
        return describe(bad)
    }

    private val ABNORMAL = setOf(
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_LOW_MEMORY,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
        ApplicationExitInfo.REASON_SIGNALED,
        ApplicationExitInfo.REASON_DEPENDENCY_DIED,
    )

    private fun reasonName(reason: Int) = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "CRASH (Java)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR (not responding)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        else -> "reason $reason"
    }

    @RequiresApi(30)
    private fun describe(exit: ApplicationExitInfo): String = buildString {
        appendLine("Folio ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Last run ended ${ZonedDateTime.ofInstant(Instant.ofEpochMilli(exit.timestamp), ZoneId.systemDefault())}")
        appendLine("Reason: ${reasonName(exit.reason)}, status ${exit.status}, importance ${exit.importance}")
        exit.description?.let { appendLine("Description: $it") }
        appendLine("Memory: pss ${exit.pss / 1024} MB, rss ${exit.rss / 1024} MB")
        val trace = runCatching { exit.traceInputStream?.use { it.readBytes() } }.getOrNull()
        if (trace != null && trace.isNotEmpty()) {
            appendLine()
            if (exit.reason == ApplicationExitInfo.REASON_ANR) {
                append(String(trace).take(MAX_TRACE))
            } else {
                // Native crashes come as a tombstone protobuf; its strings hold the signal, abort
                // message and backtrace.
                appendLine("Tombstone:")
                append(printableStrings(trace).take(MAX_TRACE))
            }
        }
    }

    private fun printableStrings(bytes: ByteArray): String {
        val out = StringBuilder()
        val run = StringBuilder()
        fun flush() {
            if (run.length >= 4) out.append(run).append('\n')
            run.setLength(0)
        }
        for (b in bytes) {
            val ch = b.toInt() and 0xff
            if (ch in 0x20..0x7e) run.append(ch.toChar()) else flush()
        }
        flush()
        return out.toString()
    }

    private const val MAX_TRACE = 60_000
    private const val FIRST_RUN_LOOKBACK_MS = 6 * 60 * 60 * 1000L

    private fun report(thread: Thread, error: Throwable): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        return buildString {
            appendLine("Folio ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("${ZonedDateTime.now()} on thread \"${thread.name}\"")
            appendLine()
            append(trace)
        }
    }
}
