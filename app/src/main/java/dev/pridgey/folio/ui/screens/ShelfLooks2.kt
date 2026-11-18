package dev.pridgey.folio.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.graphics.toArgb
import dev.pridgey.folio.data.LibraryItem
import dev.pridgey.folio.ui.theme.Display
import dev.pridgey.folio.ui.theme.Folio
import dev.pridgey.folio.ui.theme.Heading
import dev.pridgey.folio.ui.theme.Inter
import dev.pridgey.folio.ui.theme.InterDisplay

// ------------------------------------------------------------------ 6. Glass

/**
 * Frosted panes: each spine is its cover's art seen through frosted glass, catching light along its
 * top, over a soft aura made of the shelf's own colours.
 */
internal object GlassLook : ShelfLook() {
    override val topPadding = 14.dp
    override val bottomPadding = 22.dp
    override val gap = 4

    @Composable
    override fun Backdrop(row: ShelfRow, modifier: Modifier, content: @Composable () -> Unit) {
        val base = Folio.colors.background
        Box(
            modifier.drawWithCache {
                val radius = 120.dp.toPx()
                val blobs = row.books.mapNotNull { b -> b.cover?.let { it to row.centers[b.index] } }
                onDrawBehind {
                    drawRect(base)
                    blobs.forEachIndexed { i, (color, x) ->
                        val y = size.height * (if (i % 2 == 0) 0.55f else 0.72f)
                        drawCircle(
                            Brush.radialGradient(listOf(color.copy(alpha = if (row.dark) 0.34f else 0.42f), Color.Transparent), center = Offset(x, y), radius = radius),
                            radius = radius, center = Offset(x, y),
                        )
                    }
                }
            },
        ) { content() }
    }

    @Composable
    override fun Spine(b: Book, pull: Float, dark: Boolean) {
        val shape = RoundedCornerShape(5.dp)
        val tint = b.cover ?: Color.Gray
        val ink = if (dark) Color.White else Color(0xFF17171C)
        Box(
            Modifier
                .width(b.width)
                .height(b.height)
                .graphicsLayer {
                    this.shape = shape
                    clip = true
                    shadowElevation = (3f + 8f * pull).dp.toPx()
                }
                .background(tint.copy(alpha = 0.5f)),
        ) {
            CoverArt(b.item, Modifier.fillMaxSize().then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(16.dp) else Modifier))
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // The frost, a breath of the cover's colour, and light catching the top.
                        drawRect(if (dark) Color.Black.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.46f))
                        drawRect(tint.copy(alpha = 0.12f))
                        drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.42f + 0.15f * pull), 0.3f to Color.Transparent))
                        drawRect(Brush.horizontalGradient(0f to Color.White.copy(alpha = 0.18f), 0.12f to Color.Transparent, 0.88f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.08f)))
                    }
                    .border(0.75.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.15f))), shape)
                    .padding(horizontal = 4.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                SpineTitle(b.item.title, TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, letterSpacing = 0.01.em), ink, 11.5.sp)
            }
        }
    }

    @Composable
    override fun Ledge(dark: Boolean) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .graphicsLayer {
                    shape = RoundedCornerShape(3.dp)
                    clip = true
                    shadowElevation = 4.dp.toPx()
                }
                .background(if (dark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.6f))
                .drawBehind { drawRect(Color.White.copy(alpha = 0.9f), size = Size(size.width, 1.dp.toPx())) },
        )
    }
}

// ------------------------------------------------------------------ 7. Clay

/** Soft, rounded pastel books with tonal lettering, on a pillowy shelf. They bounce when touched. */
internal object ClayLook : ShelfLook() {
    override val gap = 5
    override val lift = 12f
    override val liftDamping = 0.42f
    override val liftStiffness = 420f

    @Composable
    override fun Spine(b: Book, pull: Float, dark: Boolean) {
        val fallback = (Spines.pick(b.item.id, 6) * 60f + 20f)
        val body = if (dark) hue(b.cover, 0.32f, 0.42f, fallback) else hue(b.cover, 0.55f, 0.8f, fallback)
        val ink = if (dark) hue(b.cover, 0.5f, 0.88f, fallback) else hue(b.cover, 0.45f, 0.27f, fallback)
        val shape = RoundedCornerShape(10.dp)
        Box(
            Modifier
                .width(b.width)
                .height(b.height)
                .graphicsLayer {
                    // A little squash as it springs.
                    scaleY = 1f + 0.03f * pull
                    scaleX = 1f - 0.015f * pull
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    this.shape = shape
                    clip = true
                    shadowElevation = (5f + 7f * pull).dp.toPx()
                    ambientShadowColor = shade(body, -0.35f)
                    spotShadowColor = shade(body, -0.35f)
                }
                .drawWithCache {
                    val highlight = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = if (dark) 0.22f else 0.6f), Color.Transparent),
                        center = Offset(size.width * 0.28f, size.height * 0.06f), radius = size.width * 1.6f,
                    )
                    val low = Brush.verticalGradient(0.62f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.12f))
                    val side = Brush.horizontalGradient(0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.1f))
                    onDrawBehind {
                        drawRect(body)
                        drawRect(highlight)
                        drawRect(low)
                        drawRect(side)
                    }
                }
                .padding(horizontal = 4.dp, vertical = 12.dp),
        ) {
            Box(Modifier.align(Alignment.TopCenter).padding(bottom = 26.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
                SpineTitle(b.item.title, TextStyle(fontFamily = Heading, fontWeight = FontWeight.Bold), ink, 12.5.sp)
            }
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(15.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = if (dark) 0.16f else 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(b.item.total.toString(), style = TextStyle(fontFamily = Heading, fontWeight = FontWeight.Bold, fontSize = 8.sp, color = ink), maxLines = 1)
            }
        }
    }

    @Composable
    override fun Ledge(dark: Boolean) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(13.dp)
                .graphicsLayer {
                    shape = RoundedCornerShape(50)
                    clip = true
                    shadowElevation = 4.dp.toPx()
                }
                .background(Brush.verticalGradient(if (dark) listOf(Color(0xFF3A3632), Color(0xFF2A2724)) else listOf(Color(0xFFF1ECE4), Color(0xFFDCD4C8)))),
        )
    }
}

// ------------------------------------------------------------------ 8. Riso

/**
 * Risograph prints: each cover reprinted in two inks on grainy paper, the second ink slightly off
 * register, and the title knocked out of a solid block of ink at the foot.
 */
internal object RisoLook : ShelfLook() {
    private val paper = Color(0xFFF2EDE2)
    private val inks = listOf(
        Color(0xFFFF48B0) to Color(0xFF0078BF),
        Color(0xFFFF6C2F) to Color(0xFF3D5588),
        Color(0xFF00A95C) to Color(0xFFFF48B0),
        Color(0xFF765BA7) to Color(0xFFFF6C2F),
        Color(0xFFF15060) to Color(0xFF00838A),
        Color(0xFF0078BF) to Color(0xFFFFB511),
    )

    /** Darks print in [ink], lights leave the paper bare. */
    private fun duotone(ink: Color): ColorFilter {
        fun row(i: Float, p: Float) = floatArrayOf((p - i) * 0.299f, (p - i) * 0.587f, (p - i) * 0.114f, 0f, i * 255f)
        return ColorFilter.colorMatrix(
            ColorMatrix(
                row(ink.red, paper.red) + row(ink.green, paper.green) + row(ink.blue, paper.blue) + floatArrayOf(0f, 0f, 0f, 1f, 0f),
            ),
        )
    }

    @Composable
    override fun Spine(b: Book, pull: Float, dark: Boolean) {
        val (first, second) = inks[Spines.pick(b.item.id, inks.size)]
        Box(
            Modifier
                .width(b.width)
                .height(b.height)
                .graphicsLayer {
                    shape = RoundedCornerShape(1.dp)
                    clip = true
                    shadowElevation = (1.5f + 6f * pull).dp.toPx()
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .background(paper),
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(0.68f)) {
                    CoverArt(b.item, Modifier.fillMaxSize(), colorFilter = duotone(first))
                    // The second drum, a hair off register.
                    CoverArt(
                        b.item,
                        Modifier
                            .fillMaxSize()
                            .offset(x = 1.5.dp, y = 1.dp)
                            .graphicsLayer {
                                alpha = 0.55f
                                blendMode = BlendMode.Multiply
                            },
                        alignment = Alignment.TopCenter, colorFilter = duotone(second),
                    )
                }
                Box(Modifier.fillMaxWidth().weight(0.32f).background(second).padding(horizontal = 4.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                    SpineTitle(b.item.title.uppercase(), TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.04.em), paper, 11.sp, min = 6.5.sp)
                }
            }
            Box(
                Modifier.fillMaxSize().drawWithCache {
                    val grain = Grain.brush()
                    onDrawBehind { drawRect(grain, alpha = 0.55f, blendMode = BlendMode.Multiply) }
                },
            )
        }
    }

    @Composable
    override fun Ledge(dark: Boolean) {
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            Box(Modifier.fillMaxWidth().height(2.5.dp).background(Color(0xFF0078BF).copy(alpha = 0.85f)))
            Box(Modifier.fillMaxWidth().height(2.5.dp).offset(x = 2.dp, y = 1.5.dp).background(Color(0xFFFF48B0).copy(alpha = 0.75f)))
        }
    }

    @Composable
    override fun dotColor(dark: Boolean) = Color(0xFFFF48B0)
}

// ------------------------------------------------------------------ 9. Swiss

/**
 * International style: flat blocks from a strict palette, a big chapter numeral as the image, and a
 * small lowercase title set from the head. No shading; the grid does the work.
 */
internal object SwissLook : ShelfLook() {
    private val red = Color(0xFFE3241B)
    private val black = Color(0xFF111111)
    private val white = Color(0xFFF2EFE8)
    private val yellow = Color(0xFFFFC800)
    private val blue = Color(0xFF1F4FD1)
    override val gap = 2

    private fun block(b: Book): Color {
        val hsl = FloatArray(3)
        val cover = b.cover ?: return listOf(red, black, blue, yellow, white)[Spines.pick(b.item.id, 5)]
        ColorUtils.colorToHSL(cover.toArgb(), hsl)
        return when {
            hsl[1] < 0.18f -> if (hsl[2] > 0.6f) white else black
            hsl[0] < 25f || hsl[0] >= 330f -> red
            hsl[0] < 70f -> yellow
            hsl[0] in 170f..265f -> blue
            else -> if (Spines.pick(b.item.id, 2) == 0) black else red
        }
    }

    @Composable
    override fun Spine(b: Book, pull: Float, dark: Boolean) {
        val fill = block(b)
        val ink = if (fill == yellow || fill == white) black else white
        Box(
            Modifier
                .width(b.width)
                .height(b.height)
                .graphicsLayer { shadowElevation = 5f * pull * density }
                .background(fill)
                .drawBehind { drawRect(Color.Black.copy(alpha = 0.12f), topLeft = Offset(size.width - 1.dp.toPx(), 0f), size = Size(1.dp.toPx(), size.height)) }
                .padding(horizontal = 3.dp, vertical = 8.dp),
        ) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(b.height * 0.5f), contentAlignment = Alignment.TopCenter) {
                SpineTitle(b.item.title.lowercase(), TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em), ink, 10.sp, min = 7.sp)
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(b.height * 0.42f), contentAlignment = Alignment.BottomCenter) {
                BasicText(
                    b.item.total.toString(),
                    style = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.ExtraBold, color = ink, letterSpacing = (-0.04).em, lineHeight = 0.9.em),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 34.sp, stepSize = 1.sp),
                    modifier = Modifier.downward(),
                )
            }
        }
    }

    @Composable
    override fun Ledge(dark: Boolean) = Box(Modifier.fillMaxWidth().height(6.dp).background(Folio.colors.label))

    @Composable
    override fun Label(item: LibraryItem, dark: Boolean, modifier: Modifier) {
        val (title, meta) = labelColors(dark)
        ShelfLabel(item, title, meta, red, modifier, TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = (-0.02).em))
    }

    @Composable
    override fun dotColor(dark: Boolean) = red
}

// ------------------------------------------------------------------ 10. Dimensional

/**
 * Solid books seen a little from above and to the right: each has a page block on top and its cover
 * board down the side, standing on a slab. Touching a book slides it out towards you.
 */
internal object DimensionalLook : ShelfLook() {
    private const val DX = 10f
    private const val DY = -7f
    override val reserve = DX * 1.6f
    override val gap = 2
    override val lift = 8f
    override val liftDx = -DX * 0.7f
    override val liftDy = -DY * 0.7f
    override val ledgeZ = -1f
    override val headroom = 50.dp

    @Composable
    override fun Spine(b: Book, pull: Float, dark: Boolean) {
        val cover = tone(b.cover, Color(0xFF5B6F8C), 0.35f..0.7f, 0.3f..0.58f)
        val ink = inkFor(cover)
        Box(
            Modifier
                .width(b.width)
                .height(b.height)
                .drawBehind {
                    val dx = DX.dp.toPx()
                    val dy = DY.dp.toPx()
                    val w = size.width
                    val h = size.height
                    // The cover board, running back from the spine's right edge.
                    val side = Path().apply { moveTo(w, 0f); lineTo(w + dx, dy); lineTo(w + dx, h + dy); lineTo(w, h); close() }
                    drawPath(side, shade(cover, -0.16f))
                    drawPath(side, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.12f))))
                    // The top: board edges around the block of pages.
                    val top = Path().apply { moveTo(0f, 0f); lineTo(dx, dy); lineTo(w + dx, dy); lineTo(w, 0f); close() }
                    drawPath(top, shade(cover, 0.06f))
                    val inset = 1.6.dp.toPx()
                    val pages = Path().apply {
                        moveTo(inset, -inset * 0.2f); lineTo(dx + inset * 0.2f, dy + inset * 0.6f)
                        lineTo(w + dx - inset, dy + inset * 0.6f); lineTo(w - inset * 1.2f, -inset * 0.2f); close()
                    }
                    drawPath(pages, Color(0xFFF1EADB))
                    for (k in 1..3) {
                        val t = k / 4f
                        drawLine(Color(0xFFCFC5B1), Offset(inset + dx * t, dy * t), Offset(w - inset + dx * t, dy * t), strokeWidth = 0.6f)
                    }
                    // A soft shadow where it stands.
                    drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent)), topLeft = Offset(w, h - 2.dp.toPx()), size = Size(dx * 1.6f, 2.dp.toPx()))
                }
                .graphicsLayer { shadowElevation = 6f * pull * density }
                .background(cover)
                .drawWithContent {
                    drawContent()
                    roundSpine(1.3f)
                    drawRect(Color.White.copy(alpha = 0.18f), size = Size(size.width, 1.dp.toPx()))
                },
        ) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                CoverArt(b.item, Modifier.fillMaxWidth().height(b.width * 0.95f), Alignment.TopCenter)
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp).padding(top = 9.dp, bottom = 9.dp), contentAlignment = Alignment.Center) {
                    SpineTitle(b.item.title, TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.01.em), ink, 13.sp)
                }
            }
        }
    }

    @Composable
    override fun Ledge(dark: Boolean) {
        val top = if (dark) Color(0xFF3B3834) else Color(0xFFDCD6CC)
        val front = if (dark) Color(0xFF2A2825) else Color(0xFFC3BCB0)
        Box(
            Modifier
                .fillMaxWidth()
                .height(9.dp)
                .drawBehind {
                    val dx = DX.dp.toPx() * 1.6f
                    val dy = DY.dp.toPx() * 1.6f
                    // The slab's top, running back under the books, then its front edge.
                    val surface = Path().apply { moveTo(0f, 0f); lineTo(dx, dy); lineTo(size.width + dx, dy); lineTo(size.width, 0f); close() }
                    drawPath(surface, top)
                    drawPath(surface, Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.1f), Color.Transparent), startY = dy, endY = 0f))
                    drawRect(Brush.verticalGradient(listOf(front, shade(front, -0.06f))))
                    drawRect(Color.White.copy(alpha = 0.3f), size = Size(size.width, 1.dp.toPx()))
                    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent), startY = size.height, endY = size.height + 10.dp.toPx()), topLeft = Offset(0f, size.height), size = Size(size.width, 10.dp.toPx()))
                },
        )
    }
}
