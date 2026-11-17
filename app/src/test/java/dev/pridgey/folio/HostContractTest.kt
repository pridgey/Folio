package dev.pridgey.folio

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.brotli.BrotliInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Keiyoushi's KeiSource checks the host at runtime. These are the things it checks; if any of them
 * break, every extension fails to load.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TestApp::class)
class HostContractTest {

    private class Probe : HttpSource() {
        override val name = "Probe"
        override val lang = "en"
        override val baseUrl = "https://example.org"
        override val supportsLatest = false
        override suspend fun getPopularManga(page: Int) = MangasPage(emptyList(), false)
        override suspend fun getLatestUpdates(page: Int) = MangasPage(emptyList(), false)
        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList) = MangasPage(emptyList(), false)
        override suspend fun getMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean) = SMangaUpdate(manga, chapters)
        override suspend fun getPageList(chapter: SChapter) = emptyList<Page>()
        override fun getMangaUrl(manga: SManga) = baseUrl + manga.url
        override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url
    }

    @Test fun defaultClientHasTheInterceptorsKeiSourceRequires() {
        val client = Injekt.get<NetworkHelper>().client
        val names = client.interceptors.map { it.javaClass.simpleName }
        listOf("UncaughtExceptionInterceptor", "UserAgentInterceptor", "CloudflareInterceptor").forEach {
            assertTrue("$it missing from $names", it in names)
        }
        assertTrue(client.networkInterceptors.none { it is BrotliInterceptor || it.javaClass.simpleName == "IgnoreGzipInterceptor" })
    }

    @Test fun headersDelegateCanBeReplacedByReflection() {
        val field = HttpSource::class.java.getDeclaredField("headers\$delegate")
        val probe = Probe()
        field.isAccessible = true
        field.set(probe, lazyOf(okhttp3.Headers.Builder().add("X-Test", "1").build()))
        assertEquals("1", probe.headers["X-Test"])
    }

    @Test fun injektProvidesWhatExtensionsLookUp() {
        assertNotNull(Injekt.get<Application>())
        assertNotNull(Injekt.get<kotlinx.serialization.json.Json>())
        assertNotNull(Injekt.get<kotlinx.serialization.protobuf.ProtoBuf>())
    }

    @Test fun idMatchesTheStandardFormula() {
        // The same formula every Tachiyomi-family app uses, so ids match the store's index.
        val probe = Probe()
        val key = "probe/en/1"
        val bytes = java.security.MessageDigest.getInstance("MD5").digest(key.toByteArray())
        val expected = (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
        assertEquals(expected, probe.id)
    }

    @Test fun modelFactories() {
        val m = SManga.create().apply { url = "/t"; title = "T" }
        val c = SChapter.create().apply { url = "/c"; name = "C" }
        assertEquals("T", m.title)
        assertEquals(-1f, c.chapter_number)
        assertTrue(m.memo.isEmpty())
        assertNotNull(ApplicationProvider.getApplicationContext<Application>())
    }
}

/** Builds the app graph without background work, for tests. */
class TestApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
