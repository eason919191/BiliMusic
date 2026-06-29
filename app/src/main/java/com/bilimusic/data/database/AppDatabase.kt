package com.bilimusic.data.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bilimusic.data.model.*

@Database(
    entities = [
        Music::class,
        Playlist::class,
        PlaylistSong::class,
        SearchHistory::class,
        DownloadTask::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun musicDao(): MusicDao

    companion object {
        private const val TAG = "AppDatabase"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migration from v1 to v2:
         * Original schema had the same 5 tables (music, playlist, playlist_song,
         * search_history, download_task). This migration preserves existing data.
         */
        private val MIGRATION_1_2 = Migration(1, 2) { database ->
            Log.w(TAG, "Migration: 1 → 2 — no schema changes detected")
            // Tables unchanged; this migration is a no-op placeholder.
            // If a future migration is needed, chain additional Migration objects.
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bilimusic.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    // Only fall back to destructive if no migration path exists.
                    // For future schema changes, add a new Migration instead.
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                            super.onDestructiveMigration(db)
                            Log.w(TAG, "Destructive migration triggered! User data lost.")
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
