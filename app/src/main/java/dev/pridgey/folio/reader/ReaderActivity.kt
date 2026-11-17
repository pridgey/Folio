package dev.pridgey.folio.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import dev.pridgey.folio.Graph
import dev.pridgey.folio.ui.theme.FolioTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/** Full-screen reading. */
class ReaderActivity : ComponentActivity() {

    private val vm: ReaderViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ReaderViewModel(application, intent.getLongExtra(EXTRA_MANGA, 0), intent.getLongExtra(EXTRA_CHAPTER, 0)) as T
        }
    }

    /** Volume keys turn pages when enabled: -1 back, +1 forward. */
    val pageKeys = MutableSharedFlow<Int>(extraBufferCapacity = 4)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(0), SystemBarStyle.dark(0))
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        lifecycleScope.launch {
            Graph.settings.values.collect { s ->
                if (s.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        setContent {
            val settings = Graph.settings.current
            FolioTheme(settings.theme, settings.accent, forceDark = true) {
                ReaderScreen(
                    vm, pageKeys, onExit = { finish() }, onChromeVisible = ::showSystemBars,
                    onOpenTitle = { mangaId, sourceId ->
                        startActivity(dev.pridgey.folio.ui.MainActivity.titleIntent(this, mangaId, sourceId))
                        finish()
                    },
                )
            }
        }
    }

    private fun showSystemBars(show: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (show) controller.show(WindowInsetsCompat.Type.systemBars()) else controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (Graph.settings.current.volumeKeys) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> { pageKeys.tryEmit(1); return true }
                KeyEvent.KEYCODE_VOLUME_UP -> { pageKeys.tryEmit(-1); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (Graph.settings.current.volumeKeys && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) return true
        return super.onKeyUp(keyCode, event)
    }

    companion object {
        private const val EXTRA_MANGA = "manga"
        private const val EXTRA_CHAPTER = "chapter"

        fun intent(context: Context, mangaId: Long, chapterId: Long): Intent =
            Intent(context, ReaderActivity::class.java).putExtra(EXTRA_MANGA, mangaId).putExtra(EXTRA_CHAPTER, chapterId)
    }
}
