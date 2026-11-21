package dev.pridgey.folio

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dev.pridgey.folio.image.SourceImageFetcher
import dev.pridgey.folio.work.ExtensionUpdateWorker
import dev.pridgey.folio.work.LibraryUpdateWorker
import dev.pridgey.folio.work.Notifications
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath

open class FolioApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        // The crash screen's process only shows a report; it doesn't start the app.
        if (CrashReporter.isCrashProcess()) return
        Graph.init(this)
        dev.pridgey.folio.ui.AppLock.install(this)
        Notifications.createChannels(this)
        Graph.scope.launch {
            Graph.extensions.loadInstalled()
            dev.pridgey.folio.work.TitleSync.restore(this@FolioApp)
            // Freshen extensions at start-up; this is quick and usually a no-op.
            if (Graph.settings.current.onboarded) Graph.extensions.checkForUpdates(install = true)
            // Titles read before auto-add existed join the library once, as they would have.
            val s = Graph.settings.current
            if (s.autoAddToLibrary && !s.autoAddBackfilled) Graph.library.addFinished()
            if (!s.autoAddBackfilled) Graph.settings.update { it.copy(autoAddBackfilled = true) }
            Graph.library.pruneUnused()
        }
        LibraryUpdateWorker.schedule(this)
        ExtensionUpdateWorker.schedule(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(SourceImageFetcher.CoverFactory(Graph.sources))
                add(SourceImageFetcher.PageFactory(Graph.sources))
                add(SourceImageFetcher.CoverKeyer)
                add(SourceImageFetcher.PageKeyer)
                add(OkHttpNetworkFetcherFactory(callFactory = { Graph.network.client }))
            }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("images").toOkioPath())
                    .maxSizeBytes(768L * 1024 * 1024)
                    .build()
            }
            .crossfade(220)
            .build()
}
