package dev.pridgey.folio.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pridgey.folio.CrashReporter
import dev.pridgey.folio.data.Accent
import dev.pridgey.folio.data.ThemeMode
import dev.pridgey.folio.ui.components.PrimaryButton
import dev.pridgey.folio.ui.components.TintedButton
import dev.pridgey.folio.ui.theme.Folio
import dev.pridgey.folio.ui.theme.FolioTheme

/** Shown in its own process after a crash, so it works even when the app can't start. */
class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val report = intent.getStringExtra(CrashReporter.EXTRA_REPORT) ?: "No report was saved."
        setContent {
            FolioTheme(ThemeMode.SYSTEM, Accent.PERSIMMON) {
                val c = Folio.colors
                Column(Modifier.fillMaxSize().background(c.background).safeDrawingPadding().padding(20.dp)) {
                    Icon(Icons.Rounded.WarningAmber, null, tint = c.accent, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Folio stopped", style = Folio.type.largeTitle, color = c.label)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Something went wrong. Sharing this report shows exactly where, so it can be fixed.",
                        style = Folio.type.callout,
                        color = c.secondaryLabel,
                    )
                    Spacer(Modifier.height(16.dp))
                    SelectionContainer(
                        Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)
                            .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(14.dp),
                    ) {
                        Text(report, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp, color = c.label)
                    }
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Share report", { share(report) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Share)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TintedButton("Copy", { copy(report) }, Modifier.weight(1f), icon = Icons.Rounded.ContentCopy)
                        TintedButton("Restart Folio", ::restart, Modifier.weight(1f), icon = Icons.Rounded.Refresh)
                    }
                }
            }
        }
    }

    private fun share(report: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Folio crash report")
            .putExtra(Intent.EXTRA_TEXT, report)
        startActivity(Intent.createChooser(send, "Share crash report"))
    }

    private fun copy(report: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Folio crash report", report))
    }

    private fun restart() {
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        finishAndRemoveTask()
    }
}
