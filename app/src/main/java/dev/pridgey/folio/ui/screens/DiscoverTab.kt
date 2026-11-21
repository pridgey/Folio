package dev.pridgey.folio.ui.screens

import androidx.compose.material.icons.rounded.LocalOffer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dev.pridgey.folio.Graph
import dev.pridgey.folio.image.CoverModel
import dev.pridgey.folio.source.ExtensionManager
import dev.pridgey.folio.source.Health
import dev.pridgey.folio.source.MangaDexSource
import dev.pridgey.folio.source.SourceManager
import dev.pridgey.folio.ui.LocalNav
import dev.pridgey.folio.ui.components.BottomBarSpacer
import dev.pridgey.folio.ui.components.Chip
import dev.pridgey.folio.ui.components.coverCacheKeys
import dev.pridgey.folio.ui.components.CircleButton
import dev.pridgey.folio.ui.components.Cover
import dev.pridgey.folio.ui.components.GlassState
import dev.pridgey.folio.ui.components.glassSource
import dev.pridgey.folio.ui.components.LargeTitle
import dev.pridgey.folio.ui.components.Notice
import dev.pridgey.folio.ui.components.SearchField
import dev.pridgey.folio.ui.components.SectionHeading
import dev.pridgey.folio.ui.components.SkeletonBox
import dev.pridgey.folio.ui.components.StatusDot
import dev.pridgey.folio.ui.components.TopBar
import dev.pridgey.folio.ui.components.pressable
import dev.pridgey.folio.ui.theme.Folio
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/** Something loading from a source. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

class DiscoverViewModel : ViewModel() {
    data class Feed(val popular: Load<List<SManga>> = Load.Loading, val latest: Load<List<SManga>> = Load.Loading)

    private val _feeds = MutableStateFlow<Map<Long, Feed>>(emptyMap())
    val feeds = _feeds.asStateFlow()

    /** The source being shown; null means "the primary one, once it's ready". */
    val selected = MutableStateFlow<Long?>(null)

    /** "Because you read…" shelves, drawn from the library. */
    val recommendations = MutableStateFlow<List<Recommendation>>(emptyList())

    init {
        viewModelScope.launch { recommendations.value = runCatching { Recommender.forYou() }.getOrDefault(emptyList()) }
    }

    fun load(sourceId: Long, force: Boolean = false) {
        val current = _feeds.value[sourceId]
        if (!force && current != null && current.popular !is Load.Failed) return
        _feeds.update { it + (sourceId to Feed()) }
        viewModelScope.launch {
            val popular = runCatching { Graph.sources.call(sourceId) { it.popular(1) }.mangas }
            _feeds.update { it + (sourceId to (it[sourceId] ?: Feed()).copy(popular = popular.fold({ Load.Ready(it) }, { Load.Failed(SourceManager.describe(it)) }))) }
        }
        viewModelScope.launch {
            val latest = runCatching { Graph.sources.call(sourceId) { it.latest(1) }.mangas }
            _feeds.update { it + (sourceId to (it[sourceId] ?: Feed()).copy(latest = latest.fold({ Load.Ready(it) }, { Load.Failed(SourceManager.describe(it)) }))) }
        }
    }
}

@Composable
fun DiscoverTab(glass: GlassState, vm: DiscoverViewModel = viewModel()) {
    val c = Folio.colors
    val nav = LocalNav.current
    val extensions by Graph.extensions.status.collectAsStateWithLifecycle()
    val health by Graph.sources.health.collectAsStateWithLifecycle()
    val settings by Graph.settings.values.collectAsStateWithLifecycle()
    val feeds by vm.feeds.collectAsStateWithLifecycle()
    val chosen by vm.selected.collectAsStateWithLifecycle()
    val mangaFire = extensions[ExtensionManager.MANGAFIRE]
    val primary = remember(extensions, settings.language) { Graph.sources.primary() }
    val sourceId = chosen ?: primary?.id ?: MangaDexSource.ID
    val list = rememberLazyListState()
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 1 } }

    LaunchedEffect(sourceId) { vm.load(sourceId) }
    val feed = feeds[sourceId] ?: DiscoverViewModel.Feed()
    val sourceName = Graph.sources.nameOf(sourceId)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().glassSource(glass)) {
            item { Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars).height(48.dp)) }
            item {
                LargeTitle("Discover")
                SearchField("", {}, Modifier.fillMaxWidth().padding(horizontal = 16.dp), placeholder = "Titles, authors, links", onClick = { nav.search() })
                Spacer(Modifier.height(12.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val primaryId = primary?.id
                    if (primaryId != null) {
                        item { SourceChip(primary.name, primaryId == sourceId, health[primaryId]) { vm.selected.value = primaryId } }
                    } else {
                        item { Chip("MangaFire", leading = Icons.Rounded.Extension, onClick = { nav.sources() }) }
                    }
                    Graph.sources.additional().forEach { extra ->
                        item(key = extra.id) { SourceChip(extra.name, extra.id == sourceId, health[extra.id]) { vm.selected.value = extra.id } }
                    }
                    item { SourceChip("MangaDex", sourceId == MangaDexSource.ID, health[MangaDexSource.ID]) { vm.selected.value = MangaDexSource.ID } }
                }
            }

            // MangaFire not set up yet: say so, and get it.
            if (primary == null) {
                item {
                    val busy = mangaFire?.busy != null
                    Notice(
                        title = if (busy) "Setting up MangaFire…" else "MangaFire isn't installed",
                        message = mangaFire?.error ?: "Folio gets MangaFire's source from Keiyoushi, checks its signature, and keeps it up to date.",
                        icon = Icons.Rounded.Extension, tint = c.accent, busy = busy,
                        action = if (busy) null else "Install",
                        onAction = { Graph.scope.launch { Graph.extensions.install(ExtensionManager.MANGAFIRE) } },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                    )
                }
            }

            val sourceHealth = health[sourceId]
            if (sourceHealth is Health.Failing && sourceHealth.healing) {
                item {
                    Notice(
                        "Updating $sourceName…", "The site changed; Folio is fetching a fixed version of the source.",
                        Icons.Rounded.Extension, c.warning, busy = true, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                    )
                }
            }

            item {
                AnimatedContent(feed.popular, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "hero") { popular ->
                    when (popular) {
                        is Load.Loading -> HeroSkeleton()
                        is Load.Ready -> if (popular.value.isNotEmpty()) Hero(sourceId, popular.value.take(6))
                        is Load.Failed -> FailedFeed(
                            sourceName, popular.message,
                            onRetry = { vm.load(sourceId, force = true) },
                            onFallback = if (sourceId != MangaDexSource.ID && settings.fallbackEnabled) ({ vm.selected.value = MangaDexSource.ID }) else null,
                            onOpenSite = Graph.sources.get(sourceId)?.let { s -> { nav.web(s.homeUrl, s.name) } },
                        )
                    }
                }
            }

            item(key = "for-you") {
                val recs by vm.recommendations.collectAsStateWithLifecycle()
                BecauseYouRead(recs)
            }
            item { Shelf("Popular", sourceId, (feed.popular as? Load.Ready)?.value?.drop(6), feed.popular is Load.Loading) { nav.browse(sourceId, "popular") } }
            item { Shelf("Latest Updates", sourceId, (feed.latest as? Load.Ready)?.value, feed.latest is Load.Loading) { nav.browse(sourceId, "latest") } }
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    Chip("Explore tags", leading = Icons.Rounded.LocalOffer, onClick = { nav.tags() })
                    Chip("Browse with filters", leading = Icons.Rounded.Tune, onClick = { nav.browse(sourceId, "search") })
                }
            }
            item { BottomBarSpacer() }
        }
        TopBar("Discover", glass, showTitle = scrolled) {
            CircleButton(Icons.Rounded.Search, "Search", { nav.search() })
        }
    }
}

@Composable
private fun SourceChip(name: String, selected: Boolean, health: Health?, onClick: () -> Unit) {
    val c = Folio.colors
    val dot = when (health) {
        is Health.Ok -> c.success
        is Health.Failing -> if (health.healing) c.warning else c.danger
        else -> null
    }
    Row(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) c.label else c.fill)
            .pressable(onClick = onClick, haptic = true)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            StatusDot(dot)
            Spacer(Modifier.width(7.dp))
        }
        Text(name, style = Folio.type.subheadStrong, color = if (selected) c.background else c.label)
    }
}

/** Big swipeable cards of the most popular titles, each over a blurred wash of its own cover. */
@Composable
private fun Hero(sourceId: Long, items: List<SManga>) {
    val c = Folio.colors
    val nav = LocalNav.current
    val context = LocalContext.current
    val pager = rememberPagerState { items.size }
    Column(Modifier.padding(top = 18.dp)) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
        ) { page ->
            val manga = items[page]
            val offset = (pager.currentPage - page + pager.currentPageOffsetFraction).absoluteValue
            val key = "hero-$sourceId-${manga.url}"
            Box(
                Modifier
                    .graphicsLayer {
                        val s = lerp(1f, 0.94f, offset.coerceIn(0f, 1f))
                        scaleX = s
                        scaleY = s
                    }
                    .fillMaxWidth()
                    .aspectRatio(1.55f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(c.surface)
                    .pressable(onClick = { nav.title(sourceId, manga, key) }, pressedScale = 0.98f),
            ) {
                manga.thumbnail_url?.let { url ->
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(CoverModel(sourceId, url)).coverCacheKeys(sourceId, url).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().blur(40.dp).graphicsLayer { alpha = 0.9f; scaleX = 1.3f; scaleY = 1.3f },
                    )
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.62f)))))
                Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.Bottom) {
                    Cover(sourceId, manga.thumbnail_url, manga.title, Modifier.width(104.dp), sharedKey = key, elevation = 12.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
                        Text("#${page + 1} POPULAR", style = Folio.type.caption2, color = Color.White.copy(alpha = 0.75f))
                        Spacer(Modifier.height(3.dp))
                        Text(manga.title, style = Folio.type.title3, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        manga.genre?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(3)?.joinToString(" · ")?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, style = Folio.type.footnote, color = Color.White.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
            repeat(items.size) { i ->
                val active = i == pager.currentPage
                Box(
                    Modifier.padding(horizontal = 3.dp).height(6.dp).width(if (active) 18.dp else 6.dp)
                        .clip(RoundedCornerShape(50)).background(if (active) c.label else c.tertiaryLabel),
                )
            }
        }
    }
}

@Composable
private fun HeroSkeleton() {
    SkeletonBox(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp).fillMaxWidth().aspectRatio(1.55f), corner = 22.dp)
}

@Composable
fun FailedFeed(sourceName: String, message: String, onRetry: () -> Unit, onFallback: (() -> Unit)?, onOpenSite: (() -> Unit)?) {
    val c = Folio.colors
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Icon(Icons.Rounded.CloudOff, null, tint = c.secondaryLabel, modifier = Modifier.padding(bottom = 10.dp))
        Text("$sourceName isn't answering", style = Folio.type.headline, color = c.label)
        Spacer(Modifier.height(4.dp))
        Text(message, style = Folio.type.subhead, color = c.secondaryLabel, maxLines = 3, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Try again", onClick = onRetry)
            onFallback?.let { Chip("Use MangaDex", selected = true, onClick = it) }
        }
        onOpenSite?.let {
            Spacer(Modifier.height(6.dp))
            dev.pridgey.folio.ui.components.BarTextButton("Open the site to verify", it)
        }
    }
}

@Composable
private fun Shelf(title: String, sourceId: Long, items: List<SManga>?, loading: Boolean, onSeeAll: () -> Unit) {
    val c = Folio.colors
    val nav = LocalNav.current
    if (!loading && items.isNullOrEmpty()) return
    SectionHeading(title, action = "See All", onAction = onSeeAll)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (items == null) {
            items(6) { Column(Modifier.width(112.dp)) { SkeletonBox(Modifier.fillMaxWidth().aspectRatio(2f / 3f), 10.dp); Spacer(Modifier.height(8.dp)); SkeletonBox(Modifier.fillMaxWidth(0.8f).height(12.dp), 4.dp) } }
        } else {
            items(items, key = { "$title-${it.url}" }) { manga ->
                val key = "shelf-$title-$sourceId-${manga.url}"
                Column(Modifier.width(112.dp).pressable(onClick = { nav.title(sourceId, manga, key) })) {
                    Cover(sourceId, manga.thumbnail_url, manga.title, Modifier.fillMaxWidth(), sharedKey = key)
                    Spacer(Modifier.height(7.dp))
                    Text(manga.title, style = Folio.type.footnoteStrong, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
