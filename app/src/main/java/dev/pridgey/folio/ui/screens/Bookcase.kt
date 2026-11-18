package dev.pridgey.folio.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.ColorUtils
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dev.pridgey.folio.data.LibraryItem
import dev.pridgey.folio.image.CoverModel
import dev.pridgey.folio.ui.components.coverCacheKeys
import dev.pridgey.folio.ui.components.pressable
import dev.pridgey.folio.ui.theme.Display
import dev.pridgey.folio.ui.theme.Folio
import dev.pridgey.folio.ui.theme.rememberCoverColor
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

// The library as manga standing on a bookcase. Sources only give covers, so each spine is printed from
// its cover: a little panel of the art at the head, the title down the middle in the cover's colour,
// and how many chapters you've read at the foot, where a volume number would be.

internal object Spines {
    const val GAP = 2
    private const val MIN_WIDTH = 30f
    private const val MAX_WIDTH = 36f
    const val TALLEST = 188

    /** The bookcase's sides plus the space inside them, on each side. */
    const val FRAME = 12

    /** Longer series are a touch thicker: grows quickly at first and stops at about 300 chapters. */
    fun width(chapters: Int): Float {
        val t = min(1.0, ln(1.0 + chapters.coerceAtLeast(0)) / ln(301.0)).toFloat()
        return MIN_WIDTH + (MAX_WIDTH - MIN_WIDTH) * t
    }

    /** Books aren't all the same height. Steady per title, so the shelf doesn't reshuffle. */
    fun height(id: Long): Int = TALLEST - ((id * 2654435761L ushr 7) % 4).toInt() * 6

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

/** The bookcase's wood and the wall behind the books, in light and dark. */
private class Wood(dark: Boolean) {
    val light = if (dark) Color(0xFF5E4734) else Color(0xFFD2A878)
    val mid = if (dark) Color(0xFF473526) else Color(0xFFB4865A)
    val deep = if (dark) Color(0xFF2B2017) else Color(0xFF8C6340)
    val wallTop = if (dark) Color(0xFF120E0B) else Color(0xFFCDBEA9)
    val wallBottom = if (dark) Color(0xFF1E1712) else Color(0xFFE3D7C6)
}

/**
 * One shelf of the bookcase. Drag a finger along it and the books near the finger come off the shelf
 * a little, with the one under it named above. [first] adds the bookcase's top; shelves after the
 * first sit flush under the one above (the list's spacing is taken back with [joinBelow]).
 */
@Composable
internal fun BookcaseShelf(
    row: List<LibraryItem>,
    first: Boolean,
    joinBelow: Boolean,
    gridSpacing: Int,
    modifier: Modifier = Modifier,
    onLongClick: (LibraryItem) -> Unit,
    onOpen: (LibraryItem) -> Unit,
) {
    val wood = Wood(Folio.colors.isDark)
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    var touchX by remember { mutableStateOf<Float?>(null) }

    // Each book's centre along the row, in px, for working out how close the finger is.
    val centers = remember(row, density) {
        var x = 0f
        row.map { item ->
            val w = with(density) { Spines.width(item.total).dp.toPx() }
            val c = x + w / 2
            x += w + with(density) { Spines.GAP.dp.toPx() }
            c
        }
    }
    val reach = with(density) { 30.dp.toPx() }
    val focused = touchX?.let { tx -> centers.indices.minByOrNull { abs(centers[it] - tx) } }
    LaunchedEffect(focused) { if (focused != null) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) }

    Box(
        modifier
            .fillMaxWidth()
            // Sit flush with the shelf below by giving back the list's spacing.
            .layout { m, c ->
                val p = m.measure(c)
                val give = if (joinBelow) gridSpacing.dp.roundToPx() else 0
                layout(p.width, (p.height - give).coerceAtLeast(0)) { p.place(0, 0) }
            },
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (first) Board(wood, top = true)
            Box(
                Modifier
                    .fillMaxWidth()
                    .drawWithCache {
                        val wall = Brush.verticalGradient(listOf(wood.wallTop, wood.wallBottom))
                        val underShelf = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.42f), Color.Transparent), endY = 22.dp.toPx())
                        val contact = Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)),
                            startY = size.height - 14.dp.toPx(), endY = size.height,
                        )
                        val side = 7.dp.toPx()
                        val sides = Brush.horizontalGradient(listOf(wood.mid, wood.light, wood.deep), endX = side)
                        onDrawBehind {
                            drawRect(wall)
                            drawRect(underShelf)
                            drawRect(contact)
                            // The bookcase's sides, shading into the back.
                            drawRect(sides, size = Size(side, size.height))
                            drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent), startX = side, endX = side + 10.dp.toPx()), topLeft = Offset(side, 0f), size = Size(10.dp.toPx(), size.height))
                            drawRect(Brush.horizontalGradient(listOf(wood.deep, wood.light, wood.mid), startX = size.width - side, endX = size.width), topLeft = Offset(size.width - side, 0f), size = Size(side, size.height))
                            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f)), startX = size.width - side - 10.dp.toPx(), endX = size.width - side), topLeft = Offset(size.width - side - 10.dp.toPx(), 0f), size = Size(10.dp.toPx(), size.height))
                        }
                    }
                    .padding(start = Spines.FRAME.dp, end = Spines.FRAME.dp, top = 22.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spines.GAP.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.pointerInput(row) {
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
            }
            Board(wood, top = false)
        }

        // The book under the finger, named in full above the shelf.
        val shown = focused?.let { row.getOrNull(it) }
        var label by remember { mutableStateOf<Pair<LibraryItem, Float>?>(null) }
        if (focused != null && shown != null) label = shown to (centers[focused] + with(density) { Spines.FRAME.dp.toPx() })
        val labelAlpha by animateFloatAsState(if (shown != null) 1f else 0f, tween(if (shown != null) 120 else 220), label = "spineLabel")
        label?.takeIf { labelAlpha > 0f }?.let { (item, x) ->
            SpineLabel(
                item,
                Modifier
                    .zIndex(10f)
                    .offset(y = (-26).dp)
                    .graphicsLayer { alpha = labelAlpha }
                    .layout { m, c ->
                        val p = m.measure(c.copy(minWidth = 0))
                        val left = (x - p.width / 2f).toInt().coerceIn(0, (c.maxWidth - p.width).coerceAtLeast(0))
                        layout(c.maxWidth, p.height) { p.place(left, 0) }
                    },
            )
        }
    }
}

/** A plank: a lit top edge and a grained front. */
@Composable
private fun Board(wood: Wood, top: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (top) 10.dp else 16.dp)
            .drawWithCache {
                val lip = if (top) 0f else 4.dp.toPx()
                val surface = Brush.verticalGradient(listOf(wood.deep, wood.mid), endY = lip)
                val front = Brush.verticalGradient(listOf(wood.light, wood.mid, wood.deep), startY = lip, endY = size.height)
                // A few long, gently wandering grain lines, the same every time.
                val grain = (0 until 4).map { n ->
                    Path().apply {
                        val y0 = lip + (size.height - lip) * (0.22f + 0.2f * n)
                        moveTo(0f, y0)
                        var x = 0f
                        var k = n
                        while (x < size.width) {
                            val step = 60.dp.toPx() + (k * 37 % 50).dp.toPx()
                            val dy = ((k * 53 % 7) - 3) * 0.35f * density
                            quadraticTo(x + step / 2, y0 + dy, x + step, y0)
                            x += step
                            k++
                        }
                    }
                }
                val hairline = 0.6.dp.toPx()
                onDrawBehind {
                    if (lip > 0f) drawRect(surface, size = Size(size.width, lip))
                    drawRect(front, topLeft = Offset(0f, lip), size = Size(size.width, size.height - lip))
                    grain.forEach { drawPath(it, Color.Black.copy(alpha = 0.08f), style = Stroke(hairline)) }
                    // Light catching the front edge, and the shadow under it.
                    drawRect(Color.White.copy(alpha = 0.16f), topLeft = Offset(0f, lip), size = Size(size.width, hairline))
                    drawRect(Color.Black.copy(alpha = 0.3f), topLeft = Offset(0f, size.height - hairline), size = Size(size.width, hairline))
                }
            },
    )
}

@Composable
private fun Spine(item: LibraryItem, pullTarget: Float, onLongClick: () -> Unit, onClick: () -> Unit) {
    val c = Folio.colors
    val pull by animateFloatAsState(pullTarget, spring(dampingRatio = 0.72f, stiffness = 520f), label = "pull")
    val base = spineColor(rememberCoverColor(item.sourceId, item.thumbnailUrl), c.isDark)
    val ink = if (base.luminance() < 0.42f) Color(0xFFF8F4EC) else Color(0xFF17130F)
    val width = Spines.width(item.total).dp
    val shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 1.dp, bottomEnd = 1.dp)
    val read = (item.total - item.unread).coerceAtLeast(0)

    Box(
        Modifier
            .zIndex(pull)
            .graphicsLayer {
                // Coming off the shelf: up and a touch towards you.
                translationY = -16.dp.toPx() * pull
                val s = 1f + 0.035f * pull
                scaleX = s
                scaleY = s
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            }
            .pressable(onClick = onClick, onLongClick = onLongClick, pressedScale = 1f),
    ) {
        Column(
            Modifier
                .width(width)
                .height(Spines.height(item.id).dp)
                .graphicsLayer {
                    this.shape = shape
                    clip = true
                    shadowElevation = (2.dp + 10.dp * pull).toPx()
                }
                .drawWithCache {
                    val body = Brush.verticalGradient(listOf(lift(base, 0.06f), base, lift(base, -0.08f)))
                    val grain = ShaderBrush(ImageShader(Grain.bitmap, TileMode.Repeated, TileMode.Repeated))
                    val round = Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.42f),
                        0.07f to Color.Black.copy(alpha = 0.12f),
                        0.2f to Color.White.copy(alpha = 0.16f),
                        0.3f to Color.White.copy(alpha = 0.05f),
                        0.62f to Color.Transparent,
                        0.9f to Color.Black.copy(alpha = 0.16f),
                        1f to Color.Black.copy(alpha = 0.45f),
                    )
                    onDrawWithContent {
                        drawRect(body)
                        drawRect(grain, alpha = 0.5f)
                        drawContent()
                        drawRect(round)
                        drawRect(Color.White.copy(alpha = 0.22f), size = Size(size.width, 1.dp.toPx()))
                    }
                }
                .padding(horizontal = 3.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeadPanel(item, ink, Modifier.fillMaxWidth().height(((width.value - 6f) * 1.3f).dp))
            Rule(ink, Modifier.padding(top = 5.dp))
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                BasicText(
                    item.title,
                    style = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, color = ink, letterSpacing = 0.015.em, lineHeight = 1.0.em),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 15.sp, stepSize = 0.5.sp),
                    modifier = Modifier.downward(),
                )
            }
            Rule(ink, Modifier.padding(bottom = 5.dp))
            if (read > 0) {
                Box(
                    Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(2.dp)).background(ink.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        if (read > 999) "999+" else read.toString(),
                        style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, color = base),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
                    )
                }
            } else {
                Spacer(Modifier.height(18.dp))
            }
        }
        if (item.unread > 0) {
            // A tab of the accent colour poking out of the top: there are new chapters.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-5).dp, y = (-8).dp)
                    .size(width = 7.dp, height = 14.dp)
                    .graphicsLayer {
                        this.shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                        clip = true
                        shadowElevation = 2.dp.toPx()
                    }
                    .background(Brush.verticalGradient(listOf(lift(c.accent, 0.08f), c.accent))),
            )
        }
    }
}

/** A small panel of the cover's art at the head of the spine. */
@Composable
private fun HeadPanel(item: LibraryItem, ink: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(2.dp)
    Box(modifier.clip(shape).border(0.75.dp, ink.copy(alpha = 0.35f), shape).background(ink.copy(alpha = 0.12f))) {
        item.thumbnailUrl?.let { url ->
            val context = LocalContext.current
            val request = remember(item.sourceId, url) {
                ImageRequest.Builder(context).data(CoverModel(item.sourceId, url)).coverCacheKeys(item.sourceId, url).build()
            }
            AsyncImage(request, null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun Rule(ink: Color, modifier: Modifier) =
    Box(modifier.fillMaxWidth().padding(horizontal = 2.dp).height(1.dp).background(ink.copy(alpha = 0.4f)))

/** The full title and where you are, over the book under your finger. */
@Composable
private fun SpineLabel(item: LibraryItem, modifier: Modifier) {
    val c = Folio.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .widthIn(max = 240.dp)
            .graphicsLayer {
                this.shape = shape
                clip = true
                shadowElevation = 8.dp.toPx()
            }
            .background(c.elevated)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(item.title, style = Folio.type.footnoteStrong, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            when {
                item.total == 0 -> "Not checked yet"
                item.unread == 0 -> "Up to date · ${item.total} chapters"
                else -> "${item.unread} new · ${item.total} chapters"
            },
            style = Folio.type.caption, color = if (item.unread > 0) c.accent else c.secondaryLabel, maxLines = 1,
        )
    }
}

/** A cover's colour made into printed card: kept rich, never washed out or muddy. */
private fun spineColor(cover: Color?, dark: Boolean): Color {
    if (cover == null) return if (dark) Color(0xFF3A3632) else Color(0xFF8E8780)
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(cover.toArgb(), hsl)
    hsl[1] = if (hsl[1] < 0.08f) hsl[1] else hsl[1].coerceIn(0.32f, 0.78f)
    hsl[2] = hsl[2].coerceIn(0.2f, 0.66f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/** Lighter (positive) or darker (negative) by [amount] of lightness. */
private fun lift(color: Color, amount: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[2] = (hsl[2] + amount).coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl)).copy(alpha = color.alpha)
}

/** A fine, faint speckle, so the spines read as printed card rather than flat colour. */
private object Grain {
    val bitmap: ImageBitmap by lazy {
        val size = 72
        val random = java.util.Random(11)
        val pixels = IntArray(size * size) {
            val v = if (random.nextBoolean()) 255 else 0
            android.graphics.Color.argb(random.nextInt(22), v, v, v)
        }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

/** Turns content to run top to bottom, like the title on an English-language spine. */
private fun Modifier.downward() = layout { measurable, constraints ->
    val p = measurable.measure(Constraints(maxWidth = constraints.maxHeight, maxHeight = constraints.maxWidth))
    layout(p.height, p.width) {
        p.placeWithLayer((p.height - p.width) / 2, (p.width - p.height) / 2) { rotationZ = 90f }
    }
}
