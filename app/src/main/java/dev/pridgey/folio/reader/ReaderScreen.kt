@file:OptIn(ExperimentalMaterial3Api::class)

package dev.pridgey.folio.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import dev.pridgey.folio.ui.theme.CoverAccent
import dev.pridgey.folio.ui.screens.ChapterExplorerSheet
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import dev.pridgey.folio.Graph
import dev.pridgey.folio.data.ChapterNumbers
import dev.pridgey.folio.data.PageScale
import dev.pridgey.folio.data.ReaderBackground
import dev.pridgey.folio.data.ReaderMode
import dev.pridgey.folio.source.MangaDexSource
import dev.pridgey.folio.ui.components.CircleButton
import dev.pridgey.folio.ui.components.FolioSwitch
import dev.pridgey.folio.ui.components.PrimaryButton
import dev.pridgey.folio.ui.components.SegmentedControl
import dev.pridgey.folio.ui.components.TintedButton
import dev.pridgey.folio.ui.components.pressable
import dev.pridgey.folio.ui.screens.ChallengeSheet
import dev.pridgey.folio.ui.theme.Folio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

@Composable
fun ReaderScreen(
    vm: ReaderViewModel,
    pageKeys: Flow<Int>,
    onExit: () -> Unit,
    onChromeVisible: (Boolean) -> Unit,
    onOpenTitle: (mangaId: Long, sourceId: Long) -> Unit = { _, _ -> },
) {
    val manga = vm.state.collectAsStateWithLifecycle().value.manga
    CoverAccent(manga?.sourceId ?: 0L, manga?.thumbnailUrl) { ReaderBody(vm, pageKeys, onExit, onChromeVisible, onOpenTitle) }
}

@Composable
private fun ReaderBody(
    vm: ReaderViewModel,
    pageKeys: Flow<Int>,
    onExit: () -> Unit,
    onChromeVisible: (Boolean) -> Unit,
    onOpenTitle: (mangaId: Long, sourceId: Long) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by Graph.settings.values.collectAsStateWithLifecycle()
    var chrome by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var explorer by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(chrome) { onChromeVisible(chrome) }
    BackHandler(chrome) { chrome = false }

    val background = when (settings.readerBackground) {
        ReaderBackground.BLACK, ReaderBackground.AUTO -> Color.Black
        ReaderBackground.GRAY -> Color(0xFF2C2C2E)
        ReaderBackground.WHITE -> Color.White
    }
    val toggleChrome = { chrome = !chrome }

    Box(Modifier.fillMaxSize().background(background)) {
        val items = state.items
        if (items.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = Color.White.copy(alpha = 0.6f), strokeWidth = 2.5.dp)
        } else {
            // Recreate the viewer when the reader is reset (new chapter, new mode) so it starts at the right page.
            androidx.compose.runtime.key(state.resetToken, state.mode) {
                if (state.mode == ReaderMode.WEBTOON) {
                    WebtoonReader(vm, items, state.startIndex, settings.webtoonGap, settings.tapZones, pageKeys, toggleChrome, chrome)
                } else {
                    PagedReader(vm, items, state.startIndex, state.mode, settings.pageScale, settings.tapZones, pageKeys, toggleChrome)
                }
            }
        }

        // Night filter: dims and warms the page.
        if (settings.nightFilter > 0f) {
            Box(Modifier.fillMaxSize().background(Color(0xFF1A0E00).copy(alpha = settings.nightFilter)))
        }

        // A quiet page counter while the controls are hidden.
        AnimatedVisibility(
            !chrome && settings.showPageNumber && state.pageCount > 0,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 10.dp),
        ) {
            Text(
                "${state.page + 1} / ${state.pageCount}",
                style = Folio.type.caption2, color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        ReaderChrome(
            visible = chrome, state = state, vm = vm, onExit = onExit,
            onSettings = { sheet = true },
            onChapters = { explorer = true },
            onTitle = { state.manga?.let { onOpenTitle(it.id, it.sourceId) } },
            onBookmark = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.toggleBookmark()
            },
        )
        ChallengeSheet()
    }

    if (sheet) ReaderSettingsSheet(vm, state, onDismiss = { sheet = false })
    val manga = state.manga
    if (explorer && manga != null) {
        val chapters by remember(manga.id) { Graph.library.observeChapters(manga.id) }.collectAsStateWithLifecycle(null)
        chapters?.takeIf { it.isNotEmpty() }?.let { list ->
            ChapterExplorerSheet(
                mangaId = manga.id, sourceId = manga.sourceId, chapters = list, readingNowId = state.current?.id,
                onRead = { ch -> explorer = false; chrome = false; vm.goTo(ch) },
                onDismiss = { explorer = false },
            )
        }
    }
}

// ------------------------------------------------------------------ Paged

@Composable
private fun PagedReader(
    vm: ReaderViewModel,
    items: List<ReaderItem>,
    start: Int,
    mode: ReaderMode,
    scale: PageScale,
    tapZones: Boolean,
    pageKeys: Flow<Int>,
    toggleChrome: () -> Unit,
) {
    // The pager reads its count and keys in derived state outside composition. Every lambda reads this
    // one state, so they always see the same list, even in the frame where the next chapter is appended.
    val latest = rememberUpdatedState(items)
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { latest.value.size }
    val scope = rememberCoroutineScope()
    val rtl = mode == ReaderMode.RIGHT_TO_LEFT

    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { vm.onPosition(it) }
    }
    LaunchedEffect(pager) {
        pageKeys.collect { d -> scope.launch { pager.animateScrollToPage((pager.currentPage + d).coerceIn(0, latest.value.size - 1)) } }
    }

    val turn: (Int) -> Unit = { d -> scope.launch { pager.animateScrollToPage((pager.currentPage + d).coerceIn(0, latest.value.size - 1)) } }
    val onTap: (Offset, Float, Float) -> Unit = { offset, width, height ->
        val third = if (mode == ReaderMode.VERTICAL) offset.y / height else offset.x / width
        when {
            !tapZones || third in 0.33f..0.67f -> toggleChrome()
            third < 0.33f -> turn(if (rtl) 1 else -1)
            else -> turn(if (rtl) -1 else 1)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val page: @Composable (Int) -> Unit = { i ->
            latest.value.getOrNull(i)?.let { ReaderPage(vm, it, scale, onTap = { o -> onTap(o, w, h) }, isFirst = i == start) }
        }
        if (mode == ReaderMode.VERTICAL) {
            VerticalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { latest.value.getOrNull(it)?.key ?: it }) { page(it) }
        } else {
            HorizontalPager(pager, Modifier.fillMaxSize(), reverseLayout = rtl, beyondViewportPageCount = 1, key = { latest.value.getOrNull(it)?.key ?: it }) { page(it) }
        }
    }
}

@Composable
private fun ReaderPage(vm: ReaderViewModel, item: ReaderItem, scale: PageScale, onTap: (Offset) -> Unit, isFirst: Boolean) {
    val context = LocalContext.current
    when (item) {
        is ReaderItem.Page -> {
            val zoom = rememberZoomableImageState(rememberZoomableState())
            var failed by remember(item.key) { mutableStateOf(false) }
            var attempt by remember(item.key) { mutableStateOf(0) }
            val request = remember(item.key, attempt) {
                ImageRequest.Builder(context).data(item.model)
                    .listener(
                        onError = { _, _ -> failed = true },
                        onSuccess = { _, r -> if (isFirst) vm.reportAspect(r.image.height / r.image.width.coerceAtLeast(1).toFloat()) },
                    )
                    .build()
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ZoomableAsyncImage(
                    model = request,
                    contentDescription = "Page ${item.index + 1}",
                    state = zoom,
                    contentScale = when (scale) {
                        PageScale.FIT_SCREEN -> ContentScale.Fit
                        PageScale.FIT_WIDTH -> ContentScale.FillWidth
                        PageScale.ORIGINAL -> ContentScale.None
                    },
                    onClick = onTap,
                    modifier = Modifier.fillMaxSize(),
                )
                if (!zoom.isImageDisplayed && !failed) PageSpinner(item.index + 1)
                if (failed) PageError { failed = false; attempt++ }
            }
        }
        is ReaderItem.Transition -> TransitionCard(vm, item, Modifier.pointerInput(Unit) { detectTapGestures(onTap = onTap) })
        is ReaderItem.Failed -> ChapterError(vm, item)
        is ReaderItem.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PageSpinner(null) }
    }
}

@Composable
private fun PageSpinner(number: Int?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(Modifier.size(26.dp), color = Color.White.copy(alpha = 0.55f), strokeWidth = 2.dp)
        if (number != null) {
            Spacer(Modifier.height(10.dp))
            Text("$number", style = Folio.type.footnote, color = Color.White.copy(alpha = 0.45f))
        }
    }
}

@Composable
private fun PageError(onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.BrokenImage, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(10.dp))
        Text("This page didn't load", style = Folio.type.subhead, color = Color.White.copy(alpha = 0.8f))
        Spacer(Modifier.height(12.dp))
        TintedButton("Try again", onRetry)
    }
}

// ------------------------------------------------------------------ Continuous scroll

@Composable
private fun WebtoonReader(
    vm: ReaderViewModel,
    items: List<ReaderItem>,
    start: Int,
    gap: Boolean,
    tapZones: Boolean,
    pageKeys: Flow<Int>,
    toggleChrome: () -> Unit,
    chrome: Boolean,
) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = start.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val zoom = remember { Animatable(1f) }
    var panX by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex }.distinctUntilChanged().collect { vm.onPosition(it) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = constraints.maxHeight.toFloat()
        val width = constraints.maxWidth.toFloat()
        LaunchedEffect(list) { pageKeys.collect { d -> list.animateScrollBy(d * height * 0.8f) } }
        LazyColumn(
            state = list,
            modifier = Modifier
                .fillMaxSize()
                // Pinch to zoom the whole strip; scrolling still works while zoomed.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size >= 2) {
                                val z = event.calculateZoom()
                                val pan = event.calculatePan()
                                val next = (zoom.value * z).coerceIn(1f, 3.5f)
                                scope.launch { zoom.snapTo(next) }
                                val maxPan = (width * (next - 1f)) / 2f
                                panX = (panX + pan.x).coerceIn(-maxPan, maxPan)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (zoom.value > 1f) {
                                val pan = event.calculatePan()
                                val maxPan = (width * (zoom.value - 1f)) / 2f
                                panX = (panX + pan.x).coerceIn(-maxPan, maxPan)
                            }
                            @Suppress("UNUSED_VARIABLE") val centroid = event.calculateCentroid()
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(tapZones, chrome) {
                    detectTapGestures(
                        onDoubleTap = {
                            scope.launch {
                                if (zoom.value > 1.05f) {
                                    zoom.animateTo(1f, spring(dampingRatio = 0.8f)); panX = 0f
                                } else {
                                    zoom.animateTo(2f, spring(dampingRatio = 0.8f))
                                }
                            }
                        },
                        onTap = { o ->
                            val y = o.y / height
                            when {
                                !tapZones || y in 0.3f..0.7f -> toggleChrome()
                                y < 0.3f -> scope.launch { list.animateScrollBy(-height * 0.75f, tween(260)) }
                                else -> scope.launch { list.animateScrollBy(height * 0.75f, tween(260)) }
                            }
                        },
                    )
                }
                .graphicsLayer {
                    scaleX = zoom.value
                    scaleY = zoom.value
                    translationX = panX
                },
            contentPadding = PaddingValues(bottom = with(density) { (height * 0.3f).toDp() }),
        ) {
            itemsIndexed(items, key = { _, it -> it.key }) { i, item ->
                when (item) {
                    is ReaderItem.Page -> WebtoonPage(vm, item, gap, isFirst = i == start)
                    is ReaderItem.Transition -> TransitionCard(vm, item, Modifier.heightIn(min = 420.dp))
                    is ReaderItem.Failed -> ChapterError(vm, item, Modifier.heightIn(min = 420.dp))
                    is ReaderItem.Loading -> Box(Modifier.fillMaxWidth().height(480.dp), contentAlignment = Alignment.Center) { PageSpinner(null) }
                }
            }
        }
    }
}

@Composable
private fun WebtoonPage(vm: ReaderViewModel, item: ReaderItem.Page, gap: Boolean, isFirst: Boolean) {
    val context = LocalContext.current
    var attempt by remember(item.key) { mutableStateOf(0) }
    val request = remember(item.key, attempt) {
        ImageRequest.Builder(context).data(item.model)
            .listener(onSuccess = { _, r -> if (isFirst) vm.reportAspect(r.image.height / r.image.width.coerceAtLeast(1).toFloat()) })
            .build()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = "Page ${item.index + 1}",
        contentScale = ContentScale.FillWidth,
        modifier = Modifier.fillMaxWidth().padding(bottom = if (gap) 6.dp else 0.dp),
        loading = { Box(Modifier.fillMaxWidth().aspectRatio(0.7f), contentAlignment = Alignment.Center) { PageSpinner(item.index + 1) } },
        error = { Box(Modifier.fillMaxWidth().aspectRatio(0.9f), contentAlignment = Alignment.Center) { PageError { attempt++ } } },
    )
}

// ------------------------------------------------------------------ Chapter boundaries and errors

@Composable
private fun TransitionCard(vm: ReaderViewModel, item: ReaderItem.Transition, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            if (item.forward) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Folio.colors.accent, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(14.dp))
                Text("Finished", style = Folio.type.footnote, color = Color.White.copy(alpha = 0.6f))
                Text(item.from?.name.orEmpty(), style = Folio.type.title3, color = Color.White, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(28.dp))
                if (item.to != null) {
                    Text("Up next", style = Folio.type.footnote, color = Color.White.copy(alpha = 0.6f))
                    Text(item.to.name, style = Folio.type.headline, color = Color.White, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Text(if (state.mode == ReaderMode.WEBTOON) "Keep scrolling" else "Keep swiping", style = Folio.type.caption, color = Color.White.copy(alpha = 0.45f))
                } else {
                    Text("You're all caught up", style = Folio.type.headline, color = Color.White)
                    Text("New chapters will show up in Updates.", style = Folio.type.footnote, color = Color.White.copy(alpha = 0.6f))
                }
            } else {
                if (item.from != null) {
                    Text("Previous", style = Folio.type.footnote, color = Color.White.copy(alpha = 0.6f))
                    Text(item.from.name, style = Folio.type.headline, color = Color.White, textAlign = TextAlign.Center, maxLines = 2)
                    Spacer(Modifier.height(14.dp))
                    TintedButton("Read previous chapter", { vm.goTo(item.from) })
                } else {
                    Text("Start of the series", style = Folio.type.headline, color = Color.White.copy(alpha = 0.7f))
                }
                Spacer(Modifier.height(24.dp))
                Text("Now reading", style = Folio.type.footnote, color = Color.White.copy(alpha = 0.6f))
                Text(item.to?.name.orEmpty(), style = Folio.type.title3, color = Color.White, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

@Composable
private fun ChapterError(vm: ReaderViewModel, item: ReaderItem.Failed, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val canFallBack = state.manga?.sourceId != MangaDexSource.ID && Graph.settings.current.fallbackEnabled
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.CloudOff, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text("Couldn't open ${item.chapter.name}", style = Folio.type.headline, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(state.message ?: item.message, style = Folio.type.subhead, color = Color.White.copy(alpha = 0.65f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Try again", { vm.retry(item.chapter) }, Modifier.width(240.dp))
            if (canFallBack) {
                Spacer(Modifier.height(10.dp))
                TintedButton(if (state.fallbackBusy) "Looking on MangaDex…" else "Read on MangaDex", {
                    if (!state.fallbackBusy) vm.readOnFallback(item.chapter) { _, _ -> }
                }, Modifier.width(240.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ Controls

@Composable
private fun ReaderChrome(
    visible: Boolean,
    state: ReaderViewModel.State,
    vm: ReaderViewModel,
    onExit: () -> Unit,
    onSettings: () -> Unit,
    onChapters: () -> Unit,
    onTitle: () -> Unit,
    onBookmark: () -> Unit,
) {
    val glass = Color(0xFF141416).copy(alpha = 0.86f)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut() + slideOutVertically { -it / 2 }, modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth().background(glass).windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onExit, background = Color.White.copy(alpha = 0.1f), tint = Color.White)
                Spacer(Modifier.width(12.dp))
                // Tapping the title opens the title's page.
                Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).pressable(onClick = onTitle, pressedScale = 0.98f).padding(horizontal = 4.dp, vertical = 2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.manga?.title.orEmpty(), style = Folio.type.caption, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Open title", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(start = 4.dp).size(12.dp))
                    }
                    Text(state.current?.name.orEmpty(), style = Folio.type.headline, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                CircleButton(
                    if (state.current?.bookmark == true) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, "Bookmark", onBookmark,
                    background = Color.White.copy(alpha = 0.1f), tint = if (state.current?.bookmark == true) Folio.colors.accent else Color.White,
                )
                Spacer(Modifier.width(8.dp))
                CircleButton(Icons.Rounded.GridView, "Chapters", onChapters, background = Color.White.copy(alpha = 0.1f), tint = Color.White)
                Spacer(Modifier.width(8.dp))
                CircleButton(Icons.Rounded.Tune, "Reader settings", onSettings, background = Color.White.copy(alpha = 0.1f), tint = Color.White)
            }
        }
        AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }, modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(Modifier.fillMaxWidth().background(glass).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (state.pageCount > 0) {
                    val rtl = state.mode == ReaderMode.RIGHT_TO_LEFT
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircleButton(if (rtl) Icons.Rounded.SkipNext else Icons.Rounded.SkipPrevious, "Previous chapter", { vm.previousChapter() }, background = Color.Transparent, tint = if (vm.hasPrevious) Color.White else Color.White.copy(alpha = 0.3f))
                        Text("${state.page + 1}", style = Folio.type.footnoteStrong, color = Color.White, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
                        PageScrubber(state, vm, Modifier.weight(1f))
                        Text("${state.pageCount}", style = Folio.type.footnoteStrong, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
                        CircleButton(if (rtl) Icons.Rounded.SkipPrevious else Icons.Rounded.SkipNext, "Next chapter", { vm.nextChapter() }, background = Color.Transparent, tint = if (vm.hasNext) Color.White else Color.White.copy(alpha = 0.3f))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
                    val number = state.current?.number?.let { ChapterNumbers.format(it) }.orEmpty()
                    Text(
                        listOf(state.mode.label, if (number.isNotEmpty()) "Chapter $number" else null).filterNotNull().joinToString("  ·  "),
                        style = Folio.type.caption, color = Color.White.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

/**
 * Drag to jump to a page. Jumping restarts the viewer at that page, which keeps the paged and
 * scrolling readers simple and exact.
 */
@Composable
private fun PageScrubber(state: ReaderViewModel.State, vm: ReaderViewModel, modifier: Modifier) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val haptics = LocalHapticFeedback.current
    val c = Folio.colors
    val rtl = state.mode == ReaderMode.RIGHT_TO_LEFT
    val value = dragging ?: state.page.toFloat()
    Box(modifier.graphicsLayer { if (rtl) scaleX = -1f }) {
        Slider(
            value = value,
            onValueChange = {
                val rounded = it.toInt().toFloat()
                if (rounded != dragging) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                dragging = rounded
            },
            onValueChangeFinished = {
                dragging?.let { target -> vm.jumpToPage(target.toInt()) }
                dragging = null
            },
            valueRange = 0f..(state.pageCount - 1).coerceAtLeast(1).toFloat(),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = c.accent, inactiveTrackColor = Color.White.copy(alpha = 0.2f)),
        )
    }
}

@Composable
private fun ReaderSettingsSheet(vm: ReaderViewModel, state: ReaderViewModel.State, onDismiss: () -> Unit) {
    val c = Folio.colors
    val s by Graph.settings.values.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.elevated) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column {
                Label("Reading mode for this title")
                SegmentedControl(ReaderMode.entries, state.titleMode, { it.short }, vm::setTitleMode, Modifier.fillMaxWidth())
                if (state.titleMode == ReaderMode.AUTO) {
                    Text("Automatic is using ${state.mode.label.lowercase()}.", style = Folio.type.caption, color = c.secondaryLabel, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
                }
            }
            if (state.mode != ReaderMode.WEBTOON) {
                Column {
                    Label("Page size")
                    SegmentedControl(PageScale.entries, s.pageScale, { it.label }, { v -> Graph.settings.update { it.copy(pageScale = v) } }, Modifier.fillMaxWidth())
                }
            }
            Column {
                Label("Background")
                SegmentedControl(ReaderBackground.entries.filter { it != ReaderBackground.AUTO }, s.readerBackground, { it.label }, { v -> Graph.settings.update { it.copy(readerBackground = v) } }, Modifier.fillMaxWidth())
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DarkMode, null, tint = c.secondaryLabel, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Label("Night filter", Modifier.weight(1f))
                }
                Slider(
                    value = s.nightFilter, onValueChange = { v -> Graph.settings.update { it.copy(nightFilter = v) } }, valueRange = 0f..0.7f,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = c.warning, inactiveTrackColor = c.fill),
                )
            }
            Column(Modifier.clip(RoundedCornerShape(14.dp)).background(c.surface)) {
                SheetToggle("Tap the sides to turn pages", s.tapZones) { v -> Graph.settings.update { it.copy(tapZones = v) } }
                SheetToggle("Volume buttons turn pages", s.volumeKeys) { v -> Graph.settings.update { it.copy(volumeKeys = v) } }
                SheetToggle("Show page number", s.showPageNumber) { v -> Graph.settings.update { it.copy(showPageNumber = v) } }
                if (state.mode == ReaderMode.WEBTOON) SheetToggle("Gaps between pages", s.webtoonGap) { v -> Graph.settings.update { it.copy(webtoonGap = v) } }
                SheetToggle("Keep the screen on", s.keepScreenOn) { v -> Graph.settings.update { it.copy(keepScreenOn = v) } }
            }
        }
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) =
    Text(text, style = Folio.type.footnoteStrong, color = Folio.colors.secondaryLabel, modifier = modifier.padding(bottom = 8.dp, start = 2.dp))

@Composable
private fun SheetToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick = { onChange(!checked) }, pressedScale = 1f).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Folio.type.body, color = Folio.colors.label, modifier = Modifier.weight(1f))
        FolioSwitch(checked, onChange)
    }
}
