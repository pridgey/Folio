package dev.pridgey.folio.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pridgey.folio.Graph
import dev.pridgey.folio.data.ThemeMode
import dev.pridgey.folio.ui.theme.FolioTheme
import dev.pridgey.folio.update.Updater

class MainActivity : ComponentActivity() {

    /** Something the app was opened with: a tab from a notification, or a link to a title. */
    val pending = mutableStateOf<Launch?>(null)

    sealed interface Launch {
        data class Tab(val tab: String) : Launch
        data class Link(val url: String) : Launch
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent {
            val settings by Graph.settings.values.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(0) else SystemBarStyle.light(0, 0),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(0) else SystemBarStyle.light(0, 0),
                )
            }
            FolioTheme(settings.theme, settings.accent) {
                FolioNavHost(this)
            }
        }
        Updater.checkIfDue(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_TAB)?.let { pending.value = Launch.Tab(it) }
        val link = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { Regex("""https?://\S+""").find(it)?.value }
            else -> null
        }
        link?.let { pending.value = Launch.Link(it) }
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_LIBRARY = "library"
        const val TAB_UPDATES = "updates"
        const val TAB_DISCOVER = "discover"
        const val TAB_HISTORY = "history"
        const val TAB_SETTINGS = "settings"
    }
}
