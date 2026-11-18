@file:OptIn(ExperimentalMaterial3Api::class)

package dev.pridgey.folio.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.pridgey.folio.data.LibraryItem
import dev.pridgey.folio.data.ShelfStyle
import dev.pridgey.folio.ui.components.pressable
import dev.pridgey.folio.ui.theme.Folio
import dev.pridgey.folio.ui.theme.rememberCoverColor
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

// The library as books on shelves. This file is the shelf's behaviour, shared by every look: filling
// rows, scrubbing (books near the finger rise, the one under it is named), tap and hold, and the
// lift's spring. What the books, shelf and wall look like comes from a ShelfLook (see ShelfLooks*.kt).

internal object Spines {
    private const val MIN_WIDTH = 26f
    private const val MAX_WIDTH = 46f
    const val TALLEST = 184

    /**
     * Thicker for longer series, and still growing into the thousands: a one-shot is slim, a 400-chapter
     * series middling, a 1,500-chapter one about as thick as they come.
     */
    fun width(chapters: Int): Float {
        val t = sqrt(chapters.coerceAtLeast(0) / 1500f).coerceAtMost(1f)
        return MIN_WIDTH + (MAX_WIDTH - MIN_WIDTH) * t
    }

    /** A seed from the title alone, which survives re-adding a title or moving it to another source. */
    fun seed(item: LibraryItem): Long = item.title.trim().lowercase().hashCode().toLong() and 0xFFFFFFFFL

    fun mix(id: Long, salt: Long): Long = ((id + salt) * 2654435761L) ushr 9

    /** Books aren't all the same height (within about a tenth). Steady per title. */
    fun height(id: Long): Int = TALLEST - (mix(id, 0) % 5).toInt() * 4

    /** A steady per-title choice among [n] variations. */
    fun pick(id: Long, n: Int, salt: Long = 7): Int = (mix(id, salt) % n).toInt()

    /** Fills shelves left to right, starting a new one when the next book wouldn't fit. */
    fun rows(items: List<LibraryItem>, available: Float, gap: Int = 3): List<List<LibraryItem>> {
        val rows = ArrayList<List<LibraryItem>>()
        var row = ArrayList<LibraryItem>()
        var used = 0f
        for (item in items) {
            val w = width(item.total) + if (row.isEmpty()) 0 else gap
            if (row.isNotEmpty() && used + w > available) {
                rows += row
                row = ArrayList()
                used = 0f
            }
            used += if (row.isEmpty()) width(item.total) else w
            row += item
        }
        if (row.isNotEmpty()) rows += row
        return rows
    }

    /** How far a book a [distance] from the finger comes off the shelf, 0 to 1. */
    fun pull(distance: Float, reach: Float): Float = exp(-(distance / reach) * (distance / reach))
}

/** Everything a look needs to draw one book. */
internal class Book(
    val item: LibraryItem,
    val index: Int,
    val width: Dp,
    val height: Dp,
    /** The cover's main colour, once known. */
    val cover: Color?,
) {
    /** Steady per title, so a book keeps its look through re-adding or moving sources. */
    val seed: Long get() = Spines.seed(item)
    val read get() = (item.total - item.unread).coerceAtLeast(0)
    val started get() = read > 0
}

/** One shelf row as the wall behind it sees it. */
internal class ShelfRow(
    val books: List<Book>,
    /** Each book's centre from the wall's left edge, in px. */
    val centers: List<Float>,
    val first: Boolean,
    val last: Boolean,
    val dark: Boolean,
    /** Where the finger is on the wall, in px, while touching. */
    val touch: Float? = null,
    /** How far each book is lifted, 0 to 1. */
    val pulls: List<Float> = emptyList(),
)

/**
 * One shelf. The wall bleeds to the screen's edges and joins the shelves above and below, so a
 * look's backdrop reads as one surface. [gridSpacing] is the list's spacing, given back between rows.
 */
@Composable
internal fun BookcaseShelf(
    row: List<LibraryItem>,
    look: ShelfLook,
    first: Boolean,
    last: Boolean,
    gridSpacing: Dp,
    margin: Dp,
    modifier: Modifier = Modifier,
    onLongClick: (LibraryItem) -> Unit,
    onOpen: (LibraryItem) -> Unit,
) {
    val c = Folio.colors
    val dark = look.dark ?: c.isDark
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    var touchX by remember { mutableStateOf<Float?>(null) }
    val latestOpen by rememberUpdatedState(onOpen)
    val latestLongClick by rememberUpdatedState(onLongClick)

    val books = row.mapIndexed { i, item ->
        key(item.id) {
            Book(item, i, Spines.width(item.total).dp, Spines.height(Spines.seed(item)).dp, rememberCoverColor(item.sourceId, item.thumbnailUrl))
        }
    }
    // Each book's centre along the row, in px.
    val centers = remember(row, density, look) {
        var x = 0f
        row.map { item ->
            val w = with(density) { Spines.width(item.total).dp.toPx() }
            val center = x + w / 2
            x += w + with(density) { look.gap.dp.toPx() }
            center
        }
    }
    val reach = with(density) { 26.dp.toPx() }
    val focused = touchX?.let { tx -> centers.indices.minByOrNull { abs(centers[it] - tx) } }
    LaunchedEffect(focused) { if (focused != null) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) }

    val pulls: List<State<Float>> = books.map { b ->
        key(b.item.id) {
            val target = touchX?.let { Spines.pull(abs(centers[b.index] - it), reach) } ?: 0f
            animateFloatAsState(target, spring(dampingRatio = look.liftDamping, stiffness = look.liftStiffness), label = "pull")
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            // Run edge to edge, past the list's margins, and meet the next shelf.
            .layout { m, cons ->
                val extra = margin.roundToPx() * 2
                val p = m.measure(cons.copy(minWidth = cons.maxWidth + extra, maxWidth = cons.maxWidth + extra))
                val give = if (last) 0 else gridSpacing.roundToPx()
                layout(cons.maxWidth, (p.height - give).coerceAtLeast(0)) { p.place(-extra / 2, 0) }
            },
    ) {
        val marginPx = with(density) { margin.toPx() }
        look.Backdrop(ShelfRow(books, centers.map { it + marginPx }, first, last, dark, touchX?.let { it + marginPx }, pulls.map { it.value }), Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = margin)
                    .padding(top = if (first) look.topPadding else 0.dp, bottom = if (last) look.bottomPadding else gridSpacing),
            ) {
                // The name of the book under the finger, held while it fades out.
                var label by remember { mutableStateOf<Pair<LibraryItem, Float>?>(null) }
                if (focused != null) label = row[focused] to centers[focused]
                val labelAlpha by animateFloatAsState(if (focused != null) 1f else 0f, tween(if (focused != null) 120 else 240), label = "spineLabel")
                Box(Modifier.fillMaxWidth().height(look.headroom)) {
                    label?.takeIf { labelAlpha > 0f }?.let { (item, x) ->
                        look.Label(
                            item, dark,
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(top = 2.dp)
                                .graphicsLayer {
                                    alpha = labelAlpha
                                    translationY = 4.dp.toPx() * (1f - labelAlpha)
                                }
                                .layout { m, cons ->
                                    val p = m.measure(cons.copy(minWidth = 0))
                                    val left = (x - p.width / 2f).toInt().coerceIn(0, (cons.maxWidth - p.width).coerceAtLeast(0))
                                    layout(cons.maxWidth, p.height) { p.place(left, 0) }
                                },
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(look.gap.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(1f)
                        .pointerInput(row) {
                            // One gesture handles the whole shelf: a tap opens the book under the finger,
                            // holding still opens its options, and once the finger moves it's a scrub.
                            fun nearest(x: Float) = centers.indices.minByOrNull { abs(centers[it] - x) }?.let { row[it] }
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                touchX = down.position.x
                                var moved = false
                                var held = false
                                var released = false
                                // A second finger on the same book: the easter egg, not a tap.
                                var twoFingers = false
                                while (true) {
                                    val event = if (moved || held) {
                                        awaitPointerEvent(PointerEventPass.Final)
                                    } else {
                                        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitPointerEvent(PointerEventPass.Final) }
                                    }
                                    if (event == null) {
                                        held = true
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        // Two fingers held: the easter egg's undo. One: the book's options.
                                        nearest(touchX ?: down.position.x)?.let { if (twoFingers) look.unstyle(it) else latestLongClick(it) }
                                        continue
                                    }
                                    if (event.changes.count { it.pressed } >= 2) twoFingers = true
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        released = true
                                        if (event.changes.none { it.pressed }) break else continue
                                    }
                                    // The list took the movement as a scroll: let the books settle back.
                                    if (change.isConsumed && change.positionChanged()) {
                                        moved = true
                                        break
                                    }
                                    if (abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) moved = true
                                    touchX = change.position.x
                                }
                                if (twoFingers && !moved && !held) {
                                    nearest(down.position.x)?.let { item ->
                                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                        look.restyle(item)
                                    }
                                } else if (released && !moved && !held) {
                                    nearest(down.position.x)?.let { latestOpen(it) }
                                }
                                touchX = null
                            }
                        },
                ) {
                    books.forEach { b ->
                        key(b.item.id) {
                            StandingBook(b, pulls[b.index].value, look, dark, interactive = true, onOpen = { onOpen(b.item) }, onLongClick = { onLongClick(b.item) })
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().zIndex(look.ledgeZ)) { look.Ledge(dark) }
                if (look.reflection) Reflection(books, pulls, look, dark)
            }
        }
    }
}

/** A book standing on the shelf: the new-chapter dot, the lift, and the look's spine. */
@Composable
private fun StandingBook(b: Book, pull: Float, look: ShelfLook, dark: Boolean, interactive: Boolean, onOpen: () -> Unit = {}, onLongClick: () -> Unit = {}) {
    Column(
        Modifier
            .zIndex(pull)
            .graphicsLayer {
                translationX = look.liftDx.dp.toPx() * pull
                translationY = -look.lift.dp.toPx() * pull + look.liftDy.dp.toPx() * pull
            }
            .then(
                if (interactive) {
                    // Touch is handled by the shelf; these keep each book usable with a screen reader.
                    Modifier.semantics(mergeDescendants = true) {
                        contentDescription = b.item.title
                        onClick { onOpen(); true }
                        onLongClick { onLongClick(); true }
                    }
                } else {
                    Modifier
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Chapters waiting in something you've started: a small dot above the book.
        val dot = if (interactive && b.item.unread > 0 && b.started) look.dotColor(dark) else Color.Transparent
        Box(Modifier.padding(bottom = 5.dp).size(5.dp).clip(CircleShape).background(dot))
        look.Spine(b, pull, dark)
    }
}

/** The row again, upside down and fading, as if on a glossy floor. */
@Composable
private fun Reflection(books: List<Book>, pulls: List<State<Float>>, look: ShelfLook, dark: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent)), blendMode = BlendMode.DstIn)
            },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(look.gap.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .padding(top = 3.dp)
                .graphicsLayer { scaleY = -1f },
        ) {
            books.forEach { b -> key(b.item.id) { StandingBook(b, pulls[b.index].value, look, dark, interactive = false) } }
        }
    }
}

/** Default label: the full title and where you are. */
@Composable
internal fun ShelfLabel(item: LibraryItem, title: Color, meta: Color, accent: Color, modifier: Modifier, titleStyle: TextStyle = Folio.type.footnoteStrong) {
    Column(modifier.widthIn(max = 260.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(item.title, style = titleStyle, color = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(shelfMeta(item), style = Folio.type.caption2, color = if (item.unread in 1 until item.total) accent else meta, maxLines = 1)
    }
}

internal fun shelfMeta(item: LibraryItem): String = when {
    item.total == 0 -> "Not checked yet"
    item.unread == item.total -> "Not started · ${item.total} chapters"
    item.unread == 0 -> "Up to date · ${item.total} chapters"
    else -> "${item.unread} unread · ${item.total} chapters"
}

/** Turns content to run top to bottom, like the title on an English-language spine. */
internal fun Modifier.downward() = turned(90f)

/** Turns content to run bottom to top, as on many European spines. */
internal fun Modifier.upward() = turned(-90f)

private fun Modifier.turned(degrees: Float) = layout { measurable, constraints ->
    val p = measurable.measure(Constraints(maxWidth = constraints.maxHeight, maxHeight = constraints.maxWidth))
    layout(p.height, p.width) {
        p.placeWithLayer((p.height - p.width) / 2, (p.width - p.height) / 2) { rotationZ = degrees }
    }
}

/** Picks how the bookcase looks, switching live. */
@Composable
internal fun ShelfStyleSheet(current: ShelfStyle, onPick: (ShelfStyle) -> Unit, onDismiss: () -> Unit) {
    val c = Folio.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.elevated) {
        Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
            Text("Shelf style", style = Folio.type.title3, color = c.label, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(10.dp))
            ShelfStyle.entries.forEach { style ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable(onClick = { onPick(style) })
                        .padding(horizontal = 20.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(style.label, style = Folio.type.headline, color = c.label)
                        Text(style.blurb, style = Folio.type.footnote, color = c.secondaryLabel)
                    }
                    if (style == current) {
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Rounded.Check, null, tint = c.accent)
                    }
                }
            }
        }
    }
}
