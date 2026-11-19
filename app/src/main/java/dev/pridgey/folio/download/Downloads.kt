package dev.pridgey.folio.download

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import dev.pridgey.folio.data.FolioDatabase
import dev.pridgey.folio.data.LibraryRepository
import dev.pridgey.folio.data.QueuedDownload
import dev.pridgey.folio.source.SourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Offline chapters. Pages are saved under `files/downloads/<title>/<chapter>/`, and a `.done` marker
 * says the chapter is complete. The queue lives in the database so it survives restarts.
 */
class Downloads(
    private val context: Context,
    private val db: FolioDatabase,
    private val sources: SourceManager,
    private val repo: LibraryRepository,
) {
    data class Progress(val chapterId: Long, val done: Int, val total: Int)

    private val root = File(context.filesDir, "downloads")
    private val _downloaded = MutableStateFlow(scan())
    /** Chapters that are fully downloaded. */
    val downloaded: StateFlow<Set<Long>> = _downloaded.asStateFlow()

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    val queue: Flow<List<QueuedDownload>> = db.downloads().observe()

    private fun scan(): Set<Long> = root.listFiles().orEmpty().flatMap { m ->
        m.listFiles().orEmpty().filter { File(it, DONE).exists() }.mapNotNull { it.name.toLongOrNull() }
    }.toSet()

    private fun dir(mangaId: Long, chapterId: Long) = File(root, "$mangaId/$chapterId")

    /** The saved pages of a chapter, in order, or null if it isn't downloaded. */
    fun pages(mangaId: Long, chapterId: Long): List<File>? {
        val d = dir(mangaId, chapterId)
        if (!File(d, DONE).exists()) return null
        return d.listFiles().orEmpty().filter { it.name != DONE }.sortedBy { it.name }
    }

    suspend fun enqueue(mangaId: Long, chapterIds: List<Long>) {
        val todo = chapterIds.filter { it !in _downloaded.value }
        if (todo.isEmpty()) return
        val now = System.currentTimeMillis()
        db.downloads().enqueue(todo.mapIndexed { i, id -> QueuedDownload(chapterId = id, mangaId = mangaId, addedAt = now + i) })
        start()
    }

    fun start() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DownloadWorker>().setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build(),
        )
    }

    suspend fun cancel(chapterIds: List<Long>) = db.downloads().remove(chapterIds)

    suspend fun cancelAll() {
        db.downloads().clear()
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        _progress.value = null
    }

    suspend fun delete(mangaId: Long, chapterIds: List<Long>) = withContext(Dispatchers.IO) {
        chapterIds.forEach { dir(mangaId, it).deleteRecursively() }
        _downloaded.update { it - chapterIds.toSet() }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        root.deleteRecursively()
        _downloaded.value = emptySet()
    }

    fun sizeBytes(): Long = root.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    /** Space used by one title's downloads. */
    data class Usage(val mangaId: Long, val bytes: Long, val chapters: Int)

    suspend fun usage(): List<Usage> = withContext(Dispatchers.IO) {
        root.listFiles().orEmpty().mapNotNull { m ->
            val id = m.name.toLongOrNull() ?: return@mapNotNull null
            val chapters = m.listFiles().orEmpty().filter { it.isDirectory }
            Usage(id, m.walkBottomUp().filter { it.isFile }.sumOf { it.length() }, chapters.count { File(it, DONE).exists() })
        }.filter { it.bytes > 0 }.sortedByDescending { it.bytes }
    }

    /** Deletes downloads of chapters already read; for one title, or all of them. Returns how many went. */
    suspend fun deleteRead(mangaId: Long? = null): Int {
        val ids = root.listFiles().orEmpty().mapNotNull { it.name.toLongOrNull() }.filter { mangaId == null || it == mangaId }
        var n = 0
        ids.forEach { m ->
            val read = repo.chaptersOf(m).filter { it.read && it.id in _downloaded.value }.map { it.id }
            if (read.isNotEmpty()) {
                delete(m, read)
                n += read.size
            }
        }
        return n
    }

    /** Deletes everything downloaded for a title, including half-finished chapters. */
    suspend fun deleteTitle(mangaId: Long) = withContext(Dispatchers.IO) {
        val d = File(root, mangaId.toString())
        val ids = d.listFiles().orEmpty().mapNotNull { it.name.toLongOrNull() }.toSet()
        d.deleteRecursively()
        _downloaded.update { it - ids }
    }

    /**
     * Smart downloads: keeps the next [ahead] unread chapters (from where the reader is) downloaded.
     * Does nothing when the setting is off.
     */
    suspend fun topUp(mangaId: Long, ahead: Int = settingsAhead()) {
        if (ahead <= 0) return
        val chapters = repo.chaptersOf(mangaId)
        if (chapters.none { it.read || it.lastReadAt > 0 }) return // Only titles you've started.
        val ordered = LibraryRepository.readingOrder(chapters)
        val from = repo.nextToRead(chapters)?.let { n -> ordered.indexOfFirst { it.id == n.id } } ?: return
        val want = ordered.drop(from.coerceAtLeast(0)).filter { !it.read }.take(ahead).map { it.id }
        enqueue(mangaId, want)
    }

    private fun settingsAhead() = dev.pridgey.folio.Graph.settings.current.downloadAhead

    /** Downloads the next queued chapter. Returns false when the queue is empty. */
    internal suspend fun downloadNext(onProgress: (Progress, String) -> Unit): Boolean {
        val item = db.downloads().next() ?: return false
        val chapter = repo.chapter(item.chapterId)
        val manga = repo.get(item.mangaId)
        if (chapter == null || manga == null) {
            db.downloads().remove(listOf(item.chapterId))
            return true
        }
        val label = "${manga.displayTitle} · ${chapter.name}"
        val target = dir(manga.id, chapter.id)
        val sChapter = with(repo) { chapter.toSChapter() }
        try {
            val pages = Pacer.paced(manga.sourceId) { sources.call(manga.sourceId) { it.pages(sChapter) } }
            target.mkdirs()
            pages.forEachIndexed { i, page ->
                val file = File(target, "%04d".format(i))
                if (!file.exists() || file.length() == 0L) {
                    val tmp = File(target, "%04d.part".format(i))
                    Pacer.paced(manga.sourceId) {
                        sources.call(manga.sourceId) { src ->
                            src.image(page).use { res -> tmp.outputStream().use { res.body.byteStream().copyTo(it) } }
                        }
                    }
                    tmp.renameTo(file)
                }
                val p = Progress(chapter.id, i + 1, pages.size)
                _progress.value = p
                onProgress(p, label)
            }
            File(target, DONE).createNewFile()
            _downloaded.update { it + chapter.id }
            db.downloads().remove(listOf(item.chapterId))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Leave it queued but move it to the back so one bad chapter doesn't block the rest.
            db.downloads().remove(listOf(item.chapterId))
            db.downloads().enqueue(listOf(item.copy(id = 0, addedAt = System.currentTimeMillis() + FAILED_DELAY)))
            throw e
        } finally {
            _progress.value = null
        }
        return true
    }

    companion object {
        const val WORK = "downloads"
        private const val DONE = ".done"
        private const val FAILED_DELAY = 10L * 365 * 24 * 3600 * 1000
    }
}
