package dev.pridgey.folio.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaDao {
    @Query("SELECT * FROM manga WHERE id = :id")
    suspend fun get(id: Long): MangaEntity?

    @Query("SELECT * FROM manga WHERE id = :id")
    fun observe(id: Long): Flow<MangaEntity?>

    @Query("SELECT * FROM manga WHERE sourceId = :sourceId AND url = :url")
    suspend fun find(sourceId: Long, url: String): MangaEntity?

    @Query("SELECT * FROM manga WHERE favorite = 1")
    suspend fun favorites(): List<MangaEntity>

    @Query("SELECT sourceId || ':' || url FROM manga WHERE favorite = 1")
    fun favoriteKeys(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(manga: MangaEntity): Long

    @Update
    suspend fun update(manga: MangaEntity)

    @Query("UPDATE manga SET favorite = :favorite, dateAdded = :dateAdded WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, dateAdded: Long)

    @Query("UPDATE manga SET readerMode = :mode WHERE id = :id")
    suspend fun setReaderMode(id: Long, mode: Int)

    @Query("UPDATE manga SET readingStatus = :status WHERE id = :id")
    suspend fun setReadingStatus(id: Long, status: Int)

    @Query("SELECT * FROM manga WHERE favorite = 1 AND (author LIKE '%' || :name || '%' OR artist LIKE '%' || :name || '%')")
    fun byPerson(name: String): Flow<List<MangaEntity>>

    @Query("SELECT genres FROM manga WHERE favorite = 1 AND genres IS NOT NULL")
    suspend fun libraryGenres(): List<String>

    @Query("UPDATE manga SET sortDescending = :descending WHERE id = :id")
    suspend fun setSortDescending(id: Long, descending: Boolean)

    /** Adds every title with a finished chapter to the library. */
    @Query("UPDATE manga SET favorite = 1, dateAdded = :now WHERE favorite = 0 AND id IN (SELECT mangaId FROM chapter WHERE read = 1)")
    suspend fun addFinished(now: Long): Int

    @Query("DELETE FROM manga WHERE favorite = 0 AND id NOT IN (SELECT mangaId FROM chapter WHERE lastReadAt > 0) AND id NOT IN (SELECT mangaId FROM download_queue)")
    suspend fun pruneUnused()

    @Query(
        """
        SELECT m.id, m.sourceId, m.title, m.thumbnailUrl, m.lastUpdate, m.dateAdded, m.genres, m.readingStatus,
            COALESCE(SUM(CASE WHEN c.read = 0 THEN 1 ELSE 0 END), 0) AS unread,
            COUNT(c.id) AS total,
            COALESCE(MAX(c.lastReadAt), 0) AS lastReadAt,
            COALESCE(MAX(c.dateUpload), 0) AS latestUpload
        FROM manga m LEFT JOIN chapter c ON c.mangaId = m.id
        WHERE m.favorite = 1
        GROUP BY m.id
        """,
    )
    fun library(): Flow<List<LibraryItem>>
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapter WHERE id = :id")
    suspend fun get(id: Long): ChapterEntity?

    @Query("SELECT * FROM chapter WHERE mangaId = :mangaId ORDER BY sourceOrder ASC")
    suspend fun forManga(mangaId: Long): List<ChapterEntity>

    @Query("SELECT * FROM chapter WHERE mangaId = :mangaId ORDER BY sourceOrder ASC")
    fun observeForManga(mangaId: Long): Flow<List<ChapterEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(chapters: List<ChapterEntity>): List<Long>

    @Update
    suspend fun updateAll(chapters: List<ChapterEntity>)

    @Query("DELETE FROM chapter WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("UPDATE chapter SET lastPageRead = :page, pageCount = :pageCount, read = CASE WHEN :read THEN 1 ELSE read END, lastReadAt = :at WHERE id = :id")
    suspend fun saveProgress(id: Long, page: Int, pageCount: Int, read: Boolean, at: Long)

    @Query("UPDATE chapter SET read = :read, lastPageRead = CASE WHEN :read THEN lastPageRead ELSE 0 END WHERE id IN (:ids)")
    suspend fun setRead(ids: List<Long>, read: Boolean)

    @Query("UPDATE chapter SET read = 0, lastPageRead = 0, lastReadAt = 0 WHERE id IN (:ids)")
    suspend fun resetProgress(ids: List<Long>)

    @Query("UPDATE chapter SET bookmark = :bookmark WHERE id IN (:ids)")
    suspend fun setBookmark(ids: List<Long>, bookmark: Boolean)

    @Query("UPDATE chapter SET lastReadAt = 0 WHERE mangaId = :mangaId")
    suspend fun clearHistory(mangaId: Long)

    @Query("UPDATE chapter SET lastReadAt = 0")
    suspend fun clearAllHistory()

    /** The most recently read chapter of each title. */
    @Query(
        """
        SELECT m.id AS mangaId, m.sourceId, m.title, m.thumbnailUrl, c.id AS chapterId, c.name AS chapterName,
            c.number AS chapterNumber, c.lastPageRead, c.pageCount, c.read, c.lastReadAt
        FROM chapter c JOIN manga m ON m.id = c.mangaId
        WHERE c.lastReadAt > 0 AND c.lastReadAt = (SELECT MAX(c2.lastReadAt) FROM chapter c2 WHERE c2.mangaId = c.mangaId)
        ORDER BY c.lastReadAt DESC
        LIMIT :limit
        """,
    )
    fun recentlyRead(limit: Int): Flow<List<ReadingEntry>>

    @Query(
        """
        SELECT m.id AS mangaId, m.sourceId, m.title, m.thumbnailUrl, c.id AS chapterId, c.name AS chapterName,
            c.scanlator, c.read, c.lastPageRead, c.dateFetch, c.dateUpload
        FROM chapter c JOIN manga m ON m.id = c.mangaId
        WHERE m.favorite = 1 AND c.dateFetch > m.dateAdded + 60000
        ORDER BY c.dateFetch DESC, c.sourceOrder ASC
        LIMIT 400
        """,
    )
    fun updates(): Flow<List<UpdateEntry>>

    @Query("SELECT COUNT(*) FROM chapter c JOIN manga m ON m.id = c.mangaId WHERE m.favorite = 1 AND c.read = 0 AND c.dateFetch > :since AND c.dateFetch > m.dateAdded + 60000")
    fun newSince(since: Long): Flow<Int>
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_queue ORDER BY addedAt ASC, id ASC")
    fun observe(): Flow<List<QueuedDownload>>

    @Query("SELECT * FROM download_queue ORDER BY addedAt ASC, id ASC LIMIT 1")
    suspend fun next(): QueuedDownload?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(items: List<QueuedDownload>)

    @Query("DELETE FROM download_queue WHERE chapterId IN (:chapterIds)")
    suspend fun remove(chapterIds: List<Long>)

    @Query("DELETE FROM download_queue")
    suspend fun clear()
}

@Dao
interface ShelfDao {
    @Query("SELECT * FROM shelf ORDER BY position ASC, id ASC")
    fun observe(): Flow<List<ShelfEntity>>

    @Query("SELECT * FROM shelf ORDER BY position ASC, id ASC")
    suspend fun all(): List<ShelfEntity>

    @Query("SELECT * FROM shelf_manga")
    fun observeMembers(): Flow<List<ShelfManga>>

    @Query("SELECT shelfId FROM shelf_manga WHERE mangaId = :mangaId")
    fun observeFor(mangaId: Long): Flow<List<Long>>

    @Query("SELECT shelfId FROM shelf_manga WHERE mangaId = :mangaId")
    suspend fun shelvesOf(mangaId: Long): List<Long>

    @Insert
    suspend fun insert(shelf: ShelfEntity): Long

    @Query("UPDATE shelf SET name = :name, hidden = :hidden WHERE id = :id")
    suspend fun edit(id: Long, name: String, hidden: Boolean)

    @Query("SELECT DISTINCT sm.mangaId FROM shelf_manga sm JOIN shelf s ON s.id = sm.shelfId WHERE s.hidden = 1")
    fun hiddenTitles(): Flow<List<Long>>

    @Query("DELETE FROM shelf WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM shelf")
    suspend fun nextPosition(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(items: List<ShelfManga>)

    @Query("DELETE FROM shelf_manga WHERE shelfId = :shelfId AND mangaId = :mangaId")
    suspend fun remove(shelfId: Long, mangaId: Long)
}
