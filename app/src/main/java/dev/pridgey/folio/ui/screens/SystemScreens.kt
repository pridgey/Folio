@file:OptIn(ExperimentalMaterial3Api::class)

package dev.pridgey.folio.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.TwoStatePreference
import dev.pridgey.folio.Graph
import dev.pridgey.folio.R
import dev.pridgey.folio.source.ChallengeBroker
import dev.pridgey.folio.source.ExtensionManager
import dev.pridgey.folio.source.ExtensionStatus
import dev.pridgey.folio.source.Health
import dev.pridgey.folio.source.MangaDexSource
import dev.pridgey.folio.ui.LocalNav
import dev.pridgey.folio.ui.components.BarTextButton
import dev.pridgey.folio.ui.components.CircleButton
import dev.pridgey.folio.ui.components.EmptyState
import dev.pridgey.folio.ui.components.Format
import dev.pridgey.folio.ui.components.GroupedDivider
import dev.pridgey.folio.ui.components.GroupedRow
import dev.pridgey.folio.ui.components.GroupedSection
import dev.pridgey.folio.ui.components.PrimaryButton
import dev.pridgey.folio.ui.components.ProgressBar
import dev.pridgey.folio.ui.components.SearchField
import dev.pridgey.folio.ui.components.StatusDot
import dev.pridgey.folio.ui.components.ToggleRow
import dev.pridgey.folio.ui.components.TopBar
import dev.pridgey.folio.ui.components.glassSource
import dev.pridgey.folio.ui.components.rememberGlassState
import dev.pridgey.folio.ui.theme.Folio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Orange = Color(0xFFFF9500)
private val Blue = Color(0xFF0A84FF)
private val Green = Color(0xFF30C55A)
private val Gray = Color(0xFF8E8E93)
private val Purple = Color(0xFFAF52DE)

/** A screen with a back button and a grouped, scrolling body. */
@Composable
internal fun GroupedScreen(title: String, actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val c = Folio.colors
    val nav = LocalNav.current
    val glass = rememberGlassState()
    val list = rememberLazyListState()
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 1 } }
    Box(Modifier.fillMaxSize().background(c.grouped)) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().glassSource(glass), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            item { Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars).height(48.dp)) }
            item { Text(title, style = Folio.type.largeTitle, color = c.label, modifier = Modifier.padding(horizontal = 16.dp)) }
            content()
            item { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(24.dp)) }
        }
        TopBar(title, glass, showTitle = scrolled, onBack = { nav.back() }, actions = actions)
    }
}

// ------------------------------------------------------------------ Sources

@Composable
fun SourcesScreen() {
    val c = Folio.colors
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by Graph.extensions.status.collectAsStateWithLifecycle()
    val health by Graph.sources.health.collectAsStateWithLifecycle()
    val settings by Graph.settings.values.collectAsStateWithLifecycle()
    val mf = status[ExtensionManager.MANGAFIRE] ?: ExtensionStatus(ExtensionManager.MANGAFIRE, "MangaFire")
    val primary = Graph.sources.primary()
    var untrusted by remember { mutableStateOf<String?>(null) }

    GroupedScreen("Sources") {
        item {
            GroupedSection(
                header = "Primary",
                footer = "Folio installs MangaFire's source from Keiyoushi privately, only if it's signed with Keiyoushi's key. When the site changes and the source stops working, Folio fetches the fixed version straight away, and checks for one every few hours anyway.",
            ) {
                SourceHeader("MangaFire", mf, primary?.id?.let { health[it] })
                GroupedDivider()
                when {
                    mf.busy != null -> GroupedRow(
                        when (mf.busy) {
                            ExtensionStatus.Busy.CHECKING -> "Checking Keiyoushi…"
                            ExtensionStatus.Busy.DOWNLOADING -> "Downloading…"
                            ExtensionStatus.Busy.INSTALLING -> "Verifying and installing…"
                        },
                        icon = Icons.Rounded.Sync, iconBackground = Blue,
                    ) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.accent) }
                    mf.installed == null -> GroupedRow("Install MangaFire", icon = Icons.Rounded.Download, iconBackground = Green, titleColor = c.accent, onClick = {
                        scope.launch { Graph.extensions.install(ExtensionManager.MANGAFIRE) }
                    })
                    mf.updateAvailable -> GroupedRow("Update to ${mf.available?.versionName}", icon = Icons.Rounded.Download, iconBackground = Green, titleColor = c.accent, onClick = {
                        scope.launch { Graph.extensions.install(ExtensionManager.MANGAFIRE) }
                    })
                    else -> GroupedRow(
                        "Check for a new version", subtitle = mf.lastChecked.takeIf { it > 0 }?.let { "Last checked ${Format.relative(it).lowercase()}" },
                        icon = Icons.Rounded.Refresh, iconBackground = Blue,
                        onClick = { scope.launch { Graph.extensions.checkForUpdates(install = true) } },
                    )
                }
                if (mf.installed != null) {
                    GroupedDivider(59.dp)
                    MenuRow(
                        "Language", Icons.Rounded.Language, Purple, Format.languageName(settings.language),
                        Graph.sources.primaryLanguages(), Format::languageName,
                    ) { lang -> Graph.settings.update { it.copy(language = lang) } }
                    primary?.let { src ->
                        if (src.configurable != null) {
                            GroupedDivider(59.dp)
                            GroupedRow("Source settings", icon = Icons.Rounded.Settings, iconBackground = Gray, chevron = true, onClick = { nav.sourceSettings(src.id) })
                        }
                        GroupedDivider(59.dp)
                        GroupedRow("Open MangaFire", subtitle = "Pass a check by hand, or sign in", icon = Icons.Rounded.Public, iconBackground = Orange, chevron = true, onClick = { nav.web(src.homeUrl, "MangaFire") })
                    }
                }
                mf.error?.let { err ->
                    GroupedDivider()
                    Text(err, style = Folio.type.footnote, color = c.danger, modifier = Modifier.padding(16.dp))
                    Regex("""unknown key \(([0-9a-f]{64})\)""").find(err)?.groupValues?.get(1)?.let { key ->
                        GroupedRow("Review the new signing key", titleColor = c.accent, onClick = { untrusted = key })
                    }
                }
            }
        }

        item { OptionalSource(ExtensionManager.COMIX, status, health) }

        item {
            GroupedSection(
                header = "Fallback",
                footer = "MangaDex is built into Folio and uses MangaDex's official API, so it keeps working when scraped sites push back. When MangaFire can't load a title, Folio finds it on MangaDex for you.",
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFF6740)), contentAlignment = Alignment.Center) {
                        Text("MD", style = Folio.type.headline, color = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("MangaDex", style = Folio.type.headline, color = c.label)
                        Text("Built in · ${Format.languageName(settings.language)}", style = Folio.type.footnote, color = c.secondaryLabel)
                    }
                    HealthBadge(health[MangaDexSource.ID])
                }
                GroupedDivider()
                ToggleRow("Fall back to MangaDex", settings.fallbackEnabled, { v -> Graph.settings.update { it.copy(fallbackEnabled = v) } }, icon = Icons.Rounded.SwapHoriz, iconBackground = Green)
            }
        }

        item {
            GroupedSection(header = "Trust", footer = "Extensions run inside Folio, so Folio only loads ones signed with a key it trusts.") {
                Graph.extensions.trustedKeys.forEach { key ->
                    GroupedRow(
                        if (key in ExtensionManager.PINNED_KEYS) "Keiyoushi" else "Added by you",
                        subtitle = key.chunked(4).take(8).joinToString(" ") + " …",
                        icon = Icons.Rounded.VerifiedUser, iconBackground = Green,
                    )
                }
            }
        }

        item {
            GroupedSection(header = "Troubleshooting") {
                GroupedRow("Clear cookies", subtitle = "Signs out of sites and resets their checks", icon = Icons.Rounded.Cookie, iconBackground = Gray, onClick = {
                    Graph.network.cookieJar.clear()
                    android.widget.Toast.makeText(context, "Cookies cleared", android.widget.Toast.LENGTH_SHORT).show()
                })
                GroupedDivider(59.dp)
                GroupedRow("Reinstall MangaFire", icon = Icons.Rounded.Extension, iconBackground = Orange, onClick = {
                    scope.launch {
                        Graph.extensions.uninstall(ExtensionManager.MANGAFIRE)
                        Graph.extensions.install(ExtensionManager.MANGAFIRE)
                    }
                })
            }
        }
    }

    untrusted?.let { key ->
        AlertDialog(
            onDismissRequest = { untrusted = null },
            title = { Text("Trust a new signing key?") },
            text = { Text("The newest MangaFire source is signed with a key Folio hasn't seen:\n\n${key.chunked(4).joinToString(" ")}\n\nOnly trust it if Keiyoushi announced a new key. Anything signed with it can run inside Folio.") },
            confirmButton = {
                TextButton({
                    Graph.extensions.trustKey(key)
                    untrusted = null
                    scope.launch { Graph.extensions.install(ExtensionManager.MANGAFIRE) }
                }) { Text("Trust", color = c.danger) }
            },
            dismissButton = { TextButton({ untrusted = null }) { Text("Cancel") } },
            containerColor = c.elevated,
        )
    }
}

/** An extension that's only installed when asked for. */
@Composable
private fun OptionalSource(pkg: String, status: Map<String, ExtensionStatus>, health: Map<Long, Health>) {
    val c = Folio.colors
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val title = ExtensionManager.titleFor(pkg)
    val ext = status[pkg] ?: ExtensionStatus(pkg, title)
    val source = ext.installed?.sources?.firstOrNull()
    GroupedSection(
        header = "More sources",
        footer = "$title is optional and English only. It's installed from Keiyoushi with the same signature check, then searched alongside your other sources.",
    ) {
        SourceHeader(title, ext, source?.id?.let { health[it] })
        GroupedDivider()
        when {
            ext.busy != null -> GroupedRow(
                when (ext.busy) {
                    ExtensionStatus.Busy.CHECKING -> "Checking Keiyoushi…"
                    ExtensionStatus.Busy.DOWNLOADING -> "Downloading…"
                    ExtensionStatus.Busy.INSTALLING -> "Verifying and installing…"
                },
                icon = Icons.Rounded.Sync, iconBackground = Blue,
            ) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.accent) }
            ext.installed == null -> GroupedRow("Install $title", icon = Icons.Rounded.Download, iconBackground = Green, titleColor = c.accent, onClick = {
                scope.launch { Graph.extensions.install(pkg) }
            })
            ext.updateAvailable -> GroupedRow("Update to ${ext.available?.versionName}", icon = Icons.Rounded.Download, iconBackground = Green, titleColor = c.accent, onClick = {
                scope.launch { Graph.extensions.install(pkg) }
            })
        }
        source?.let { src ->
            if (ext.busy == null && ext.updateAvailable) GroupedDivider(59.dp)
            if (src.configurable != null) {
                GroupedRow("Source settings", icon = Icons.Rounded.Settings, iconBackground = Gray, chevron = true, onClick = { nav.sourceSettings(src.id) })
                GroupedDivider(59.dp)
            }
            GroupedRow("Open $title", subtitle = "Pass a check by hand, or sign in", icon = Icons.Rounded.Public, iconBackground = Orange, chevron = true, onClick = { nav.web(src.homeUrl, title) })
            GroupedDivider(59.dp)
            GroupedRow("Remove $title", subtitle = "Titles stay in your library", icon = Icons.Rounded.Extension, iconBackground = Gray, titleColor = c.danger, onClick = {
                Graph.extensions.uninstall(pkg)
            })
        }
        ext.error?.let { err ->
            GroupedDivider()
            Text(err, style = Folio.type.footnote, color = c.danger, modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun SourceHeader(name: String, status: ExtensionStatus, health: Health?) {
    val c = Folio.colors
    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Color(0xFFFF7A45), Color(0xFFE2382A)))), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Bolt, null, tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = Folio.type.headline, color = c.label)
            val installed = status.installed
            Text(
                if (installed == null) "Not installed" else "Version ${installed.versionName} · signed by Keiyoushi",
                style = Folio.type.footnote, color = c.secondaryLabel,
            )
        }
        HealthBadge(health)
    }
}

@Composable
private fun HealthBadge(health: Health?) {
    val c = Folio.colors
    val (color, text) = when (health) {
        is Health.Ok -> c.success to "Working"
        is Health.Failing -> if (health.healing) c.warning to "Updating" else c.danger to "Trouble"
        else -> c.tertiaryLabel to "Ready"
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color, size = 7.dp)
        Spacer(Modifier.width(6.dp))
        Text(text, style = Folio.type.captionStrong, color = color)
    }
}

// ------------------------------------------------------------------ Source settings (an extension's own preferences)

@Composable
@SuppressLint("RestrictedApi") // Hosting an extension's own PreferenceScreen needs a PreferenceManager.
fun SourceSettingsScreen(sourceId: Long) {
    val c = Folio.colors
    val context = LocalContext.current
    val source = Graph.sources.get(sourceId)
    val configurable = source?.configurable
    var tick by remember { mutableIntStateOf(0) }
    val screen = remember(sourceId) {
        configurable?.let { cfg ->
            runCatching {
                val manager = PreferenceManager(context).apply { sharedPreferencesName = "source_$sourceId" }
                manager.createPreferenceScreen(context).also { cfg.setupPreferenceScreen(it) }
            }.getOrNull()
        }
    }
    GroupedScreen(source?.name?.let { "$it Settings" } ?: "Source Settings") {
        if (screen == null) {
            item { EmptyState(Icons.Rounded.Settings, "No settings", "This source doesn't have any settings.") }
            return@GroupedScreen
        }
        @Suppress("UNUSED_EXPRESSION") tick
        val groups = mutableListOf<Pair<String?, List<Preference>>>()
        var loose = mutableListOf<Preference>()
        for (i in 0 until screen.preferenceCount) {
            val p = screen.getPreference(i)
            if (!p.isVisible) continue
            if (p is PreferenceCategory) {
                if (loose.isNotEmpty()) groups += null to loose
                loose = mutableListOf()
                groups += p.title?.toString() to p.children()
            } else {
                loose += p
            }
        }
        if (loose.isNotEmpty()) groups += null to loose
        groups.forEach { (title, prefs) ->
            item {
                GroupedSection(header = title) {
                    prefs.forEachIndexed { i, p ->
                        if (i > 0) GroupedDivider()
                        PreferenceRow(p) { tick++ }
                    }
                }
            }
        }
    }
}

private fun PreferenceGroup.children(): List<Preference> = (0 until preferenceCount).map { getPreference(it) }.filter { it.isVisible }

@Composable
private fun PreferenceRow(p: Preference, changed: () -> Unit) {
    val c = Folio.colors
    var editing by remember { mutableStateOf(false) }
    val title = p.title?.toString() ?: p.key ?: ""
    val summary = when (p) {
        is ListPreference -> p.entry?.toString() ?: p.summary?.toString()
        is EditTextPreference -> p.text?.takeIf { it.isNotBlank() } ?: p.summary?.toString()
        else -> p.summary?.toString()
    }
    when (p) {
        is TwoStatePreference -> ToggleRow(title, p.isChecked, { v -> if (p.callChangeListener(v)) { p.isChecked = v; changed() } }, subtitle = summary, enabled = p.isEnabled)
        else -> GroupedRow(title, subtitle = summary, chevron = p is ListPreference || p is MultiSelectListPreference || p is EditTextPreference, onClick = if (p.isEnabled) ({ editing = true }) else null)
    }
    if (!editing) return
    when (p) {
        is ListPreference -> ChoiceDialog(title, p.entries.map { it.toString() }, setOfNotNull(p.findIndexOfValue(p.value).takeIf { it >= 0 }), multi = false, onDismiss = { editing = false }) { chosen ->
            val v = p.entryValues[chosen.first()].toString()
            if (p.callChangeListener(v)) p.value = v
            changed()
        }
        is MultiSelectListPreference -> ChoiceDialog(title, p.entries.map { it.toString() }, p.entryValues.indices.filter { p.entryValues[it].toString() in p.values }.toSet(), multi = true, onDismiss = { editing = false }) { chosen ->
            val v = chosen.map { p.entryValues[it].toString() }.toSet()
            if (p.callChangeListener(v)) p.values = v
            changed()
        }
        is EditTextPreference -> {
            var text by remember { mutableStateOf(p.text.orEmpty()) }
            AlertDialog(
                onDismissRequest = { editing = false },
                title = { Text(title) },
                text = { SearchField(text, { text = it }, Modifier.fillMaxWidth(), placeholder = p.dialogMessage?.toString() ?: title) },
                confirmButton = { TextButton({ if (p.callChangeListener(text)) p.text = text; editing = false; changed() }) { Text("Save") } },
                dismissButton = { TextButton({ editing = false }) { Text("Cancel") } },
                containerColor = c.elevated,
            )
        }
        else -> editing = false
    }
}

@Composable
private fun ChoiceDialog(title: String, options: List<String>, selected: Set<Int>, multi: Boolean, onDismiss: () -> Unit, onDone: (Set<Int>) -> Unit) {
    val c = Folio.colors
    var chosen by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(options.indices.toList()) { i ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (i in chosen) c.accentSoft else Color.Transparent)
                            .clickable {
                                if (multi) chosen = if (i in chosen) chosen - i else chosen + i
                                else { onDone(setOf(i)); onDismiss() }
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(options[i], style = Folio.type.body, color = c.label, modifier = Modifier.weight(1f))
                        if (i in chosen) Icon(Icons.Rounded.CheckCircle, null, tint = c.accent)
                    }
                }
            }
        },
        confirmButton = { if (multi) TextButton({ onDone(chosen); onDismiss() }) { Text("Done") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        containerColor = c.elevated,
    )
}

// ------------------------------------------------------------------ Downloads

@Composable
fun DownloadsScreen() {
    val c = Folio.colors
    val scope = rememberCoroutineScope()
    val queue by Graph.downloads.queue.collectAsStateWithLifecycle(emptyList())
    val progress by Graph.downloads.progress.collectAsStateWithLifecycle()
    val downloaded by Graph.downloads.downloaded.collectAsStateWithLifecycle()
    var size by remember { mutableStateOf<Long?>(null) }
    var cache by remember { mutableStateOf<Long?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(downloaded) {
        size = withContext(Dispatchers.IO) { Graph.downloads.sizeBytes() }
        cache = withContext(Dispatchers.IO) { context.cacheDir.resolve("images").walkBottomUp().filter { it.isFile }.sumOf { it.length() } }
    }
    val titles = remember(queue) { mutableStateOf<Map<Long, String>>(emptyMap()) }
    LaunchedEffect(queue) {
        titles.value = queue.associate { q ->
            val ch = Graph.library.chapter(q.chapterId)
            val m = Graph.library.get(q.mangaId)
            q.chapterId to listOfNotNull(m?.displayTitle, ch?.name).joinToString(" · ")
        }
    }

    GroupedScreen("Downloads") {
        item {
            GroupedSection(header = "Queue", footer = if (queue.isEmpty()) "Download chapters from a title's page to read them offline." else null) {
                if (queue.isEmpty()) {
                    GroupedRow("Nothing downloading", icon = Icons.Rounded.Download, iconBackground = Green)
                } else {
                    queue.forEachIndexed { i, q ->
                        if (i > 0) GroupedDivider()
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                            Text(titles.value[q.chapterId] ?: "Chapter", style = Folio.type.subheadStrong, color = c.label, maxLines = 1)
                            val p = progress?.takeIf { it.chapterId == q.chapterId }
                            if (p != null) {
                                Spacer(Modifier.height(6.dp))
                                ProgressBar(p.done / p.total.coerceAtLeast(1).toFloat(), Modifier.fillMaxWidth())
                                Text("${p.done} of ${p.total} pages", style = Folio.type.caption, color = c.secondaryLabel, modifier = Modifier.padding(top = 4.dp))
                            } else {
                                Text(if (i == 0) "Starting…" else "Waiting", style = Folio.type.caption, color = c.secondaryLabel)
                            }
                        }
                    }
                    GroupedDivider()
                    GroupedRow("Cancel all", titleColor = c.danger, onClick = { scope.launch { Graph.downloads.cancelAll() } })
                }
            }
        }
        item {
            GroupedSection(header = "Storage") {
                GroupedRow("Downloaded chapters", value = "${downloaded.size} · ${size?.let(Format::bytes) ?: "…"}", icon = Icons.Rounded.AutoStories, iconBackground = Blue)
                GroupedDivider(59.dp)
                GroupedRow("Image cache", value = cache?.let(Format::bytes) ?: "…", icon = Icons.Rounded.Shield, iconBackground = Gray, onClick = {
                    scope.launch {
                        coil3.SingletonImageLoader.get(context).diskCache?.clear()
                        coil3.SingletonImageLoader.get(context).memoryCache?.clear()
                        cache = 0
                    }
                })
                GroupedDivider(59.dp)
                GroupedRow("Delete all downloads", titleColor = c.danger, icon = Icons.Rounded.DeleteForever, iconBackground = Color(0xFFFF3B30), onClick = { confirm = true })
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete all downloads?") },
            text = { Text("Downloaded chapters are removed from this phone. You can download them again any time.") },
            confirmButton = { TextButton({ scope.launch { Graph.downloads.deleteAll() }; confirm = false }) { Text("Delete", color = c.danger) } },
            dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
            containerColor = c.elevated,
        )
    }
}

// ------------------------------------------------------------------ Web

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(url: String, title: String) {
    val c = Folio.colors
    val nav = LocalNav.current
    val context = LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    var pageTitle by remember { mutableStateOf(title) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    Column(Modifier.fillMaxSize().background(c.background)) {
        TopBar(pageTitle, null, showTitle = true, onBack = { if (webView?.canGoBack() == true) webView?.goBack() else nav.back() }) {
            CircleButton(Icons.Rounded.Refresh, "Reload", { webView?.reload() })
            Spacer(Modifier.width(8.dp))
            CircleButton(Icons.AutoMirrored.Rounded.OpenInNew, "Open in browser", {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webView?.url ?: url))) }
            })
        }
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp), color = c.accent, trackColor = Color.Transparent)
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = Graph.network.defaultUserAgent
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = WebViewClient()
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                        override fun onReceivedTitle(view: WebView, t: String?) { if (!t.isNullOrBlank()) pageTitle = t }
                    }
                    loadUrl(url)
                    webView = this
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
        )
    }
}

// ------------------------------------------------------------------ Human checks

/** When a site wants a human to pass a check, show it here; the waiting request carries on after. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChallengeSheet() {
    val c = Folio.colors
    val challenge by ChallengeBroker.current.collectAsStateWithLifecycle()
    val current = challenge ?: return
    var solved by remember(current) { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(current) {
        while (!solved) {
            delay(500)
            if (current.solvedNow()) solved = true
        }
        delay(900)
        ChallengeBroker.dismiss(current)
    }
    ModalBottomSheet(onDismissRequest = { ChallengeBroker.dismiss(current) }, sheetState = sheet, containerColor = c.elevated) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.86f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Quick check", style = Folio.type.title3, color = c.label)
                    Text("${current.host} wants to make sure you're a person. Complete it below and Folio will carry on.", style = Folio.type.footnote, color = c.secondaryLabel)
                }
                BarTextButton("Cancel", { ChallengeBroker.dismiss(current) })
            }
            Spacer(Modifier.height(12.dp))
            AnimatedContent(solved, transitionSpec = { (fadeIn(tween(250)) + scaleIn(initialScale = 0.9f)) togetherWith fadeOut(tween(150)) }, label = "solved") { done ->
                if (done) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = c.success, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("You're through", style = Folio.type.headline, color = c.label)
                    }
                } else {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString = current.userAgent
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webViewClient = WebViewClient()
                                loadUrl(current.url)
                            }
                        },
                        onRelease = { it.destroy() },
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp)),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Onboarding

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val c = Folio.colors
    val scope = rememberCoroutineScope()
    val status by Graph.extensions.status.collectAsStateWithLifecycle()
    val mf = status[ExtensionManager.MANGAFIRE]
    var started by remember { mutableStateOf(false) }
    val finish = {
        Graph.settings.update { it.copy(onboarded = true) }
        onDone()
    }
    LaunchedEffect(mf?.installed) { if (started && mf?.installed != null) { delay(700); finish() } }

    Column(
        Modifier.fillMaxSize().background(c.background).windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.8f))
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(24.dp))) {
            androidx.compose.foundation.Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
            androidx.compose.foundation.Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(22.dp))
        Text("Welcome to Folio", style = Folio.type.largeTitle, color = c.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Feature(Icons.Rounded.AutoStories, "A reader that gets out of the way", "Pages, right-to-left, or one long scroll for webtoons. Folio picks the right one.")
        Feature(Icons.Rounded.Sync, "Fixes itself", "When MangaFire changes, Folio fetches the fixed source by itself. If it still fights back, MangaDex takes over.")
        Feature(Icons.Rounded.Download, "Read anywhere", "New chapters arrive in the background. Download them for the train.")
        Spacer(Modifier.weight(1f))
        when {
            mf?.installed != null && started -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = c.success)
                Spacer(Modifier.width(8.dp))
                Text("MangaFire is ready", style = Folio.type.headline, color = c.label)
            }
            started && mf?.busy != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(28.dp), color = c.accent, strokeWidth = 2.5.dp)
                Spacer(Modifier.height(10.dp))
                Text(
                    when (mf.busy) {
                        ExtensionStatus.Busy.CHECKING -> "Finding MangaFire…"
                        ExtensionStatus.Busy.DOWNLOADING -> "Downloading MangaFire…"
                        ExtensionStatus.Busy.INSTALLING -> "Checking its signature…"
                    },
                    style = Folio.type.subhead, color = c.secondaryLabel,
                )
            }
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                mf?.error?.takeIf { started }?.let {
                    Text("Couldn't set up MangaFire: $it", style = Folio.type.footnote, color = c.danger, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                }
                PrimaryButton(if (started) "Try Again" else "Get Started", {
                    started = true
                    scope.launch {
                        if (Graph.extensions.status.value[ExtensionManager.MANGAFIRE]?.installed != null) finish()
                        else Graph.extensions.install(ExtensionManager.MANGAFIRE)
                    }
                }, Modifier.fillMaxWidth())
                if (started) {
                    BarTextButton("Continue with MangaDex only", { finish() }, Modifier.padding(top = 6.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Feature(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    val c = Folio.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.padding(top = 2.dp).size(30.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = Folio.type.headline, color = c.label)
            Text(body, style = Folio.type.subhead, color = c.secondaryLabel)
        }
    }
}
