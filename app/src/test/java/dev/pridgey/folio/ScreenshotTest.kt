package dev.pridgey.folio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigation.compose.rememberNavController
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import com.github.takahirom.roborazzi.captureRoboImage
import dev.pridgey.folio.data.ChapterEntity
import dev.pridgey.folio.data.MangaEntity
import dev.pridgey.folio.data.ThemeMode
import dev.pridgey.folio.image.CoverModel
import dev.pridgey.folio.source.MangaDexSource
import dev.pridgey.folio.ui.LocalNav
import dev.pridgey.folio.ui.Nav
import dev.pridgey.folio.ui.Routes
import dev.pridgey.folio.ui.screens.DetailsScreen
import dev.pridgey.folio.ui.screens.HomeScreen
import dev.pridgey.folio.ui.screens.OnboardingScreen
import dev.pridgey.folio.ui.screens.SourcesScreen
import dev.pridgey.folio.ui.theme.FolioTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/**
 * Renders the main screens to PNGs for design review (`./gradlew recordRoborazziDebug`). Covers are
 * generated abstract art, and nothing touches the network.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = TestApp::class, qualifiers = "w412dp-h892dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val titles = listOf(
        "Lanterns of the Quiet Harbor", "Ninefold Sky", "The Cartographer's Daughter", "Ember & Ash",
        "A Garden Between Stations", "Paper Moon Courier", "Seventh Bell", "Saltwater Requiem", "Copper Fox Diaries",
    )

    @Before fun seed() = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        val app = Graph.app
        SingletonImageLoader.setUnsafe(ImageLoader.Builder(app).components { add(ArtFetcher.Factory()) }.build())
        Unit
        val db = Graph.db
        db.clearAllTables()
        val now = System.currentTimeMillis()
        titles.forEachIndexed { i, t ->
            val id = db.manga().insert(
                MangaEntity(
                    sourceId = MangaDexSource.ID, url = "/title/$i", title = t, author = listOf("Mika Tanabe", "Ren Ishida", "Hana Ko")[i % 3],
                    description = "A quiet harbor town keeps its lanterns lit for ships that never come home, until one night one does. " +
                        "Told across four seasons, it's a story about waiting, returning, and the people who keep the lights on.",
                    genres = listOf("Drama", "Fantasy", "Slice of Life", "Mystery", "Seinen").shuffled(Random(i)).take(4).joinToString(", "),
                    status = if (i % 3 == 0) 2 else 1, thumbnailUrl = "art://$i", initialized = true, favorite = true,
                    dateAdded = now - 30L * 86_400_000 + i, lastUpdate = now,
                ),
            )
            val count = 12 + i * 3
            db.chapters().insertAll(
                (0 until count).map { n ->
                    val number = count - n
                    ChapterEntity(
                        mangaId = id, url = "/c/$i/$number", name = "Chapter $number" + if (n % 4 == 0) " – The Long Way Round" else "",
                        number = number.toFloat(), scanlator = if (n % 2 == 0) "Lighthouse Scans" else null,
                        dateUpload = now - n * 6L * 86_400_000, sourceOrder = n,
                        read = n > 2 + i % 4, lastPageRead = if (n == 2 + i % 4) 8 else 0, pageCount = if (n == 2 + i % 4) 22 else 0,
                        lastReadAt = if (n == 2 + i % 4 && i < 4) now - i * 3_600_000L else 0,
                        dateFetch = if (n < 2) now - i * 7_200_000L else 0,
                    )
                },
            )
        }
        Graph.settings.update { it.copy(onboarded = true) }
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    private fun shoot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            val controller = rememberNavController()
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            FolioTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, Graph.settings.current.accent) {
                CompositionLocalProvider(LocalNav provides Nav(controller, context, scope)) { content() }
            }
        }
        compose.waitForIdle()
        Thread.sleep(400)
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun library() {
        shoot("library_light", false) { HomeScreen(null) {} }
    }

    @Test fun libraryDark() {
        shoot("library_dark", true) { HomeScreen(null) {} }
    }

    @Test fun updates() {
        shoot("updates_dark", true) { HomeScreen("updates") {} }
    }

    @Test fun history() {
        shoot("history_light", false) { HomeScreen("history") {} }
    }

    @Test fun settings() {
        shoot("settings_light", false) { HomeScreen("settings") {} }
    }

    @Test fun details() {
        val id = runBlocking { Graph.db.manga().favorites().first().id }
        shoot("details_dark", true) { DetailsScreen(Routes.Details(id, MangaDexSource.ID, "art://0", null)) }
    }

    @Test fun detailsLight() {
        val id = runBlocking { Graph.db.manga().favorites()[2].id }
        shoot("details_light", false) { DetailsScreen(Routes.Details(id, MangaDexSource.ID, "art://2", null)) }
    }

    @Test fun sources() {
        shoot("sources_light", false) { SourcesScreen() }
    }

    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test fun chapterExplorer() {
        // A long series where the reader has wandered: read to 92, peeked at 140 and 141.
        val chapters = (1..187).map { n ->
            ChapterEntity(
                id = 10_000L + n, mangaId = 1, url = "/x/$n", name = "Chapter $n" + if (n % 9 == 0) " – Harbor Lights" else "",
                number = n.toFloat(), sourceOrder = 187 - n, dateUpload = System.currentTimeMillis() - (187 - n) * 7L * 86_400_000,
                read = n <= 92, bookmark = n == 47 || n == 88,
                lastPageRead = when (n) { 140 -> 6; 141 -> 2; else -> 0 }, pageCount = if (n in 140..141) 24 else 0,
                lastReadAt = when (n) { 141 -> 2000L; 140 -> 1000L; else -> 0L },
            )
        }
        compose.setContent {
            val controller = rememberNavController()
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            FolioTheme(ThemeMode.DARK, Graph.settings.current.accent) {
                CompositionLocalProvider(LocalNav provides Nav(controller, context, scope)) {
                    dev.pridgey.folio.ui.screens.ChapterExplorerSheet(1, MangaDexSource.ID, chapters, onRead = {}, onDismiss = {})
                }
            }
        }
        compose.waitForIdle()
        Thread.sleep(400)
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("screenshots/explorer_dark.png")
    }

    @Test fun onboarding() {
        shoot("onboarding_dark", true) { OnboardingScreen {} }
    }
}

/** Original abstract "covers": soft gradients with a few shapes, seeded by the title. */
private class ArtFetcher(private val seed: Int) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val w = 300
        val h = 450
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val r = Random(seed * 7919)
        val hues = floatArrayOf(r.nextFloat() * 360f, r.nextFloat() * 360f)
        fun color(hue: Float, s: Float, v: Float) = android.graphics.Color.HSVToColor(floatArrayOf(hue, s, v))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), color(hues[0], 0.55f, 0.9f), color(hues[1], 0.75f, 0.35f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
        repeat(3) {
            paint.color = color((hues[0] + 40f * it) % 360f, 0.3f, 1f)
            paint.alpha = 70 + r.nextInt(80)
            canvas.drawCircle(r.nextInt(w).toFloat(), r.nextInt(h).toFloat(), 40f + r.nextInt(110), paint)
        }
        paint.color = android.graphics.Color.WHITE
        paint.alpha = 220
        canvas.drawRect(24f, h - 90f, w - 24f, h - 84f, paint)
        return ImageFetchResult(bmp.asImage(), isSampled = false, dataSource = DataSource.MEMORY)
    }

    class Factory : Fetcher.Factory<CoverModel> {
        override fun create(data: CoverModel, options: Options, imageLoader: ImageLoader): Fetcher =
            ArtFetcher(data.url.substringAfterLast('/').toIntOrNull() ?: data.url.hashCode())
    }
}
