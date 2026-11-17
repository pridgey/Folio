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

class FolioApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        Notifications.createChannels(this)
        Graph.scope.launch {
            Graph.extensions.loadInstalled()
            // Freshen extensions at start-up; this is quick and usually a no-op.
            if (Graph.settings.current.onboarded) Graph.extensions.checkForUpdates(install = true)
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
