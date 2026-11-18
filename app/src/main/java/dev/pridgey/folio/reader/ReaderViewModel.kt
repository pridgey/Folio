package dev.pridgey.folio.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import dev.pridgey.folio.Graph
import dev.pridgey.folio.data.ChapterEntity
import dev.pridgey.folio.data.LibraryRepository
import dev.pridgey.folio.data.MangaEntity
import dev.pridgey.folio.data.ReaderMode
import dev.pridgey.folio.image.PageModel
import dev.pridgey.folio.source.MangaDexSource
import dev.pridgey.folio.source.SourceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** Something the reader shows: a page, a chapter boundary, or a chapter that couldn't load. */
sealed interface ReaderItem {
    val key: String

    data class Page(val chapter: ChapterEntity, val index: Int, val total: Int, val model: Any) : ReaderItem {
        override val key get() = "p-${chapter.id}-$index"
    }

    /** Between [from] and [to]; at the very start [from] is null, at the very end [to] is null. */
    data class Transition(val from: ChapterEntity?, val to: ChapterEntity?, val forward: Boolean) : ReaderItem {
        override val key get() = "t-${from?.id}-${to?.id}-$forward"
    }

    data class Failed(val chapter: ChapterEntity, val message: String) : ReaderItem {
        override val key get() = "f-${chapter.id}"
    }

    data class Loading(val chapter: ChapterEntity) : ReaderItem {
        override val key get() = "l-${chapter.id}"
    }
}

class ReaderViewModel(app: Application, private var mangaId: Long, startChapterId: Long) : AndroidViewModel(app) {

    data class State(
        val manga: MangaEntity? = null,
        val mode: ReaderMode = ReaderMode.RIGHT_TO_LEFT,
        /** The title's own mode, or AUTO when it follows the default. */
        val titleMode: ReaderMode = ReaderMode.AUTO,
        val items: List<ReaderItem> = emptyList(),
        /** Index in [items] to start at (changes only when the reader is reset). */
        val startIndex: Int = 0,
        val resetToken: Int = 0,
        val current: ChapterEntity? = null,
        val page: Int = 0,
        val pageCount: Int = 0,
        val fallbackBusy: Boolean = false,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val repo = Graph.library
    private var order: List<ChapterEntity> = emptyList()
    private var appendJob: Job? = null
    private var saveJob: Job? = null
    private val loaded = mutableSetOf<Long>()

    init {
        viewModelScope.launch { open(startChapterId) }
    }

    /** Starts (or restarts) the reader at [chapterId]. */
    private suspend fun open(chapterId: Long) {
        val manga = repo.get(mangaId) ?: return
        order = LibraryRepository.readingOrder(repo.chaptersOf(mangaId))
        val chapter = order.firstOrNull { it.id == chapterId } ?: order.firstOrNull() ?: return
        val titleMode = ReaderMode.entries.getOrElse(manga.readerMode) { ReaderMode.AUTO }
        loaded.clear()
        _state.update {
            it.copy(
                manga = manga, titleMode = titleMode,
                mode = resolveMode(titleMode, manga),
                items = listOf(ReaderItem.Loading(chapter)), startIndex = 0, resetToken = it.resetToken + 1,
                current = chapter, page = 0, pageCount = 0,
            )
        }
        val pages = loadPages(chapter)
        val prev = previous(chapter)
        val items = buildList {
            add(ReaderItem.Transition(prev, chapter, forward = false))
            addAll(pages)
            if (pages.firstOrNull() is ReaderItem.Page) add(ReaderItem.Transition(chapter, next(chapter), forward = true))
        }
        // Resume where the chapter was left, unless it was finished.
        val resume = if (!chapter.read && chapter.lastPageRead > 0) chapter.lastPageRead.coerceAtMost(pages.size - 1) else 0
        _state.update {
            it.copy(items = items, startIndex = 1 + resume.coerceAtLeast(0), resetToken = it.resetToken + 1, page = resume, pageCount = pages.count { p -> p is ReaderItem.Page })
        }
        if (_state.value.titleMode == ReaderMode.AUTO) refineAuto(pages)
    }

    private fun previous(c: ChapterEntity) = order.getOrNull(order.indexOfFirst { it.id == c.id } - 1)
    private fun next(c: ChapterEntity) = order.getOrNull(order.indexOfFirst { it.id == c.id } + 1)

    private suspend fun loadPages(chapter: ChapterEntity): List<ReaderItem> {
        loaded += chapter.id
        val manga = _state.value.manga ?: return emptyList()
        Graph.downloads.pages(manga.id, chapter.id)?.takeIf { it.isNotEmpty() }?.let { files ->
            return files.mapIndexed { i, f -> ReaderItem.Page(chapter, i, files.size, f) }
        }
        return try {
            val sChapter = with(repo) { chapter.toSChapter() }
            val pages = Graph.sources.call(manga.sourceId) { it.pages(sChapter) }
            if (pages.isEmpty()) return listOf(ReaderItem.Failed(chapter, "This chapter has no pages."))
            pages.mapIndexed { i, p -> ReaderItem.Page(chapter, i, pages.size, PageModel(manga.sourceId, p.url, p.imageUrl, i)) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            loaded -= chapter.id
            listOf(ReaderItem.Failed(chapter, SourceManager.describe(e)))
        }
    }

    /** Called as the visible item changes. */
    fun onPosition(index: Int) {
        val items = _state.value.items
        val item = items.getOrNull(index) ?: return
        if (item is ReaderItem.Page) {
            _state.update { it.copy(current = item.chapter, page = item.index, pageCount = item.total) }
            prefetch(index)
            saveProgress(item)
            // Getting close to the end: line up the next chapter.
            if (item.index >= item.total - 3) appendNext(item.chapter)
        } else if (item is ReaderItem.Transition && item.forward && item.from != null) {
            markFinished(item.from)
            appendNext(item.from)
        }
    }

    private fun prefetch(index: Int) {
        val loader = SingletonImageLoader.get(getApplication())
        _state.value.items.drop(index + 1).take(PREFETCH).filterIsInstance<ReaderItem.Page>().forEach { p ->
            loader.enqueue(ImageRequest.Builder(getApplication()).data(p.model).build())
        }
    }

    private fun saveProgress(page: ReaderItem.Page) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(350)
            val finished = page.index == page.total - 1
            repo.saveProgress(page.chapter.id, page.index, page.total, finished)
            if (finished) onFinished(page.chapter)
        }
    }

    private fun markFinished(chapter: ChapterEntity) {
        viewModelScope.launch {
            repo.saveProgress(chapter.id, (chapter.pageCount - 1).coerceAtLeast(0), chapter.pageCount, true)
            onFinished(chapter)
        }
    }

    private suspend fun onFinished(chapter: ChapterEntity) {
        if (Graph.settings.current.deleteAfterRead && chapter.id in Graph.downloads.downloaded.value) {
            Graph.downloads.delete(mangaId, listOf(chapter.id))
        }
        // Keep the next few chapters ready offline.
        Graph.downloads.topUp(mangaId)
    }

    /** Appends the chapter after [chapter] to the end of the reader, once. */
    private fun appendNext(chapter: ChapterEntity) {
        val next = next(chapter) ?: return
        if (next.id in loaded || appendJob?.isActive == true) return
        appendJob = viewModelScope.launch {
            val pages = loadPages(next)
            if (pages.firstOrNull() !is ReaderItem.Page) return@launch
            _state.update { s ->
                // Replace the trailing transition (it stays, pointing at this chapter) and add the new pages after it.
                s.copy(items = s.items + pages + ReaderItem.Transition(next, next(next), forward = true))
            }
        }
    }

    /** Jumps within the current chapter (from the scrubber). */
    fun jumpToPage(page: Int) {
        val s = _state.value
        val chapterId = s.current?.id ?: return
        val index = s.items.indexOfFirst { it is ReaderItem.Page && it.chapter.id == chapterId && it.index == page }
        if (index >= 0) _state.update { it.copy(startIndex = index, resetToken = it.resetToken + 1, page = page) }
    }

    fun retry(chapter: ChapterEntity) = viewModelScope.launch { open(chapter.id) }

    fun goTo(chapter: ChapterEntity) = viewModelScope.launch { open(chapter.id) }

    fun nextChapter() = _state.value.current?.let { next(it) }?.let { goTo(it) }
    fun previousChapter() = _state.value.current?.let { previous(it) }?.let { goTo(it) }
    val hasNext: Boolean get() = _state.value.current?.let { next(it) } != null
    val hasPrevious: Boolean get() = _state.value.current?.let { previous(it) } != null

    fun setTitleMode(mode: ReaderMode) {
        val manga = _state.value.manga ?: return
        viewModelScope.launch { repo.setReaderMode(manga.id, mode) }
        _state.update { it.copy(titleMode = mode, mode = resolveMode(mode, manga)) }
        if (mode == ReaderMode.AUTO) refineAuto(_state.value.items)
    }

    fun toggleBookmark() {
        val current = _state.value.current ?: return
        viewModelScope.launch {
            repo.setBookmark(listOf(current.id), !current.bookmark)
            val updated = current.copy(bookmark = !current.bookmark)
            order = order.map { if (it.id == updated.id) updated else it }
            _state.update { it.copy(current = updated) }
        }
    }

    /** Reads this chapter from MangaDex instead: finds the title and the same chapter number there. */
    fun readOnFallback(chapter: ChapterEntity, onSwitched: (Long, Long) -> Unit) {
        val manga = _state.value.manga ?: return
        if (manga.sourceId == MangaDexSource.ID) return
        _state.update { it.copy(fallbackBusy = true, message = null) }
        viewModelScope.launch {
            val result = runCatching {
                val match = repo.findOnFallback(manga.title) ?: error("Couldn't find “${manga.title}” on MangaDex")
                val id = repo.remember(MangaDexSource.ID, match)
                repo.refresh(id)
                val target = repo.chaptersOf(id).filter { it.number >= 0 && it.number == chapter.number }.minByOrNull { it.sourceOrder }
                    ?: error("MangaDex doesn't have chapter ${chapter.number.toString().removeSuffix(".0")} in this language")
                id to target.id
            }
            _state.update { it.copy(fallbackBusy = false, message = result.exceptionOrNull()?.message) }
            result.getOrNull()?.let { (m, c) ->
                mangaId = m
                onSwitched(m, c)
                open(c)
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun resolveMode(titleMode: ReaderMode, manga: MangaEntity): ReaderMode {
        val mode = if (titleMode != ReaderMode.AUTO) titleMode else Graph.settings.current.readerMode
        if (mode != ReaderMode.AUTO) return mode
        val genres = manga.genres.orEmpty().lowercase()
        return if (WEBTOON_HINTS.any { it in genres }) ReaderMode.WEBTOON else ReaderMode.RIGHT_TO_LEFT
    }

    /** With Automatic, very tall pages mean a webtoon even when the genres don't say so. */
    private fun refineAuto(items: List<ReaderItem>) {
        if (_state.value.mode == ReaderMode.WEBTOON) return
        val file = items.filterIsInstance<ReaderItem.Page>().firstOrNull()?.model as? File ?: return
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth > 0 && opts.outHeight / opts.outWidth.toFloat() > 2.4f) _state.update { it.copy(mode = ReaderMode.WEBTOON) }
    }

    /** Lets the screen report a tall first page when it loads from the network. */
    fun reportAspect(ratio: Float) {
        val s = _state.value
        if (s.titleMode == ReaderMode.AUTO && Graph.settings.current.readerMode == ReaderMode.AUTO && s.mode != ReaderMode.WEBTOON && ratio > 2.4f) {
            _state.update { it.copy(mode = ReaderMode.WEBTOON) }
        }
    }

    companion object {
        private const val PREFETCH = 4
        private val WEBTOON_HINTS = listOf("webtoon", "manhwa", "manhua", "long strip", "full color", "full colour")
    }
}
