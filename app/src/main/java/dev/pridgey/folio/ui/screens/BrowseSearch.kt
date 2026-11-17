@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.pridgey.folio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pridgey.folio.Graph
import dev.pridgey.folio.source.MangaDexSource
import dev.pridgey.folio.source.SourceManager
import dev.pridgey.folio.ui.LocalNav
import dev.pridgey.folio.ui.Routes
import dev.pridgey.folio.ui.components.BarTextButton
import dev.pridgey.folio.ui.components.Chip
import dev.pridgey.folio.ui.components.CircleButton
import dev.pridgey.folio.ui.components.Cover
import dev.pridgey.folio.ui.components.EmptyState
import dev.pridgey.folio.ui.components.FolioSwitch
import dev.pridgey.folio.ui.components.PrimaryButton
import dev.pridgey.folio.ui.components.SearchField
import dev.pridgey.folio.ui.components.SectionHeading
import dev.pridgey.folio.ui.components.SegmentedControl
import dev.pridgey.folio.ui.components.SkeletonBox
import dev.pridgey.folio.ui.components.StatusDot
import dev.pridgey.folio.ui.components.TopBar
import dev.pridgey.folio.ui.components.glassSource
import dev.pridgey.folio.ui.components.pressable
import dev.pridgey.folio.ui.components.rememberGlassState
import dev.pridgey.folio.ui.theme.Folio
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ Browse

class BrowseViewModel(private val sourceId: Long, private val mode: String, initialQuery: String) : ViewModel() {
    data class State(
        val items: List<SManga> = emptyList(),
        val page: Int = 0,
        val hasMore: Boolean = true,
        val loading: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    var query by mutableStateOf(initialQuery)
    var filters: FilterList = Graph.sources.get(sourceId)?.filters() ?: FilterList()
        private set
    var filterVersion by mutableIntStateOf(0)
        private set
    private var job: Job? = null

    init { loadMore() }

    fun loadMore() {
        val s = _state.value
        if (s.loading || !s.hasMore) return
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            val page = s.page + 1
            val result = runCatching {
                Graph.sources.call(sourceId) { src ->
                    when {
                        mode == "popular" && query.isBlank() -> src.popular(page)
                        mode == "latest" && query.isBlank() -> src.latest(page)
                        else -> src.search(page, query, filters)
                    }
                }
            }
            result.onSuccess { r ->
                _state.update { st ->
                    val seen = st.items.map { it.url }.toHashSet()
                    st.copy(items = st.items + r.mangas.filter { seen.add(it.url) }, page = page, hasMore = r.hasNextPage && r.mangas.isNotEmpty(), loading = false)
                }
            }.onFailure { e -> _state.update { it.copy(loading = false, error = SourceManager.describe(e)) } }
        }
    }

    fun restart() {
        job?.cancel()
        _state.value = State()
        loadMore()
    }

    fun resetFilters() {
        filters = Graph.sources.get(sourceId)?.filters() ?: FilterList()
        filterVersion++
    }

    fun filtersChanged() { filterVersion++ }
}

@Composable
fun BrowseScreen(route: Routes.Browse) {
    val vm: BrowseViewModel = viewModel(key = "browse-${route.sourceId}-${route.mode}-${route.query}") { BrowseViewModel(route.sourceId, route.mode, route.query) }
    val c = Folio.colors
    val nav = LocalNav.current
    val state by vm.state.collectAsStateWithLifecycle()
    val glass = rememberGlassState()
    val grid = rememberLazyGridState()
    var showFilters by remember { mutableStateOf(route.mode == "search" && route.query.isBlank()) }
    val title = when (route.mode) {
        "popular" -> "Popular"
        "latest" -> "Latest Updates"
        else -> if (route.query.isNotBlank()) "“${route.query}”" else "Browse"
    }
    val scrolled by remember { derivedStateOf { grid.firstVisibleItemIndex > 0 } }

    LaunchedEffect(grid) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (last >= grid.layoutInfo.totalItemsCount - 8) vm.loadMore()
        }
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        LazyVerticalGrid(
            state = grid,
            columns = GridCells.Adaptive(108.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize().glassSource(glass),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars).height(40.dp))
                    Text(title, style = Folio.type.largeTitle, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(Graph.sources.nameOf(route.sourceId), style = Folio.type.subhead, color = c.secondaryLabel)
                }
            }
            itemsIndexed(state.items, key = { _, m -> m.url }) { _, manga ->
                val key = "browse-${route.sourceId}-${manga.url}"
                Column(Modifier.pressable(onClick = { nav.title(route.sourceId, manga, key) })) {
                    Cover(route.sourceId, manga.thumbnail_url, manga.title, Modifier.fillMaxWidth(), sharedKey = key)
                    Spacer(Modifier.height(7.dp))
                    Text(manga.title, style = Folio.type.footnoteStrong, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (state.loading && state.items.isEmpty()) {
                items(12) { Column { SkeletonBox(Modifier.fillMaxWidth().aspectRatio(2f / 3f), 10.dp); Spacer(Modifier.height(8.dp)); SkeletonBox(Modifier.fillMaxWidth(0.7f).height(12.dp), 4.dp) } }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    when {
                        state.error != null -> FailedFeed(
                            Graph.sources.nameOf(route.sourceId), state.error!!, onRetry = vm::restart,
                            onFallback = if (route.sourceId != MangaDexSource.ID) ({ nav.browse(MangaDexSource.ID, route.mode, vm.query) }) else null,
                            onOpenSite = Graph.sources.get(route.sourceId)?.let { s -> { nav.web(s.homeUrl, s.name) } },
                        )
                        state.loading && state.items.isNotEmpty() -> CircularProgressIndicator(Modifier.size(24.dp), color = c.secondaryLabel, strokeWidth = 2.dp)
                        !state.loading && state.items.isEmpty() -> EmptyState(Icons.Rounded.SearchOff, "Nothing found", "Try different words or fewer filters.")
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
        }
        TopBar(title, glass, showTitle = scrolled, onBack = { nav.back() }) {
            if (vm.filters.isNotEmpty()) CircleButton(Icons.Rounded.Tune, "Filters", { showFilters = true })
        }
    }

    if (showFilters) {
        FilterSheet(
            filters = vm.filters,
            version = vm.filterVersion,
            query = vm.query,
            onQuery = { vm.query = it },
            onChanged = vm::filtersChanged,
            onReset = vm::resetFilters,
            onApply = { showFilters = false; vm.restart() },
            onDismiss = { showFilters = false },
        )
    }
}

/** Shows a source's own filters (whatever kinds it uses) as native controls. */
@Composable
fun FilterSheet(
    filters: FilterList,
    version: Int,
    query: String,
    onQuery: (String) -> Unit,
    onChanged: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Folio.colors
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.grouped, dragHandle = null) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BarTextButton("Reset", onReset)
            Text("Filters", style = Folio.type.headline, color = c.label, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            BarTextButton("Done", onApply, strong = true)
        }
        @Suppress("UNUSED_EXPRESSION") version
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                SearchField(query, onQuery, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), placeholder = "Title (optional)", onSubmit = onApply)
            }
            items(filters.list, key = { System.identityHashCode(it) }) { filter -> FilterControl(filter, onChanged) }
            item {
                PrimaryButton("Show results", onApply, Modifier.fillMaxWidth().padding(16.dp))
            }
        }
    }
}

@Composable
private fun FilterControl(filter: Filter<*>, onChanged: () -> Unit) {
    val c = Folio.colors
    var tick by remember { mutableIntStateOf(0) }
    val changed = { tick++; onChanged() }
    @Suppress("UNUSED_EXPRESSION") tick
    when (filter) {
        is Filter.Header -> Text(filter.name.uppercase(), style = Folio.type.sectionHeader, color = c.secondaryLabel, modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 6.dp))
        is Filter.Separator -> Spacer(Modifier.height(8.dp))
        is Filter.Text -> Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(filter.name, style = Folio.type.footnote, color = c.secondaryLabel, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            SearchField(filter.state, { filter.state = it; changed() }, Modifier.fillMaxWidth(), placeholder = filter.name)
        }
        is Filter.CheckBox -> FilterToggle(filter.name, filter.state) { filter.state = it; changed() }
        is Filter.TriState -> TriChip(filter.name, filter.state) { filter.state = it; changed() }
        is Filter.Select<*> -> FilterGroupBox(filter.name) {
            FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                filter.values.forEachIndexed { i, v -> Chip(v.toString(), selected = filter.state == i, onClick = { filter.state = i; changed() }) }
            }
        }
        is Filter.Sort -> FilterGroupBox(filter.name) {
            FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                filter.values.forEachIndexed { i, v ->
                    val sel = filter.state?.index == i
                    val asc = filter.state?.ascending == true
                    Chip(
                        v, selected = sel, leading = if (sel) (if (asc) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward) else null,
                        onClick = { filter.state = Filter.Sort.Selection(i, if (sel) !asc else false); changed() },
                    )
                }
            }
        }
        is Filter.Group<*> -> FilterGroupBox(filter.name) {
            val children = filter.state.filterIsInstance<Filter<*>>()
            if (children.all { it is Filter.CheckBox || it is Filter.TriState }) {
                FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    children.forEach { child ->
                        when (child) {
                            is Filter.CheckBox -> Chip(child.name, selected = child.state, onClick = { child.state = !child.state; changed() })
                            is Filter.TriState -> TriChip(child.name, child.state) { child.state = it; changed() }
                            else -> Unit
                        }
                    }
                }
            } else {
                Column { children.forEach { FilterControl(it, onChanged) } }
            }
        }
        else -> Unit
    }
}

@Composable
private fun FilterGroupBox(title: String, content: @Composable () -> Unit) {
    val c = Folio.colors
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(title.uppercase(), style = Folio.type.sectionHeader, color = c.secondaryLabel, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp, top = 6.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface)) { content() }
    }
}

@Composable
private fun FilterToggle(name: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Folio.colors
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 3.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = Folio.type.body, color = c.label, modifier = Modifier.weight(1f))
        FolioSwitch(checked, onChange)
    }
}

/** Include / exclude / ignore in one chip: tap cycles through them. */
@Composable
private fun TriChip(name: String, state: Int, onChange: (Int) -> Unit) {
    val c = Folio.colors
    val (bg, fg, prefix) = when (state) {
        Filter.TriState.STATE_INCLUDE -> Triple(c.accent, c.onAccent, "+ ")
        Filter.TriState.STATE_EXCLUDE -> Triple(c.danger.copy(alpha = 0.18f), c.danger, "− ")
        else -> Triple(c.fill, c.label, "")
    }
    Box(
        Modifier.height(32.dp).clip(RoundedCornerShape(50)).background(bg)
            .pressable(onClick = { onChange((state + 1) % 3) }, haptic = true).padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(prefix + name, style = Folio.type.footnoteStrong, color = fg, maxLines = 1)
    }
}

// ------------------------------------------------------------------ Search

class SearchViewModel(initial: String) : ViewModel() {
    var query by mutableStateOf(initial)
    private val _results = MutableStateFlow<Map<Long, Load<List<SManga>>>>(emptyMap())
    val results = _results.asStateFlow()
    private var job: Job? = null
    private val prefs = Graph.app.getSharedPreferences("search", 0)
    var recent by mutableStateOf(prefs.getString("recent", "")!!.split("\n").filter { it.isNotBlank() })
        private set

    init { if (initial.isNotBlank()) search(debounce = false) }

    fun search(debounce: Boolean = true) {
        job?.cancel()
        val q = query.trim()
        if (q.length < 2) {
            _results.value = emptyMap()
            return
        }
        job = viewModelScope.launch {
            if (debounce) delay(450)
            val sources = listOfNotNull(Graph.sources.primary(), Graph.sources.mangaDex)
            _results.value = sources.associate { it.id to Load.Loading }
            sources.forEach { src ->
                launch {
                    val r = runCatching { Graph.sources.search(src.id, q) }
                    _results.update { it + (src.id to r.fold({ v -> Load.Ready(v.mangas) }, { e -> Load.Failed(SourceManager.describe(e)) })) }
                }
            }
        }
    }

    fun remember(q: String) {
        val t = q.trim().takeIf { it.length >= 2 } ?: return
        recent = (listOf(t) + recent.filter { !it.equals(t, true) }).take(8)
        prefs.edit().putString("recent", recent.joinToString("\n")).apply()
    }

    fun clearRecent() {
        recent = emptyList()
        prefs.edit().remove("recent").apply()
    }
}

@Composable
fun SearchScreen(initial: String) {
    val vm: SearchViewModel = viewModel { SearchViewModel(initial) }
    val c = Folio.colors
    val nav = LocalNav.current
    val results by vm.results.collectAsStateWithLifecycle()
    val health by Graph.sources.health.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var scope by remember { mutableStateOf("All") }
    LaunchedEffect(Unit) { if (initial.isBlank()) runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize().background(c.background)) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(
                vm.query, { vm.query = it; vm.search() }, Modifier.weight(1f),
                placeholder = "Titles, authors, or a link", focusRequester = focus,
                onSubmit = { keyboard?.hide(); vm.remember(vm.query); vm.search(debounce = false) },
            )
            BarTextButton("Cancel", { nav.back() })
        }
        val sources = listOfNotNull(Graph.sources.primary(), Graph.sources.mangaDex)
        if (results.isNotEmpty()) {
            SegmentedControl(listOf("All") + sources.map { it.name }, scope, { it }, { scope = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (results.isEmpty()) {
                if (vm.recent.isNotEmpty()) {
                    item { SectionHeading("Recent", action = "Clear", onAction = vm::clearRecent) }
                    items(vm.recent) { q ->
                        Row(
                            Modifier.fillMaxWidth().pressable(onClick = { vm.query = q; vm.search(debounce = false) }, pressedScale = 0.99f).padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.History, null, tint = c.tertiaryLabel, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(q, style = Folio.type.body, color = c.label)
                        }
                    }
                } else {
                    item {
                        EmptyState(
                            Icons.Rounded.SearchOff, "Search everything",
                            "Folio searches MangaFire and MangaDex together. Paste a link to a title to open it directly.",
                        )
                    }
                }
            } else {
                sources.filter { scope == "All" || it.name == scope }.forEach { src ->
                    val r = results[src.id] ?: return@forEach
                    item(key = "s-${src.id}") {
                        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(src.name, style = Folio.type.title3, color = c.label)
                            Spacer(Modifier.width(8.dp))
                            when (val h = health[src.id]) {
                                is dev.pridgey.folio.source.Health.Failing -> StatusDot(if (h.healing) c.warning else c.danger)
                                is dev.pridgey.folio.source.Health.Ok -> StatusDot(c.success)
                                else -> Unit
                            }
                            Spacer(Modifier.weight(1f))
                            if (r is Load.Ready && r.value.isNotEmpty()) BarTextButton("See All", { vm.remember(vm.query); nav.browse(src.id, "search", vm.query.trim()) })
                        }
                    }
                    item(key = "r-${src.id}") {
                        when (r) {
                            is Load.Loading -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(5) { SkeletonBox(Modifier.width(104.dp).aspectRatio(2f / 3f), 10.dp) }
                            }
                            is Load.Failed -> Text(r.message, style = Folio.type.subhead, color = c.secondaryLabel, modifier = Modifier.padding(horizontal = 16.dp))
                            is Load.Ready -> if (r.value.isEmpty()) {
                                Text("No matches", style = Folio.type.subhead, color = c.secondaryLabel, modifier = Modifier.padding(horizontal = 16.dp))
                            } else {
                                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(r.value, key = { it.url }) { manga ->
                                        val key = "search-${src.id}-${manga.url}"
                                        Column(Modifier.width(104.dp).pressable(onClick = { vm.remember(vm.query); nav.title(src.id, manga, key) })) {
                                            Cover(src.id, manga.thumbnail_url, manga.title, Modifier.fillMaxWidth(), sharedKey = key)
                                            Spacer(Modifier.height(7.dp))
                                            Text(manga.title, style = Folio.type.footnoteStrong, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
