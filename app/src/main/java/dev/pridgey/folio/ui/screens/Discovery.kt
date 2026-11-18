@file:OptIn(ExperimentalLayoutApi::class)

package dev.pridgey.folio.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pridgey.folio.Graph
import dev.pridgey.folio.source.MangaDexSource
import dev.pridgey.folio.source.MangaDexSource.TagInfo
import dev.pridgey.folio.source.SourceManager
import dev.pridgey.folio.source.TitleMatcher
import dev.pridgey.folio.ui.LocalNav
import dev.pridgey.folio.ui.components.Chip
import dev.pridgey.folio.ui.components.Cover
import dev.pridgey.folio.ui.components.EmptyState
import dev.pridgey.folio.ui.components.Format
import dev.pridgey.folio.ui.components.PrimaryButton
import dev.pridgey.folio.ui.components.SectionHeading
import dev.pridgey.folio.ui.components.SkeletonBox
import dev.pridgey.folio.ui.components.TopBar
import dev.pridgey.folio.ui.components.glassSource
import dev.pridgey.folio.ui.components.pressable
import dev.pridgey.folio.ui.components.rememberGlassState
import dev.pridgey.folio.ui.theme.Folio
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ Paged MangaDex results

/** A growing list of titles from MangaDex, fetched a page at a time. */
class PagedTitles(private val fetch: suspend (page: Int) -> MangasPage) {
    data class State(val items: List<SManga> = emptyList(), val page: Int = 0, val hasMore: Boolean = true, val loading: Boolean = false, val error: String? = null)

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private var job: Job? = null

    fun loadMore(scope: kotlinx.coroutines.CoroutineScope) {
        val s = _state.value
        if (s.loading || !s.hasMore) return
        _state.update { it.copy(loading = true, error = null) }
        job = scope.launch {
            val page = s.page + 1
            runCatching { Graph.sources.call(MangaDexSource.ID) { fetch(page) } }
                .onSuccess { r ->
                    _state.update { st ->
                        val seen = st.items.map { it.url }.toHashSet()
                        st.copy(items = st.items + r.mangas.filter { seen.add(it.url) }, page = page, hasMore = r.hasNextPage && r.mangas.isNotEmpty(), loading = false)
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = SourceManager.describe(e)) } }
        }
    }
}

/** A grid of MangaDex titles under a header, loading more as it nears the end. */
@Composable
private fun TitleGridScreen(
    title: String,
    paged: PagedTitles?,
    loadMore: () -> Unit,
    header: @Composable () -> Unit = {},
    empty: String = "Nothing here yet",
    error: String? = null,
) {
    val c = Folio.colors
    val nav = LocalNav.current
    val glass = rememberGlassState()
    val grid = rememberLazyGridState()
    val state = paged?.state?.collectAsStateWithLifecycle()?.value ?: PagedTitles.State(loading = error == null)
    val scrolled by remember { derivedStateOf { grid.firstVisibleItemIndex > 0 } }
    LaunchedEffect(grid, paged) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (last >= grid.layoutInfo.totalItemsCount - 8) loadMore()
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
                    Text(title, style = Folio.type.largeTitle, color = c.label, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    header()
                }
            }
            items(state.items, key = { it.url }) { manga -> TitleTile(MangaDexSource.ID, manga, "grid-$title") }
            if (state.loading && state.items.isEmpty()) {
                items(9) { SkeletonBox(Modifier.fillMaxWidth().aspectRatio(2f / 3f), 10.dp) }
            }
            val message = error ?: state.error ?: if (!state.loading && state.items.isEmpty()) empty else null
            message?.let {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(it, style = Folio.type.subhead, color = c.secondaryLabel, modifier = Modifier.padding(vertical = 20.dp))
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(24.dp)) }
        }
        TopBar(title, glass, showTitle = scrolled, onBack = { nav.back() })
    }
}

@Composable
private fun TitleTile(sourceId: Long, manga: SManga, keyPrefix: String, width: androidx.compose.ui.unit.Dp? = null) {
    val c = Folio.colors
    val nav = LocalNav.current
    val key = "$keyPrefix-$sourceId-${manga.url}"
    Column((if (width != null) Modifier.width(width) else Modifier).pressable(onClick = { nav.title(sourceId, manga, key) })) {
        Cover(sourceId, manga.thumbnail_url, manga.title, Modifier.fillMaxWidth(), sharedKey = key)
        Spacer(Modifier.height(7.dp))
        Text(manga.title, style = Folio.type.footnoteStrong, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ------------------------------------------------------------------ Tagged titles

class TaggedViewModel(names: List<String>) : ViewModel() {
    var tags by mutableStateOf<List<TagInfo>?>(null)
        private set
    var paged by mutableStateOf<PagedTitles?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            runCatching { Graph.sources.mangaDex.tagsNamed(names) }
                .onSuccess { found ->
                    tags = found
                    if (found.isEmpty()) {
                        error = "MangaDex has no tag called ${names.joinToString(" or ") { "“$it”" }}."
                    } else {
                        val ids = found.map { it.id }
                        paged = PagedTitles { page -> Graph.sources.mangaDex.byTags(ids, page) }.also { it.loadMore(viewModelScope) }
                    }
                }
                .onFailure { error = SourceManager.describe(it) }
        }
    }

    fun loadMore() { paged?.loadMore(viewModelScope) }
}

@Composable
fun TaggedScreen(names: List<String>) {
    val vm: TaggedViewModel = viewModel(key = "tagged-${names.joinToString("|")}") { TaggedViewModel(names) }
    val nav = LocalNav.current
    TitleGridScreen(
        title = names.joinToString(" + "),
        paged = vm.paged,
        loadMore = vm::loadMore,
        error = vm.error,
        empty = "No titles carry all of these tags.",
        header = {
            Spacer(Modifier.height(6.dp))
            Text("On MangaDex, most followed first", style = Folio.type.subhead, color = Folio.colors.secondaryLabel)
            Spacer(Modifier.height(10.dp))
            Chip("All tags", leading = Icons.Rounded.LocalOffer, onClick = { nav.tags() })
        },
    )
}

// ------------------------------------------------------------------ Tag explorer

class TagExplorerViewModel : ViewModel() {
    var tags by mutableStateOf<Load<List<TagInfo>>>(Load.Loading)
        private set

    /** How many library titles carry each tag (by normalised name). */
    var affinity by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    init { load() }

    fun load() {
        tags = Load.Loading
        viewModelScope.launch {
            tags = runCatching { Graph.sources.mangaDex.tags() }.fold({ Load.Ready(it) }, { Load.Failed(SourceManager.describe(it)) })
        }
        viewModelScope.launch { affinity = Graph.library.genreWeights().toMap() }
    }

    companion object {
        /** A few covers per tag, kept for the run so the map fills instantly on return. */
        val clusters = androidx.compose.runtime.mutableStateMapOf<String, List<SManga>>()
        private val pending = HashSet<String>()

        suspend fun cluster(tag: TagInfo) {
            if (tag.id in clusters || !pending.add(tag.id)) return
            runCatching { Graph.sources.mangaDex.byTags(listOf(tag.id), 1).mangas.take(3) }
                .onSuccess { clusters[tag.id] = it }
            pending.remove(tag.id)
        }
    }
}

/**
 * Every tag as a tile with a little fan of its most-followed covers. Tags from the reader's own
 * library come first. Tap a tile to browse it; long-press several to combine them.
 */
@Composable
fun TagExplorerScreen(vm: TagExplorerViewModel = viewModel()) {
    val c = Folio.colors
    val nav = LocalNav.current
    val glass = rememberGlassState()
    val grid = rememberLazyGridState()
    val scrolled by remember { derivedStateOf { grid.firstVisibleItemIndex > 0 } }
    val picked = remember { mutableStateListOf<TagInfo>() }

    Box(Modifier.fillMaxSize().background(c.background)) {
        LazyVerticalGrid(
            state = grid,
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().glassSource(glass),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars).height(40.dp))
                    Text("Tags", style = Folio.type.largeTitle, color = c.label)
                    Text("Tap to browse. Long-press a few to find titles with all of them.", style = Folio.type.subhead, color = c.secondaryLabel)
                }
            }
            when (val t = vm.tags) {
                is Load.Loading -> items(8) { SkeletonBox(Modifier.fillMaxWidth().height(128.dp), 18.dp) }
                is Load.Failed -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(Icons.Rounded.LocalOffer, "Couldn't load tags", t.message, action = "Try again", onAction = vm::load)
                }
                is Load.Ready -> {
                    fun norm(s: String) = s.lowercase()
                    val yours = t.value.filter { (vm.affinity[norm(it.name)] ?: 0) > 0 }.sortedByDescending { vm.affinity[norm(it.name)] ?: 0 }
                    val sections = listOfNotNull(
                        ("From your library" to yours).takeIf { yours.isNotEmpty() },
                        "Genres" to t.value.filter { it.group == "genre" },
                        "Themes" to t.value.filter { it.group == "theme" },
                        "Formats" to t.value.filter { it.group == "format" },
                    )
                    sections.forEach { (heading, list) ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "h-$heading") {
                            Text(heading, style = Folio.type.title2, color = c.label, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
                        }
                        items(list, key = { "$heading-${it.id}" }) { tag ->
                            TagTile(
                                tag, vm.affinity[norm(tag.name)] ?: 0, selected = tag in picked,
                                onClick = { if (picked.isNotEmpty()) { if (tag in picked) picked.remove(tag) else picked.add(tag) } else nav.tagged(tag.name) },
                                onLongClick = { if (tag in picked) picked.remove(tag) else picked.add(tag) },
                            )
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(96.dp)) }
        }
        TopBar("Tags", glass, showTitle = scrolled, onBack = { nav.back() })

        AnimatedVisibility(
            picked.isNotEmpty(), enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(Modifier.fillMaxWidth().background(c.elevated).windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp)) {
                Text(picked.joinToString(" + ") { it.name }, style = Folio.type.footnote, color = c.secondaryLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    if (picked.size == 1) "Browse ${picked[0].name}" else "Titles with all ${picked.size}",
                    { nav.tagged(*picked.map { it.name }.toTypedArray()); picked.clear() },
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TagTile(tag: TagInfo, inLibrary: Int, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = Folio.colors
    LaunchedEffect(tag.id) { TagExplorerViewModel.cluster(tag) }
    val covers = TagExplorerViewModel.clusters[tag.id]
    Box(
        Modifier
            .fillMaxWidth()
            .height(128.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) c.accentSoft else c.surface)
            .then(if (selected) Modifier.border(2.dp, c.accent, RoundedCornerShape(18.dp)) else Modifier)
            .pressable(onClick = onClick, onLongClick = onLongClick, pressedScale = 0.97f),
    ) {
        // The cluster: three covers fanned in from the right edge.
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).width(96.dp).height(108.dp)) {
            val shown = covers.orEmpty()
            shown.forEachIndexed { i, m ->
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = (i * 20).dp, top = (i * 6).dp)
                        .width(56.dp)
                        .zIndex((shown.size - i).toFloat())
                        .graphicsLayer { rotationZ = (i - 1) * 5f },
                ) {
                    Cover(MangaDexSource.ID, m.thumbnail_url, m.title, Modifier.fillMaxWidth(), corner = 6.dp, elevation = 6.dp)
                }
            }
            if (covers == null) {
                SkeletonBox(Modifier.align(Alignment.CenterEnd).width(56.dp).aspectRatio(2f / 3f), 6.dp)
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp).width(100.dp)) {
            Text(tag.name, style = Folio.type.headline, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (inLibrary > 0) Text("${Format.plural(inLibrary, "title")} of yours", style = Folio.type.caption, color = c.accent, maxLines = 1)
        }
    }
}

// ------------------------------------------------------------------ Author pages

class AuthorViewModel(val name: String) : ViewModel() {
    /** The MangaDex people who match, best first. */
    var people by mutableStateOf<Load<List<MangaDexSource.Person>>>(Load.Loading)
        private set
    var paged by mutableStateOf<PagedTitles?>(null)
        private set
    var elsewhere by mutableStateOf<Load<List<SManga>>?>(null)
        private set
    val primary = Graph.sources.primary()?.takeIf { it.id != MangaDexSource.ID }

    init {
        viewModelScope.launch {
            people = runCatching {
                val found = Graph.sources.call(MangaDexSource.ID) { Graph.sources.mangaDex.people(name) }
                found.sortedByDescending { TitleMatcher.similarity(name, it.name) }
            }.fold({ Load.Ready(it) }, { Load.Failed(SourceManager.describe(it)) })
            (people as? Load.Ready)?.value?.firstOrNull()?.let { pick(it) }
        }
        primary?.let { src ->
            elsewhere = Load.Loading
            viewModelScope.launch {
                elsewhere = runCatching { Graph.sources.search(src.id, name).mangas }.fold({ Load.Ready(it) }, { Load.Failed(SourceManager.describe(it)) })
            }
        }
    }

    var picked by mutableStateOf<MangaDexSource.Person?>(null)
        private set

    fun pick(person: MangaDexSource.Person) {
        picked = person
        paged = PagedTitles { page -> Graph.sources.mangaDex.byPerson(person.id, page) }.also { it.loadMore(viewModelScope) }
    }

    fun loadMore() { paged?.loadMore(viewModelScope) }
}

/** Everything by one author or artist: what's in the library, their works on MangaDex, and a search elsewhere. */
@Composable
fun AuthorScreen(name: String) {
    val vm: AuthorViewModel = viewModel(key = "author-$name") { AuthorViewModel(name) }
    val c = Folio.colors
    val owned by remember(name) { Graph.library.byPerson(name) }.collectAsStateWithLifecycle(emptyList())
    val people = (vm.people as? Load.Ready)?.value.orEmpty()
    TitleGridScreen(
        title = name,
        paged = vm.paged,
        loadMore = vm::loadMore,
        error = (vm.people as? Load.Failed)?.message ?: if (vm.people is Load.Ready && people.isEmpty()) "MangaDex doesn't know anyone by that name." else null,
        empty = "No titles on MangaDex.",
        header = {
            Text("Author & artist", style = Folio.type.subhead, color = c.secondaryLabel)
            // Several people can share a name: let the reader pick the right one.
            if (people.size > 1) {
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(people.take(8), key = { it.id }) { p -> Chip(p.name, selected = p == vm.picked, onClick = { vm.pick(p) }) }
                }
            }
            if (owned.isNotEmpty()) {
                SectionHeading("In your library", Modifier.padding(top = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(owned, key = { it.id }) { m ->
                        val nav = LocalNav.current
                        Column(Modifier.width(104.dp).pressable(onClick = { nav.details(m.id, m.sourceId, m.thumbnailUrl) })) {
                            Cover(m.sourceId, m.thumbnailUrl, m.title, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(6.dp))
                            Text(m.title, style = Folio.type.footnoteStrong, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            vm.primary?.let { src ->
                val r = vm.elsewhere
                if (r is Load.Ready && r.value.isNotEmpty()) {
                    SectionHeading("Searching ${src.name} for “$name”", Modifier.padding(top = 8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(r.value.take(12), key = { it.url }) { m -> TitleTile(src.id, m, "author-else", 104.dp) }
                    }
                }
            }
            SectionHeading("On MangaDex", Modifier.padding(top = 8.dp))
        },
    )
}

// ------------------------------------------------------------------ Because you read…

/** A recommendation shelf seeded by one library title. */
data class Recommendation(val seed: String, val tags: List<String>, val titles: List<SManga>)

object Recommender {
    /**
     * For the titles read most recently, finds MangaDex series sharing their strongest tags, leaving
     * out anything already in the library.
     */
    suspend fun forYou(seeds: Int = 2): List<Recommendation> {
        val dex = Graph.sources.mangaDex
        // Private titles never seed recommendations: they'd show up on Discover.
        val hidden = Graph.library.hiddenTitles().first()
        val library = Graph.library.library().first().filter { it.id !in hidden }
        if (library.isEmpty()) return emptyList()
        val owned = library.map { TitleMatcher.normalize(it.title) }.toHashSet()
        val weights = Graph.library.genreWeights().toMap()
        val picks = library.filter { !it.genres.isNullOrBlank() }.sortedByDescending { it.lastReadAt }.take(seeds)
        val shown = HashSet<String>()
        return picks.mapNotNull { seed ->
            val names = seed.genres.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            // Prefer what the reader reads most; genres before themes; skip formats like "Long Strip".
            val tags = dex.tagsNamed(names).filter { it.group != "format" && it.group != "content" }
                .sortedWith(compareByDescending<TagInfo> { weights[it.name.lowercase()] ?: 0 }.thenBy { if (it.group == "genre") 0 else 1 })
            if (tags.isEmpty()) return@mapNotNull null
            // Three tags in common is a strong match; fall back to two when that's too narrow.
            var used = tags.take(3)
            var found = fresh(dex.byTags(used.map { it.id }, 1).mangas, owned, shown)
            if (found.size < 6 && tags.size >= 2) {
                used = tags.take(2)
                found = fresh(dex.byTags(used.map { it.id }, 1).mangas, owned, shown)
            }
            found = found.take(15)
            if (found.isEmpty()) null else Recommendation(seed.title, used.map { it.name }, found.also { list -> list.forEach { shown += it.url } })
        }
    }

    private fun fresh(list: List<SManga>, owned: Set<String>, shown: Set<String>) =
        list.filter { TitleMatcher.normalize(it.title) !in owned && it.url !in shown }
}

/** "Because you read X" shelves for Discover. */
@Composable
fun BecauseYouRead(recommendations: List<Recommendation>) {
    val c = Folio.colors
    val nav = LocalNav.current
    recommendations.forEach { r ->
        Column {
            SectionHeading("Because you read ${r.seed}", action = "More", onAction = { nav.tagged(*r.tags.toTypedArray()) })
            Text(
                r.tags.joinToString(" · "), style = Folio.type.caption, color = c.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp).padding(top = 0.dp),
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(r.titles, key = { "byr-${r.seed}-${it.url}" }) { m -> TitleTile(MangaDexSource.ID, m, "byr-${r.seed}", 112.dp) }
            }
        }
    }
}

/** Search results for people: MangaDex authors plus names from the library. */
@Composable
fun PeopleResults(query: String, people: Load<List<String>>) {
    val c = Folio.colors
    val nav = LocalNav.current
    when (people) {
        is Load.Loading -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { repeat(4) { SkeletonBox(Modifier.fillMaxWidth(0.6f).height(18.dp), 4.dp) } }
        is Load.Failed -> Text(people.message, style = Folio.type.subhead, color = c.secondaryLabel, modifier = Modifier.padding(16.dp))
        is Load.Ready -> if (people.value.isEmpty()) {
            EmptyState(Icons.Rounded.PersonSearch, "No one by that name", "Try part of the name, like a surname.")
        } else {
            Column {
                people.value.forEach { name ->
                    Row(
                        Modifier.fillMaxWidth().pressable(onClick = { nav.author(name) }, pressedScale = 0.99f).padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Icon(Icons.Rounded.PersonSearch, null, tint = c.tertiaryLabel)
                        Spacer(Modifier.width(12.dp))
                        Text(name, style = Folio.type.body, color = c.label)
                    }
                }
            }
        }
    }
}

/** Search results for tags. */
@Composable
fun TagResults(tags: List<TagInfo>?) {
    val c = Folio.colors
    val nav = LocalNav.current
    Column(Modifier.padding(16.dp)) {
        when {
            tags == null -> SkeletonBox(Modifier.fillMaxWidth(0.5f).height(32.dp), 16.dp)
            tags.isEmpty() -> Text("No tags match", style = Folio.type.subhead, color = c.secondaryLabel)
            else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { t -> Chip(t.name, leading = Icons.Rounded.LocalOffer, onClick = { nav.tagged(t.name) }) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Chip("Explore all tags", onClick = { nav.tags() })
    }
}
