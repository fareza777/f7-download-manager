package com.f7.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DownloadEntity::class,
        SegmentEntity::class,
        ScheduleEntity::class,
        DownloadEventEntity::class,
        BrowserHistoryEntity::class,
        BrowserBookmarkEntity::class,
    ],
    version = 3,
    exportSchema = true
)
abstract class F7Database : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun statsDao(): StatsDao
    abstract fun browserDao(): BrowserDao

    companion object {
        @Volatile
        private var instance: F7Database? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `download_events` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`downloadId` INTEGER NOT NULL, `fileName` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, `bytes` INTEGER NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL, `averageSpeed` INTEGER NOT NULL, " +
                        "`success` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_download_events_createdAt` ON `download_events` (`createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_download_events_success` ON `download_events` (`success`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `browser_history` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url` TEXT NOT NULL, " +
                        "`title` TEXT, `visitedAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_browser_history_visitedAt` ON `browser_history` (`visitedAt`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `browser_bookmarks` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url` TEXT NOT NULL, " +
                        "`title` TEXT, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_browser_bookmarks_url` ON `browser_bookmarks` (`url`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `startedAt` INTEGER")
            }
        }

        fun get(context: Context): F7Database =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    F7Database::class.java,
                    "f7_downloads.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}

