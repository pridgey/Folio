package dev.pridgey.folio

import dev.pridgey.folio.data.ChapterEntity
import dev.pridgey.folio.data.ChapterNumbers
import dev.pridgey.folio.data.LibraryRepository
import dev.pridgey.folio.source.TitleMatcher
import dev.pridgey.folio.update.Updater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TestApp::class)
class LogicTest {
    @Test fun chapterNumbers() {
        assertEquals(14.5f, ChapterNumbers.parse("Vol. 2 Ch. 14.5 – The Return"))
        assertEquals(103f, ChapterNumbers.parse("Chapter 103"))
        assertEquals(7f, ChapterNumbers.parse("Episode 7: Rain"))
        assertEquals(-1f, ChapterNumbers.parse("Oneshot"))
        assertEquals("12", ChapterNumbers.format(12f))
        assertEquals("12.5", ChapterNumbers.format(12.5f))
        assertEquals("", ChapterNumbers.format(-1f))
    }

    @Test fun titleMatching() {
        assertTrue(TitleMatcher.similarity("The Apothecary Diaries", "Apothecary Diaries") > 0.9)
        assertTrue(TitleMatcher.similarity("Solo Leveling (Official)", "Solo Leveling") > 0.95)
        assertTrue(TitleMatcher.similarity("One Piece", "Bleach") < 0.3)
    }

    @Test fun readingOrderIsOldestFirst() {
        val list = listOf(
            ChapterEntity(id = 1, mangaId = 1, url = "/3", name = "3", sourceOrder = 0),
            ChapterEntity(id = 2, mangaId = 1, url = "/2", name = "2", sourceOrder = 1),
            ChapterEntity(id = 3, mangaId = 1, url = "/1", name = "1", sourceOrder = 2),
        )
        assertEquals(listOf(3L, 2L, 1L), LibraryRepository.readingOrder(list).map { it.id })
    }

    @Test fun versionCompare() {
        assertTrue(Updater.isNewer("1.0.10", "1.0.9"))
        assertFalse(Updater.isNewer("1.0.9", "1.0.9"))
        assertFalse(Updater.isNewer("1.0.8", "1.0.9"))
    }
}
