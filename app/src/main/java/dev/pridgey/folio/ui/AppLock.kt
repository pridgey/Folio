package dev.pridgey.folio.ui

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pridgey.folio.Graph
import dev.pridgey.folio.ui.components.PrimaryButton
import dev.pridgey.folio.ui.theme.Folio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Locks Folio behind the phone's fingerprint, face or screen lock: when it opens, and again after it
 * has been in the background for a while. Also guards private shelves.
 */
object AppLock {
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /** Private shelves stay open until Folio goes to the background. */
    private val _privateOpen = MutableStateFlow(false)
    val privateOpen: StateFlow<Boolean> = _privateOpen.asStateFlow()

    private var started = 0
    private var backgroundedAt = 0L

    fun install(app: Application) {
        _locked.value = Graph.settings.current.appLock && canLock(app)
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (started++ == 0 && backgroundedAt > 0) {
                    val away = System.currentTimeMillis() - backgroundedAt
                    val s = Graph.settings.current
                    if (s.appLock && away >= s.lockAfterSeconds * 1000L && canLock(app)) _locked.value = true
                    if (away >= 5_000) _privateOpen.value = false
                }
                secureRecents(activity)
            }

            override fun onActivityStopped(activity: Activity) {
                if (--started == 0) backgroundedAt = System.currentTimeMillis()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /** With the lock on, the app switcher shows a blank card instead of what you're reading. */
    fun secureRecents(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 33) activity.setRecentsScreenshotEnabled(!Graph.settings.current.appLock)
    }

    /** True when the phone has a screen lock, so there's something to unlock with. */
    fun canLock(context: Context): Boolean = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** Asks for the fingerprint, face or screen lock. */
    fun authenticate(activity: Activity, title: String, subtitle: String? = null, onResult: (Boolean) -> Unit) {
        if (!canLock(activity)) return onResult(true)
        val builder = BiometricPrompt.Builder(activity).setTitle(title)
        subtitle?.let { builder.setSubtitle(it) }
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        builder.build().authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            },
        )
    }

    fun unlock() { _locked.value = false }

    fun openPrivate() { _privateOpen.value = true }

    fun lockNow() { if (Graph.settings.current.appLock) _locked.value = true }
}

/** Shows [content], or the lock screen while Folio is locked. */
@Composable
fun LockGate(content: @Composable () -> Unit) {
    val locked by AppLock.locked.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        content()
        if (locked) LockScreen()
    }
}

@Composable
private fun LockScreen() {
    val c = Folio.colors
    val activity = LocalActivity.current
    val prompt = {
        activity?.let { a -> AppLock.authenticate(a, "Unlock Folio") { ok -> if (ok) AppLock.unlock() } }
    }
    LaunchedEffect(Unit) { prompt() }
    // Opaque and swallowing touches, so nothing behind it shows or can be tapped.
    Box(
        Modifier.fillMaxSize().background(c.background).pointerBlock(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Rounded.Lock, null, tint = c.accent, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("Folio is locked", style = Folio.type.title2, color = c.label)
            Spacer(Modifier.height(6.dp))
            Text("Use your fingerprint, face or screen lock to open it.", style = Folio.type.subhead, color = c.secondaryLabel, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Unlock", { prompt() })
        }
    }
}

/** Takes every touch, so nothing underneath can be used. */
private fun Modifier.pointerBlock() = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent().changes.forEach { it.consume() }
    }
}
