package dev.pridgey.folio

import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.work.Configuration
import androidx.work.WorkManager
import dev.pridgey.folio.ui.CrashActivity
import dev.pridgey.folio.ui.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Boots the real Application and launches the main screen, the way a cold start does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = LaunchTestApp::class)
class LaunchTest {
    @Test
    fun coldStart() {
        ActivityScenario.launch(MainActivity::class.java).use {
            repeat(20) { shadowOf(Looper.getMainLooper()).idle() }
        }
    }

    @Test
    fun crashScreenShowsReport() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), CrashActivity::class.java)
            .putExtra(CrashReporter.EXTRA_REPORT, "java.lang.IllegalStateException: boom")
        ActivityScenario.launch<CrashActivity>(intent).use {
            repeat(5) { shadowOf(Looper.getMainLooper()).idle() }
        }
    }
}

/** Robolectric doesn't run androidx.startup, which initializes WorkManager on a device. */
class LaunchTestApp : FolioApp() {
    override fun onCreate() {
        if (!WorkManager.isInitialized()) WorkManager.initialize(this, Configuration.Builder().build())
        super.onCreate()
    }
}
