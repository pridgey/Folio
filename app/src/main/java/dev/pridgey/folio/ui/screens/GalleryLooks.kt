package dev.pridgey.folio.ui.screens

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.pridgey.folio.data.LibraryItem
import dev.pridgey.folio.ui.theme.Folio
import dev.pridgey.folio.ui.theme.Inter
import kotlin.math.PI
import kotlin.math.sin

// The Gallery family: spines that are slices of their covers' art, hung in a room whose light, wall and
// shelf change from look to look. GalleryLook is the original; the others vary it.

/** Cover art on a dark wall under warm spotlights. Books rest in shade and rise into the light. */
internal open class GalleryLook : ShelfLook() {
    override val dark: Boolean? = true
    override val topPadding = 18.dp
    override val bottomPadding = 26.dp
    override val lift = 11f

    /** How dim books are at rest, and the colour of the light that falls on a lifted one. */
    open val restShade = 0.3f
    open val liftLight = Color(0xFFFFE9C4)
    open val elevation = 3f

    /** Anything the look animates on its own (light following the finger, flicker). */
    @Composable
    open fun animated(row: ShelfRow): FloatArray = NONE

    open fun DrawScope.wall(row: ShelfRow, anim: FloatArray) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF151311), Color(0xFF0D0C0B))))
        spotlights(row, Color(0xFFFFE1AE), 0.11f)
    }

    /** Drawn over the books: light, glass, shadow that falls across everything. */
    open fun DrawScope.overWall(row: ShelfRow, anim: FloatArray) = Unit

    /** Drawn behind a book, outside its edges (its shadow on the wall). */
    open fun DrawScope.behindSpine(b: Book, pull: Float, dark: Boolean) = Unit

    /** Drawn over a book's art, under its title. */
    open fun DrawScope.overArt(b: Book, pull: Float) = Unit

    @Composable
    open fun artFilter(b: Book, pull: Float): ColorFilter? = null

    open fun spineScale(pull: Float) = 1f

    /** Whether the first shelf fades in from the page; off for walls far from the page's colour. */
    open val fadeIn = true

    @Composable
    override fun Backdrop(row: ShelfRow, modifier: Modifier, content: @Composable () -> Unit) {
        val page = Folio.colors.background
        val anim = animated(row)
        Box(
            modifier.drawWithContent {
                clipRect { wall(row, anim) }
                // The first shelf rises out of the page instead of starting at a hard edge.
                if (row.first && fadeIn) {
                    val fade = 28.dp.toPx()
                    drawRect(Brush.verticalGradient(listOf(page, Color.Transparent), endY = fade), size = Size(size.width, fade))
                }
                drawContent()
                clipRect { overWall(row, anim) }
            },
        ) { content() }
    }

    @Composable
    override fun Spine(b: Book, pull: Float, dark: Boolean) {
        val filter = artFilter(b, pull)
        Box(
            Modifier
                .width(b.width)
                .height(b.height)
                .drawBehind { behindSpine(b, pull, dark) }
                .graphicsLayer {
                    val s = spineScale(pull)
                    scaleX = s
                    scaleY = s
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    shape = RoundedCornerShape(1.dp)
                    clip = true
                    shadowElevation = (elevation + 12f * pull).dp.toPx()
                }
                .background(tone(b.cover, Color(0xFF3A342E), 0.2f..0.6f, 0.2f..0.4f)),
        ) {
            CoverArt(b.item, Modifier.fillMaxSize(), colorFilter = filter)
            Box(
                Modifier.fillMaxSize().drawBehind {
                    drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.04f), 0.5f to Color.Black.copy(alpha = 0.2f), 1f to Color.Black.copy(alpha = 0.86f)))
                    overArt(b, pull)
                    // Resting in shade until it's lifted into the light.
                    drawRect(Color.Black.copy(alpha = restShade * (1f - pull)))
                    drawRect(liftLight.copy(alpha = 0.07f * pull))
                    roundSpine(1.4f)
                    drawRect(Color.White.copy(alpha = 0.25f), size = Size(size.width, 1.dp.toPx()))
                },
            )
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(b.height * 0.5f).padding(bottom = 12.dp), contentAlignment = Alignment.BottomCenter) {
                SpineTitle(b.item.title.uppercase(), TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, letterSpacing = 0.16.em), Color.White, 8.5.sp, min = 6.5.sp)
            }
        }
    }

    @Composable
    override fun Ledge(dark: Boolean) = MetalLedge(Color(0xFFD6B57A), Color(0xFF7C6034))

    @Composable
    override fun dotColor(dark: Boolean) = Color(0xFFFFC978)

    companion object {
        val NONE = FloatArray(0)
    }
}

// ------------------------------------------------------------------ Shared light and fittings

/** Cones of light falling from just above the shelf, every so often along the wall. */
private fun DrawScope.spotlights(row: ShelfRow, color: Color, strength: Float, spacing: Float = 132f, radius: Float = 150f) {
    val step = spacing.dp.toPx()
    val r = radius.dp.toPx()
    val y = if (row.first) 0f else -r * 0.25f
    var x = step / 2
    while (x < size.width + step) {
        drawOval(
            Brush.radialGradient(listOf(color.copy(alpha = strength), Color.Transparent), center = Offset(x, y + r * 0.7f), radius = r),
            topLeft = Offset(x - r * 0.8f, y - r * 0.1f), size = Size(r * 1.6f, r * 1.8f),
        )
        x += step
    }
}

/** A filled shape with blurred edges, for shadows and glows. */
private fun DrawScope.soft(color: Color, blur: Float, draw: (android.graphics.Canvas, android.graphics.Paint) -> Unit) {
    drawIntoCanvas { canvas ->
        val paint = Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            maskFilter = BlurMaskFilter(blur.coerceAtLeast(0.1f), BlurMaskFilter.Blur.NORMAL)
        }
        draw(canvas.nativeCanvas, paint)
    }
}

/** A metal ledge: a lit bar and the shadow it casts. */
@Composable
private fun MetalLedge(top: Color, bottom: Color, height: Float = 3f, shadow: Float = 0.55f) {
    Column {
        Box(
            Modifier.fillMaxWidth().height(height.dp)
                .background(Brush.verticalGradient(listOf(top, bottom)))
                .drawBehind { drawRect(Color.White.copy(alpha = 0.35f), size = Size(size.width, 0.75.dp.toPx())) },
        )
        Box(Modifier.fillMaxWidth().height(14.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = shadow), Color.Transparent))))
    }
}

// ------------------------------------------------------------------ 1. Spotlight

/** A dark room with one warm light, which follows your finger and settles where you leave it. */
internal object SpotlightLook : GalleryLook() {
    override val restShade = 0.55f
    override val lift = 12f

    @Composable
    override fun animated(row: ShelfRow): FloatArray {
        var parked by remember { mutableFloatStateOf(-1f) }
        row.touch?.let { parked = it }
        val target = if (parked < 0f) row.centers.firstOrNull() ?: 0f else parked
        val x by animateFloatAsState(target, tween(380), label = "spotX")
        val on by animateFloatAsState(if (row.touch != null) 1f else 0.45f, tween(450), label = "spotOn")
        return floatArrayOf(x, on)
    }

    override fun DrawScope.wall(row: ShelfRow, anim: FloatArray) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0E0D0C), Color(0xFF080807))))
        if (anim.size < 2) return
        val (x, on) = anim[0] to anim[1]
        val r = 170.dp.toPx()
        // The beam, and the pool it leaves on the shelf.
        val beam = Path().apply {
            moveTo(x - 18.dp.toPx(), 0f); lineTo(x + 18.dp.toPx(), 0f)
            lineTo(x + r * 0.55f, size.height); lineTo(x - r * 0.55f, size.height); close()
        }
        drawPath(beam, Brush.verticalGradient(listOf(Color(0xFFFFE6BF).copy(alpha = 0.1f * on), Color(0xFFFFE6BF).copy(alpha = 0.03f * on))))
        drawOval(
            Brush.radialGradient(listOf(Color(0xFFFFDDA8).copy(alpha = 0.22f * on), Color.Transparent), center = Offset(x, size.height * 0.62f), radius = r),
            topLeft = Offset(x - r, size.height * 0.62f - r * 1.1f), size = Size(r * 2f, r * 2.2f),
        )
    }

    override fun DrawScope.overWall(row: ShelfRow, anim: FloatArray) {
        if (anim.size < 2) return
        // Light spilling onto the books themselves.
        val r = 120.dp.toPx()
        drawOval(
            Brush.radialGradient(listOf(Color(0xFFFFE2B0).copy(alpha = 0.1f * anim[1]), Color.Transparent), center = Offset(anim[0], size.height * 0.6f), radius = r),
            topLeft = Offset(anim[0] - r, size.height * 0.6f - r * 1.2f), size = Size(r * 2f, r * 2.4f),
        )
    }
}

// ------------------------------------------------------------------ 2. White Cube

/** A bright contemporary gallery: white walls, a floating white shelf, and a wall label. */
internal object WhiteCubeLook : GalleryLook() {
    override val dark = false
    override val fadeIn = false
    override val restShade = 0.04f
    override val elevation = 5f
    override val bottomPadding = 30.dp

    override fun DrawScope.wall(row: ShelfRow, anim: FloatArray) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFFF4F3F0), Color(0xFFECEAE6))))
        spotlights(row, Color.White, 0.55f, spacing = 150f, radius = 170f)
    }

    @Composable
    override fun Ledge(dark: Boolean) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(7.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFFFBFAF8), Color(0xFFEDEBE7))))
                    .drawBehind { drawRect(Color.Black.copy(alpha = 0.06f), topLeft = Offset(0f, size.height - 0.75.dp.toPx()), size = Size(size.width, 0.75.dp.toPx())) },
            )
            // A floating shelf: only its shadow says it's there.
            Box(Modifier.fillMaxWidth().height(16.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.14f), Color.Transparent))))
        }
    }

    @Composable
    override fun Label(item: LibraryItem, dark: Boolean, modifier: Modifier) {
        // A museum wall label.
        Column(
            modifier
                .widthIn(max = 240.dp)
                .graphicsLayer {
                    shape = RoundedCornerShape(1.5.dp)
                    clip = true
                    shadowElevation = 3.dp.toPx()
                }
                .background(Color.White)
                .padding(horizontal = 11.dp, vertical = 7.dp),
        ) {
            Text(item.title, style = Folio.type.footnoteStrong, color = Color(0xFF111111), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(shelfMeta(item), style = Folio.type.caption2, color = Color(0xFF111111).copy(alpha = 0.55f), maxLines = 1)
        }
    }

    @Composable
    override fun dotColor(dark: Boolean) = Folio.colors.accent
}

// ------------------------------------------------------------------ 3. Salon

/** A deep oxblood wall, with a brass picture light washing down over each group of books. */
internal object SalonLook : GalleryLook() {
    override val restShade = 0.22f

    override fun DrawScope.wall(row: ShelfRow, anim: FloatArray) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF3A1519), Color(0xFF220B0E))))
        drawRect(Grain.brush(), alpha = 0.18f)
        val step = 132.dp.toPx()
        val barW = 46.dp.toPx()
        val top = if (row.first) 22.dp.toPx() else 4.dp.toPx()
        var x = step / 2
        while (x < size.width + step) {
            // The wash: a widening fan of warm light below each lamp.
            val wash = Path().apply {
                moveTo(x - barW / 2, top); lineTo(x + barW / 2, top)
                lineTo(x + barW * 1.6f, size.height * 0.9f); lineTo(x - barW * 1.6f, size.height * 0.9f); close()
            }
            drawPath(wash, Brush.verticalGradient(listOf(Color(0xFFFFD49A).copy(alpha = 0.2f), Color.Transparent), startY = top, endY = size.height * 0.9f))
            // The lamp: a brass hood and its arm.
            drawRect(Color(0xFF8C6A35), topLeft = Offset(x - 1.dp.toPx(), top - 6.dp.toPx()), size = Size(2.dp.toPx(), 6.dp.toPx()))
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFFE3C489), Color(0xFF7E5F2E)), startY = top, endY = top + 5.dp.toPx()),
                topLeft = Offset(x - barW / 2, top), size = Size(barW, 5.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            )
            x += step
        }
    }

    @Composable
    override fun Ledge(dark: Boolean) = MetalLedge(Color(0xFFE0C287), Color(0xFF7A5B2C), height = 4f, shadow = 0.6f)
}

// ------------------------------------------------------------------ 4. Moonlight

/** After hours: cool light falls through tall windows in long diagonal shafts. A lifted book warms. */
internal object MoonlightLook : GalleryLook() {
    override val restShade = 0.2f
    override val liftLight = Color(0xFFFFE2B8)
    private val moon = Color(0xFFC9D8FF)

    override fun DrawScope.wall(row: ShelfRow, anim: FloatArray) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0E131C), Color(0xFF080A10))))
    }

    override fun DrawScope.overWall(row: ShelfRow, anim: FloatArray) {
        // Window light: two panes, split by a mullion, slanting down to the right.
        val slant = size.height * 0.55f
        listOf(0.08f to 0.16f, 0.2f to 0.06f, 0.52f to 0.14f, 0.64f to 0.05f).forEach { (start, width) ->
            val x0 = size.width * start
            val w = size.width * width
            val shaft = Path().apply { moveTo(x0, 0f); lineTo(x0 + w, 0f); lineTo(x0 + w + slant, size.height); lineTo(x0 + slant, size.height); close() }
            drawPath(shaft, Brush.verticalGradient(listOf(moon.copy(alpha = 0.1f), moon.copy(alpha = 0.035f))))
        }
    }

    override fun DrawScope.overArt(b: Book, pull: Float) {
        drawRect(Color(0xFF2E4C8C).copy(alpha = 0.22f * (1f - pull)))
    }

    @Composable
    override fun Ledge(dark: Boolean) = MetalLedge(Color(0xFFC3CBDA), Color(0xFF5E6676), height = 2.5f)

    @Composable
    override fun dotColor(dark: Boolean) = moon
}

// ------------------------------------------------------------------ 5. Vitrine

/** Under glass in a lit display case: an LED strip along the top, reflections across the front. */
internal object VitrineLook : GalleryLook() {
    override val restShade = 0.16f

    override fun DrawScope.wall(row: ShelfRow, anim: FloatArray) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1C1E21), Color(0xFF0E0F11))))
        val top = if (row.first) 18.dp.toPx() else 0f
        // The strip light inside the top of each shelf, and its fall.
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), startY = top, endY = top + 90.dp.toPx()), topLeft = Offset(0f, top), size = Size(size.width, 90.dp.toPx()))
        drawRect(Color.White.copy(alpha = 0.85f), topLeft = Offset(0f, top), size = Size(size.width, 1.5.dp.toPx()))
        // The case's inner corners.
        drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), endX = 22.dp.toPx()), size = Size(22.dp.toPx(), size.height))
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), startX = size.width - 22.dp.toPx(), endX = size.width), topLeft = Offset(size.width - 22.dp.toPx(), 0f), size = Size(22.dp.toPx(), size.height))
    }

    override fun DrawScope.overWall(row: ShelfRow, anim: FloatArray) {
        // The glass front: one broad reflection and a thin one, slanting across.
        fun streak(at: Float, width: Float, alpha: Float) {
            val x0 = size.width * at
            val w = width.dp.toPx()
            val lean = size.height * 0.35f
            val p = Path().apply { moveTo(x0 + lean, 0f); lineTo(x0 + lean + w, 0f); lineTo(x0 + w, size.height); lineTo(x0, size.height); close() }
            drawPath(p, Color.White.copy(alpha = alpha))
        }
        streak(0.12f, 56f, 0.045f)
        streak(0.12f + 0.17f, 9f, 0.06f)
        streak(0.68f, 34f, 0.035f)
        // The glass's edges catching light.
        drawRect(Color.White.copy(alpha = 0.1f), size = Size(1.dp.toPx(), size.height))
        drawRect(Color.White.copy(alpha = 0.1f), topLeft = Offset(size.width - 1.dp.toPx(), 0f), size = Size(1.dp.toPx(), size.height))
    }

    @Composable
    override fun Ledge(dark: Boolean) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(10.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF3A3C40), Color(0xFF1E1F22))))
                    .drawBehind { drawRect(Color.White.copy(alpha = 0.22f), size = Size(size.width, 1.dp.toPx())) },
            )
            Box(Modifier.fillMaxWidth().height(10.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))))
        }
    }

    @Composable
    override fun dotColor(dark: Boolean) = Color.White
}

// ------------------------------------------------------------------ 6. Concrete

/** An industrial space: a mottled concrete wall, a track of lights, and a brushed steel ledge. */
internal object ConcreteLook : GalleryLook() {
    override val dark: Boolean? = null
    override val restShade = 0.14f

    override fun DrawScope.wall(row: ShelfRow, anim: FloatArray) {
        val light = !row.dark
        drawRect(if (light) Color(0xFFC9C6C0) else Color(0xFF2D2C2A))
        // Mottling: a few broad, soft patches, the same every time.
        val random = java.util.Random(31L + row.books.firstOrNull()?.item?.id.hashCode())
        repeat(7) {
            val c = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
            val r = (60 + random.nextInt(90)).dp.toPx()
            val tint = if (random.nextBoolean()) Color.Black else Color.White
            drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.06f), Color.Transparent), center = c, radius = r), radius = r, center = c)
        }
        drawRect(Grain.brush(), alpha = 0.45f)
        // The track along the top, its lights, and their cones.
        val track = if (row.first) 18.dp.toPx() else 2.dp.toPx()
        drawRect(Color(0xFF1C1C1C), topLeft = Offset(0f, track), size = Size(size.width, 3.dp.toPx()))
        val step = 120.dp.toPx()
        var x = step / 2
        while (x < size.width + step) {
            val lens = track + 18.dp.toPx()
            val cone = Path().apply {
                moveTo(x - 5.dp.toPx(), lens); lineTo(x + 5.dp.toPx(), lens)
                lineTo(x + 62.dp.toPx(), size.height); lineTo(x - 62.dp.toPx(), size.height); close()
            }
            drawPath(cone, Brush.verticalGradient(listOf(Color(0xFFFFF1D8).copy(alpha = if (light) 0.42f else 0.17f), Color.Transparent), startY = lens, endY = size.height))
            drawRoundRect(Color(0xFF202020), topLeft = Offset(x - 6.dp.toPx(), track + 1.dp.toPx()), size = Size(12.dp.toPx(), 17.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            drawOval(Color(0xFFFFF4DE), topLeft = Offset(x - 4.dp.toPx(), lens - 2.dp.toPx()), size = Size(8.dp.toPx(), 3.dp.toPx()))
            x += step
        }
    }

    @Composable
    override fun Ledge(dark: Boolean) = MetalLedge(Color(0xFFC9CDD2), Color(0xFF6F7379), height = 4f, shadow = if (dark) 0.55f else 0.3f)

    @Composable
    override fun dotColor(dark: Boolean) = Folio.colors.accent
}

// ------------------------------------------------------------------ 7. Monochrome

/** Black and white until you start a title, so what you're reading stands out in colour. */
internal object MonochromeLook : GalleryLook() {
    override val restShade = 0.22f
    override val liftLight = Color.White

    override fun DrawScope.wall(row: ShelfRow, anim: FloatArray) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF161616), Color(0xFF0C0C0C))))
        spotlights(row, Color.White, 0.08f)
    }

    @Composable
    override fun artFilter(b: Book, pull: Float): ColorFilter? {
        if (b.started) return null
        // Colour comes back under your finger.
        val m = ColorMatrix().apply { setToSaturation(pull) }
        return ColorFilter.colorMatrix(m)
    }

    @Composable
    override fun Ledge(dark: Boolean) = MetalLedge(Color(0xFFDADADA), Color(0xFF6A6A6A), height = 2.5f)

    @Composable
    override fun dotColor(dark: Boolean) = Folio.colors.accent
}

// ------------------------------------------------------------------ 8. Candlelight

/** A warm glow from below that never quite holds still, and the dark closing in at the edges. */
internal object CandlelightLook : GalleryLook() {
    override val restShade = 0.18f
    override val liftLight = Color(0xFFFFC27A)

    @Composable
    override fun animated(row: ShelfRow): FloatArray {
        val t by rememberInfiniteTransition(label = "flame").animateFloat(0f, 1f, infiniteRepeatable(tween(4_200, easing = LinearEasing), RepeatMode.Restart), label = "flameT")
        // A few unrelated waves, so the flicker never visibly repeats.
        val f = 0.9f + 0.05f * sin(2 * PI * t * 3).toFloat() + 0.035f * sin(2 * PI * t * 7 + 1.3).toFloat() + 0.025f * sin(2 * PI * t * 13 + 0.4).toFloat()
        return floatArrayOf(f)
    }

    override fun DrawScope.wall(row: ShelfRow, anim: FloatArray) {
        val f = anim.firstOrNull() ?: 1f
        drawRect(Brush.verticalGradient(listOf(Color(0xFF150E09), Color(0xFF0A0705))))
        val r = size.width * 0.62f * f
        val c = Offset(size.width * 0.5f, size.height * 0.98f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFA94D).copy(alpha = 0.26f * f), Color(0xFFFF8A2A).copy(alpha = 0.06f), Color.Transparent), center = c, radius = r), radius = r, center = c)
    }

    override fun DrawScope.overWall(row: ShelfRow, anim: FloatArray) {
        val f = anim.firstOrNull() ?: 1f
        // Warmth on the books, and the vignette.
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFFF9A3C).copy(alpha = 0.07f * f))))
        drawRect(
            Brush.radialGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                center = Offset(size.width / 2, size.height * 0.75f), radius = size.width * 0.75f,
            ),
        )
    }

    override fun DrawScope.overArt(b: Book, pull: Float) {
        drawRect(Color(0xFFFF9A3C).copy(alpha = 0.1f))
    }

    @Composable
    override fun Ledge(dark: Boolean) = MetalLedge(Color(0xFF9C7A4E), Color(0xFF3E2C18), height = 3f, shadow = 0.7f)

    @Composable
    override fun dotColor(dark: Boolean) = Color(0xFFFFB866)
}

// ------------------------------------------------------------------ 9. Mounted

/** Hung on a plaster wall like framed pieces, with no shelf. Lifting brings a book off the wall. */
internal object MountedLook : GalleryLook() {
    override val dark: Boolean? = null
    override val restShade = 0.03f
    override val elevation = 0f
    override val lift = 4f
    override val gap = 7

    override fun spineScale(pull: Float) = 1f + 0.05f * pull

    override fun DrawScope.wall(row: ShelfRow, anim: FloatArray) {
        drawRect(if (row.dark) Color(0xFF1F1D1B) else Color(0xFFEAE6DF))
        drawRect(Grain.brush(), alpha = if (row.dark) 0.25f else 0.35f)
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (row.dark) 0.03f else 0.25f), Color.Transparent)))
    }

    override fun DrawScope.behindSpine(b: Book, pull: Float, dark: Boolean) {
        // The shadow it casts on the wall, falling further as it comes away.
        val dx = (4f + 6f * pull).dp.toPx()
        val dy = (8f + 10f * pull).dp.toPx()
        val blur = (7f + 8f * pull).dp.toPx()
        soft(Color.Black.copy(alpha = if (dark) 0.6f else 0.32f), blur) { canvas, paint ->
            canvas.drawRect(dx * 0.4f, dy * 0.5f, size.width + dx, size.height + dy, paint)
        }
    }

    @Composable
    override fun Ledge(dark: Boolean) = Box(Modifier.fillMaxWidth().height(12.dp))

    @Composable
    override fun dotColor(dark: Boolean) = Folio.colors.accent
}

// ------------------------------------------------------------------ 10. Halo

/** A black room where every book is lit from behind in its own colour, brighter as you touch it. */
internal object HaloLook : GalleryLook() {
    override val restShade = 0.12f

    override fun DrawScope.wall(row: ShelfRow, anim: FloatArray) {
        drawRect(Color(0xFF050505))
        val ledgeY = size.height - (if (row.last) bottomPadding.toPx() else 18.dp.toPx())
        row.books.forEach { b ->
            val glow = hue(b.cover, 0.9f, 0.55f, (Spines.pick(b.item.id, 6) * 60f + 20f) % 360f)
            val p = row.pulls.getOrElse(b.index) { 0f }
            val h = b.height.toPx()
            val cx = row.centers[b.index]
            val cy = ledgeY - h * 0.55f
            val rx = b.width.toPx() * 1.9f
            val ry = h * 0.75f
            drawOval(
                Brush.radialGradient(listOf(glow.copy(alpha = 0.32f + 0.4f * p), Color.Transparent), center = Offset(cx, cy), radius = ry),
                topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2f, ry * 2f),
            )
        }
    }

    @Composable
    override fun Ledge(dark: Boolean) = Box(Modifier.fillMaxWidth().height(1.5.dp).background(Color.White.copy(alpha = 0.22f)))

    @Composable
    override fun dotColor(dark: Boolean) = Color.White
}
