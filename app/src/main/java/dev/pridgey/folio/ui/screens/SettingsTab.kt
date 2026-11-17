package dev.pridgey.folio.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pridgey.folio.Graph
import dev.pridgey.folio.data.Accent
import dev.pridgey.folio.data.ReaderMode
import dev.pridgey.folio.data.ThemeMode
import dev.pridgey.folio.source.ExtensionManager
import dev.pridgey.folio.ui.LocalNav
import dev.pridgey.folio.ui.components.BottomBarSpacer
import dev.pridgey.folio.ui.components.Format
import dev.pridgey.folio.ui.components.GlassState
import dev.pridgey.folio.ui.components.GroupedDivider
import dev.pridgey.folio.ui.components.GroupedRow
import dev.pridgey.folio.ui.components.GroupedSection
import dev.pridgey.folio.ui.components.LargeTitle
import dev.pridgey.folio.ui.components.SegmentedControl
import dev.pridgey.folio.ui.components.ToggleRow
import dev.pridgey.folio.ui.components.TopBar
import dev.pridgey.folio.ui.components.pressable
import dev.pridgey.folio.ui.theme.Folio
import dev.pridgey.folio.ui.theme.folioColors
import dev.pridgey.folio.update.UpdateRow
import dev.pridgey.folio.work.LibraryUpdateWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Blue = Color(0xFF0A84FF)
private val Green = Color(0xFF30C55A)
private val Orange = Color(0xFFFF9500)
private val Purple = Color(0xFFAF52DE)
private val Gray = Color(0xFF8E8E93)
private val Red = Color(0xFFFF3B30)
private val Teal = Color(0xFF30B0C7)

@Composable
fun SettingsTab(glass: GlassState) {
    val c = Folio.colors
    val nav = LocalNav.current
    val context = LocalContext.current
    val s by Graph.settings.values.collectAsStateWithLifecycle()
    val extensions by Graph.extensions.status.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 1 } }
    val update = { f: (dev.pridgey.folio.data.Settings.Values) -> dev.pridgey.folio.data.Settings.Values -> Graph.settings.update(f) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        Graph.scope.launch {
            val text = Graph.library.exportBackup()
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
            toast(context, "Library saved")
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        Graph.scope.launch {
            val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } } ?: return@launch
            val n = runCatching { Graph.library.importBackup(text) }
            toast(context, n.fold({ "Restored ${Format.plural(it, "title")}" }, { "That file isn't a Folio backup" }))
        }
    }

    Box(Modifier.fillMaxSize().background(c.grouped)) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            item { Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars).height(48.dp)) }
            item { LargeTitle("Settings", Modifier.padding(bottom = 0.dp)) }

            item {
                GroupedSection(header = "Appearance") {
                    Row(Modifier.padding(16.dp)) {
                        SegmentedControl(ThemeMode.entries, s.theme, { it.name.lowercase().replaceFirstChar(Char::uppercase) }, { m -> update { it.copy(theme = m) } }, Modifier.fillMaxWidth())
                    }
                    GroupedDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Accent.entries.forEach { accent ->
                            val color = folioColors(c.isDark, accent).accent
                            val selected = accent == s.accent
                            Box(
                                Modifier.size(38.dp).clip(CircleShape).background(color)
                                    .then(if (selected) Modifier.border(3.dp, c.surface, CircleShape) else Modifier)
                                    .pressable(onClick = { update { it.copy(accent = accent) } }, haptic = true),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) Icon(Icons.Rounded.Check, accent.label, tint = folioColors(c.isDark, accent).onAccent, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            item {
                GroupedSection(header = "Reading", footer = "Automatic picks continuous scroll for webtoons and right-to-left pages for manga.") {
                    MenuRow("Reading mode", Icons.Rounded.MenuBook, Blue, s.readerMode.label, ReaderMode.entries, { it.label }) { m -> update { it.copy(readerMode = m) } }
                    GroupedDivider(59.dp)
                    ToggleRow("Tap the sides to turn pages", s.tapZones, { v -> update { it.copy(tapZones = v) } }, icon = Icons.Rounded.TouchApp, iconBackground = Teal)
                    GroupedDivider(59.dp)
                    ToggleRow("Volume buttons turn pages", s.volumeKeys, { v -> update { it.copy(volumeKeys = v) } }, icon = Icons.Rounded.VolumeUp, iconBackground = Purple)
                    GroupedDivider(59.dp)
                    ToggleRow("Keep the screen on", s.keepScreenOn, { v -> update { it.copy(keepScreenOn = v) } }, icon = Icons.Rounded.LightMode, iconBackground = Orange)
                }
            }

            item {
                GroupedSection(header = "Library", footer = "Folio checks for new chapters in the background and lets you know.") {
                    MenuRow(
                        "Check for new chapters", Icons.Rounded.Schedule, Green,
                        intervalLabel(s.updateIntervalHours), listOf(0, 6, 12, 24, 48), ::intervalLabel,
                    ) { h -> update { it.copy(updateIntervalHours = h) }; LibraryUpdateWorker.schedule(context) }
                    GroupedDivider(59.dp)
                    ToggleRow("Only on Wi-Fi", s.updateOnlyOnWifi, { v -> update { it.copy(updateOnlyOnWifi = v) }; LibraryUpdateWorker.schedule(context) }, icon = Icons.Rounded.Wifi, iconBackground = Blue)
                    GroupedDivider(59.dp)
                    ToggleRow("Notify about new chapters", s.notifyNewChapters, { v -> update { it.copy(notifyNewChapters = v) } }, icon = Icons.Rounded.Notifications, iconBackground = Red)
                    GroupedDivider(59.dp)
                    ToggleRow("Download new chapters", s.autoDownload, { v -> update { it.copy(autoDownload = v) } }, icon = Icons.Rounded.CloudDownload, iconBackground = Teal)
                    GroupedDivider(59.dp)
                    ToggleRow("Delete downloads once read", s.deleteAfterRead, { v -> update { it.copy(deleteAfterRead = v) } }, icon = Icons.Rounded.DeleteForever, iconBackground = Gray)
                }
            }

            item {
                val mf = extensions[ExtensionManager.MANGAFIRE]
                GroupedSection(header = "Sources") {
                    GroupedRow(
                        "Sources", icon = Icons.Rounded.Extension, iconBackground = Orange, chevron = true,
                        value = mf?.installed?.let { "MangaFire ${it.versionName}" } ?: "Set up",
                        onClick = { nav.sources() },
                    )
                    GroupedDivider(59.dp)
                    GroupedRow("Downloads", icon = Icons.Rounded.Download, iconBackground = Green, chevron = true, onClick = { nav.downloads() })
                }
            }

            item {
                GroupedSection(header = "Backup", footer = "A backup holds your library and reading progress. Restore it on any phone running Folio.") {
                    GroupedRow("Save library to a file", icon = Icons.Rounded.Backup, iconBackground = Blue, onClick = { export.launch("folio-backup-${java.time.LocalDate.now()}.json") })
                    GroupedDivider(59.dp)
                    GroupedRow("Restore from a file", icon = Icons.Rounded.Restore, iconBackground = Purple, onClick = { import.launch(arrayOf("application/json", "text/plain", "*/*")) })
                }
            }

            item {
                GroupedSection(header = "About", footer = "Folio loads MangaFire through Keiyoushi's open-source extensions (Apache 2.0), checked against Keiyoushi's signing key, and reads MangaDex through its public API. Set in Inter (SIL Open Font License).") {
                    UpdateRow()
                    GroupedDivider(59.dp)
                    GroupedRow("Folio on GitHub", icon = Icons.Rounded.Info, iconBackground = Gray, chevron = true, onClick = {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/${dev.pridgey.folio.BuildConfig.UPDATE_REPO}")))
                    })
                }
            }
            item { BottomBarSpacer() }
        }
        TopBar("Settings", glass, showTitle = scrolled)
    }
}

private fun intervalLabel(h: Int) = when (h) {
    0 -> "Never"
    24 -> "Daily"
    48 -> "Every 2 days"
    else -> "Every $h hours"
}

private fun toast(context: Context, text: String) =
    android.os.Handler(android.os.Looper.getMainLooper()).post { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }

/** A row that opens a menu of choices, showing the current one on the right. */
@Composable
fun <T> MenuRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    iconColor: Color,
    value: String,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val c = Folio.colors
    var open by remember { mutableStateOf(false) }
    Box {
        GroupedRow(title, icon = icon, iconBackground = iconColor, value = value, chevron = true, onClick = { open = true })
        DropdownMenu(open, { open = false }, containerColor = c.elevated, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), modifier = Modifier) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(label(o), style = Folio.type.body) },
                    trailingIcon = { if (label(o) == value) Icon(Icons.Rounded.Check, null, tint = c.accent) },
                    onClick = { open = false; onSelect(o) },
                )
            }
        }
    }
}
