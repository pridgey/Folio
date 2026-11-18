package dev.pridgey.folio.ui.screens

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dev.pridgey.folio.data.LibraryItem
import dev.pridgey.folio.data.ShelfStyle
import dev.pridgey.folio.image.CoverModel
import dev.pridgey.folio.ui.components.coverCacheKeys
import dev.pridgey.folio.ui.theme.Display
import dev.pridgey.folio.ui.theme.Folio
import dev.pridgey.folio.ui.theme.Inter

/** How one shelf style draws its books, shelf, wall and label. Behaviour lives in Bookcase.kt. */
internal abstract class ShelfLook {
    /** Forces the wall dark (true) or light (false); null follows the app's theme. */
    open val dark: Boolean? = null
    open val gap = 3
    /** Room after the last book for anything drawn past it (3D side faces). */
    open val reserve = 0f
    open val headroom: Dp = 46.dp
    open val topPadding: Dp = 0.dp
    open val bottomPadding: Dp = 0.dp
    /** How far a book rises when the finger is on it, and any sideways or forward travel. */
    open val lift = 9f
    open val liftDx = 0f
    open val liftDy = 0f
    open val liftDamping = 0.9f
    open val liftStiffness = 320f
    open val reflection = false
    /** The ledge draws above or below the books. */
    open val ledgeZ = 0f

    @Composable
    open fun Backdrop(row: ShelfRow, modifier: Modifier, content: @Composable () -> Unit) = Box(modifier) { content() }

    @Composable
    abstract fun Spine(b: Book, pull: Float, dark: Boolean)

    @Composable
    abstract fun Ledge(dark: Boolean)

    @Composable
    open fun Label(item: LibraryItem, dark: Boolean, modifier: Modifier) {
        val c = Folio.colors
        val (title, meta) = labelColors(dark)
        ShelfLabel(item, title, meta, c.accent, modifier)
    }

    @Composable
    fun labelColors(dark: Boolean): Pair<Color, Color> {
        val c = Folio.colors
        return when {
            dark == c.isDark -> c.label to c.secondaryLabel
            dark -> Color(0xFFF4F1EA) to Color(0xFFF4F1EA).copy(alpha = 0.6f)
            else -> Color(0xFF16130F) to Color(0xFF16130F).copy(alpha = 0.55f)
        }
    }

    @Composable
    open fun dotColor(dark: Boolean): Color = Folio.colors.accent
}

internal fun lookFor(style: ShelfStyle): ShelfLook = when (style) {
    ShelfStyle.COLLECTION -> CollectionLook
    ShelfStyle.PAPERBACK -> PaperbackLook
    ShelfStyle.TANKOBON -> TankobonLook
    ShelfStyle.GALLERY -> GalleryLook
    ShelfStyle.NEON -> NeonLook
    ShelfStyle.GLASS -> GlassLook
    ShelfStyle.CLAY -> ClayLook
    ShelfStyle.RISO -> RisoLook
    ShelfStyle.SWISS -> SwissLook
    ShelfStyle.DIMENSIONAL -> DimensionalLook
}

// ------------------------------------------------------------------ Shared drawing

/** A cover's colour with its saturation and lightness pulled into a range; near-greys stay grey. */
internal fun tone(cover: Color?, fallback: Color, sat: ClosedFloatingPointRange<Float>, light: ClosedFloatingPointRange<Float>): Color {
    if (cover == null) return fallback
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(cover.toArgb(), hsl)
    if (hsl[1] > 0.1f) hsl[1] = hsl[1].coerceIn(sat.start, sat.endInclusive)
    hsl[2] = hsl[2].coerceIn(light.start, light.endInclusive)
    return Color(ColorUtils.HSLToColor(hsl))
}

/** The cover's hue with a fixed saturation and lightness. */
internal fun hue(cover: Color?, sat: Float, light: Float, fallbackHue: Float): Color {
    val hsl = FloatArray(3)
    if (cover != null) ColorUtils.colorToHSL(cover.toArgb(), hsl) else hsl[0] = fallbackHue
    return Color(ColorUtils.HSLToColor(floatArrayOf(hsl[0], sat, light)))
}

internal fun hueOf(cover: Color?): Float? {
    cover ?: return null
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(cover.toArgb(), hsl)
    return if (hsl[1] < 0.12f) null else hsl[0]
}

internal fun shade(color: Color, amount: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[2] = (hsl[2] + amount).coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl)).copy(alpha = color.alpha)
}

internal val LightInk = Color(0xFFFAF7F1)
internal val DarkInk = Color(0xFF15120E)

/** Whichever ink reads better on [paper], by contrast ratio. */
internal fun inkFor(paper: Color, light: Color = LightInk, dark: Color = DarkInk): Color {
    val l = paper.luminance()
    val onLight = (l + 0.05f) / (dark.luminance() + 0.05f)
    val onDark = (light.luminance() + 0.05f) / (l + 0.05f)
    return if (onDark >= onLight) light else dark
}

@Composable
internal fun CoverArt(
    item: LibraryItem,
    modifier: Modifier,
    alignment: Alignment = Alignment.Center,
    colorFilter: ColorFilter? = null,
) {
    val url = item.thumbnailUrl ?: return
    val context = LocalContext.current
    val request = remember(item.sourceId, url) {
        ImageRequest.Builder(context).data(CoverModel(item.sourceId, url)).coverCacheKeys(item.sourceId, url).build()
    }
    AsyncImage(request, null, contentScale = ContentScale.Crop, alignment = alignment, colorFilter = colorFilter, modifier = modifier)
}

/** The title running down a spine, sized to fit between [min] and [max]. */
@Composable
internal fun SpineTitle(
    text: String,
    style: TextStyle,
    color: Color,
    max: TextUnit,
    modifier: Modifier = Modifier,
    min: TextUnit = 7.5.sp,
) {
    BasicText(
        text,
        style = style.copy(color = color, lineHeight = 1.0.em),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = if (min.value <= max.value) min else max, maxFontSize = max, stepSize = 0.5.sp),
        modifier = modifier.downward(),
    )
}

/** Light along the left edge, a little shade into the right: a spine that's slightly round. */
internal fun DrawScope.roundSpine(strength: Float = 1f) {
    drawRect(
        Brush.horizontalGradient(
            0f to Color.White.copy(alpha = 0.10f * strength),
            0.07f to Color.White.copy(alpha = 0.04f * strength),
            0.5f to Color.Transparent,
            0.9f to Color.Black.copy(alpha = 0.05f * strength),
            1f to Color.Black.copy(alpha = 0.18f * strength),
        ),
    )
}

/** A fine speckle, for paper. */
internal object Grain {
    val bitmap: ImageBitmap by lazy {
        val size = 96
        val random = java.util.Random(11)
        val pixels = IntArray(size * size) {
            val v = if (random.nextBoolean()) 255 else 0
            android.graphics.Color.argb(random.nextInt(40), v, v, v)
        }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    fun brush() = ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
}

/** A soft glow around a rounded rectangle, drawn with a blurred stroke. */
internal fun DrawScope.glow(color: Color, radius: Float, stroke: Float, corner: Float, alpha: Float) {
    drawIntoCanvas { canvas ->
        val paint = Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = stroke
            this.color = color.copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()
            maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, corner, corner, paint)
    }
}

/** A plain rule for a shelf. */
@Composable
internal fun Rule(color: Color, thickness: Dp, modifier: Modifier = Modifier) =
    Box(modifier.fillMaxWidth().height(thickness).clip(RoundedCornerShape(thickness / 2)).background(color))

// ------------------------------------------------------------------ 1. Collection

/**
 * A boxed-set collection: cream papers that differ a shade from book to book, condensed black
 * capitals, and a chip of the cover's colour at the head. Restrained; the colour is a signature.
 */
internal object CollectionLook : ShelfLook() {
    private val papers = listOf(Color(0xFFF3EEE4), Color(0xFFF8F5EE), Color(0xFFECE5D7), Color(0xFFE4E0D7))
    private val ink = Color(0xFF1B1915)

    @Composable
    override fun Spine(b: Book, pull: Float, dark: Boolean) {
        val paper = papers[Spines.pick(b.item.id, papers.size)]
        val chip = tone(b.cover, Color(0xFF9A3B2B), 0.4f..0.7f, 0.36f..0.52f)
        Box(
            Modifier
                .width(b.width)
                .height(b.height)
                .graphicsLayer {
                    shape = RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp)
                    clip = true
                    shadowElevation = (1.5f + 6f * pull).dp.toPx()
                }
                .drawWithCache {
                    val grain = Grain.brush()
                    onDrawBehind {
                        drawRect(paper)
                        drawRect(grain, alpha = 0.25f)
                        roundSpine(0.8f)
                    }
                },
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 5.dp).padding(top = 9.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(14.dp).background(chip))
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    SpineTitle(
                        b.item.title.uppercase(),
                        TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.07.em),
                        ink, 12.sp,
                    )
                }
                // The imprint: a small mark at the foot.
                Box(Modifier.size(7.dp).clip(CircleShape).background(ink))
            }
        }
    }

    @Composable
    override fun Ledge(dark: Boolean) = Rule(Folio.colors.label.copy(alpha = 0.55f), 1.5.dp)

    @Composable
    override fun Label(item: LibraryItem, dark: Boolean, modifier: Modifier) {
        val (title, meta) = labelColors(dark)
        ShelfLabel(item, title, meta, Folio.colors.accent, modifier, TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, letterSpacing = 0.06.em))
    }
}

// ------------------------------------------------------------------ 2. Paperback

/** Three bands, like a classic paperback: the cover's colour at the head and foot, cream between. */
internal object PaperbackLook : ShelfLook() {
    private val cream = Color(0xFFF3EDE0)

    @Composable
    override fun Spine(b: Book, pull: Float, dark: Boolean) {
        val band = tone(b.cover, Color(0xFFE0662E), 0.5f..0.85f, 0.42f..0.52f)
        Box(
            Modifier
                .width(b.width)
                .height(b.height)
                .graphicsLayer {
                    shape = RoundedCornerShape(topStart = 1.5.dp, topEnd = 1.5.dp)
                    clip = true
                    shadowElevation = (2f + 6f * pull).dp.toPx()
                }
                .drawWithContent {
                    drawContent()
                    // A little age at the foot, and the round of the spine.
                    drawRect(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color(0xFF5A3A12).copy(alpha = 0.07f)))
                    roundSpine()
                },
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(0.26f).background(band))
                Box(Modifier.fillMaxWidth().weight(0.56f).background(cream).padding(horizontal = 4.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
                    SpineTitle(b.item.title, TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, letterSpacing = 0.01.em), DarkInk, 11.sp, min = 6.5.sp)
                }
                Box(Modifier.fillMaxWidth().weight(0.18f).background(band), contentAlignment = Alignment.Center) {
                    // The publisher's oval, carrying the chapter count.
                    Box(
                        Modifier.width(b.width - 9.dp).height(15.dp).clip(RoundedCornerShape(50)).background(cream),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            b.item.total.toString(),
                            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 7.5.sp, color = shade(band, -0.12f)),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }

    @Composable
    override fun Ledge(dark: Boolean) = Rule(Folio.colors.label.copy(alpha = 0.85f), 3.dp)
}

// ------------------------------------------------------------------ 3. Tankōbon

/**
 * A manga volume's spine: white stock, the series art at the head, the title stacked letter by letter
 * when it's short enough (as Japanese spines set it), and a block of the series' colour at the foot.
 */
internal object TankobonLook : ShelfLook() {
    private val stock = Color(0xFFF8F7F3)

    @Composable
    override fun Spine(b: Book, pull: Float, dark: Boolean) {
        val series = tone(b.cover, Color(0xFFD23B3B), 0.55f..0.85f, 0.4f..0.5f)
        Box(
            Modifier
                .width(b.width)
                .height(b.height)
                .graphicsLayer {
                    shape = RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp)
                    clip = true
                    shadowElevation = (2f + 6f * pull).dp.toPx()
                }
                .drawWithContent {
                    drawRect(stock)
                    drawContent()
                    roundSpine(0.9f)
                },
        ) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                CoverArt(b.item, Modifier.fillMaxWidth().height(b.width * 1.2f), Alignment.TopCenter)
                Box(Modifier.fillMaxWidth().height(3.dp).background(series))
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 3.dp, vertical = 8.dp), contentAlignment = Alignment.TopCenter) {
                    StackedTitle(b.item.title, DarkInk)
                }
                Box(Modifier.fillMaxWidth().height(30.dp).background(series), contentAlignment = Alignment.Center) {
                    BasicText(
                        b.item.total.toString(),
                        style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 12.sp, color = inkFor(series)),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
                    )
                }
            }
        }
    }

    /** Letters stacked upright, one under another; long titles fall back to running down the spine. */
    @Composable
    private fun StackedTitle(title: String, ink: Color) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val density = LocalDensity.current
            val letters = title.uppercase().trim()
            val step = with(density) { (maxHeight / letters.length.coerceAtLeast(1)).toSp() }
            val size = minOf(step.value * 0.92f, with(density) { (maxWidth * 0.78f).toSp().value }, 15f)
            if (size >= 8.5f) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    letters.forEach { ch ->
                        if (ch == ' ') {
                            Spacer(Modifier.height(with(density) { (size * 0.45f).sp.toDp() }))
                        } else {
                            BasicText(
                                ch.toString(),
                                style = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = size.sp, lineHeight = (size * 1.02f).sp, color = ink, textAlign = TextAlign.Center),
                            )
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SpineTitle(title, TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold), ink, 13.sp)
                }
            }
        }
    }

    @Composable
    override fun Ledge(dark: Boolean) = Rule(Folio.colors.label.copy(alpha = 0.8f), 2.dp)
}

// ------------------------------------------------------------------ 4. Gallery

/**
 * A dark gallery wall under warm spotlights. Each spine is a slice of the cover's art, resting a little
 * in shadow; the one under your finger rises into the light.
 */
internal object GalleryLook : ShelfLook() {
    override val dark = true
    override val topPadding = 18.dp
    override val bottomPadding = 26.dp
    override val lift = 11f

    @Composable
    override fun Backdrop(row: ShelfRow, modifier: Modifier, content: @Composable () -> Unit) {
        val pageColor = Folio.colors.background
        Box(
            modifier.drawWithCache {
                val wall = Brush.verticalGradient(listOf(Color(0xFF151311), Color(0xFF0D0C0B)))
                val spacing = 132.dp.toPx()
                val radius = 150.dp.toPx()
                val lights = generateSequence(spacing / 2) { it + spacing }.takeWhile { it < size.width + spacing }.toList()
                // Each light falls in a cone onto the books from just above the shelf's top.
                val lightY = if (row.first) 0f else -radius * 0.25f
                val fade = 28.dp.toPx()
                onDrawBehind {
                    drawRect(wall)
                    lights.forEach { x ->
                        drawOval(
                            Brush.radialGradient(listOf(Color(0xFFFFE1AE).copy(alpha = 0.11f), Color.Transparent), center = Offset(x, lightY + radius * 0.7f), radius = radius),
                            topLeft = Offset(x - radius * 0.8f, lightY - radius * 0.1f), size = Size(radius * 1.6f, radius * 1.8f),
                        )
                    }
                    // The first shelf rises out of the page instead of starting at a hard edge.
                    if (row.first) drawRect(Brush.verticalGradient(listOf(pageColor, Color.Transparent), endY = fade), size = Size(size.width, fade))
                }
            },
        ) { content() }
    }

    @Composable
    override fun Spine(b: Book, pull: Float, dark: Boolean) {
        Box(
            Modifier
                .width(b.width)
                .height(b.height)
                .graphicsLayer {
                    shape = RoundedCornerShape(1.dp)
                    clip = true
                    shadowElevation = (3f + 12f * pull).dp.toPx()
                }
                .background(tone(b.cover, Color(0xFF3A342E), 0.2f..0.6f, 0.2f..0.4f)),
        ) {
            CoverArt(b.item, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().drawBehind {
                    drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.04f), 0.5f to Color.Black.copy(alpha = 0.2f), 1f to Color.Black.copy(alpha = 0.86f)))
                    // Resting in shade until it's lifted into the light.
                    drawRect(Color.Black.copy(alpha = 0.3f * (1f - pull)))
                    drawRect(Color(0xFFFFE9C4).copy(alpha = 0.07f * pull))
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
    override fun Ledge(dark: Boolean) {
        Column {
            Box(Modifier.fillMaxWidth().height(3.dp).background(Brush.verticalGradient(listOf(Color(0xFFD6B57A), Color(0xFF7C6034)))))
            Box(Modifier.fillMaxWidth().height(14.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))))
        }
    }

    @Composable
    override fun dotColor(dark: Boolean) = Color(0xFFFFC978)
}

// ------------------------------------------------------------------ 5. Neon

/**
 * Night: each book outlined in a glowing tube of its cover's hue, its title lit the same way, all of
 * it reflected in a glossy floor. Brightens as you touch it.
 */
internal object NeonLook : ShelfLook() {
    override val dark = true
    override val topPadding = 16.dp
    override val bottomPadding = 4.dp
    override val reflection = true
    override val gap = 6

    @Composable
    override fun Backdrop(row: ShelfRow, modifier: Modifier, content: @Composable () -> Unit) {
        Box(modifier.background(Brush.verticalGradient(listOf(Color(0xFF0E0C22), Color(0xFF07060F))))) { content() }
    }

    @Composable
    override fun Spine(b: Book, pull: Float, dark: Boolean) {
        val tube = hue(b.cover, 1f, 0.62f, (Spines.pick(b.item.id, 6) * 60f + 300f) % 360f)
        val core = shade(tube, 0.18f)
        val density = LocalDensity.current
        val glowPx = with(density) { 14.dp.toPx() }
        Box(
            Modifier
                .width(b.width)
                .height(b.height)
                .drawWithCache {
                    val r = 4.dp.toPx()
                    val body = hue(b.cover, 0.4f, 0.075f, 280f)
                    onDrawBehind {
                        glow(tube, 9.dp.toPx(), 3.dp.toPx(), r, 0.55f + 0.45f * pull)
                        drawRoundRect(body, cornerRadius = CornerRadius(r))
                        drawRoundRect(Brush.verticalGradient(listOf(tube.copy(alpha = 0.08f + 0.1f * pull), Color.Transparent)), cornerRadius = CornerRadius(r))
                        drawRoundRect(core, cornerRadius = CornerRadius(r), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
                    }
                }
                .padding(horizontal = 4.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            SpineTitle(
                b.item.title.uppercase(),
                TextStyle(
                    fontFamily = Display, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.1.em,
                    shadow = Shadow(tube.copy(alpha = 0.6f + 0.4f * pull), blurRadius = glowPx),
                ),
                shade(tube, 0.26f), 12.sp,
            )
        }
    }

    @Composable
    override fun Ledge(dark: Boolean) {
        Box(
            Modifier.fillMaxWidth().height(2.dp).drawBehind {
                glow(Color(0xFFB9B4FF), 6.dp.toPx(), 2.dp.toPx(), 1.dp.toPx(), 0.8f)
                drawRect(Color(0xFFEDEBFF))
            },
        )
    }

    @Composable
    override fun Label(item: LibraryItem, dark: Boolean, modifier: Modifier) {
        val density = LocalDensity.current
        ShelfLabel(
            item, Color.White, Color.White.copy(alpha = 0.6f), Color(0xFFFF6BD5), modifier,
            Folio.type.footnoteStrong.copy(shadow = Shadow(Color(0xFFB9B4FF), blurRadius = with(density) { 10.dp.toPx() })),
        )
    }

    @Composable
    override fun dotColor(dark: Boolean) = Color(0xFFFF6BD5)
}
