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

    @Test fun bookcaseFillsShelvesWithoutOverflowing() {
        fun book(id: Long, chapters: Int) = dev.pridgey.folio.data.LibraryItem(id, 1, "T$id", null, 0, chapters, 0, 0, 0, 0, null)
        val books = (1L..40L).map { book(it, (it * 17 % 400).toInt()) }
        val rows = dev.pridgey.folio.ui.screens.Spines.rows(books, available = 340f)
        assertEquals(books, rows.flatten())
        rows.forEach { row ->
            val used = row.sumOf { dev.pridgey.folio.ui.screens.Spines.width(it.total).toDouble() } + (row.size - 1) * 3
            assertTrue(used <= 340.0)
        }
        assertTrue(dev.pridgey.folio.ui.screens.Spines.width(0) < dev.pridgey.folio.ui.screens.Spines.width(300))
    }

    @Test fun spineRecipesAreSteadyPerTitle() {
        fun book(id: Long, title: String) = dev.pridgey.folio.data.LibraryItem(id, 1, title, null, 0, 10, 0, 0, 0, 0, null)
        val S = dev.pridgey.folio.ui.screens.Spines
        // The same title gets the same seed, whatever its id or source, and the same recipe every time.
        assertEquals(S.seed(book(1, "One Piece")), S.seed(book(99, " one piece ")))
        val seed = S.seed(book(1, "One Piece"))
        assertEquals(dev.pridgey.folio.ui.screens.recipeFor(seed), dev.pridgey.folio.ui.screens.recipeFor(seed))
        assertEquals(S.height(seed), S.height(S.seed(book(5, "One Piece"))))
    }

    @Test fun spineRecipesUseEveryVariation() {
        val recipes = (0L until 3000L).map { dev.pridgey.folio.ui.screens.recipeFor(it * 7919) }
        dev.pridgey.folio.ui.screens.ArtFrame.entries.forEach { f -> assertTrue("$f", recipes.any { it.frame == f }) }
        dev.pridgey.folio.ui.screens.Finish.entries.forEach { f -> assertTrue("$f", recipes.any { it.finish == f }) }
        dev.pridgey.folio.ui.screens.TitleSpot.entries.forEach { s -> assertTrue("$s", recipes.any { it.spot == s }) }
        assertTrue(recipes.any { it.up } && recipes.any { !it.up })
        // Titles set against an edge are an accent: about one in ten.
        val edged = recipes.count { it.side != 0 } / recipes.size.toFloat()
        assertTrue(edged in 0.06f..0.14f)
        // Most books stay plain; the finishes are accents.
        assertTrue(recipes.count { it.finish == dev.pridgey.folio.ui.screens.Finish.PLAIN } > recipes.size * 0.6)
    }

    @Test fun longTitlesBecomeInitials() {
        fun f(t: String) = dev.pridgey.folio.ui.screens.spineText(t)
        assertEquals("ORV", f("Omniscient Reader's Viewpoint").text)
        assertEquals("TBATE", f("The Beginning After the End").text)
        assertEquals("ACAGS", f("All-Class Awakening: God Slayer").text)
        // Bracketed asides go first; short titles stay whole.
        assertEquals(dev.pridgey.folio.ui.screens.SpineText("Overgeared", false), f("Overgeared (Team Argo)"))
        assertEquals(dev.pridgey.folio.ui.screens.SpineText("The Remarried Empress", false), f("The Remarried Empress"))
        // Too many words for a tidy set of initials: keep the title.
        assertFalse(f("I Became the Tyrant's Secretary and Then Some More Words").initials)
    }

    @Test fun spineWidthsKeepGrowingWithLongSeries() {
        fun w(c: Int) = dev.pridgey.folio.ui.screens.Spines.width(c)
        assertTrue(w(1540) - w(475) >= 6f)
        assertTrue(w(475) - w(40) >= 6f)
        assertEquals(w(1500), w(5000), 0.001f)
    }

    @Test fun spineCropsSteerAroundLettering() {
        val crop = dev.pridgey.folio.ui.screens.SpineCrop
        // A logo across the top fifth and credits across the bottom tenth.
        val boxes = listOf(floatArrayOf(0.05f, 0.03f, 0.95f, 0.2f), floatArrayOf(0.1f, 0.9f, 0.9f, 0.97f))
        // A window that can't fit between them keeps as little as it can; one that can, keeps none.
        val tall = crop.best(boxes, 0.25f, 0.95f, 0.5f, 0.5f)
        val short = crop.best(boxes, 0.25f, 0.6f, 0.5f, 0.2f)
        assertTrue(tall.text > 0f)
        assertEquals(0f, short.text, 0.0001f)
        assertTrue(short.y - 0.3f > 0.2f - 0.0001f) // pushed down below the logo
        // With no lettering, it stays where the recipe wanted.
        val free = crop.best(emptyList(), 0.25f, 0.6f, 0.4f, 0.45f)
        assertEquals(0.4f, free.x, 0.03f)
        assertEquals(0.45f, free.y, 0.03f)
        // A vertical title down the left: the window moves right to miss it.
        val side = crop.best(listOf(floatArrayOf(0.02f, 0.1f, 0.2f, 0.9f)), 0.25f, 0.8f, 0.2f, 0.5f)
        assertEquals(0f, side.text, 0.0001f)
        assertTrue(side.x - 0.125f >= 0.2f)
    }
}
