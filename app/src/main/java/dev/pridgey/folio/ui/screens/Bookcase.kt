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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dev.pridgey.folio.image.CoverModel
import dev.pridgey.folio.ui.components.coverCacheKeys
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.ColorUtils
import dev.pridgey.folio.data.LibraryItem
import dev.pridgey.folio.ui.components.pressable
import dev.pridgey.folio.ui.theme.Display
import dev.pridgey.folio.ui.theme.Folio
import dev.pridgey.folio.ui.theme.Heading
import dev.pridgey.folio.ui.theme.Inter
import dev.pridgey.folio.ui.theme.rememberCoverColor
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

// The library as books standing on a shelf. No furniture: spines in their covers' colours on one firm
// line, like a well-kept shelf photographed straight on. Each title gets one of a few house styles for
// its lettering, so the row reads as books from different publishers rather than one template.

internal object Spines {
    const val GAP = 3
    private const val MIN_WIDTH = 30f
    private const val MAX_WIDTH = 36f
    const val TALLEST = 184

    /** Room above the books for the name of the one under your finger. */
    const val HEADROOM = 46

    /** Longer series are a touch thicker: grows quickly at first and stops at about 300 chapters. */
    fun width(chapters: Int): Float {
        val t = min(1.0, ln(1.0 + chapters.coerceAtLeast(0)) / ln(301.0)).toFloat()
        return MIN_WIDTH + (MAX_WIDTH - MIN_WIDTH) * t
    }

    private fun mix(id: Long, salt: Long): Long = ((id + salt) * 2654435761L) ushr 9

    /** Books aren't all the same height (within about a tenth). Steady per title. */
    fun height(id: Long): Int = TALLEST - (mix(id, 0) % 5).toInt() * 4

    /** Which house style a title's lettering uses. Steady per title. */
    fun style(id: Long): Int = (mix(id, 7) % 4).toInt()

    /** Fills shelves left to right, starting a new one when the next book wouldn't fit. */
    fun rows(items: List<LibraryItem>, available: Float): List<List<LibraryItem>> {
        val rows = ArrayList<List<LibraryItem>>()
        var row = ArrayList<LibraryItem>()
        var used = 0f
        for (item in items) {
            val w = width(item.total) + if (row.isEmpty()) 0 else GAP
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

/**
 * One shelf. Drag a finger along it and the books near the finger rise a little, with the one under
 * it named above the row.
 */
@Composable
internal fun BookcaseShelf(
    row: List<LibraryItem>,
    modifier: Modifier = Modifier,
    onLongClick: (LibraryItem) -> Unit,
    onOpen: (LibraryItem) -> Unit,
) {
    val c = Folio.colors
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    var touchX by remember { mutableStateOf<Float?>(null) }

    // Each book's centre along the row, in px, for working out how close the finger is.
    val centers = remember(row, density) {
        var x = 0f
        row.map { item ->
            val w = with(density) { Spines.width(item.total).dp.toPx() }
            val center = x + w / 2
            x += w + with(density) { Spines.GAP.dp.toPx() }
            center
        }
    }
    val reach = with(density) { 26.dp.toPx() }
    val focused = touchX?.let { tx -> centers.indices.minByOrNull { abs(centers[it] - tx) } }
    LaunchedEffect(focused) { if (focused != null) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) }

    Column(modifier.fillMaxWidth()) {
        // The name of the book under the finger, held while it fades out.
        var label by remember { mutableStateOf<Pair<LibraryItem, Float>?>(null) }
        if (focused != null) label = row[focused] to centers[focused]
        val labelAlpha by animateFloatAsState(if (focused != null) 1f else 0f, tween(if (focused != null) 120 else 240), label = "spineLabel")
        Box(Modifier.fillMaxWidth().height(Spines.HEADROOM.dp)) {
            label?.takeIf { labelAlpha > 0f }?.let { (item, x) ->
                SpineLabel(
                    item,
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
            horizontalArrangement = Arrangement.spacedBy(Spines.GAP.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(row) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        touchX = down.position.x
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            // The list took the movement as a scroll: let the books settle back.
                            if (change.isConsumed && change.positionChanged()) break
                            touchX = change.position.x
                        }
                        touchX = null
                    }
                },
        ) {
            row.forEachIndexed { i, item ->
                val target = touchX?.let { Spines.pull(abs(centers[i] - it), reach) } ?: 0f
                Spine(item, target, onLongClick = { onLongClick(item) }) { onOpen(item) }
            }
        }
        // The shelf: one firm line.
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(1.5.dp)).background(c.label.copy(alpha = 0.88f)))
    }
}

@Composable
private fun Spine(item: LibraryItem, pullTarget: Float, onLongClick: () -> Unit, onClick: () -> Unit) {
    val c = Folio.colors
    val pull by animateFloatAsState(pullTarget, spring(dampingRatio = 0.9f, stiffness = 320f), label = "pull")
    val paper = spineColor(rememberCoverColor(item.sourceId, item.thumbnailUrl), c.isDark)
    val ink = inkFor(paper)
    val width = Spines.width(item.total).dp
    val height = Spines.height(item.id).dp
    val read = (item.total - item.unread).coerceAtLeast(0)
    val house = Spines.style(item.id)

    Column(
        Modifier
            .zIndex(pull)
            .graphicsLayer { translationY = -9.dp.toPx() * pull }
            .pressable(onClick = onClick, onLongClick = onLongClick, pressedScale = 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // New chapters: a small dot above the book, out of the way of its lettering.
        Box(Modifier.padding(bottom = 5.dp).size(5.dp).clip(CircleShape).background(if (item.unread > 0) c.accent else Color.Transparent))
        Box(
            Modifier
                .width(width)
                .height(height)
                .graphicsLayer {
                    shape = RoundedCornerShape(topStart = 1.5.dp, topEnd = 1.5.dp)
                    clip = true
                    shadowElevation = 6.dp.toPx() * pull
                }
                .drawWithCache {
                    // Barely-there roundness: light along the left edge, a little shade into the right.
                    val edge = Brush.horizontalGradient(
                        0f to Color.White.copy(alpha = 0.10f),
                        0.06f to Color.White.copy(alpha = 0.04f),
                        0.5f to Color.Transparent,
                        0.9f to Color.Black.copy(alpha = 0.05f),
                        1f to Color.Black.copy(alpha = 0.16f),
                    )
                    onDrawWithContent {
                        drawRect(paper)
                        drawContent()
                        drawRect(edge)
                        drawRect(Color.White.copy(alpha = 0.12f), size = Size(size.width, 1.dp.toPx()))
                    }
                }
,
        ) {
            Column(Modifier.matchParentSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                // A strip of the cover's art across the head, printed edge to edge as manga spines do.
                CoverStrip(item, Modifier.fillMaxWidth().height(width * 0.95f))
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp).padding(top = 9.dp, bottom = 8.dp)) {
                    SpineLettering(item.title, house, ink, Modifier.align(if (house == 1) Alignment.TopCenter else Alignment.Center).padding(bottom = 18.dp))
                    if (read > 0) {
                        BasicText(
                            if (read > 999) "999+" else read.toString(),
                            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, color = ink.copy(alpha = 0.78f), letterSpacing = 0.02.em),
                            maxLines = 1,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverStrip(item: LibraryItem, modifier: Modifier) {
    val url = item.thumbnailUrl ?: return
    val context = LocalContext.current
    val request = remember(item.sourceId, url) {
        ImageRequest.Builder(context).data(CoverModel(item.sourceId, url)).coverCacheKeys(item.sourceId, url).build()
    }
    AsyncImage(request, null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = modifier)
}

/** The title down the spine, in one of four house styles. */
@Composable
private fun SpineLettering(title: String, house: Int, ink: Color, modifier: Modifier) {
    val (style, text, max) = when (house) {
        // Condensed and bold, the poster voice.
        0 -> Triple(TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.01.em), title, 14.sp)
        // Small spaced capitals, set from the head.
        1 -> Triple(TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, letterSpacing = 0.14.em), title.uppercase(), 9.5.sp)
        // A heavier, rounder heading face.
        2 -> Triple(TextStyle(fontFamily = Heading, fontWeight = FontWeight.Bold, letterSpacing = 0.em), title, 12.5.sp)
        // Quiet: plain and light.
        else -> Triple(TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, letterSpacing = 0.01.em), title, 11.sp)
    }
    BasicText(
        text,
        style = style.copy(color = ink, lineHeight = 1.0.em),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = smaller(8.sp, max), maxFontSize = max, stepSize = 0.5.sp),
        modifier = modifier.downward(),
    )
}

private fun smaller(a: TextUnit, b: TextUnit): TextUnit = if (a.value <= b.value) a else b

/** The full title and where you are, over the book under your finger. */
@Composable
private fun SpineLabel(item: LibraryItem, modifier: Modifier) {
    val c = Folio.colors
    Column(modifier.widthIn(max = 260.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(item.title, style = Folio.type.footnoteStrong, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            when {
                item.total == 0 -> "Not checked yet"
                item.unread == 0 -> "Up to date · ${item.total} chapters"
                else -> "${item.unread} new · ${item.total} chapters"
            },
            style = Folio.type.caption2, color = if (item.unread > 0) c.accent else c.secondaryLabel, maxLines = 1,
        )
    }
}

/**
 * A cover's colour as a printed spine: rich enough to have presence, never neon, washed out or muddy.
 * Near-greys stay grey.
 */
private fun spineColor(cover: Color?, dark: Boolean): Color {
    if (cover == null) return if (dark) Color(0xFF3B3833) else Color(0xFFCFC8BE)
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(cover.toArgb(), hsl)
    if (hsl[1] > 0.1f) hsl[1] = hsl[1].coerceIn(0.28f, 0.62f)
    hsl[2] = hsl[2].coerceIn(if (dark) 0.24f else 0.3f, if (dark) 0.62f else 0.72f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private val LightInk = Color(0xFFFAF7F1)
private val DarkInk = Color(0xFF15120E)

/** Whichever ink reads better on [paper], by contrast ratio. */
private fun inkFor(paper: Color): Color {
    val l = paper.luminance()
    val onLight = (l + 0.05f) / (DarkInk.luminance() + 0.05f)
    val onDark = (LightInk.luminance() + 0.05f) / (l + 0.05f)
    return if (onDark >= onLight) LightInk else DarkInk
}

/** Turns content to run top to bottom, like the title on an English-language spine. */
private fun Modifier.downward() = layout { measurable, constraints ->
    val p = measurable.measure(Constraints(maxWidth = constraints.maxHeight, maxHeight = constraints.maxWidth))
    layout(p.height, p.width) {
        p.placeWithLayer((p.height - p.width) / 2, (p.width - p.height) / 2) { rotationZ = 90f }
    }
}
