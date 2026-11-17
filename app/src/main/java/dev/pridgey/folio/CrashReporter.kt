package dev.pridgey.folio

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import dev.pridgey.folio.ui.CrashActivity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.ZonedDateTime
import kotlin.system.exitProcess

/**
 * Catches crashes and shows them in [CrashActivity], which runs in its own process and doesn't
 * start any of the app. That way a report can be read and shared even when Folio crashes on launch.
 */
object CrashReporter {
    const val PROCESS_SUFFIX = ":crash"
    const val EXTRA_REPORT = "report"

    fun isCrashProcess(): Boolean = Application.getProcessName().endsWith(PROCESS_SUFFIX)

    fun lastReportFile(app: Application) = File(app.filesDir, "last-crash.txt")

    fun install(app: Application) {
        if (isCrashProcess()) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val report = report(thread, error)
                runCatching { lastReportFile(app).writeText(report) }
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
