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
    ShelfStyle.GALLERY -> GalleryLook()
    ShelfStyle.SPOTLIGHT -> SpotlightLook
    ShelfStyle.WHITE_CUBE -> WhiteCubeLook
    ShelfStyle.SALON -> SalonLook
    ShelfStyle.MOONLIGHT -> MoonlightLook
    ShelfStyle.VITRINE -> VitrineLook
    ShelfStyle.CONCRETE -> ConcreteLook
    ShelfStyle.MONOCHROME -> MonochromeLook
    ShelfStyle.CANDLELIGHT -> CandlelightLook
    ShelfStyle.MOUNTED -> MountedLook
    ShelfStyle.HALO -> HaloLook
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

