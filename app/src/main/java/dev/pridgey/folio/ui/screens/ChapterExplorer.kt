package dev.pridgey.folio.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.pridgey.folio.Graph
import dev.pridgey.folio.data.ChapterEntity
import dev.pridgey.folio.data.ChapterNumbers
import dev.pridgey.folio.data.LibraryRepository
import dev.pridgey.folio.image.PageModel
import dev.pridgey.folio.source.SourceManager
import dev.pridgey.folio.ui.components.Chip
import dev.pridgey.folio.ui.components.CircleButton
import dev.pridgey.folio.ui.components.PrimaryButton
import dev.pridgey.folio.ui.components.ProgressBar
import dev.pridgey.folio.ui.components.SearchField
import dev.pridgey.folio.ui.components.TintedButton
import dev.pridgey.folio.ui.components.pressable
import dev.pridgey.folio.ui.components.Format
import dev.pridgey.folio.ui.theme.Folio
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val PER_ROW = 10

/**
 * A map of every chapter in a title, for finding your place: jump by number or landmark, look inside a
 * chapter before opening it, and set where reading continues from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterExplorerSheet(
    mangaId: Long,
    sourceId: Long,
    chapters: List<ChapterEntity>,
    onRead: (ChapterEntity) -> Unit,
    onDismiss: () -> Unit,
    readingNowId: Long? = null,
) {
    val c = Folio.colors
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val ordered = remember(chapters) { LibraryRepository.readingOrder(chapters) }
    val place = remember(chapters) { Graph.library.nextToRead(chapters) }
    val rows = remember(ordered) { ordered.chunked(PER_ROW) }
    val readCount = remember(ordered) { ordered.count { it.read } }
    var selectedId by remember { mutableStateOf(readingNowId ?: place?.id) }
    val selectedIndex = ordered.indexOfFirst { it.id == selectedId }
    val selected = ordered.getOrNull(selectedIndex)
    var query by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<ChapterEntity?>(null) }
    val map = rememberLazyListState()

    fun select(ch: ChapterEntity?, scroll: Boolean = true) {
        ch ?: return
        selectedId = ch.id
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (scroll) {
            val row = ordered.indexOfFirst { it.id == ch.id } / PER_ROW
            scope.launch { map.animateScrollToItem((row - 2).coerceAtLeast(0)) }
        }
    }

    LaunchedEffect(Unit) {
        if (selectedIndex >= 0) map.scrollToItem((selectedIndex / PER_ROW - 2).coerceAtLeast(0))
    }

    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.elevated) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 16.dp)) {
            // Where you are, at a glance.
            Text("Chapters", style = Folio.type.title2, color = c.label)
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append("$readCount of ${ordered.size} read")
                    place?.let { p -> ChapterNumbers.format(p.number).takeIf { it.isNotEmpty() }?.let { append("  ·  Your place: chapter $it") } }
                },
                style = Folio.type.footnote, color = c.secondaryLabel,
            )
            Spacer(Modifier.height(8.dp))
            ProgressBar(if (ordered.isEmpty()) 0f else readCount / ordered.size.toFloat(), Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))

            // Landmarks.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val lastOpened = ordered.filter { it.lastReadAt > 0 }.maxByOrNull { it.lastReadAt }
                val firstUnread = ordered.firstOrNull { !it.read }
                if (readingNowId != null) Chip("Reading now", selected = selectedId == readingNowId, onClick = { select(ordered.firstOrNull { it.id == readingNowId }) })
                place?.let { Chip("Your place", selected = selectedId == it.id, onClick = { select(it) }) }
                firstUnread?.let { Chip("First unread", selected = selectedId == it.id && it.id != place?.id, onClick = { select(it) }) }
                lastOpened?.let { Chip("Last opened", selected = selectedId == it.id && it.id != place?.id, onClick = { select(it) }) }
                ordered.firstOrNull()?.let { Chip("First chapter", selected = false, onClick = { select(it) }) }
                ordered.lastOrNull()?.let { Chip("Latest", selected = false, onClick = { select(it) }) }
            }
            Spacer(Modifier.height(10.dp))
            SearchField(
                query,
                { text ->
                    query = text
                    text.toFloatOrNull()?.let { n -> select(ordered.filter { it.number >= 0 }.minByOrNull { abs(it.number - n) }) }
                },
                Modifier.fillMaxWidth(),
                placeholder = "Go to chapter number",
                icon = Icons.Rounded.Tag,
                keyboardType = KeyboardType.Decimal,
            )
            Spacer(Modifier.height(12.dp))

            // The map: one tile per chapter, ten to a row.
            LazyColumn(state = map, modifier = Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(rows, key = { i, _ -> i }) { i, row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        val label = ChapterNumbers.format(row.first().number).ifEmpty { "${i * PER_ROW + 1}" }
                        Text(label, style = Folio.type.caption2, color = c.tertiaryLabel, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(34.dp).padding(end = 6.dp))
                        repeat(PER_ROW) { j ->
                            val ch = row.getOrNull(j)
                            Box(Modifier.weight(1f).padding(2.dp).aspectRatio(1f)) {
                                if (ch != null) {
                                    Tile(ch, selected = ch.id == selectedId, isPlace = ch.id == place?.id, isReadingNow = ch.id == readingNowId) { select(ch, scroll = false) }
                                }
                            }
                        }
                    }
                }
            }
            Legend()
            Spacer(Modifier.height(10.dp))

            // The chosen chapter.
            selected?.let { ch ->
                SelectedChapter(
                    ch = ch, position = selectedIndex + 1, total = ordered.size, mangaId = mangaId, sourceId = sourceId,
                    isPlace = ch.id == place?.id,
                    onPrevious = ordered.getOrNull(selectedIndex - 1)?.let { p -> { select(p) } },
                    onNext = ordered.getOrNull(selectedIndex + 1)?.let { n -> { select(n) } },
                    onRead = { onRead(ch) },
                    onContinueFromHere = { confirm = ch },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    confirm?.let { ch ->
        val name = ChapterNumbers.format(ch.number).let { if (it.isEmpty()) ch.name else "chapter $it" }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Continue from $name?") },
            text = { Text("Everything before it will be marked read, and it and every chapter after it will be marked unread. Continue will open $name next.") },
            confirmButton = {
                TextButton({
                    confirm = null
                    scope.launch { Graph.library.continueFrom(mangaId, ch.id) }
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }) { Text("Set my place") }
            },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } },
            containerColor = c.elevated,
        )
    }
}

@Composable
private fun Tile(ch: ChapterEntity, selected: Boolean, isPlace: Boolean, isReadingNow: Boolean, onClick: () -> Unit) {
    val c = Folio.colors
    val inProgress = !ch.read && ch.lastPageRead > 0 && ch.pageCount > 0
    val fill by animateColorAsState(if (ch.read) c.accent.copy(alpha = 0.55f) else c.fill, label = "tile")
    val scale by animateFloatAsState(if (selected) 1.18f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "tileScale")
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(fill)
            .then(
                when {
                    selected -> Modifier.border(2.dp, c.label, shape)
                    isPlace -> Modifier.border(2.dp, c.accent, shape)
                    isReadingNow -> Modifier.border(1.5.dp, c.secondaryLabel, shape)
                    else -> Modifier
                },
            )
            .pressable(onClick = onClick, pressedScale = 0.9f),
    ) {
        if (inProgress) {
            // Fills from the bottom with how far the chapter was read.
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .fillMaxHeight((ch.lastPageRead + 1f) / ch.pageCount)
                    .background(c.accent.copy(alpha = 0.55f)),
            )
        }
        if (ch.bookmark) {
            Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(5.dp).clip(CircleShape).background(c.warning))
        }
    }
}

@Composable
private fun Legend() {
    val c = Folio.colors
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 34.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem("Read") { Box(Modifier.fillMaxSize().background(c.accent.copy(alpha = 0.55f))) }
        LegendItem("Unread") { Box(Modifier.fillMaxSize().background(c.fill)) }
        LegendItem("Your place") { Box(Modifier.fillMaxSize().background(c.fill).border(2.dp, c.accent, RoundedCornerShape(3.dp))) }
        LegendItem("Bookmark") { Box(Modifier.fillMaxSize().background(c.fill), contentAlignment = Alignment.Center) { Box(Modifier.size(4.dp).clip(CircleShape).background(c.warning)) } }
    }
}

@Composable
private fun LegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp))) { swatch() }
        Spacer(Modifier.width(5.dp))
        Text(label, style = Folio.type.caption2, color = Folio.colors.secondaryLabel)
    }
}

@Composable
private fun SelectedChapter(
    ch: ChapterEntity,
    position: Int,
    total: Int,
    mangaId: Long,
    sourceId: Long,
    isPlace: Boolean,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onRead: () -> Unit,
    onContinueFromHere: () -> Unit,
) {
    val c = Folio.colors
    var preview by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleButton(Icons.Rounded.ChevronLeft, "Previous chapter", { onPrevious?.invoke() }, tint = if (onPrevious != null) c.label else c.tertiaryLabel)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ch.bookmark) Icon(Icons.Rounded.Bookmark, "Bookmarked", tint = c.accent, modifier = Modifier.padding(end = 4.dp).size(15.dp))
                    Text(ch.name, style = Folio.type.headline, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val status = when {
                    ch.read -> "Read"
                    ch.lastPageRead > 0 && ch.pageCount > 0 -> "Page ${ch.lastPageRead + 1} of ${ch.pageCount}"
                    else -> "Unread"
                }
                Text(
                    listOfNotNull("$position of $total", status, if (isPlace) "Your place" else null, Format.date(ch.dateUpload).takeIf { it.isNotEmpty() })
                        .joinToString("  ·  "),
                    style = Folio.type.caption, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            CircleButton(Icons.Rounded.ChevronRight, "Next chapter", { onNext?.invoke() }, tint = if (onNext != null) c.label else c.tertiaryLabel)
        }
        AnimatedVisibility(preview, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            PagePreview(mangaId, sourceId, ch, onOpen = onRead)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TintedButton(if (preview) "Hide pages" else "Peek inside", { preview = !preview }, Modifier.weight(1f), icon = if (preview) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility)
            TintedButton(if (isPlace) "Your place" else "Set as my place", onContinueFromHere, Modifier.weight(1f), icon = Icons.Rounded.Flag, enabled = !isPlace)
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            if (!ch.read && ch.lastPageRead > 0) "Continue reading" else "Read this chapter", onRead,
            Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow,
        )
    }
}

/** The first few pages of a chapter, to recognise where it is in the story. */
@Composable
private fun PagePreview(mangaId: Long, sourceId: Long, ch: ChapterEntity, onOpen: () -> Unit) {
    val c = Folio.colors
    var pages by remember(ch.id) { mutableStateOf<List<Any>?>(null) }
    var error by remember(ch.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(ch.id) {
        runCatching { previewPages(mangaId, sourceId, ch) }
            .onSuccess { pages = it }
            .onFailure { error = SourceManager.describe(it) }
    }
    Box(Modifier.fillMaxWidth().height(150.dp).padding(top = 10.dp), contentAlignment = Alignment.Center) {
        val list = pages
        when {
            error != null -> Text(error!!, style = Folio.type.footnote, color = c.secondaryLabel, textAlign = TextAlign.Center)
            list == null -> CircularProgressIndicator(Modifier.size(22.dp), color = c.secondaryLabel, strokeWidth = 2.dp)
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                itemsIndexed(list) { _, model ->
                    AsyncImage(
                        model = model, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxHeight().aspectRatio(0.7f).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.2f))
                            .pressable(onClick = onOpen),
                    )
                }
            }
        }
    }
}

private suspend fun previewPages(mangaId: Long, sourceId: Long, ch: ChapterEntity): List<Any> {
    Graph.downloads.pages(mangaId, ch.id)?.takeIf { it.isNotEmpty() }?.let { return it.take(PREVIEW_PAGES) }
    val chapter = with(Graph.library) { ch.toSChapter() }
    val pages = Graph.sources.call(sourceId) { it.pages(chapter) }
    return pages.take(PREVIEW_PAGES).mapIndexed { i, p -> PageModel(sourceId, p.url, p.imageUrl, i) }
}

private const val PREVIEW_PAGES = 8
