package dev.pridgey.folio.ui.theme

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dev.pridgey.folio.image.CoverModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The colour of a cover: the most prominent saturated hue in it, or null for covers that are
 * essentially black and white (those keep the user's accent).
 */
object CoverTint {
    private val cache = LruCache<String, Int>(300)
    private const val NONE = 0

    fun cached(sourceId: Long, url: String): Color? = cache.get(key(sourceId, url))?.takeIf { it != NONE }?.let { Color(it) }

    suspend fun of(context: android.content.Context, sourceId: Long, url: String): Color? {
        val key = key(sourceId, url)
        cache.get(key)?.let { return if (it == NONE) null else Color(it) }
        val request = ImageRequest.Builder(context)
            .data(CoverModel(sourceId, url))
            .memoryCacheKey("cover:$sourceId:$url")
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
        val color = withContext(Dispatchers.Default) { dominantHue(result.image.toBitmap()) }
        cache.put(key, color ?: NONE)
        return color?.let { Color(it) }
    }

    private fun key(sourceId: Long, url: String) = "$sourceId|$url"

    /** Buckets pixels by hue, weighted by how vivid they are, and averages the strongest bucket. */
    internal fun dominantHue(source: Bitmap): Int? {
        val bitmap = Bitmap.createScaledBitmap(source, 48, 72, true)
        val bins = 18
        val weight = FloatArray(bins)
        val r = FloatArray(bins)
        val g = FloatArray(bins)
        val b = FloatArray(bins)
        val hsv = FloatArray(3)
        var total = 0f
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val p = bitmap.getPixel(x, y)
            android.graphics.Color.colorToHSV(p, hsv)
            val vivid = hsv[1] * hsv[2]
            total += 1f
            if (hsv[1] < 0.22f || hsv[2] < 0.18f) continue
            val bin = ((hsv[0] / 360f) * bins).toInt().coerceIn(0, bins - 1)
            val w = vivid * vivid
            weight[bin] += w
            r[bin] += android.graphics.Color.red(p) * w
            g[bin] += android.graphics.Color.green(p) * w
            b[bin] += android.graphics.Color.blue(p) * w
        }
        if (bitmap !== source) bitmap.recycle()
        // Neighbouring hues belong together; pick the strongest pair of bins.
        var best = -1
        var bestWeight = 0f
        for (i in 0 until bins) {
            val w = weight[i] + weight[(i + 1) % bins] * 0.6f + weight[(i + bins - 1) % bins] * 0.6f
            if (w > bestWeight) { bestWeight = w; best = i }
        }
        if (best < 0 || bestWeight / total < 0.012f) return null
        val w = weight[best]
        return android.graphics.Color.rgb((r[best] / w).toInt(), (g[best] / w).toInt(), (b[best] / w).toInt())
    }

    /** Tunes a cover colour so it works as an accent on the current background. */
    fun asAccent(color: Color, dark: Boolean): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(android.graphics.Color.argb(255, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()), hsl)
        hsl[1] = hsl[1].coerceIn(0.5f, 0.85f)
        hsl[2] = if (dark) hsl[2].coerceIn(0.58f, 0.7f) else hsl[2].coerceIn(0.3f, 0.38f)
        return Color(ColorUtils.HSLToColor(hsl))
    }

    /** A deep shade of the cover colour for backdrops behind art. */
    fun asBackdrop(color: Color, dark: Boolean): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(android.graphics.Color.argb(255, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()), hsl)
        hsl[1] = hsl[1].coerceIn(0.25f, 0.55f)
        hsl[2] = if (dark) 0.14f else 0.88f
        return Color(ColorUtils.HSLToColor(hsl))
    }
}

/** The cover's colour once it's known, animating in from [fallback]. */
@Composable
fun rememberCoverColor(sourceId: Long, url: String?): Color? {
    val context = LocalContext.current
    var color by remember(sourceId, url) { mutableStateOf(url?.let { CoverTint.cached(sourceId, it) }) }
    LaunchedEffect(sourceId, url) {
        if (url != null && color == null) color = runCatching { CoverTint.of(context, sourceId, url) }.getOrNull()
    }
    return color
}

/** Gives [content] the cover's colour as its accent, so buttons and highlights match the art. */
@Composable
fun CoverAccent(sourceId: Long, url: String?, content: @Composable () -> Unit) {
    val base = Folio.colors
    val cover = rememberCoverColor(sourceId, url)
    val target = cover?.let { CoverTint.asAccent(it, base.isDark) } ?: base.accent
    val accent by animateColorAsState(target, tween(450), label = "coverAccent")
    val colors = base.copy(
        accent = accent,
        accentSoft = accent.copy(alpha = if (base.isDark) 0.16f else 0.12f),
        onAccent = if (accent.luminance() > 0.55f) Color(0xFF111111) else Color.White,
    )
    CompositionLocalProvider(LocalFolioColors provides colors, content = content)
}
