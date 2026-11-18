package dev.pridgey.folio.data

import androidx.room.withTransaction
import dev.pridgey.folio.source.MangaDexSource
import dev.pridgey.folio.source.SourceManager
import dev.pridgey.folio.source.TitleMatcher
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Library, chapters and reading progress, kept in sync with sources. */
class LibraryRepository(
    private val db: FolioDatabase,
    private val sources: SourceManager,
    private val json: Json,
) {
    private val manga = db.manga()
    private val chapters = db.chapters()
    private val shelfDao = db.shelves()

    fun library(): Flow<List<LibraryItem>> = manga.library()
    fun favoriteKeys(): Flow<List<String>> = manga.favoriteKeys()
    fun observe(id: Long): Flow<MangaEntity?> = manga.observe(id)
    fun observeChapters(id: Long): Flow<List<ChapterEntity>> = chapters.observeForManga(id)
    /** Titles on private shelves. */
    fun hiddenTitles(): Flow<Set<Long>> = shelfDao.hiddenTitles().map { it.toSet() }

    // Reading on a private shelf is incognito: it never shows in history, Continue reading or Updates.
    fun recentlyRead(limit: Int = 200): Flow<List<ReadingEntry>> =
        combine(chapters.recentlyRead(limit), hiddenTitles()) { list, hidden -> list.filter { it.mangaId !in hidden } }
    fun updates(): Flow<List<UpdateEntry>> =
        combine(chapters.updates(), hiddenTitles()) { list, hidden -> list.filter { it.mangaId !in hidden } }
    fun newSince(since: Long): Flow<Int> = chapters.newSince(since)

    suspend fun get(id: Long) = manga.get(id)
    suspend fun chapter(id: Long) = chapters.get(id)
    suspend fun chaptersOf(id: Long) = chapters.forManga(id)

    /** Saves a title seen while browsing (or finds the copy already saved) and returns its id. */
    suspend fun remember(sourceId: Long, s: SManga): Long {
        manga.find(sourceId, s.url)?.let { existing ->
            if (existing.thumbnailUrl == null && s.thumbnail_url != null) manga.update(existing.copy(thumbnailUrl = s.thumbnail_url))
            return existing.id
        }
        return manga.insert(s.toEntity(sourceId))
    }

    /** Fetches details and chapters from the source. Returns how many chapters are new. */
    suspend fun refresh(mangaId: Long, details: Boolean = true): Int {
        val current = manga.get(mangaId) ?: return 0
        val existing = chapters.forManga(mangaId)
        val result = sources.call(current.sourceId) { src ->
            src.update(current.toSManga(), existing.map { it.toSChapter() }, details || !current.initialized, true)
        }
        return db.withTransaction {
            val now = System.currentTimeMillis()
            val m = result.manga
            manga.update(
                current.copy(
                    title = m.title.ifBlank { current.title },
                    author = m.author ?: current.author,
                    artist = m.artist ?: current.artist,
                    description = m.description ?: current.description,
                    genres = m.genre ?: current.genres,
                    status = if (m.status != 0) m.status else current.status,
                    thumbnailUrl = m.thumbnail_url ?: current.thumbnailUrl,
                    memo = json.encodeToString(JsonObject.serializer(), m.memo).takeIf { m.memo.isNotEmpty() } ?: current.memo,
                    initialized = true,
                    lastUpdate = now,
                ),
            )
            syncChapters(mangaId, existing, result.chapters, now)
        }
    }

    private suspend fun syncChapters(mangaId: Long, existing: List<ChapterEntity>, fetched: List<SChapter>, now: Long): Int {
        val byUrl = existing.associateBy { it.url }
        val seen = HashSet<String>()
        val inserts = mutableListOf<ChapterEntity>()
        val updates = mutableListOf<ChapterEntity>()
        // The first fetch isn't "new chapters"; date them before the title was added.
        val firstFetch = existing.isEmpty()
        fetched.forEachIndexed { index, c ->
            if (!seen.add(c.url)) return@forEachIndexed
            val number = if (c.chapter_number >= 0) c.chapter_number else ChapterNumbers.parse(c.name)
            val memo = if (c.memo.isEmpty()) "{}" else json.encodeToString(JsonObject.serializer(), c.memo)
            val old = byUrl[c.url]
            if (old == null) {
                inserts += ChapterEntity(
                    mangaId = mangaId, url = c.url, name = c.name, number = number, scanlator = c.scanlator,
                    dateUpload = c.date_upload, sourceOrder = index, dateFetch = if (firstFetch) 0 else now, memo = memo,
                )
            } else {
                val changed = old.copy(name = c.name, number = number, scanlator = c.scanlator, dateUpload = c.date_upload.takeIf { it > 0 } ?: old.dateUpload, sourceOrder = index, memo = memo)
                if (changed != old) updates += changed
            }
        }
        val removed = existing.filter { it.url !in seen }.map { it.id }
        if (removed.isNotEmpty() && fetched.isNotEmpty()) chapters.delete(removed)
        if (updates.isNotEmpty()) chapters.updateAll(updates)
        if (inserts.isNotEmpty()) chapters.insertAll(inserts)
        return if (firstFetch) 0 else inserts.size
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) = manga.setFavorite(id, favorite, if (favorite) System.currentTimeMillis() else 0)
    suspend fun setReaderMode(id: Long, mode: ReaderMode) = manga.setReaderMode(id, mode.ordinal)
    suspend fun setSortDescending(id: Long, descending: Boolean) = manga.setSortDescending(id, descending)
    suspend fun setRead(ids: List<Long>, read: Boolean) = chapters.setRead(ids, read)
    suspend fun setBookmark(ids: List<Long>, bookmark: Boolean) = chapters.setBookmark(ids, bookmark)
    suspend fun clearHistory(mangaId: Long) = chapters.clearHistory(mangaId)
    suspend fun clearAllHistory() = chapters.clearAllHistory()
    suspend fun saveProgress(chapterId: Long, page: Int, pageCount: Int, finished: Boolean) =
        chapters.saveProgress(chapterId, page, pageCount, finished, System.currentTimeMillis())

    /** Marks every chapter up to and including [chapterId] (in reading order) as read. */
    suspend fun markPreviousRead(mangaId: Long, chapterId: Long) {
        val list = readingOrder(chapters.forManga(mangaId))
        val idx = list.indexOfFirst { it.id == chapterId }
        if (idx >= 0) chapters.setRead(list.take(idx + 1).map { it.id }, true)
    }

    /**
     * Makes [chapterId] the place to continue from: everything before it (in reading order) is read,
     * and it and everything after start fresh.
     */
    suspend fun continueFrom(mangaId: Long, chapterId: Long) {
        val list = readingOrder(chapters.forManga(mangaId))
        val idx = list.indexOfFirst { it.id == chapterId }
        if (idx < 0) return
        list.take(idx).map { it.id }.chunked(500).forEach { chapters.setRead(it, true) }
        list.drop(idx).map { it.id }.chunked(500).forEach { chapters.resetProgress(it) }
    }

    /** The chapter to open when the user taps Read/Continue. */
    fun nextToRead(list: List<ChapterEntity>): ChapterEntity? {
        val ordered = readingOrder(list)
        val inProgress = ordered.filter { !it.read && it.lastReadAt > 0 }.maxByOrNull { it.lastReadAt }
        if (inProgress != null) return inProgress
        val lastRead = ordered.indexOfLast { it.read }
        return ordered.getOrNull(lastRead + 1) ?: ordered.firstOrNull { !it.read } ?: ordered.lastOrNull()
    }

    suspend fun pruneUnused() = manga.pruneUnused()

    /** Adds titles you've finished a chapter of, as reading one does from now on. */
    suspend fun addFinished(): Int = manga.addFinished(System.currentTimeMillis())

    // ------------------------------------------------------------------ Status and shelves

    suspend fun setReadingStatus(id: Long, status: ReadingStatus) = manga.setReadingStatus(id, status.ordinal)
    fun byPerson(name: String): Flow<List<MangaEntity>> = manga.byPerson(name)

    /** How often each genre appears across the library, most common first. */
    suspend fun genreWeights(): List<Pair<String, Int>> =
        manga.libraryGenres().flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }
            .groupingBy { it.lowercase() }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }

    fun shelves(): Flow<List<ShelfEntity>> = shelfDao.observe()
    fun shelfMembers(): Flow<List<ShelfManga>> = shelfDao.observeMembers()
    fun shelvesOf(mangaId: Long): Flow<List<Long>> = shelfDao.observeFor(mangaId)
    suspend fun createShelf(name: String, hidden: Boolean = false): Long =
        shelfDao.insert(ShelfEntity(name = name.trim(), position = shelfDao.nextPosition(), hidden = hidden))
    suspend fun editShelf(id: Long, name: String, hidden: Boolean) = shelfDao.edit(id, name.trim(), hidden)
    suspend fun deleteShelf(id: Long) = shelfDao.delete(id)
    suspend fun addToShelf(shelfId: Long, mangaIds: Collection<Long>) = shelfDao.add(mangaIds.map { ShelfManga(shelfId, it) })
    suspend fun removeFromShelf(shelfId: Long, mangaId: Long) = shelfDao.remove(shelfId, mangaId)

    // ------------------------------------------------------------------ Moving between sources

    /** Finds candidates for [title] on [sourceId], best match first. */
    suspend fun findOn(sourceId: Long, title: String): List<SManga> {
        val found = sources.search(sourceId, title).mangas
        return found.sortedByDescending { TitleMatcher.similarity(title, it.title) }
    }

    /**
     * Moves a title to [target] on [sourceId]: carries over read chapters and bookmarks by chapter
     * number, plus library membership, reading status, shelves and reader mode. Returns the new id.
     */
    suspend fun migrate(mangaId: Long, sourceId: Long, target: SManga): Long? {
        val old = manga.get(mangaId) ?: return null
        val newId = remember(sourceId, target)
        if (newId == mangaId) return mangaId
        refresh(newId)
        val oldChapters = chapters.forManga(mangaId).filter { it.number >= 0 }
        val read = oldChapters.filter { it.read }.map { it.number }.toSet()
        val marked = oldChapters.filter { it.bookmark }.map { it.number }.toSet()
        val lastRead = oldChapters.filter { it.lastReadAt > 0 }.associate { it.number to it }
        val moved = chapters.forManga(newId).map { ch ->
            val prior = lastRead[ch.number]
            ch.copy(
                read = ch.read || ch.number in read,
                bookmark = ch.bookmark || ch.number in marked,
                lastPageRead = prior?.lastPageRead ?: ch.lastPageRead,
                lastReadAt = maxOf(ch.lastReadAt, prior?.lastReadAt ?: 0),
            )
        }
        db.withTransaction {
            if (moved.isNotEmpty()) chapters.updateAll(moved)
            manga.get(newId)?.let { manga.update(it.copy(readingStatus = old.readingStatus, readerMode = old.readerMode)) }
            shelfDao.shelvesOf(mangaId).forEach { shelfDao.add(listOf(ShelfManga(it, newId))) }
            if (old.favorite) {
                manga.setFavorite(newId, true, old.dateAdded.takeIf { it > 0 } ?: System.currentTimeMillis())
                manga.setFavorite(mangaId, false, 0)
            }
        }
        return newId
    }

    /**
     * Moves a title to MangaDex: finds the same series there, adds it to the library and carries reading
     * progress over by chapter number. Returns the new title's id.
     */
    suspend fun moveToFallback(mangaId: Long): Long? {
        val old = manga.get(mangaId) ?: return null
        val match = findOnFallback(old.title) ?: return null
        return migrate(mangaId, MangaDexSource.ID, match)
    }

    /** Looks for [title] on MangaDex without changing anything. */
    suspend fun findOnFallback(title: String): SManga? {
        val dex = sources.mangaDex
        return TitleMatcher.best(title, dex.search(1, title, dex.filters()).mangas)
    }

    // ------------------------------------------------------------------ Backup

    @Serializable
    data class Backup(
        val version: Int = 1,
        val titles: List<BackupTitle>,
        val shelves: List<String> = emptyList(),
        val privateShelves: List<String> = emptyList(),
    )

    @Serializable
    data class BackupTitle(
        val sourceId: Long,
        val url: String,
        val title: String,
        val thumbnailUrl: String? = null,
        val readerMode: Int = 0,
        val dateAdded: Long = 0,
        val chapters: List<BackupChapter> = emptyList(),
        val readingStatus: Int = 0,
        /** Names of the shelves the title is on. */
        val shelves: List<String> = emptyList(),
    )

    @Serializable
    data class BackupChapter(val url: String, val read: Boolean, val lastPageRead: Int = 0, val bookmark: Boolean = false, val lastReadAt: Long = 0)

    suspend fun exportBackup(): String {
        val allShelves = shelfDao.all()
        val shelfNames = allShelves.associate { it.id to it.name }
        val titles = manga.favorites().map { m ->
            BackupTitle(
                m.sourceId, m.url, m.title, m.thumbnailUrl, m.readerMode, m.dateAdded,
                chapters.forManga(m.id).filter { it.read || it.bookmark || it.lastPageRead > 0 }
                    .map { BackupChapter(it.url, it.read, it.lastPageRead, it.bookmark, it.lastReadAt) },
                readingStatus = m.readingStatus,
                shelves = shelfDao.shelvesOf(m.id).mapNotNull { shelfNames[it] },
            )
        }
        return json.encodeToString(
            Backup.serializer(),
            Backup(titles = titles, shelves = shelfNames.values.toList(), privateShelves = allShelves.filter { it.hidden }.map { it.name }),
        )
    }

    /** Restores titles and progress. Chapters are matched once each title is refreshed. */
    suspend fun importBackup(text: String): Int {
        val backup = json.decodeFromString(Backup.serializer(), text)
        // Shelves match by name, so restoring twice doesn't double them up.
        val shelfIds = shelfDao.all().associate { it.name to it.id }.toMutableMap()
        (backup.shelves + backup.titles.flatMap { it.shelves }).distinct().forEach { name ->
            if (name !in shelfIds) shelfIds[name] = createShelf(name, hidden = name in backup.privateShelves)
        }
        var count = 0
        for (t in backup.titles) {
            val id = manga.find(t.sourceId, t.url)?.id ?: manga.insert(
                MangaEntity(sourceId = t.sourceId, url = t.url, title = t.title, thumbnailUrl = t.thumbnailUrl, readerMode = t.readerMode),
            )
            manga.setFavorite(id, true, t.dateAdded.takeIf { it > 0 } ?: System.currentTimeMillis())
            manga.setReadingStatus(id, t.readingStatus)
            t.shelves.mapNotNull { shelfIds[it] }.forEach { shelfDao.add(listOf(ShelfManga(it, id))) }
            runCatching { refresh(id) }
            val byUrl = chapters.forManga(id).associateBy { it.url }
            val restored = t.chapters.mapNotNull { b ->
                byUrl[b.url]?.copy(read = b.read, lastPageRead = b.lastPageRead, bookmark = b.bookmark, lastReadAt = b.lastReadAt)
            }
            if (restored.isNotEmpty()) chapters.updateAll(restored)
            count++
        }
        return count
    }

    // ------------------------------------------------------------------ Mapping

    fun MangaEntity.toSManga(): SManga = SManga.create().also {
        it.url = url
        it.title = title
        it.author = author
        it.artist = artist
        it.description = description
        it.genre = genres
        it.status = status
        it.thumbnail_url = thumbnailUrl
        it.memo = parseMemo(memo)
        it.initialized = initialized
    }

    fun ChapterEntity.toSChapter(): SChapter = SChapter.create().also {
        it.url = url
        it.name = name
        it.chapter_number = number
        it.scanlator = scanlator
        it.date_upload = dateUpload
        it.memo = parseMemo(memo)
    }

    private fun SManga.toEntity(sourceId: Long) = MangaEntity(
        sourceId = sourceId, url = url, title = title, author = author, artist = artist, description = description,
        genres = genre, status = status, thumbnailUrl = thumbnail_url,
        memo = if (memo.isEmpty()) "{}" else json.encodeToString(JsonObject.serializer(), memo),
        initialized = false,
    )

    private fun parseMemo(text: String): JsonObject =
        runCatching { json.decodeFromString(JsonObject.serializer(), text) }.getOrDefault(JsonObject(emptyMap()))

    companion object {
        /** Oldest chapter first. Sources list newest first, so this is the reverse of source order. */
        fun readingOrder(list: List<ChapterEntity>): List<ChapterEntity> =
            list.sortedWith(compareByDescending<ChapterEntity> { it.sourceOrder })
    }
}

/** Pulls a chapter number out of a title like "Vol. 2 Ch. 14.5 – The Return". */
object ChapterNumbers {
    private val patterns = listOf(
        Regex("""(?:ch(?:apter)?|ep(?:isode)?|#)\.?\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
        Regex("""(\d+(?:\.\d+)?)"""),
    )

    fun parse(name: String): Float {
        for (p in patterns) p.find(name)?.groupValues?.get(1)?.toFloatOrNull()?.let { return it }
        return -1f
    }

    fun format(number: Float): String = when {
        number < 0 -> ""
        number == number.toLong().toFloat() -> number.toLong().toString()
        else -> "%.1f".format(number).trimEnd('0').trimEnd('.')
    }
}
