package dev.pridgey.folio.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pridgey.folio.ui.components.GroupedRow
import dev.pridgey.folio.ui.components.ProgressBar
import dev.pridgey.folio.ui.components.TintedButton
import dev.pridgey.folio.ui.components.pressable
import dev.pridgey.folio.ui.theme.Folio

// Folio updates itself from its GitHub releases (see Updater).

@Composable
fun rememberUpdateBannerVisible(): Boolean = Updater.state.collectAsStateWithLifecycle().value.showsBanner

/** Picks up an update after the user grants install permission and comes back. */
@Composable
private fun ResumeUpdates() {
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        Updater.onResume(context)
        onPauseOrDispose { }
    }
}

/** The "Folio updates" row in Settings: status, and the one action that fits. */
@Composable
fun UpdateRow() {
    val c = Folio.colors
    val context = LocalContext.current
    val state by Updater.state.collectAsStateWithLifecycle()
    ResumeUpdates()
    val text = when {
        !Updater.supported -> "Updates come to release builds"
        else -> when (val s = state) {
            UpdateState.Idle -> "Version ${Updater.currentVersion}"
            UpdateState.Checking -> "Checking…"
            UpdateState.UpToDate -> "Version ${Updater.currentVersion} is the latest"
            is UpdateState.Available -> "Folio ${s.release.version} is available"
            is UpdateState.Downloading -> "Downloading ${(s.progress * 100).toInt()}%"
            is UpdateState.Installing -> "Installing…"
            is UpdateState.NeedsPermission -> "Allow Folio to install its updates"
            is UpdateState.Failed -> "Couldn't update: ${s.reason}"
        }
    }
    val action: Pair<String, () -> Unit>? = if (!Updater.supported) null else when (val s = state) {
        UpdateState.Idle, UpdateState.UpToDate -> "Check" to { Updater.check(context) }
        is UpdateState.Available -> "Update" to { Updater.install(context, s.release) }
        is UpdateState.NeedsPermission -> "Allow" to { context.startActivity(Updater.permissionIntent(context)) }
        is UpdateState.Failed -> "Retry" to { s.release?.let { Updater.install(context, it) } ?: Updater.check(context) }
        else -> null
    }
    val busy = state is UpdateState.Checking || state is UpdateState.Installing
    GroupedRow(
        title = "Folio updates",
        subtitle = text,
        icon = Icons.Rounded.SystemUpdate,
        iconBackground = c.accent,
    ) {
        when {
            busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.accent)
            action != null -> Text(
                action.first, style = Folio.type.subheadStrong, color = c.accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).pressable(onClick = action.second).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
    (state as? UpdateState.Downloading)?.let {
        ProgressBar(it.progress, Modifier.fillMaxWidth().padding(start = 59.dp, end = 16.dp, bottom = 12.dp))
    }
}

/** Shown at the top of the library while an update is waiting. */
@Composable
fun UpdateBanner() {
    val c = Folio.colors
    val context = LocalContext.current
    val state by Updater.state.collectAsStateWithLifecycle()
    ResumeUpdates()
    val release = when (val s = state) {
        is UpdateState.Available -> s.release
        is UpdateState.Downloading -> s.release
        is UpdateState.Installing -> s.release
        is UpdateState.NeedsPermission -> s.release
        else -> return
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.accentSoft).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SystemUpdate, null, tint = c.accent)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Folio ${release.version} is ready", style = Folio.type.headline, color = c.label)
                Text("Your library and settings stay as they are.", style = Folio.type.footnote, color = c.secondaryLabel)
            }
        }
        Spacer(Modifier.height(12.dp))
        when (val s = state) {
            is UpdateState.Downloading -> ProgressBar(s.progress, Modifier.fillMaxWidth().padding(vertical = 8.dp))
            is UpdateState.Installing -> Text("Installing…", style = Folio.type.subhead, color = c.secondaryLabel)
            is UpdateState.NeedsPermission -> TintedButton("Allow installing updates", { context.startActivity(Updater.permissionIntent(context)) })
            else -> TintedButton("Update now", { Updater.install(context, release) })
        }
    }
}
