package dev.pridgey.folio.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "manga", indices = [Index(value = ["sourceId", "url"], unique = true), Index("favorite")])
data class MangaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val url: String,
    val title: String,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genres: String? = null,
    val status: Int = 0,
    val thumbnailUrl: String? = null,
    /** Source-private JSON; returned to the source untouched. */
    val memo: String = "{}",
    val initialized: Boolean = false,
    val favorite: Boolean = false,
    val dateAdded: Long = 0,
    /** When the chapter list was last fetched. */
    val lastUpdate: Long = 0,
    /** Reader mode for this title, or 0 to use the default. */
    val readerMode: Int = 0,
    /** Newest first (true) or oldest first in the chapter list. */
    val sortDescending: Boolean = true,
    /** A [ReadingStatus] ordinal. */
    @ColumnInfo(defaultValue = "0") val readingStatus: Int = 0,
    /** An English name the reader chose to see in place of the source's [title], or null to use that. */
    val englishTitle: String? = null,
) {
    /** The name to show: the English title when one is set. */
    val displayTitle: String get() = englishTitle ?: title
}

/** Where a title stands for the reader. Stored by ordinal, so only ever add to the end. */
enum class ReadingStatus(val label: String) {
    READING("Reading"), PLAN_TO_READ("Plan to Read"), ON_HOLD("On Hold"), COMPLETED("Completed"), DROPPED("Dropped");

    companion object {
        fun of(ordinal: Int) = entries.getOrElse(ordinal) { READING }
    }
}

/** A collection the reader names and fills. */
@Entity(tableName = "shelf")
data class ShelfEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int = 0,
    /** A private shelf: its titles stay out of the library, history and updates, and open with the phone's lock. */
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
)

@Entity(
    tableName = "shelf_manga",
    primaryKeys = ["shelfId", "mangaId"],
    indices = [Index("mangaId")],
    foreignKeys = [
        ForeignKey(ShelfEntity::class, ["id"], ["shelfId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(MangaEntity::class, ["id"], ["mangaId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ShelfManga(val shelfId: Long, val mangaId: Long)

@Entity(
    tableName = "chapter",
    indices = [Index(value = ["mangaId", "url"], unique = true), Index("lastReadAt"), Index("dateFetch")],
    foreignKeys = [ForeignKey(MangaEntity::class, ["id"], ["mangaId"], onDelete = ForeignKey.CASCADE)],
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mangaId: Long,
    val url: String,
    val name: String,
    val number: Float = -1f,
    val scanlator: String? = null,
    val dateUpload: Long = 0,
    /** Position in the source's list (0 = what the source lists first, usually the newest). */
    val sourceOrder: Int = 0,
    val read: Boolean = false,
    val bookmark: Boolean = false,
    val lastPageRead: Int = 0,
    val pageCount: Int = 0,
    val lastReadAt: Long = 0,
    /** When Folio first saw this chapter. */
    val dateFetch: Long = 0,
    val memo: String = "{}",
)

@Entity(tableName = "download_queue", indices = [Index("chapterId", unique = true)])
data class QueuedDownload(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chapterId: Long,
    val mangaId: Long,
    val addedAt: Long,
)

/** A library tile. [title] is the name to show; [sourceTitle] is the source's own, which keys spine art. */
data class LibraryItem(
    val id: Long,
    val sourceId: Long,
    val title: String,
    val sourceTitle: String,
    val thumbnailUrl: String?,
    val unread: Int,
    val total: Int,
    val lastReadAt: Long,
    val latestUpload: Long,
    val lastUpdate: Long,
    val dateAdded: Long,
    val genres: String?,
    val readingStatus: Int = 0,
)

/** A row in "Continue reading" and History. */
data class ReadingEntry(
    val mangaId: Long,
    val sourceId: Long,
    val title: String,
    val thumbnailUrl: String?,
    val chapterId: Long,
    val chapterName: String,
    val chapterNumber: Float,
    val lastPageRead: Int,
    val pageCount: Int,
    val read: Boolean,
    val lastReadAt: Long,
)

/** A new chapter in Updates. */
data class UpdateEntry(
    val mangaId: Long,
    val sourceId: Long,
    val title: String,
    val thumbnailUrl: String?,
    val chapterId: Long,
    val chapterName: String,
    val scanlator: String?,
    val read: Boolean,
    val lastPageRead: Int,
    val dateFetch: Long,
    val dateUpload: Long,
)

data class ChapterCount(@ColumnInfo(name = "mangaId") val mangaId: Long, val unread: Int)
