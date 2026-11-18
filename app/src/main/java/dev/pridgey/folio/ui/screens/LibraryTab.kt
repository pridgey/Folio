@file:OptIn(ExperimentalMaterial3Api::class)

package dev.pridgey.folio.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import dev.pridgey.folio.ui.theme.rememberCoverColor
import dev.pridgey.folio.ui.theme.CoverTint
import dev.pridgey.folio.ui.theme.CoverAccent
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pridgey.folio.Graph
import dev.pridgey.folio.data.LibraryItem
import dev.pridgey.folio.data.LibrarySort
import dev.pridgey.folio.data.ReadingStatus
import dev.pridgey.folio.ui.AppLock
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import dev.pridgey.folio.data.ReadingEntry
import dev.pridgey.folio.ui.LocalNav
import dev.pridgey.folio.ui.components.BottomBarSpacer
import dev.pridgey.folio.ui.components.CircleButton
import dev.pridgey.folio.ui.components.CountBadge
import dev.pridgey.folio.ui.components.Cover
import dev.pridgey.folio.ui.components.EmptyState
import dev.pridgey.folio.ui.components.Format
import dev.pridgey.folio.ui.components.GlassState
import dev.pridgey.folio.ui.components.glassSource
import dev.pridgey.folio.ui.components.LargeTitle
import dev.pridgey.folio.ui.components.ProgressBar
import dev.pridgey.folio.ui.components.SearchField
import dev.pridgey.folio.ui.components.SectionHeading
import dev.pridgey.folio.ui.components.TopBar
import dev.pridgey.folio.ui.components.pressable
import dev.pridgey.folio.ui.theme.Folio
import dev.pridgey.folio.update.UpdateBanner
import dev.pridgey.folio.update.rememberUpdateBannerVisible
import dev.pridgey.folio.work.LibrarySync
import dev.pridgey.folio.work.LibraryUpdateWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class LibraryViewModel : ViewModel() {
    private val visible = combine(Graph.library.library(), Graph.library.hiddenTitles()) { list, hidden -> list to hidden }

    val query = MutableStateFlow("")
    val filter = MutableStateFlow<LibraryFilter>(LibraryFilter.All)
    val shelves = Graph.library.shelves().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val members = Graph.library.shelfMembers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val shelfCounts = combine(members, Graph.library.library()) { m, lib ->
        val ids = lib.map { it.id }.toHashSet()
        m.filter { it.mangaId in ids }.groupingBy { it.shelfId }.eachCount()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** The library minus private titles, for the filter bar's counts. */
    val all = visible.map { (list, hidden) -> list.filter { it.id !in hidden } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val items = combine(visible, Graph.settings.values, query, filter, members) { (all, hidden), settings, q, f, m ->
        // Private titles only appear inside their own shelf.
        val list = if (f is LibraryFilter.Shelf) all else all.filter { it.id !in hidden }
        val scoped = when (f) {
            LibraryFilter.All -> list
            is LibraryFilter.Status -> list.filter { ReadingStatus.of(it.readingStatus) == f.status }
            is LibraryFilter.Shelf -> {
                val on = m.filter { it.shelfId == f.id }.map { it.mangaId }.toHashSet()
                list.filter { it.id in on }
            }
        }
        val filtered = if (q.isBlank()) scoped else scoped.filter { it.title.contains(q.trim(), ignoreCase = true) }
        when (settings.librarySort) {
            LibrarySort.LAST_READ -> filtered.sortedWith(compareByDescending<LibraryItem> { it.lastReadAt }.thenByDescending { it.dateAdded })
            LibrarySort.LAST_UPDATED -> filtered.sortedByDescending { it.latestUpload }
            LibrarySort.UNREAD -> filtered.sortedWith(compareByDescending<LibraryItem> { it.unread }.thenBy { it.title.lowercase() })
            LibrarySort.TITLE -> filtered.sortedBy { it.title.lowercase() }
            LibrarySort.DATE_ADDED -> filtered.sortedByDescending { it.dateAdded }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val continueReading = Graph.library.recentlyRead(12)
        .map { list -> list.filter { !it.read || it.lastReadAt > System.currentTimeMillis() - 30L * 24 * 3600 * 1000 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryTab(glass: GlassState, onDiscover: () -> Unit, vm: LibraryViewModel = viewModel()) {
    val c = Folio.colors
    val nav = LocalNav.current
    val context = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle()
    val recent by vm.continueReading.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val settings by Graph.settings.values.collectAsStateWithLifecycle()
    val sync by LibrarySync.state.collectAsStateWithLifecycle()
    val grid = rememberLazyGridState()
    val scrolled by remember { derivedStateOf { grid.firstVisibleItemIndex > 1 || (grid.firstVisibleItemIndex == 1 && grid.firstVisibleItemScrollOffset > 0) } }
    val showBanner = rememberUpdateBannerVisible()
    var sortMenu by remember { mutableStateOf(false) }
    var refreshing by rememberSaveable { mutableStateOf(false) }
    val filter by vm.filter.collectAsStateWithLifecycle()
    val shelves by vm.shelves.collectAsStateWithLifecycle()
    val shelfCounts by vm.shelfCounts.collectAsStateWithLifecycle()
    val whole by vm.all.collectAsStateWithLifecycle()
    var organizing by remember { mutableStateOf<LibraryItem?>(null) }
    var newShelf by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<dev.pridgey.folio.data.ShelfEntity?>(null) }
    if (refreshing && sync == null) refreshing = false

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = refreshing || sync != null,
            onRefresh = {
                refreshing = true
                LibraryUpdateWorker.runNow(context)
            },
            modifier = Modifier.fillMaxSize().glassSource(glass),
        ) {
            LazyVerticalGrid(
                state = grid,
                columns = GridCells.Fixed(settings.libraryColumns.coerceIn(2, 5)),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "top") { Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars).height(48.dp)) }
                item(span = { GridItemSpan(maxLineSpan) }, key = "title") {
                    Column {
                        LargeTitle("Library", inset = 0.dp)
                        SearchField(query, { vm.query.value = it }, Modifier.fillMaxWidth().padding(bottom = 4.dp), placeholder = "Search your library")
                        if (whole.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            LibraryFilterBar(
                                whole, shelves, shelfCounts, filter,
                                onSelect = { f ->
                                    val private = (f as? LibraryFilter.Shelf)?.let { s -> shelves.firstOrNull { it.id == s.id }?.hidden } == true
                                    val activity = context as? android.app.Activity
                                    if (private && !AppLock.privateOpen.value && activity != null) {
                                        AppLock.authenticate(activity, "Open private shelf") { ok -> if (ok) { AppLock.openPrivate(); vm.filter.value = f } }
                                    } else {
                                        vm.filter.value = f
                                    }
                                },
                                onNewShelf = { newShelf = true }, onEditShelf = { editing = it },
                            )
                        }
                        AnimatedVisibility(sync != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            sync?.let { s ->
                                Column(Modifier.padding(top = 12.dp)) {
                                    Text("Checking ${s.done} of ${s.total} for new chapters…", style = Folio.type.footnote, color = c.secondaryLabel)
                                    Spacer(Modifier.height(6.dp))
                                    ProgressBar(if (s.total == 0) 0f else s.done / s.total.toFloat(), Modifier.fillMaxWidth())
                                }
                            }
                        }
                        if (showBanner) {
                            Spacer(Modifier.height(12.dp))
                            UpdateBanner()
                        }
                    }
                }

                if (recent.isNotEmpty() && query.isBlank() && filter == LibraryFilter.All) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "continue") {
                        Column {
                            Text("Continue reading", style = Folio.type.title2, color = c.label, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(end = 16.dp),
                                modifier = Modifier.padding(end = 0.dp),
                            ) {
                                items(recent, key = { it.mangaId }) { entry -> ContinueCard(entry, onOpenTitle = { nav.details(entry.mangaId, entry.sourceId, entry.thumbnailUrl) }) { nav.continueReading(entry.mangaId) } }
                            }
                        }
                    }
                }

                val list = items
                val shelf = (filter as? LibraryFilter.Shelf)?.let { f -> shelves.firstOrNull { it.id == f.id } }
                if (shelf != null && list != null) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "shelf-header") { ShelfHeader(shelf, list, onEdit = { editing = shelf }) }
                }
                if (list != null && list.isEmpty() && query.isBlank() && filter != LibraryFilter.All) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "empty-filter") {
                        Text(
                            if (shelf != null) "Nothing on this shelf yet. Long-press a title, or use Organize on its page, to add it."
                            else "No titles here yet. Long-press a title to set its status.",
                            style = Folio.type.subhead, color = c.secondaryLabel, modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                } else if (list != null && list.isEmpty() && query.isBlank()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                        EmptyState(
                            icon = Icons.Rounded.AutoStories,
                            title = "Your library is empty",
                            message = "Titles you add show up here, with new chapters checked for you in the background.",
                            action = "Discover manga",
                            onAction = onDiscover,
                        )
                    }
                } else if (list != null) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "heading") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Text(
                                when {
                                    query.isNotBlank() -> "Results"
                                    filter is LibraryFilter.Status -> (filter as LibraryFilter.Status).status.label
                                    shelf != null -> "On this shelf"
                                    else -> "All titles"
                                },
                                style = Folio.type.title2, color = c.label, modifier = Modifier.weight(1f),
                            )
                            Text(Format.plural(list.size, "title"), style = Folio.type.footnote, color = c.secondaryLabel)
                        }
                    }
                    items(list, key = { it.id }) { item ->
                        LibraryTile(item, Modifier.animateItem(), onLongClick = { organizing = item }) {
                            nav.details(item.id, item.sourceId, item.thumbnailUrl, "lib-${item.id}")
                        }
                    }
                    if (list.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text("Nothing in your library matches “$query”.", style = Folio.type.subhead, color = c.secondaryLabel, modifier = Modifier.padding(vertical = 24.dp))
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { BottomBarSpacer() }
            }
        }

        TopBar("Library", glass, showTitle = scrolled) {
            Box {
                CircleButton(Icons.AutoMirrored.Rounded.Sort, "Sort", { sortMenu = true })
                DropdownMenu(sortMenu, { sortMenu = false }, shape = RoundedCornerShape(14.dp), containerColor = c.elevated) {
                    Text("Sort by", style = Folio.type.footnote, color = c.secondaryLabel, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    LibrarySort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.label, style = Folio.type.body) },
                            trailingIcon = { if (sort == settings.librarySort) Icon(Icons.Rounded.Check, null, tint = c.accent) },
                            onClick = {
                                Graph.settings.update { it.copy(librarySort = sort) }
                                sortMenu = false
                            },
                        )
                    }
                    HorizontalDivider(color = c.separator)
                    Text("Grid", style = Folio.type.footnote, color = c.secondaryLabel, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    listOf(2, 3, 4).forEach { n ->
                        DropdownMenuItem(
                            text = { Text("$n per row", style = Folio.type.body) },
                            leadingIcon = { Icon(Icons.Rounded.GridView, null) },
                            trailingIcon = { if (n == settings.libraryColumns) Icon(Icons.Rounded.Check, null, tint = c.accent) },
                            onClick = {
                                Graph.settings.update { it.copy(libraryColumns = n) }
                                sortMenu = false
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            CircleButton(Icons.Rounded.Refresh, "Check for new chapters", {
                refreshing = true
                LibraryUpdateWorker.runNow(context)
            })
        }
    }
    LibraryDialogs(
        organizing, onOrganized = { organizing = null },
        newShelf, onNewShelfDone = { id -> newShelf = false; if (id != null) vm.filter.value = LibraryFilter.Shelf(id) },
        editing, onEditDone = { editing = null },
        onDeleted = { id -> if (vm.filter.value == LibraryFilter.Shelf(id)) vm.filter.value = LibraryFilter.All },
    )
}

@Composable
private fun LibraryDialogs(
    organizing: LibraryItem?,
    onOrganized: () -> Unit,
    newShelf: Boolean,
    onNewShelfDone: (Long?) -> Unit,
    editing: dev.pridgey.folio.data.ShelfEntity?,
    onEditDone: () -> Unit,
    onDeleted: (Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    organizing?.let { OrganizeSheet(it.id, it.title, onDismiss = onOrganized) }
    if (newShelf) {
        ShelfNameDialog("New shelf", "", onDismiss = { onNewShelfDone(null) }) { name, hidden ->
            scope.launch {
                if (hidden) AppLock.openPrivate()
                onNewShelfDone(Graph.library.createShelf(name, hidden))
            }
        }
    }
    editing?.let { shelf ->
        ShelfNameDialog(
            "Edit shelf", shelf.name, onDismiss = onEditDone,
            onDelete = { scope.launch { Graph.library.deleteShelf(shelf.id) }; onDeleted(shelf.id); onEditDone() },
            initialHidden = shelf.hidden,
        ) { name, hidden -> scope.launch { Graph.library.editShelf(shelf.id, name, hidden) }; onEditDone() }
    }
}

@Composable
private fun LibraryTile(item: LibraryItem, modifier: Modifier = Modifier, onLongClick: () -> Unit, onClick: () -> Unit) {
    val c = Folio.colors
    Column(modifier.pressable(onClick = onClick, onLongClick = onLongClick)) {
        Box {
            Cover(item.sourceId, item.thumbnailUrl, item.title, Modifier.fillMaxWidth(), sharedKey = "lib-${item.id}")
            if (item.unread > 0) {
                CountBadge(item.unread, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(item.title, style = Folio.type.footnoteStrong, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            when {
                item.total == 0 -> "Not checked yet"
                item.unread == 0 -> "Up to date"
                else -> "${item.unread} unread"
            },
            style = Folio.type.caption, color = if (item.unread > 0) c.accent else c.secondaryLabel, maxLines = 1,
        )
    }
}

@Composable
private fun ContinueCard(entry: ReadingEntry, onOpenTitle: () -> Unit, onClick: () -> Unit) = CoverAccent(entry.sourceId, entry.thumbnailUrl) {
    val c = Folio.colors
    val tint = rememberCoverColor(entry.sourceId, entry.thumbnailUrl)
    val backdrop by animateColorAsState(tint?.let { CoverTint.asBackdrop(it, c.isDark) } ?: c.surface, tween(450), label = "cardBackdrop")
    val progress = when {
        entry.read -> 1f
        entry.pageCount > 0 -> (entry.lastPageRead + 1f) / entry.pageCount
        else -> 0f
    }
    Row(
        Modifier
            .width(304.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(backdrop)
            .border(0.75.dp, c.coverStroke, RoundedCornerShape(22.dp))
            .pressable(onClick = onClick, onLongClick = onOpenTitle)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The cover opens the title's page; the rest of the card keeps reading.
        Cover(entry.sourceId, entry.thumbnailUrl, entry.title, Modifier.width(68.dp).pressable(onClick = onOpenTitle, haptic = true), corner = 10.dp, elevation = 6.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (entry.read) "FINISHED" else "CONTINUE", style = Folio.type.eyebrow, color = c.accent)
            Spacer(Modifier.height(3.dp))
            Text(entry.title, style = Folio.type.title3.copy(fontSize = 18.sp, lineHeight = 21.sp), color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(entry.chapterName, style = Folio.type.footnote, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressBar(progress, Modifier.weight(1f), track = c.label.copy(alpha = 0.12f))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(50)).background(c.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, "Continue", tint = c.onAccent, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
