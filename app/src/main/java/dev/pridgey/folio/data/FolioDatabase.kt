package dev.pridgey.folio.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MangaEntity::class, ChapterEntity::class, QueuedDownload::class, ShelfEntity::class, ShelfManga::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class FolioDatabase : RoomDatabase() {
    abstract fun manga(): MangaDao
    abstract fun chapters(): ChapterDao
    abstract fun downloads(): DownloadDao
    abstract fun shelves(): ShelfDao

    companion object {
        fun create(context: Context): FolioDatabase =
            Room.databaseBuilder(context, FolioDatabase::class.java, "folio.db").build()
    }
}
