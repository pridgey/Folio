package dev.pridgey.folio

import android.graphics.Color
import dev.pridgey.folio.data.ChapterEntity
import dev.pridgey.folio.reader.CropBorders
import dev.pridgey.folio.reader.NightSchedule
import dev.pridgey.folio.reader.ReaderItem
import dev.pridgey.folio.reader.spreadsOf
import dev.pridgey.folio.source.SourceLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TestApp::class)
class FeatureLogicTest {

    @Test fun nightScheduleCrossesMidnight() {
        val from = 21 * 60
        val to = 7 * 60
        assertTrue(NightSchedule.active(from, to, LocalTime.of(23, 30)))
        assertTrue(NightSchedule.active(from, to, LocalTime.of(2, 0)))
        assertFalse(NightSchedule.active(from, to, LocalTime.of(7, 0)))
        assertFalse(NightSchedule.active(from, to, LocalTime.of(12, 0)))
        assertTrue(NightSchedule.active(from, to, LocalTime.of(21, 0)))
    }

    @Test fun nightScheduleWithinADay() {
        assertTrue(NightSchedule.active(13 * 60, 15 * 60, LocalTime.of(14, 0)))
        assertFalse(NightSchedule.active(13 * 60, 15 * 60, LocalTime.of(16, 0)))
    }

    private fun chapter(id: Long) = ChapterEntity(id = id, mangaId = 1, url = "/$id", name = "$id")

    private fun pages(ch: ChapterEntity, n: Int) = (0 until n).map { ReaderItem.Page(ch, it, n, "m") }

    @Test fun spreadsPairPagesAfterTheCover() {
        val a = chapter(1)
        val items = pages(a, 5)
        // Page 0 (the cover) alone, then 1+2, 3+4.
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3, 4)), spreadsOf(items, emptySet()))
    }

    @Test fun spreadsKeepWidePagesAndChapterEdgesAlone() {
        val a = chapter(1)
        val b = chapter(2)
        val items = pages(a, 4) + ReaderItem.Transition(a, b, true) + pages(b, 3)
        val wide = setOf(items[1].key)
        assertEquals(
            listOf(listOf(0), listOf(1), listOf(2, 3), listOf(4), listOf(5), listOf(6, 7)),
            spreadsOf(items, wide),
        )
    }

    /** A 400×600 page: [paper] margins of [margin] pixels around purple art. */
    private fun page(margin: Int, paper: Int = Color.WHITE): IntArray {
        val w = 400
        val h = 600
        val art = Color.rgb(90, 60, 140)
        return IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            if (x in margin until w - margin && y in margin until h - margin) art else paper
        }
    }

    private fun bounds(px: IntArray, sidesOnly: Boolean) = CropBorders.bounds(
        400, 600, sidesOnly,
        row = { y, out -> System.arraycopy(px, y * 400, out, 0, 400) },
        col = { x, out -> for (y in 0 until 600) out[y] = px[y * 400 + x] },
    )

    @Test fun cropTrimsPlainMargins() {
        val r = bounds(page(40), sidesOnly = false)
        assertNotNull(r)
        r!!
        // Trimmed to the art, plus a few pixels of breathing room.
        assertEquals(listOf(36, 36, 364, 564), r.toList())
    }

    @Test fun cropKeepsTopAndBottomForStrips() {
        val r = bounds(page(40), sidesOnly = true)!!
        assertEquals(listOf(36, 0, 364, 600), r.toList())
    }

    @Test fun cropTrimsBlackMarginsToo() {
        assertNotNull(bounds(page(40, paper = Color.BLACK), sidesOnly = false))
    }

    @Test fun cropLeavesFullBleedArtAlone() {
        assertNull(bounds(page(0), sidesOnly = false))
        // A coloured background isn't a margin.
        assertNull(bounds(page(40, paper = Color.rgb(200, 40, 40)), sidesOnly = false))
    }

    @Test fun cropNeverTakesMoreThanAQuarter() {
        // Mostly-blank page: stop at 25% per side rather than eating the art.
        val r = bounds(page(180), sidesOnly = false)!!
        assertEquals(100 - 4, r[0])
        assertEquals(150 - 4, r[1])
    }

    @Test fun uptimeNeedsEnoughCalls() {
        val today = LocalDate.now().toEpochDay()
        val s = SourceLog.Snapshot(days = mapOf(1L to listOf(SourceLog.Day(today, ok = 9, failed = 1)), 2L to listOf(SourceLog.Day(today, ok = 1))))
        assertEquals(0.9f, s.uptime(1L)!!, 0.001f)
        assertNull(s.uptime(2L))
        // Days outside the window don't count.
        val old = SourceLog.Snapshot(days = mapOf(1L to listOf(SourceLog.Day(today - 30, failed = 10))))
        assertNull(old.uptime(1L))
    }
}
