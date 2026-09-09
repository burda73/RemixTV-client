package com.remixtv.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.remixtv.data.models.PlaylistItem
import com.remixtv.data.models.Video

/**
 * Локальная БД. При изменении схемы:
 * 1) увеличьте [version]
 * 2) добавьте объект [Migration] в [MIGRATIONS]
 */
@Database(
    entities = [Video::class, PlaylistItem::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun playlistItemDao(): PlaylistItemDao

    companion object {
        private val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE playlist_items_new (rowId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, playlistItemId INTEGER NOT NULL, videoId INTEGER NOT NULL, `order` INTEGER NOT NULL, FOREIGN KEY(videoId) REFERENCES videos(id) ON DELETE CASCADE)")
                db.execSQL("INSERT INTO playlist_items_new (rowId, playlistItemId, videoId, `order`) SELECT rowId, playlistId AS playlistItemId, videoId, `order` FROM playlist_items")
                db.execSQL("DROP TABLE playlist_items")
                db.execSQL("ALTER TABLE playlist_items_new RENAME TO playlist_items")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_items_video_id ON playlist_items(video_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_items_playlist_item_id_order ON playlist_items(playlistItemId, `order`)")
            }
        }

        private val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2
        )

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "remixtv.db"
                )
                    .addMigrations(*MIGRATIONS)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
