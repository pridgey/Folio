@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package dev.pridgey.folio.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dev.pridgey.folio.Graph
import dev.pridgey.folio.data.ChapterEntity
import dev.pridgey.folio.data.ChapterNumbers
import dev.pridgey.folio.data.LibraryRepository
import dev.pridgey.folio.data.MangaEntity
import dev.pridgey.folio.image.CoverModel
import dev.pridgey.folio.source.Health
import dev.pridgey.folio.source.MangaDexSource
import dev.pridgey.folio.source.SourceManager
import dev.pridgey.folio.ui.LocalNav
import dev.pridgey.folio.ui.Routes
import dev.pridgey.folio.ui.components.Chip
import dev.pridgey.folio.ui.components.CircleButton
import dev.pridgey.folio.ui.components.Cover
import dev.pridgey.folio.ui.components.Format
import dev.pridgey.folio.ui.components.Notice
import dev.pridgey.folio.ui.components.PrimaryButton
import dev.pridgey.folio.ui.components.SkeletonBox
import dev.pridgey.folio.ui.components.TopBar
import dev.pridgey.folio.ui.components.glass
import dev.pridgey.folio.ui.components.glassSource
import dev.pridgey.folio.ui.components.pressable
import dev.pridgey.folio.ui.components.rememberGlassState
import dev.pridgey.folio.ui.theme.Folio
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ChapterFilter(val label: String) { ALL("All chapters"), UNREAD("Unread"), DOWNLOADED("Downloaded"), BOOKMARKED("Bookmarked") }

class DetailsViewModel(private val mangaId: Long) : ViewModel() {
    private val repo = Graph.library
    val manga = repo.observe(mangaId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val chapters = repo.observeChapters(mangaId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val filter = MutableStateFlow(ChapterFilter.ALL)
    val selection = MutableStateFlow<Set<Long>>(emptySet())

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    /** The same series on MangaDex, offered when the main source fails. */
    private val _fallback = MutableStateFlow<SManga?>(null)
    val fallback = _fallback.asStateFlow()
    private val _moving = MutableStateFlow(false)
    val moving = _moving.asStateFlow()

    val visibleChapters = combine(chapters, filter, manga, Graph.downloads.downloaded) { list, f, m, downloaded ->
        list ?: return@combine null
        val filtered = when (f) {
            ChapterFilter.ALL -> list
            ChapterFilter.UNREAD -> list.filter { !it.read }
            ChapterFilter.DOWNLOADED -> list.filter { it.id in downloaded }
            ChapterFilter.BOOKMARKED -> list.filter { it.bookmark }
        }
        if (m?.sortDescending == false) filtered.sortedByDescending { it.sourceOrder } else filtered.sortedBy { it.sourceOrder }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            val m = repo.get(mangaId) ?: return@launch
            val stale = System.currentTimeMillis() - m.lastUpdate > 60 * 60 * 1000
            if (!m.initialized || stale || repo.chaptersOf(mangaId).isEmpty()) refresh()
        }
    }

    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        _error.value = null
        viewModelScope.launch {
            runCatching { repo.refresh(mangaId) }.onFailure { e ->
                _error.value = SourceManager.describe(e)
                val m = repo.get(mangaId)
                if (m != null && m.sourceId != MangaDexSource.ID && Graph.settings.current.fallbackEnabled) {
                    _fallback.value = runCatching { repo.findOnFallback(m.title) }.getOrNull()
                }
            }
            _refreshing.value = false
        }
    }

    fun toggleLibrary() = viewModelScope.launch { manga.value?.let { repo.setFavorite(it.id, !it.favorite) } }
    fun toggleSort() = viewModelScope.launch { manga.value?.let { repo.setSortDescending(it.id, !it.sortDescending) } }

    fun select(id: Long) { selection.value = if (id in selection.value) selection.value - id else selection.value + id }
    fun selectAll() { selection.value = visibleChapters.value.orEmpty().map { it.id }.toSet() }
    fun clearSelection() { selection.value = emptySet() }

    fun markRead(ids: Collection<Long>, read: Boolean) = viewModelScope.launch { repo.setRead(ids.toList(), read); clearSelection() }
    fun bookmark(ids: Collection<Long>, on: Boolean) = viewModelScope.launch { repo.setBookmark(ids.toList(), on); clearSelection() }
    fun markPreviousRead(id: Long) = viewModelScope.launch { repo.markPreviousRead(mangaId, id) }
    fun download(ids: Collection<Long>) = viewModelScope.launch { Graph.downloads.enqueue(mangaId, ids.toList()); clearSelection() }
    fun deleteDownloads(ids: Collection<Long>) = viewModelScope.launch { Graph.downloads.delete(mangaId, ids.toList()); clearSelection() }
    fun downloadUnread() = viewModelScope.launch {
        val unread = LibraryRepository.readingOrder(chapters.value.orEmpty()).filter { !it.read }.map { it.id }
        Graph.downloads.enqueue(mangaId, unread)
    }

    fun moveToFallback(onMoved: (Long) -> Unit) {
        _moving.value = true
        viewModelScope.launch {
            val id = runCatching { repo.moveToFallback(mangaId) }.getOrNull()
            _moving.value = false
            if (id != null) onMoved(id) else _error.value = "Couldn't find this title on MangaDex"
        }
    }
}

@Composable
fun DetailsScreen(route: Routes.Details) {
    val vm: DetailsViewModel = viewModel(key = "details-${route.mangaId}") { DetailsViewModel(route.mangaId) }
    val c = Folio.colors
    val nav = LocalNav.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val manga by vm.manga.collectAsStateWithLifecycle()
    val all by vm.chapters.collectAsStateWithLifecycle()
    val chapters by vm.visibleChapters.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val fallback by vm.fallback.collectAsStateWithLifecycle()
    val moving by vm.moving.collectAsStateWithLifecycle()
    val downloaded by Graph.downloads.downloaded.collectAsStateWithLifecycle()
    val queue by Graph.downloads.queue.collectAsStateWithLifecycle(emptyList())
    val progress by Graph.downloads.progress.collectAsStateWithLifecycle()
    val health by Graph.sources.health.collectAsStateWithLifecycle()
    val glass = rememberGlassState()
    val list = rememberLazyListState()
    val density = LocalDensity.current
    val collapsed by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > with(density) { 300.dp.toPx() } } }
    val selecting = selection.isNotEmpty()
    BackHandler(selecting) { vm.clearSelection() }

    val title = manga?.title ?: ""
    val sourceId = manga?.sourceId ?: route.sourceId
    val cover = manga?.thumbnailUrl ?: route.cover
    val queued = remember(queue) { queue.map { it.chapterId }.toSet() }
    val next = remember(all) { all?.let { Graph.library.nextToRead(it) } }
    val sourceHealth = health[sourceId]

    Box(Modifier.fillMaxSize().background(c.background)) {
        PullToRefreshBox(isRefreshing = refreshing && all != null && all!!.isNotEmpty(), onRefresh = vm::refresh, modifier = Modifier.fillMaxSize().glassSource(glass)) {
            LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                item(key = "hero") {
                    Hero(sourceId, cover, title, manga, route.coverKey, all?.size)
                }
                item(key = "actions") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        val nextLabel = next?.let { n ->
                            val number = ChapterNumbers.format(n.number)
                            when {
                                n.lastReadAt > 0 && !n.read -> "Continue" to (if (number.isNotEmpty()) "Chapter $number · page ${n.lastPageRead + 1}" else n.name)
                                all!!.any { it.read } -> "Read Next" to n.name
                                else -> "Start Reading" to n.name
                            }
                        }
                        PrimaryButton(
                            text = nextLabel?.first ?: if (refreshing) "Loading chapters…" else "No chapters yet",
                            subtitle = nextLabel?.second,
                            icon = Icons.Rounded.PlayArrow,
                            enabled = next != null,
                            loading = refreshing && next == null,
                            onClick = { next?.let { nav.read(route.mangaId, it.id) } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val inLibrary = manga?.favorite == true
                            ActionTile(
                                if (inLibrary) "In Library" else "Add to Library",
                                if (inLibrary) Icons.Rounded.Check else Icons.Rounded.Add,
                                active = inLibrary, modifier = Modifier.weight(1f),
                            ) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.toggleLibrary()
                            }
                            ActionTile("Download", Icons.Rounded.Download, modifier = Modifier.weight(1f)) { vm.downloadUnread() }
                            ActionTile("Share", Icons.Rounded.Share, modifier = Modifier.weight(1f)) {
                                manga?.let { m ->
                                    Graph.sources.get(m.sourceId)?.webUrl(with(Graph.library) { m.toSManga() })?.let { url ->
                                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "$title\n$url"), null))
                                    }
                                }
                            }
                        }
                    }
                }

                // Source trouble, with a way out.
                if (sourceHealth is Health.Failing && sourceHealth.healing) {
                    item {
                        Notice(
                            "Updating ${Graph.sources.nameOf(sourceId)}…", "The site changed. Folio is fetching a fixed source and will retry.",
                            Icons.Rounded.SyncProblem, c.warning, busy = true, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                        )
                    }
                } else if (error != null) {
                    item {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Notice(
                                "Couldn't refresh", error, Icons.Rounded.CloudOff, c.danger,
                                action = "Retry", onAction = vm::refresh,
                            )
                            fallback?.let { match ->
                                Notice(
                                    "Found on MangaDex", "“${match.title}” is on MangaDex. Read it there, or move it over with your progress.",
                                    Icons.Rounded.SwapHoriz, c.accent, busy = moving,
                                    action = if (moving) null else "Open",
                                    onAction = { nav.title(MangaDexSource.ID, match) },
                                )
                            }
                        }
                    }
                }

                item(key = "about") { About(manga) }

                item(key = "chapter-header") {
                    ChapterHeader(
                        count = all?.size, filter = filter, descending = manga?.sortDescending != false,
                        onSort = vm::toggleSort, onFilter = { vm.filter.value = it },
                        onMarkAllRead = { all?.let { vm.markRead(it.map { ch -> ch.id }, true) } },
                        onSelectAll = vm::selectAll,
                    )
                }

                val visible = chapters
                if (visible == null || (visible.isEmpty() && refreshing)) {
                    items(6) { ChapterSkeleton() }
                } else if (visible.isEmpty()) {
                    item {
                        Text(
                            if (filter == ChapterFilter.ALL) "No chapters in this language yet." else "No ${filter.label.lowercase()} chapters.",
                            style = Folio.type.subhead, color = c.secondaryLabel, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                        )
                    }
                } else {
                    items(visible, key = { it.id }) { ch ->
                        val dl = when {
                            ch.id in downloaded -> DownloadState.Done
                            progress?.chapterId == ch.id -> DownloadState.Running(progress!!.done / progress!!.total.coerceAtLeast(1).toFloat())
                            ch.id in queued -> DownloadState.Queued
                            else -> DownloadState.None
                        }
                        ChapterRow(
                            ch, dl, selecting = selecting, selected = ch.id in selection,
                            onClick = { if (selecting) vm.select(ch.id) else nav.read(route.mangaId, ch.id) },
                            onLongClick = { vm.select(ch.id) },
                            onDownload = {
                                when (dl) {
                                    DownloadState.None -> vm.download(listOf(ch.id))
                                    DownloadState.Done -> vm.deleteDownloads(listOf(ch.id))
                                    else -> Graph.scope.launch { Graph.downloads.cancel(listOf(ch.id)) }
                                }
                            },
                            onMarkPrevious = { vm.markPreviousRead(ch.id) },
                        )
                    }
                }
                item { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(if (selecting) 96.dp else 32.dp)) }
            }
        }

        // Bar: transparent over the hero, glass once scrolled.
        TopBar(
            title = title, glass = glass, showTitle = collapsed, showDivider = collapsed,
            onBack = if (selecting) null else ({ nav.back() }),
        ) {
            if (selecting) {
                Text("${selection.size} selected", style = Folio.type.headline, color = c.label, modifier = Modifier.padding(end = 8.dp))
                CircleButton(Icons.Rounded.Close, "Done", vm::clearSelection, background = if (collapsed) c.fill else Color.Black.copy(alpha = 0.25f), tint = if (collapsed) c.label else Color.White)
            } else {
                var menu by remember { mutableStateOf(false) }
                val overHero = !collapsed
                Box {
                    CircleButton(
                        Icons.Rounded.MoreHoriz, "More", { menu = true },
                        background = if (overHero) Color.Black.copy(alpha = 0.25f) else c.fill, tint = if (overHero) Color.White else c.label,
                    )
                    DropdownMenu(menu, { menu = false }, containerColor = c.elevated, shape = RoundedCornerShape(14.dp)) {
                        DropdownMenuItem(
                            text = { Text("Open in browser") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                            onClick = {
                                menu = false
                                manga?.let { m -> Graph.sources.get(m.sourceId)?.webUrl(with(Graph.library) { m.toSManga() })?.let { nav.web(it, m.title) } }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Download unread") }, leadingIcon = { Icon(Icons.Rounded.Download, null) },
                            onClick = { menu = false; vm.downloadUnread() },
                        )
                        if (sourceId != MangaDexSource.ID) {
                            DropdownMenuItem(
                                text = { Text("Move to MangaDex") }, leadingIcon = { Icon(Icons.Rounded.SwapHoriz, null) },
                                onClick = {
                                    menu = false
                                    vm.moveToFallback { id -> nav.back(); nav.details(id, MangaDexSource.ID) }
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Select chapters") }, leadingIcon = { Icon(Icons.Rounded.CheckCircle, null) },
                            onClick = { menu = false; vm.selectAll() },
                        )
                    }
                }
            }
        }

        // Bulk actions for selected chapters.
        AnimatedVisibility(
            selecting,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .glass(glass, c.glass)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BulkAction("Read", Icons.Rounded.DoneAll) { vm.markRead(selection, true) }
                BulkAction("Unread", Icons.Rounded.RemoveDone) { vm.markRead(selection, false) }
                BulkAction("Bookmark", Icons.Rounded.Bookmark) { vm.bookmark(selection, true) }
                BulkAction("Download", Icons.Rounded.Download) { vm.download(selection) }
                BulkAction("Delete", Icons.Rounded.DeleteOutline) { vm.deleteDownloads(selection) }
            }
        }
    }
}

@Composable
private fun Hero(sourceId: Long, cover: String?, title: String, manga: MangaEntity?, coverKey: String?, chapterCount: Int?) {
    val c = Folio.colors
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth()) {
        // A blurred wash of the cover behind everything, fading into the page.
        if (cover != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(CoverModel(sourceId, cover)).memoryCacheKey("cover:$sourceId:$cover").build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(420.dp).blur(48.dp).graphicsLayer { alpha = if (c.isDark) 0.75f else 0.6f; scaleX = 1.25f; scaleY = 1.25f },
            )
        }
        Box(Modifier.fillMaxWidth().height(420.dp).background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to c.background.copy(alpha = 0.55f), 1f to c.background)))
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars).height(60.dp))
            Cover(sourceId, cover, title, Modifier.width(168.dp), corner = 12.dp, sharedKey = coverKey, elevation = 18.dp)
            Spacer(Modifier.height(20.dp))
            Text(title, style = Folio.type.title2, color = c.label, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
            val people = listOfNotNull(manga?.author, manga?.artist?.takeIf { it != manga.author }).joinToString(" · ")
            if (people.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(people, style = Folio.type.subhead, color = c.accent, textAlign = TextAlign.Center, maxLines = 2)
            }
            Spacer(Modifier.height(6.dp))
            val meta = listOfNotNull(
                manga?.status?.let { Format.status(it) },
                Graph.sources.nameOf(sourceId),
                chapterCount?.takeIf { it > 0 }?.let { Format.plural(it, "chapter") },
            ).joinToString("  ·  ")
            Text(meta, style = Folio.type.footnote, color = c.secondaryLabel, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ActionTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, active: Boolean = false, onClick: () -> Unit) {
    val c = Folio.colors
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) c.accentSoft else c.fill)
            .pressable(onClick = onClick, haptic = true)
            .padding(vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = Folio.type.caption2, color = if (active) c.accent else c.label, maxLines = 1)
    }
}

@Composable
private fun About(manga: MangaEntity?) {
    val c = Folio.colors
    var expanded by remember { mutableStateOf(false) }
    val description = manga?.description?.trim().orEmpty()
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp)) {
        if (manga == null || (!manga.initialized && description.isEmpty())) {
            SkeletonBox(Modifier.fillMaxWidth().height(14.dp), 4.dp)
            Spacer(Modifier.height(8.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.9f).height(14.dp), 4.dp)
            Spacer(Modifier.height(8.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.7f).height(14.dp), 4.dp)
            return@Column
        }
        if (description.isNotEmpty()) {
            Box(Modifier.animateContentSize().pressable(onClick = { expanded = !expanded }, pressedScale = 1f)) {
                Text(
                    description, style = Folio.type.subhead, color = c.label.copy(alpha = 0.86f),
                    maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
                )
            }
            if (!expanded && description.length > 220) {
                Text("more", style = Folio.type.subheadStrong, color = c.accent, modifier = Modifier.padding(top = 2.dp).pressable(onClick = { expanded = true }))
            }
        }
        val genres = manga.genres?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        if (genres.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                genres.take(14).forEach { Chip(it) }
            }
        }
    }
}

@Composable
private fun ChapterHeader(
    count: Int?,
    filter: ChapterFilter,
    descending: Boolean,
    onSort: () -> Unit,
    onFilter: (ChapterFilter) -> Unit,
    onMarkAllRead: () -> Unit,
    onSelectAll: () -> Unit,
) {
    val c = Folio.colors
    var menu by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (descending) 0f else 180f, label = "sort")
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 26.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (count == null) "Chapters" else Format.plural(count, "Chapter"), style = Folio.type.title3, color = c.label)
            if (filter != ChapterFilter.ALL) Text(filter.label, style = Folio.type.footnote, color = c.accent)
        }
        CircleButton(Icons.Rounded.SwapVert, if (descending) "Newest first" else "Oldest first", onSort, modifier = Modifier.graphicsLayer { rotationX = rotation })
        Spacer(Modifier.width(8.dp))
        Box {
            CircleButton(Icons.Rounded.FilterList, "Filter", { menu = true }, active = filter != ChapterFilter.ALL)
            DropdownMenu(menu, { menu = false }, containerColor = c.elevated, shape = RoundedCornerShape(14.dp)) {
                ChapterFilter.entries.forEach { f ->
                    DropdownMenuItem(
                        text = { Text(f.label) },
                        trailingIcon = { if (f == filter) Icon(Icons.Rounded.Check, null, tint = c.accent) },
                        onClick = { menu = false; onFilter(f) },
                    )
                }
                HorizontalDivider(color = c.separator)
                DropdownMenuItem(text = { Text("Mark all as read") }, leadingIcon = { Icon(Icons.Rounded.DoneAll, null) }, onClick = { menu = false; onMarkAllRead() })
                DropdownMenuItem(text = { Text("Select all") }, leadingIcon = { Icon(Icons.Rounded.CheckCircle, null) }, onClick = { menu = false; onSelectAll() })
            }
        }
    }
}

sealed interface DownloadState {
    data object None : DownloadState
    data object Queued : DownloadState
    data class Running(val progress: Float) : DownloadState
    data object Done : DownloadState
}

@Composable
private fun ChapterRow(
    ch: ChapterEntity,
    download: DownloadState,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownload: () -> Unit,
    onMarkPrevious: () -> Unit,
) {
    val c = Folio.colors
    val dim = ch.read && !selected
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (selected) c.accentSoft else Color.Transparent)
                .pressable(onClick = onClick, onLongClick = { if (selecting) onLongClick() else menu = true }, pressedScale = 0.985f)
                .padding(start = 16.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                Icon(
                    if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                    tint = if (selected) c.accent else c.tertiaryLabel, modifier = Modifier.padding(end = 12.dp).size(24.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ch.bookmark) Icon(Icons.Rounded.Bookmark, "Bookmarked", tint = c.accent, modifier = Modifier.padding(end = 4.dp).size(15.dp))
                    Text(
                        ch.name, style = Folio.type.subheadStrong, color = if (dim) c.tertiaryLabel else c.label,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                val parts = listOfNotNull(
                    Format.date(ch.dateUpload).takeIf { it.isNotEmpty() },
                    ch.scanlator,
                    if (!ch.read && ch.lastPageRead > 0 && ch.pageCount > 0) "Page ${ch.lastPageRead + 1} of ${ch.pageCount}" else null,
                )
                if (parts.isNotEmpty()) {
                    Text(
                        parts.joinToString("  ·  "), style = Folio.type.caption,
                        color = if (dim) c.tertiaryLabel else c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (!selecting) DownloadButton(download, onDownload)
        }
        DropdownMenu(menu, { menu = false }, containerColor = c.elevated, shape = RoundedCornerShape(14.dp), offset = androidx.compose.ui.unit.DpOffset(16.dp, 0.dp)) {
            DropdownMenuItem(text = { Text(if (ch.read) "Mark as unread" else "Mark as read") }, leadingIcon = { Icon(if (ch.read) Icons.Rounded.RemoveDone else Icons.Rounded.DoneAll, null) }, onClick = {
                menu = false
                Graph.scope.launch { Graph.library.setRead(listOf(ch.id), !ch.read) }
            })
            DropdownMenuItem(text = { Text("Mark previous as read") }, leadingIcon = { Icon(Icons.Rounded.DoneAll, null) }, onClick = { menu = false; onMarkPrevious() })
            DropdownMenuItem(text = { Text(if (ch.bookmark) "Remove bookmark" else "Bookmark") }, leadingIcon = { Icon(if (ch.bookmark) Icons.Rounded.BookmarkBorder else Icons.Rounded.Bookmark, null) }, onClick = {
                menu = false
                Graph.scope.launch { Graph.library.setBookmark(listOf(ch.id), !ch.bookmark) }
            })
            DropdownMenuItem(text = { Text("Select") }, leadingIcon = { Icon(Icons.Rounded.CheckCircle, null) }, onClick = { menu = false; onLongClick() })
        }
    }
}

@Composable
private fun DownloadButton(state: DownloadState, onClick: () -> Unit) {
    val c = Folio.colors
    Box(Modifier.size(40.dp).clip(CircleShape).pressable(onClick = onClick, haptic = true), contentAlignment = Alignment.Center) {
        when (state) {
            DownloadState.None -> Icon(Icons.Rounded.Download, "Download", tint = c.tertiaryLabel, modifier = Modifier.size(21.dp))
            DownloadState.Queued -> CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp, color = c.tertiaryLabel)
            is DownloadState.Running -> CircularProgressIndicator(progress = { state.progress }, modifier = Modifier.size(19.dp), strokeWidth = 2.5.dp, color = c.accent, trackColor = c.fill)
            DownloadState.Done -> Icon(Icons.Rounded.DownloadDone, "Downloaded", tint = c.accent, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun ChapterSkeleton() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        SkeletonBox(Modifier.fillMaxWidth(0.55f).height(14.dp), 4.dp)
        Spacer(Modifier.height(6.dp))
        SkeletonBox(Modifier.fillMaxWidth(0.3f).height(10.dp), 4.dp)
    }
}

@Composable
private fun BulkAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = Folio.colors
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).pressable(onClick = onClick, haptic = true).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = c.accent, modifier = Modifier.size(24.dp))
        Text(label, style = Folio.type.caption2, color = c.accent)
    }
}
