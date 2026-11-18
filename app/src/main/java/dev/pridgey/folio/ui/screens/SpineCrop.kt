package dev.pridgey.folio.ui.screens

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Choosing which part of a cover a spine shows. A spine is a tall, narrow window onto the cover; this
 * finds where to put that window so it holds as little of the cover's lettering as it can, staying near
 * where the book's recipe would have put it.
 */
internal object SpineCrop {
    /** Text is treated as a little larger than its box, so a window doesn't graze a letter's edge. */
    private const val MARGIN = 0.012f

    /** The share of a window [l, t, r, b] (cover fractions) that text covers. */
    fun textIn(boxes: List<FloatArray>, l: Float, t: Float, r: Float, b: Float): Float {
        val area = (r - l) * (b - t)
        if (area <= 0f) return 0f
        var covered = 0f
        for (box in boxes) {
            val w = min(r, box[2] + MARGIN) - max(l, box[0] - MARGIN)
            val h = min(b, box[3] + MARGIN) - max(t, box[1] - MARGIN)
            if (w > 0f && h > 0f) covered += w * h
        }
        return (covered / area).coerceAtMost(1f)
    }

    /**
     * The centre for a [visW] by [visH] window (cover fractions) with the least text, preferring places
     * near ([preferX], [preferY]). Returns the centre and how much text it still holds. Across, the
     * centre stays where a [travelW]-wide window would fit.
     */
    fun best(boxes: List<FloatArray>, visW: Float, visH: Float, preferX: Float, preferY: Float, travelW: Float = visW): Window {
        val w = visW.coerceIn(0.01f, 1f)
        val h = visH.coerceIn(0.01f, 1f)
        val tw = travelW.coerceIn(w, 1f)
        fun span(size: Float): List<Float> {
            val lo = size / 2
            val hi = 1f - size / 2
            if (hi - lo < 0.001f) return listOf(0.5f)
            val steps = ((hi - lo) / 0.025f).toInt().coerceAtLeast(1)
            return (0..steps).map { lo + (hi - lo) * it / steps }
        }
        var best = Window(preferX.coerceIn(tw / 2, 1 - tw / 2), preferY.coerceIn(h / 2, 1 - h / 2), 1f)
        var bestCost = Float.MAX_VALUE
        // Across, the window can only travel as far as the unzoomed crop allows ([travelW]).
        for (x in span(tw)) for (y in span(h)) {
            val text = textIn(boxes, x - w / 2, y - h / 2, x + w / 2, y + h / 2)
            val dx = x - preferX
            val dy = y - preferY
            // Text matters far more than drifting from where the recipe would sit.
            val cost = text + 0.12f * sqrt(dx * dx + dy * dy)
            if (cost < bestCost) {
                bestCost = cost
                best = Window(x, y, text)
            }
        }
        return best
    }

    data class Window(val x: Float, val y: Float, val text: Float)

    /**
     * Where the cover's logo sits, as [left, top, right, bottom]: the larger lettering in its upper part
     * (lines at least half as tall as the tallest there), taken together. That leaves out taglines and
     * imprints, which are set smaller than a logo. Stylised logos are often only partly read, so this is
     * the logo's readable heart rather than its full outline. Null when there's no lettering up there.
     */
    fun logo(boxes: List<FloatArray>): FloatArray? {
        val upper = boxes.filter { (it[1] + it[3]) / 2 < 0.45f }
        val tallest = upper.maxOfOrNull { it[3] - it[1] } ?: return null
        val big = upper.filter { it[3] - it[1] >= tallest * 0.5f }
        return floatArrayOf(big.minOf { it[0] }, big.minOf { it[1] }, big.maxOf { it[2] }, big.maxOf { it[3] })
    }
}
