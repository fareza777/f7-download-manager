package com.f7.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED','CONNECTING','DOWNLOADING','PAUSED','FAILED','VERIFYING') ORDER BY createdAt DESC")
    fun observeActiveAndQueued(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun observeById(id: Long): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE url = :url AND status != 'CANCELLED' LIMIT 1")
    suspend fun findByUrl(url: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED','CONNECTING','DOWNLOADING','PAUSED','FAILED','VERIFYING') ORDER BY priority DESC, createdAt ASC")
    suspend fun getQueue(): List<DownloadEntity>

    @Query("UPDATE downloads SET priority = :priority WHERE id = :id")
    suspend fun updatePriority(id: Long, priority: Int)

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' AND completedAt IS NOT NULL AND completedAt < :cutoff")
    suspend fun getCompletedBefore(cutoff: Long): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED','CONNECTING','DOWNLOADING','VERIFYING')")
    suspend fun getRunnable(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE status = 'PAUSED'")
    suspend fun getPaused(): List<DownloadEntity>

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    suspend fun getAll(): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity): Long

    @Update
    suspend fun update(download: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, error = :error, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, error: String?, now: Long)

    @Query(
        "UPDATE downloads SET downloadedBytes = :downloaded, totalBytes = :total, activeConnections = :active, " +
            "sizeIsExact = :exact, updatedAt = :now WHERE id = :id"
    )
    suspend fun updateProgress(
        id: Long,
        downloaded: Long,
        total: Long,
        active: Int,
        exact: Boolean,
        now: Long = System.currentTimeMillis()
    )

    @Query("UPDATE downloads SET attempts = :attempts, error = :error, updatedAt = :now WHERE id = :id")
    suspend fun updateAttempts(id: Long, attempts: Int, error: String?, now: Long)

    @Query("UPDATE downloads SET status = 'COMPLETED', completedAt = :completedAt, downloadedBytes = totalBytes, activeConnections = 0, error = NULL WHERE id = :id")
    suspend fun markCompleted(id: Long, completedAt: Long)

    @Query("UPDATE downloads SET savedUri = :uri, displayPath = :path WHERE id = :id")
    suspend fun updateLocation(id: Long, uri: String?, path: String?)

    @Query("UPDATE downloads SET checksum = :checksum, checksumType = :type WHERE id = :id")
    suspend fun updateChecksum(id: Long, checksum: String?, type: String?)

    @Query("UPDATE downloads SET fileName = :name WHERE id = :id")
    suspend fun updateFileName(id: Long, name: String)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads WHERE status = 'COMPLETED'")
    suspend fun deleteCompleted()

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'COMPLETED'")
    suspend fun completedCount(): Int

    @Query("SELECT COALESCE(SUM(downloadedBytes), 0) FROM downloads WHERE status = 'COMPLETED'")
    suspend fun totalCompletedBytes(): Long

    @Query("UPDATE downloads SET status = 'PAUSED', activeConnections = 0, updatedAt = :now WHERE status IN ('CONNECTING','DOWNLOADING','VERIFYING')")
    suspend fun reconcileInterrupted(now: Long)

    // Segments -----------------------------------------------------------------

    @Query("SELECT * FROM segments WHERE downloadId = :downloadId ORDER BY `index` ASC")
    suspend fun getSegments(downloadId: Long): List<SegmentEntity>

    @Query("SELECT * FROM segments WHERE downloadId = :downloadId ORDER BY `index` ASC")
    fun observeSegments(downloadId: Long): Flow<List<SegmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<SegmentEntity>)

    @Query("UPDATE segments SET downloaded = :downloaded WHERE downloadId = :downloadId AND `index` = :index")
    suspend fun updateSegment(downloadId: Long, index: Int, downloaded: Long)

    @Query("DELETE FROM segments WHERE downloadId = :downloadId")
    suspend fun deleteSegments(downloadId: Long)

    @Transaction
    suspend fun replaceSegments(downloadId: Long, segments: List<SegmentEntity>) {
        deleteSegments(downloadId)
        insertSegments(segments)
    }

    @Query("SELECT COALESCE(SUM(downloaded), 0) FROM segments WHERE downloadId = :downloadId")
    suspend fun sumSegmentBytes(downloadId: Long): Long
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY triggerAt ASC")
    fun observeAll(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules ORDER BY triggerAt ASC")
    suspend fun getAll(): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun getById(id: Long): ScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: ScheduleEntity): Long

    @Update
    suspend fun update(schedule: ScheduleEntity)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface StatsDao {
    @Query("SELECT * FROM download_events ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEventEntity>>

    @Query("SELECT * FROM download_events ORDER BY createdAt DESC")
    suspend fun getAll(): List<DownloadEventEntity>

    @Insert
    suspend fun insert(event: DownloadEventEntity): Long

    @Query("DELETE FROM download_events")
    suspend fun clear()
}

@Dao
interface BrowserDao {
    @Query("SELECT * FROM browser_history ORDER BY visitedAt DESC LIMIT 300")
    fun observeHistory(): Flow<List<BrowserHistoryEntity>>

    @Insert
    suspend fun insertHistory(entry: BrowserHistoryEntity): Long

    @Query("DELETE FROM browser_history WHERE id = :id")
    suspend fun deleteHistory(id: Long)

    @Query("DELETE FROM browser_history")
    suspend fun clearHistory()

    @Query("SELECT * FROM browser_bookmarks ORDER BY createdAt DESC")
    fun observeBookmarks(): Flow<List<BrowserBookmarkEntity>>

    @Query("SELECT * FROM browser_bookmarks WHERE url = :url LIMIT 1")
    suspend fun findBookmark(url: String): BrowserBookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BrowserBookmarkEntity): Long

    @Query("DELETE FROM browser_bookmarks WHERE url = :url")
    suspend fun deleteBookmark(url: String)

    @Query("DELETE FROM browser_bookmarks")
    suspend fun clearBookmarks()
}

