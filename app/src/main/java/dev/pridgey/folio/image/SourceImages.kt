package dev.pridgey.folio.image

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import dev.pridgey.folio.source.FolioSource
import dev.pridgey.folio.source.SourceManager
import dev.pridgey.folio.source.SourceUnavailableException
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Response
import okio.FileSystem
import okio.buffer
import okio.sink

/** A cover, loaded through its source so cookies, referers and clearance apply. */
data class CoverModel(val sourceId: Long, val url: String)

/** A page of a chapter, fetched through its source. */
data class PageModel(val sourceId: Long, val pageUrl: String, val imageUrl: String?, val index: Int) {
    val cacheKey: String get() = "page:$sourceId:${imageUrl ?: pageUrl}#$index"
}

/**
 * Fetches [CoverModel]s and [PageModel]s with the source's own client and stores them in Coil's disk
 * cache, so they load instantly next time, survive scrolling back, and can be subsampled by the reader.
 */
class SourceImageFetcher(
    private val key: String,
    private val sourceId: Long,
    private val sources: SourceManager,
    private val options: Options,
    private val imageLoader: ImageLoader,
    private val load: suspend (FolioSource) -> Response,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val diskCache = imageLoader.diskCache
        diskCache?.openSnapshot(key)?.let { snapshot ->
            return SourceFetchResult(
                source = ImageSource(snapshot.data, diskCache.fileSystem, key, snapshot),
                mimeType = null,
                dataSource = DataSource.DISK,
            )
        }
        val source = sources.get(sourceId) ?: throw SourceUnavailableException("This source isn't available")
        val response = load(source)
        response.use { res ->
            val mime = res.body.contentType()?.toString()
            if (diskCache != null) {
                val editor = diskCache.openEditor(key)
                if (editor != null) {
                    try {
                        diskCache.fileSystem.sink(editor.data).buffer().use { sink -> sink.writeAll(res.body.source()) }
                        val snapshot = editor.commitAndOpenSnapshot()
                        if (snapshot != null) {
                            return SourceFetchResult(
                                source = ImageSource(snapshot.data, diskCache.fileSystem, key, snapshot),
                                mimeType = mime,
                                dataSource = DataSource.NETWORK,
                            )
                        }
                    } catch (e: Exception) {
                        editor.abort()
                        throw e
                    }
                }
            }
            // No disk cache: keep it in a temp file.
            val tmp = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "folio-${key.hashCode()}-${System.nanoTime()}"
            FileSystem.SYSTEM.sink(tmp).buffer().use { it.writeAll(res.body.source()) }
            return SourceFetchResult(ImageSource(tmp, FileSystem.SYSTEM), mime, DataSource.NETWORK)
        }
    }

    class CoverFactory(private val sources: SourceManager) : Fetcher.Factory<CoverModel> {
        override fun create(data: CoverModel, options: Options, imageLoader: ImageLoader): Fetcher =
            SourceImageFetcher("cover:${data.sourceId}:${data.url}", data.sourceId, sources, options, imageLoader) { it.cover(data.url) }
    }

    class PageFactory(private val sources: SourceManager) : Fetcher.Factory<PageModel> {
        override fun create(data: PageModel, options: Options, imageLoader: ImageLoader): Fetcher =
            SourceImageFetcher(data.cacheKey, data.sourceId, sources, options, imageLoader) {
                it.image(Page(data.index, data.pageUrl, data.imageUrl))
            }
    }

    object CoverKeyer : Keyer<CoverModel> {
        override fun key(data: CoverModel, options: Options) = "cover:${data.sourceId}:${data.url}"
    }

    object PageKeyer : Keyer<PageModel> {
        override fun key(data: PageModel, options: Options) = data.cacheKey
    }
}
