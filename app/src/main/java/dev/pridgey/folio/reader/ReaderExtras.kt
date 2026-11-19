package dev.pridgey.folio.reader

import android.graphics.Bitmap
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import coil3.size.Size
import coil3.transform.Transformation
import dev.pridgey.folio.data.PageEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime
import kotlin.math.abs

// ------------------------------------------------------------------ System gestures

/**
 * Leaves drags that start in the system's gesture areas (the home bar, the back edges, the status bar)
 * to the system. Android shows the app the start of a home or back swipe before it takes the gesture
 * over, so without this the page scrolls or turns on the way out. Taps there still reach the pages.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Modifier.leaveSystemGestures(): Modifier {
    val gestures = WindowInsets.systemGestures
    val navigation = WindowInsets.navigationBarsIgnoringVisibility
    val status = WindowInsets.statusBarsIgnoringVisibility
    val direction = LocalLayoutDirection.current
    return pointerInput(gestures, navigation, status, direction) {
        awaitEachGesture {
            // The initial pass runs before the pagers and lists below see the event.
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val p = down.position
            // The bars are hidden while reading, so count their full height as well as the gesture insets.
            val top = maxOf(gestures.getTop(this), status.getTop(this))
            val bottom = maxOf(gestures.getBottom(this), navigation.getBottom(this))
            val left = gestures.getLeft(this, direction)
            val right = gestures.getRight(this, direction)
            val atEdge = p.y < top || p.y > size.height - bottom || p.x < left || p.x > size.width - right
            if (!atEdge) return@awaitEachGesture
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}

// ------------------------------------------------------------------ Auto-crop

/**
 * Trims plain white or black margins from a page. [sidesOnly] keeps the top and bottom, so a
 * continuous strip doesn't lose the gutters that separate its panels.
 */
class CropBorders(private val sidesOnly: Boolean) : Transformation() {
    override val cacheKey = "cropBorders:$sidesOnly"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = withContext(Dispatchers.Default) {
        val r = bounds(input, sidesOnly) ?: return@withContext input
        Bitmap.createBitmap(input, r[0], r[1], r[2] - r[0], r[3] - r[1])
    }

    companion object {
        /** Never trim more than this share of a side: past it, the "margin" is probably art. */
        private const val MAX_SHARE = 0.25f
        private const val TOLERANCE = 28
        private const val PADDING = 4

        /** Returns [left, top, right, bottom] to keep, or null when there's nothing worth trimming. */
        internal fun bounds(b: Bitmap, sidesOnly: Boolean): IntArray? = bounds(
            b.width, b.height, sidesOnly,
            row = { y, out -> b.getPixels(out, 0, b.width, 0, y, b.width, 1) },
            col = { x, out -> b.getPixels(out, 0, 1, x, 0, 1, b.height) },
        )

        /** The trimming itself, over any pixel source: [row] and [col] fill a buffer with one line. */
        internal fun bounds(w: Int, h: Int, sidesOnly: Boolean, row: (Int, IntArray) -> Unit, col: (Int, IntArray) -> Unit): IntArray? {
            if (w < 64 || h < 64) return null
            val rowBuf = IntArray(w)
            val colBuf = IntArray(h)
            row(2, rowBuf)
            val corner = rowBuf[2]
            // Only plain paper (white) or plain black counts as a margin.
            if (luma(corner) in 40..215) return null

            fun rowPlain(y: Int): Boolean { row(y, rowBuf); return plain(rowBuf, corner) }
            fun colPlain(x: Int): Boolean { col(x, colBuf); return plain(colBuf, corner) }

            val maxX = (w * MAX_SHARE).toInt()
            val maxY = (h * MAX_SHARE).toInt()
            var top = 0
            var bottom = h
            if (!sidesOnly) {
                while (top < maxY && rowPlain(top)) top++
                while (h - bottom < maxY && rowPlain(bottom - 1)) bottom--
            }
            var left = 0
            var right = w
            while (left < maxX && colPlain(left)) left++
            while (w - right < maxX && colPlain(right - 1)) right--

            left = (left - PADDING).coerceAtLeast(0)
            top = (top - PADDING).coerceAtLeast(0)
            right = (right + PADDING).coerceAtMost(w)
            bottom = (bottom + PADDING).coerceAtMost(h)
            val trimmed = (left + (w - right)) / w.toFloat() + (top + (h - bottom)) / h.toFloat()
            return if (trimmed < 0.02f) null else intArrayOf(left, top, right, bottom)
        }

        /** A line is plain when nearly every pixel (sampled) is within tolerance of the margin colour. */
        private fun plain(pixels: IntArray, ref: Int): Boolean {
            var off = 0
            var n = 0
            val step = (pixels.size / 300).coerceAtLeast(1)
            var i = 0
            while (i < pixels.size) {
                if (!near(pixels[i], ref)) off++
                n++
                i += step
            }
            return off <= n / 100
        }

        private fun near(a: Int, b: Int): Boolean =
            abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) <= TOLERANCE &&
                abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) <= TOLERANCE &&
                abs((a and 0xFF) - (b and 0xFF)) <= TOLERANCE

        private fun luma(c: Int) = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
    }
}

// ------------------------------------------------------------------ Night filter schedule

object NightSchedule {
    /** True when [now] falls between [from] and [to] (minutes after midnight); the window may cross midnight. */
    fun active(from: Int, to: Int, now: LocalTime = LocalTime.now()): Boolean {
        val m = now.hour * 60 + now.minute
        return when {
            from == to -> true
            from < to -> m in from until to
            else -> m >= from || m < to
        }
    }

    fun format(minutes: Int): String =
        LocalTime.of(minutes / 60 % 24, minutes % 60).format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))
}

// ------------------------------------------------------------------ Two-page spreads

/**
 * Groups reader items into what one screen shows. Pages pair up within a chapter; a chapter's first
 * page (usually its cover), pages already known to be wide, and everything that isn't a page stand alone.
 */
internal fun spreadsOf(items: List<ReaderItem>, wide: Set<String>): List<List<Int>> {
    val out = ArrayList<List<Int>>()
    var i = 0
    while (i < items.size) {
        val a = items[i]
        val b = items.getOrNull(i + 1)
        val pairs = a is ReaderItem.Page && b is ReaderItem.Page &&
            a.chapter.id == b.chapter.id && a.index != 0 && a.key !in wide && b.key !in wide
        if (pairs) {
            out += listOf(i, i + 1)
            i += 2
        } else {
            out += listOf(i)
            i++
        }
    }
    return out
}

// ------------------------------------------------------------------ Page turn effects

/**
 * Draws a pager page with [effect]. Works from each page's distance from the current one, so it
 * follows the finger and needs no recomposition.
 */
internal fun Modifier.pageEffect(pager: PagerState, page: Int, effect: PageEffect, vertical: Boolean, rtl: Boolean): Modifier {
    if (effect == PageEffect.SLIDE) return this
    // Stack: later pages slide over earlier ones. Turn: earlier pages lift off later ones.
    val z = when (effect) {
        PageEffect.STACK -> page.toFloat()
        PageEffect.TURN -> -page.toFloat()
        else -> 0f
    }
    return this.zIndex(z).graphicsLayer {
        val o = pager.getOffsetDistanceInPages(page).coerceIn(-1f, 1f)
        val extent = if (vertical) size.height else size.width
        val dir = if (!vertical && rtl) -1f else 1f
        // Where the pager has moved this page, and the translation that holds it still instead.
        val pin = -o * extent * dir
        fun hold() { if (vertical) translationY = pin else translationX = pin }
        when (effect) {
            PageEffect.FADE -> {
                hold()
                alpha = 1f - abs(o)
            }
            PageEffect.STACK -> if (o < 0f) {
                // The page being covered settles back a little and dims.
                hold()
                val s = 1f - 0.06f * -o
                scaleX = s
                scaleY = s
                alpha = 1f - 0.45f * -o
            }
            PageEffect.TURN -> {
                hold()
                if (o < 0f) {
                    // Hinged at the spine: the left edge for left-to-right, the right for manga, the top for vertical.
                    cameraDistance = 14f * density
                    transformOrigin = when {
                        vertical -> TransformOrigin(0.5f, 0f)
                        rtl -> TransformOrigin(1f, 0.5f)
                        else -> TransformOrigin(0f, 0.5f)
                    }
                    if (vertical) rotationX = 90f * -o else rotationY = 90f * o * dir
                    alpha = if (o <= -0.98f) 0f else 1f
                } else {
                    val s = 0.96f + 0.04f * (1f - o)
                    scaleX = s
                    scaleY = s
                }
            }
            PageEffect.SLIDE -> Unit
        }
    }
}
