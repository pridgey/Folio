@file:OptIn(ExperimentalMaterial3Api::class)

package dev.pridgey.folio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pridgey.folio.Graph
import dev.pridgey.folio.data.ReadingEntry
import dev.pridgey.folio.data.UpdateEntry
import dev.pridgey.folio.ui.LocalNav
import dev.pridgey.folio.ui.components.BottomBarSpacer
import dev.pridgey.folio.ui.components.CircleButton
import dev.pridgey.folio.ui.components.Cover
import dev.pridgey.folio.ui.components.EmptyState
import dev.pridgey.folio.ui.components.Format
import dev.pridgey.folio.ui.components.GlassState
import dev.pridgey.folio.ui.components.LargeTitle
import dev.pridgey.folio.ui.components.ProgressBar
import dev.pridgey.folio.ui.components.TopBar
import dev.pridgey.folio.ui.components.pressable
import dev.pridgey.folio.ui.theme.Folio
import dev.pridgey.folio.work.LibrarySync
import dev.pridgey.folio.work.LibraryUpdateWorker
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ Updates

@Composable
fun UpdatesTab(glass: GlassState) {
    val c = Folio.colors
    val nav = LocalNav.current
    val context = LocalContext.current
    val updates by remember { Graph.library.updates() }.collectAsStateWithLifecycle(null)
    val sync by LibrarySync.state.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 120 } }
    var refreshing by remember { mutableStateOf(false) }
    if (refreshing && sync == null) refreshing = false

    // Seen: clears the badge on the tab.
    DisposableEffect(Unit) {
        onDispose { Graph.settings.update { it.copy(lastUpdatesSeen = System.currentTimeMillis()) } }
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = refreshing || sync != null,
            onRefresh = { refreshing = true; LibraryUpdateWorker.runNow(context) },
        ) {
            LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                item { Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars).height(48.dp)) }
                item {
                    LargeTitle("Updates")
                    sync?.let { s ->
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text("Checking ${s.done} of ${s.total}…", style = Folio.type.footnote, color = c.secondaryLabel)
                            Spacer(Modifier.height(6.dp))
                            ProgressBar(if (s.total == 0) 0f else s.done / s.total.toFloat(), Modifier.fillMaxWidth())
                        }
                    }
                }
                val entries = updates
                if (entries != null && entries.isEmpty()) {
                    item {
                        EmptyState(
                            Icons.Rounded.Notifications, "No new chapters yet",
                            "New chapters from titles in your library appear here. Folio checks every few hours, or pull down to check now.",
                        )
                    }
                } else if (entries != null) {
                    val groups = entries.groupBy { Format.day(it.dateFetch) }
                    groups.forEach { (_, rows) ->
                        item(key = "h-${rows.first().dateFetch}") { DayHeader(Format.date(rows.first().dateFetch)) }
                        items(rows, key = { "u-${it.chapterId}" }) { u -> UpdateRow(u) { nav.read(u.mangaId, u.chapterId) } }
                    }
                }
                item { BottomBarSpacer() }
            }
        }
        TopBar("Updates", glass, showTitle = scrolled) {
            CircleButton(Icons.Rounded.Refresh, "Check now", { refreshing = true; LibraryUpdateWorker.runNow(context) })
        }
    }
}

@Composable
private fun DayHeader(text: String) {
    Text(
        text, style = Folio.type.headline, color = Folio.colors.label,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun UpdateRow(u: UpdateEntry, onClick: () -> Unit) {
    val c = Folio.colors
    val nav = LocalNav.current
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick, pressedScale = 0.985f).padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(
            u.sourceId, u.thumbnailUrl, u.title,
            Modifier.width(46.dp).pressable(onClick = { nav.details(u.mangaId, u.sourceId, u.thumbnailUrl) }),
            corner = 6.dp, elevation = 2.dp,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(u.title, style = Folio.type.subheadStrong, color = if (u.read) c.secondaryLabel else c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(u.chapterName, style = Folio.type.subhead, color = if (u.read) c.tertiaryLabel else c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(Format.relative(u.dateFetch), u.scanlator).joinToString(" · "),
                style = Folio.type.caption, color = c.tertiaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (!u.read) {
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(9.dp).clip(CircleShape).background(c.accent))
        }
    }
}

// ------------------------------------------------------------------ History

@Composable
fun HistoryTab(glass: GlassState) {
    val c = Folio.colors
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val history by remember { Graph.library.recentlyRead(300) }.collectAsStateWithLifecycle(null)
    val list = rememberLazyListState()
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 120 } }
    var confirmClear by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            item { Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars).height(48.dp)) }
            item { LargeTitle("History") }
            val entries = history
            if (entries != null && entries.isEmpty()) {
                item {
                    EmptyState(Icons.Rounded.History, "Nothing read yet", "The chapters you read show up here, so you can pick up right where you left off.")
                }
            } else if (entries != null) {
                entries.groupBy { Format.day(it.lastReadAt) }.forEach { (_, rows) ->
                    item(key = "h-${rows.first().lastReadAt}") { DayHeader(Format.date(rows.first().lastReadAt)) }
                    items(rows, key = { "r-${it.mangaId}" }) { entry ->
                        HistoryRow(
                            entry,
                            onClick = { nav.continueReading(entry.mangaId) },
                            onOpen = { nav.details(entry.mangaId, entry.sourceId, entry.thumbnailUrl) },
                            onRemove = { scope.launch { Graph.library.clearHistory(entry.mangaId) } },
                        )
                    }
                }
            }
            item { BottomBarSpacer() }
        }
        TopBar("History", glass, showTitle = scrolled) {
            if (!history.isNullOrEmpty()) CircleButton(Icons.Rounded.DeleteSweep, "Clear history", { confirmClear = true })
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("Your reading progress stays; only the list of what you read recently is cleared.") },
            confirmButton = {
                TextButton({ scope.launch { Graph.library.clearAllHistory() }; confirmClear = false }) { Text("Clear", color = c.danger) }
            },
            dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel") } },
            containerColor = c.elevated,
        )
    }
}

@Composable
private fun HistoryRow(entry: ReadingEntry, onClick: () -> Unit, onOpen: () -> Unit, onRemove: () -> Unit) {
    val c = Folio.colors
    val state = rememberSwipeToDismissBoxState()
    if (state.currentValue == SwipeToDismissBoxValue.EndToStart) onRemove()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(c.danger).padding(end = 24.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Rounded.RemoveCircleOutline, "Remove", tint = Color.White)
            }
        },
    ) {
        Row(
            Modifier.fillMaxWidth().background(c.background).pressable(onClick = onClick, pressedScale = 0.985f).padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(entry.sourceId, entry.thumbnailUrl, entry.title, Modifier.width(46.dp).pressable(onClick = onOpen), corner = 6.dp, elevation = 2.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = Folio.type.subheadStrong, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.chapterName, style = Folio.type.subhead, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val progress = when {
                        entry.read -> 1f
                        entry.pageCount > 0 -> (entry.lastPageRead + 1f) / entry.pageCount
                        else -> 0f
                    }
                    ProgressBar(progress, Modifier.width(80.dp), height = 3.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (entry.read) "Finished · ${Format.relative(entry.lastReadAt)}" else if (entry.pageCount > 0) "Page ${entry.lastPageRead + 1} of ${entry.pageCount} · ${Format.relative(entry.lastReadAt)}" else Format.relative(entry.lastReadAt),
                        style = Folio.type.caption, color = c.tertiaryLabel, maxLines = 1,
                    )
                }
            }
        }
    }
}
