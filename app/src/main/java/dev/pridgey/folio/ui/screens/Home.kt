package dev.pridgey.folio.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pridgey.folio.Graph
import dev.pridgey.folio.ui.MainActivity
import dev.pridgey.folio.ui.components.TabBar
import dev.pridgey.folio.ui.components.TabItem
import dev.pridgey.folio.ui.components.rememberGlassState
import dev.pridgey.folio.ui.theme.Folio
import dev.pridgey.folio.update.Updater
import dev.pridgey.folio.update.showsBanner
import dev.pridgey.folio.work.Notifications

@Composable
fun HomeScreen(initialTab: String?, onTabConsumed: () -> Unit) {
    var tab by rememberSaveable { mutableStateOfTab(MainActivity.TAB_LIBRARY) }
    LaunchedEffect(initialTab) {
        if (initialTab != null) {
            tab = initialTab
            onTabConsumed()
        }
    }
    val glass = rememberGlassState()
    val settings by Graph.settings.values.collectAsStateWithLifecycle()
    val newChapters by remember(settings.lastUpdatesSeen) { Graph.library.newSince(settings.lastUpdatesSeen) }.collectAsState(0)
    val updateBanner = Updater.state.collectAsStateWithLifecycle().value.showsBanner

    NotificationPermission()

    val tabs = listOf(
        TabItem(MainActivity.TAB_LIBRARY, "Library", Icons.Outlined.AutoStories, Icons.Rounded.AutoStories),
        TabItem(MainActivity.TAB_UPDATES, "Updates", Icons.Outlined.Notifications, Icons.Rounded.Notifications, badge = if (tab == MainActivity.TAB_UPDATES) 0 else newChapters),
        TabItem(MainActivity.TAB_DISCOVER, "Discover", Icons.Outlined.Explore, Icons.Rounded.Explore),
        TabItem(MainActivity.TAB_HISTORY, "History", Icons.Outlined.History, Icons.Rounded.History),
        TabItem(MainActivity.TAB_SETTINGS, "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings, badge = if (updateBanner) 1 else 0),
    )

    Box(Modifier.fillMaxSize().background(Folio.colors.background)) {
        // Each tab marks its own scrolling content as the glass source; the bars must stay outside it.
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                label = "tab",
            ) { current ->
                when (current) {
                    MainActivity.TAB_UPDATES -> UpdatesTab(glass)
                    MainActivity.TAB_DISCOVER -> DiscoverTab(glass)
                    MainActivity.TAB_HISTORY -> HistoryTab(glass)
                    MainActivity.TAB_SETTINGS -> SettingsTab(glass)
                    else -> LibraryTab(glass, onDiscover = { tab = MainActivity.TAB_DISCOVER })
                }
            }
        }
        TabBar(tabs, tab, { tab = it }, glass, Modifier.align(Alignment.BottomCenter))
    }
}

private fun mutableStateOfTab(value: String) = androidx.compose.runtime.mutableStateOf(value)

/** Asks once for notification permission, for new-chapter alerts. */
@Composable
private fun NotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("folio", 0)
        if (!Notifications.canNotify(context) && !prefs.getBoolean("askedNotifications", false)) {
            prefs.edit().putBoolean("askedNotifications", true).apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
