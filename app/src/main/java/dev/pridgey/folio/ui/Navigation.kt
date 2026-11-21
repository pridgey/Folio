@file:OptIn(ExperimentalSharedTransitionApi::class)

package dev.pridgey.folio.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import dev.pridgey.folio.Graph
import dev.pridgey.folio.reader.ReaderActivity
import dev.pridgey.folio.source.MangaDexSource
import dev.pridgey.folio.ui.components.LocalSharedScope
import dev.pridgey.folio.ui.components.NAV_MS
import dev.pridgey.folio.ui.components.ProvideNavScope
import dev.pridgey.folio.ui.screens.BrowseScreen
import dev.pridgey.folio.ui.screens.ChallengeSheet
import dev.pridgey.folio.ui.screens.DetailsScreen
import dev.pridgey.folio.ui.screens.DownloadsScreen
import dev.pridgey.folio.ui.screens.HomeScreen
import dev.pridgey.folio.ui.screens.OnboardingScreen
import dev.pridgey.folio.ui.screens.SearchScreen
import dev.pridgey.folio.ui.screens.SourceSettingsScreen
import dev.pridgey.folio.ui.screens.SourcesScreen
import dev.pridgey.folio.ui.screens.WebScreen
import dev.pridgey.folio.ui.screens.AuthorScreen
import dev.pridgey.folio.ui.screens.SourceHealthScreen
import dev.pridgey.folio.ui.screens.StorageScreen
import dev.pridgey.folio.ui.screens.TagExplorerScreen
import dev.pridgey.folio.ui.screens.TaggedScreen
import dev.pridgey.folio.ui.theme.Folio
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

object Routes {
    @Serializable data object Home
    @Serializable data object Onboarding
    @Serializable data class Details(val mangaId: Long, val sourceId: Long, val cover: String? = null, val coverKey: String? = null)
    @Serializable data class Browse(val sourceId: Long, val mode: String, val query: String = "")
    @Serializable data class Search(val query: String = "")
    @Serializable data object Sources
    @Serializable data class SourceSettings(val sourceId: Long)
    @Serializable data object Downloads
    @Serializable data class Web(val url: String, val title: String)
    @Serializable data object Tags
    /** Titles carrying all of these tags; names are matched to MangaDex tags. */
    @Serializable data class Tagged(val names: List<String>)
    @Serializable data class Author(val name: String)
    @Serializable data object Storage
    @Serializable data object SourceHealth
}

/** App-wide actions screens use to move around. */
class Nav(private val controller: NavHostController, private val context: Context, private val scope: CoroutineScope) {
    fun back() { controller.popBackStack() }

    fun details(mangaId: Long, sourceId: Long, cover: String? = null, coverKey: String? = null) =
        controller.navigate(Routes.Details(mangaId, sourceId, cover, coverKey)) { launchSingleTop = true }

    /** Opens a title seen while browsing: saves it first so it has an id. */
    fun title(sourceId: Long, manga: SManga, coverKey: String? = null) {
        scope.launch {
            val id = Graph.library.remember(sourceId, manga)
            details(id, sourceId, manga.thumbnail_url, coverKey)
        }
    }

    fun browse(sourceId: Long, mode: String, query: String = "") = controller.navigate(Routes.Browse(sourceId, mode, query))
    fun search(query: String = "") = controller.navigate(Routes.Search(query)) { launchSingleTop = true }
    fun sources() = controller.navigate(Routes.Sources) { launchSingleTop = true }
    fun sourceSettings(sourceId: Long) = controller.navigate(Routes.SourceSettings(sourceId))
    fun downloads() = controller.navigate(Routes.Downloads) { launchSingleTop = true }
    fun web(url: String, title: String) = controller.navigate(Routes.Web(url, title))
    fun tags() = controller.navigate(Routes.Tags) { launchSingleTop = true }
    fun tagged(vararg names: String) = controller.navigate(Routes.Tagged(names.toList()))
    fun author(name: String) = controller.navigate(Routes.Author(name))
    fun storage() = controller.navigate(Routes.Storage) { launchSingleTop = true }
    fun sourceHealth() = controller.navigate(Routes.SourceHealth) { launchSingleTop = true }

    fun read(mangaId: Long, chapterId: Long) =
        context.startActivity(ReaderActivity.intent(context, mangaId, chapterId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK.takeIf { context !is android.app.Activity } ?: 0))

    /** Continues a title: opens the chapter after the last one read, or the one in progress. */
    fun continueReading(mangaId: Long) {
        scope.launch {
            var chapters = Graph.library.chaptersOf(mangaId)
            if (chapters.isEmpty()) {
                runCatching { Graph.library.refresh(mangaId) }
                chapters = Graph.library.chaptersOf(mangaId)
            }
            Graph.library.nextToRead(chapters)?.let { read(mangaId, it.id) }
        }
    }
}

val LocalNav = staticCompositionLocalOf<Nav> { error("No Nav") }

private const val PUSH_MS = NAV_MS

@Composable
fun FolioNavHost(activity: MainActivity) {
    val controller = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nav = remember(controller) { Nav(controller, context, scope) }
    val settings by Graph.settings.values.collectAsStateWithLifecycle()
    val launch by activity.pending

    LaunchedEffect(launch) {
        when (val l = launch) {
            is MainActivity.Launch.Link -> {
                activity.pending.value = null
                openLink(nav, l.url)
            }
            is MainActivity.Launch.Title -> {
                activity.pending.value = null
                // Already on that title's page (the reader was opened from it): just show it.
                val top = controller.currentBackStackEntry
                val showing = top?.destination?.hasRoute<Routes.Details>() == true && top.toRoute<Routes.Details>().mangaId == l.mangaId
                if (!showing) controller.navigate(Routes.Details(l.mangaId, l.sourceId))
            }
            else -> Unit
        }
    }

    Box(Modifier.fillMaxSize().background(Folio.colors.background)) {
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedScope provides this, LocalNav provides nav) {
                NavHost(
                    navController = controller,
                    startDestination = if (settings.onboarded) Routes.Home else Routes.Onboarding,
                    enterTransition = { slideInHorizontally(tween(PUSH_MS)) { it } + fadeIn(tween(PUSH_MS)) },
                    exitTransition = { slideOutHorizontally(tween(PUSH_MS)) { -it / 4 } + fadeOut(tween(PUSH_MS)) },
                    popEnterTransition = { slideInHorizontally(tween(PUSH_MS)) { -it / 4 } + fadeIn(tween(PUSH_MS)) },
                    popExitTransition = { slideOutHorizontally(tween(PUSH_MS)) { it } + fadeOut(tween(PUSH_MS)) },
                ) {
                    composable<Routes.Onboarding>(enterTransition = { EnterTransition.None }, exitTransition = { fadeOut(tween(400)) }) {
                        OnboardingScreen(onDone = {
                            controller.navigate(Routes.Home) { popUpTo(Routes.Onboarding) { inclusive = true } }
                        })
                    }
                    composable<Routes.Home>(
                        enterTransition = { fadeIn(tween(300)) },
                        exitTransition = { fadeOut(tween(PUSH_MS)) },
                        popEnterTransition = { fadeIn(tween(PUSH_MS)) },
                        popExitTransition = { ExitTransition.None },
                    ) {
                        ProvideNavScope(this) {
                            val tab = (launch as? MainActivity.Launch.Tab)?.tab
                            HomeScreen(initialTab = tab, onTabConsumed = { if (activity.pending.value is MainActivity.Launch.Tab) activity.pending.value = null })
                        }
                    }
                    composable<Routes.Details>(
                        enterTransition = { fadeIn(tween(PUSH_MS)) },
                        popExitTransition = { fadeOut(tween(PUSH_MS)) },
                    ) { entry ->
                        val route = entry.toRoute<Routes.Details>()
                        ProvideNavScope(this) { DetailsScreen(route) }
                    }
                    composable<Routes.Browse> { entry ->
                        ProvideNavScope(this) { BrowseScreen(entry.toRoute()) }
                    }
                    composable<Routes.Search>(
                        enterTransition = { fadeIn(tween(220)) },
                        exitTransition = { fadeOut(tween(220)) },
                        popEnterTransition = { fadeIn(tween(220)) },
                        popExitTransition = { fadeOut(tween(220)) },
                    ) { entry ->
                        ProvideNavScope(this) { SearchScreen(entry.toRoute<Routes.Search>().query) }
                    }
                    composable<Routes.Sources> { SourcesScreen() }
                    composable<Routes.SourceSettings> { entry -> SourceSettingsScreen(entry.toRoute<Routes.SourceSettings>().sourceId) }
                    composable<Routes.Downloads> { DownloadsScreen() }
                    composable<Routes.Tags> { ProvideNavScope(this) { TagExplorerScreen() } }
                    composable<Routes.Tagged> { entry -> ProvideNavScope(this) { TaggedScreen(entry.toRoute<Routes.Tagged>().names) } }
                    composable<Routes.Author> { entry -> ProvideNavScope(this) { AuthorScreen(entry.toRoute<Routes.Author>().name) } }
                    composable<Routes.Storage> { StorageScreen() }
                    composable<Routes.SourceHealth> { SourceHealthScreen() }
                    composable<Routes.Web> { entry ->
                        val r = entry.toRoute<Routes.Web>()
                        WebScreen(r.url, r.title)
                    }
                }
            }
        }
        ChallengeSheet()
    }
}

/** Opens a MangaFire, Comix or MangaDex link as a title in Folio. */
private suspend fun openLink(nav: Nav, url: String) {
    val sources = Graph.sources
    val sourceId = when {
        "mangadex.org" in url -> MangaDexSource.ID
        "comix.to" in url -> sources.fromPackage(dev.pridgey.folio.source.ExtensionManager.COMIX)?.id ?: return
        else -> sources.primary()?.id ?: return
    }
    runCatching { sources.search(sourceId, url) }.getOrNull()?.mangas?.firstOrNull()?.let { nav.title(sourceId, it) }
}
