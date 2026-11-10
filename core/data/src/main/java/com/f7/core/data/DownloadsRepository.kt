package com.f7.core.data

import com.f7.core.data.db.BrowserBookmarkEntity
import com.f7.core.data.db.BrowserDao
import com.f7.core.data.db.BrowserHistoryEntity
import com.f7.core.data.db.DownloadDao
import com.f7.core.data.db.DownloadEntity
import com.f7.core.data.db.DownloadEventEntity
import com.f7.core.data.db.ScheduleDao
import com.f7.core.data.db.ScheduleEntity
import com.f7.core.data.db.SegmentEntity
import com.f7.core.data.db.StatsDao
import com.f7.core.data.prefs.SettingsStore
import com.f7.core.data.storage.StorageManager
import com.f7.core.model.AppSettings
import com.f7.core.model.DownloadItem
import com.f7.core.model.DownloadKind
import com.f7.core.model.DownloadStatus
import com.f7.core.model.F7Format
import com.f7.core.model.FileCategory
import com.f7.core.model.ScheduleItem
import com.f7.core.model.SegmentInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.json.JSONObject

data class NewDownload(
    val url: String,
    val fileName: String? = null,
    val mimeType: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val category: FileCategory? = null,
    val connections: Int = 0,
    val kind: DownloadKind? = null,
    val speedLimitKbps: Int = 0,
)

class DownloadsRepository(
    private val dao: DownloadDao,
    private val scheduleDao: ScheduleDao,
    private val statsDao: StatsDao,
    private val browserDao: BrowserDao,
    val storageManager: StorageManager,
    private val settingsStore: SettingsStore,
) {

    private val liveStats = MutableStateFlow<Map<Long, LiveStats>>(emptyMap())

    val downloads: Flow<List<DownloadItem>> =
        combine(dao.observeAll(), liveStats) { rows, live ->
            rows.map { row ->
                val stats = live[row.id]
                row.toItem(
                    liveSpeed = stats?.speed ?: 0L,
                    liveConnections = stats?.connections ?: row.activeConnections,
                    liveEta = stats?.eta ?: 0L,
                )
            }
        }

    val schedules: Flow<List<ScheduleItem>> =
        scheduleDao.observeAll().map { rows -> rows.map { it.toItem() } }

    fun observeDownload(id: Long): Flow<DownloadItem?> =
        combine(dao.observeById(id), liveStats) { row, live ->
            row?.toItem(
                liveSpeed = live[id]?.speed ?: 0L,
                liveConnections = live[id]?.connections ?: row.activeConnections,
                liveEta = live[id]?.eta ?: 0L,
            )
        }

    fun observeSegments(id: Long): Flow<List<SegmentInfo>> =
        dao.observeSegments(id).map { list -> list.map { it.toInfo() } }

    fun setLiveStats(id: Long, speed: Long, connections: Int, eta: Long) {
        liveStats.update { current ->
            if (speed <= 0 && connections <= 0) current - id
            else current + (id to LiveStats(speed, connections, eta))
        }
    }

    fun clearLiveStats(id: Long) {
        liveStats.update { it - id }
    }

    suspend fun settings(): AppSettings = settingsStore.current()

    suspend fun enqueue(request: NewDownload): Long {
        val settings = settingsStore.current()
        val kind = request.kind ?: if (F7Format.isPlaylistUrl(request.url)) DownloadKind.HLS else DownloadKind.HTTP
        val fileName = F7Format.sanitizeFileName(
            request.fileName?.takeIf { it.isNotBlank() }
                ?: F7Format.guessFileName(request.url, null, request.mimeType)
        )
        val mime = request.mimeType?.takeIf { it.isNotBlank() }
            ?: guessMime(fileName)
        val category = request.category ?: FileCategory.fromMime(mime, fileName)
        val connections = if (request.connections > 0) request.connections else settings.maxConnections
        val entity = DownloadEntity(
            url = request.url,
            originalUrl = request.url,
            fileName = fileName,
            mimeType = mime,
            category = category.name,
            kind = kind.name,
            status = DownloadStatus.QUEUED.name,
            totalBytes = 0,
            downloadedBytes = 0,
            requestedConnections = connections,
            headersJson = if (request.headers.isEmpty()) null else JSONObject(request.headers).toString(),
            speedLimitKbps = request.speedLimitKbps,
            autoResume = true,
        )
        return dao.insert(entity)
    }

    suspend fun get(id: Long): DownloadEntity? = dao.getById(id)

    suspend fun rows(): List<DownloadEntity> = dao.getAll()

    suspend fun runnable(): List<DownloadEntity> = dao.getRunnable()

    suspend fun update(entity: DownloadEntity) = dao.update(entity)

    suspend fun setStatus(id: Long, status: DownloadStatus, error: String? = null) =
        dao.updateStatus(id, status.name, error, System.currentTimeMillis())

    suspend fun progress(id: Long, downloaded: Long, total: Long, active: Int, exact: Boolean) =
        dao.updateProgress(id, downloaded, total, active, exact)

    suspend fun attempts(id: Long, attempts: Int, error: String?) =
        dao.updateAttempts(id, attempts, error, System.currentTimeMillis())

    suspend fun completed(id: Long) {
        dao.markCompleted(id, System.currentTimeMillis())
        clearLiveStats(id)
    }

    suspend fun location(id: Long, uri: String?, path: String?) = dao.updateLocation(id, uri, path)

    suspend fun checksum(id: Long, value: String?, type: String?) = dao.updateChecksum(id, value, type)

    suspend fun rename(id: Long, name: String) = dao.updateFileName(id, name)

    suspend fun segments(id: Long): List<SegmentEntity> = dao.getSegments(id)

    suspend fun replaceSegments(id: Long, segments: List<SegmentEntity>) = dao.replaceSegments(id, segments)

    suspend fun updateSegment(id: Long, index: Int, downloaded: Long) = dao.updateSegment(id, index, downloaded)

    suspend fun sumSegments(id: Long): Long = dao.sumSegmentBytes(id)

    suspend fun pause(id: Long) {
        dao.update((dao.getById(id) ?: return).copy(
            status = DownloadStatus.PAUSED.name,
            autoResume = false,
            activeConnections = 0,
            error = null,
            updatedAt = System.currentTimeMillis(),
        ))
        clearLiveStats(id)
    }

    suspend fun queue(id: Long) {
        dao.update((dao.getById(id) ?: return).copy(
            status = DownloadStatus.QUEUED.name,
            error = null,
            autoResume = true,
            attempts = 0,
            updatedAt = System.currentTimeMillis(),
        ))
    }

    suspend fun markForNetworkWait(id: Long) {
        val current = dao.getById(id) ?: return
        if (!current.status.isActiveStatus()) return
        dao.update(current.copy(
            status = DownloadStatus.PAUSED.name,
            autoResume = true,
            activeConnections = 0,
            error = "waiting_network",
            updatedAt = System.currentTimeMillis(),
        ))
        clearLiveStats(id)
    }

    /** Pauses a job for a temporary device condition (battery, metered network, ...). */
    suspend fun pauseAuto(id: Long, reason: String) {
        val current = dao.getById(id) ?: return
        if (current.status == DownloadStatus.COMPLETED.name ||
            current.status == DownloadStatus.CANCELLED.name ||
            current.status == DownloadStatus.PAUSED.name
        ) return
        dao.update(current.copy(
            status = DownloadStatus.PAUSED.name,
            autoResume = true,
            activeConnections = 0,
            error = reason,
            updatedAt = System.currentTimeMillis(),
        ))
        clearLiveStats(id)
    }

    suspend fun findByUrl(url: String): DownloadEntity? = dao.findByUrl(url)

    suspend fun markStarted(id: Long) {
        val row = dao.getById(id) ?: return
        if (row.startedAt == null) {
            dao.update(row.copy(startedAt = System.currentTimeMillis()))
        }
    }

    suspend fun queueRows(): List<DownloadEntity> = dao.getQueue()

    suspend fun moveQueueItem(id: Long, delta: Int) {
        val rows = dao.getQueue().sortedWith(
            compareByDescending<DownloadEntity> { it.priority }.thenBy { it.createdAt }
        )
        val index = rows.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = (index + delta).coerceIn(0, rows.lastIndex)
        if (target == index) return
        val reordered = rows.toMutableList().apply {
            add(target, removeAt(index))
        }
        reordered.forEachIndexed { order, row ->
            val priority = reordered.size - order
            if (row.priority != priority) dao.updatePriority(row.id, priority)
        }
    }

    suspend fun cleanupOldCompleted(days: Int): Int {
        if (days <= 0) return 0
        val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        var deleted = 0
        dao.getCompletedBefore(cutoff).forEach { row ->
            row.savedUri?.let { storageManager.delete(row.storageKind, it) }
            dao.deleteById(row.id)
            deleted++
        }
        return deleted
    }

    // Statistics -----------------------------------------------------------------

    fun events(): Flow<List<DownloadEventEntity>> = statsDao.observeAll()

    suspend fun recordEvent(
        downloadId: Long,
        fileName: String,
        category: String,
        bytes: Long,
        durationMs: Long,
        success: Boolean,
    ) {
        val safeDuration = durationMs.coerceAtLeast(1)
        val averageSpeed = (bytes * 1000L / safeDuration).coerceAtLeast(0)
        statsDao.insert(
            DownloadEventEntity(
                downloadId = downloadId,
                fileName = fileName,
                category = category,
                bytes = bytes,
                durationMs = safeDuration,
                averageSpeed = averageSpeed,
                success = success,
            )
        )
    }

    suspend fun clearStatistics() = statsDao.clear()

    // Browser data ---------------------------------------------------------------

    fun browserHistory(): Flow<List<BrowserHistoryEntity>> = browserDao.observeHistory()

    fun bookmarks(): Flow<List<BrowserBookmarkEntity>> = browserDao.observeBookmarks()

    suspend fun addHistory(url: String, title: String?) {
        if (url.isBlank() || url == "about:blank") return
        browserDao.insertHistory(BrowserHistoryEntity(url = url, title = title?.take(200)))
    }

    suspend fun deleteHistory(id: Long) = browserDao.deleteHistory(id)

    suspend fun clearHistory() = browserDao.clearHistory()

    suspend fun isBookmarked(url: String): Boolean = browserDao.findBookmark(url) != null

    suspend fun toggleBookmark(url: String, title: String?): Boolean {
        if (url.isBlank() || url == "about:blank") return false
        return if (browserDao.findBookmark(url) != null) {
            browserDao.deleteBookmark(url)
            false
        } else {
            browserDao.insertBookmark(BrowserBookmarkEntity(url = url, title = title?.take(200)))
            true
        }
    }

    suspend fun deleteBookmark(url: String) = browserDao.deleteBookmark(url)

    suspend fun clearBookmarks() = browserDao.clearBookmarks()

    suspend fun fail(id: Long, message: String) {
        dao.update((dao.getById(id) ?: return).copy(
            status = DownloadStatus.FAILED.name,
            error = message,
            activeConnections = 0,
            autoResume = false,
            updatedAt = System.currentTimeMillis(),
        ))
        clearLiveStats(id)
    }

    suspend fun delete(id: Long, deleteFile: Boolean) {
        val row = dao.getById(id) ?: return
        if (deleteFile && row.savedUri != null) {
            storageManager.delete(row.storageKind, row.savedUri)
        }
        dao.deleteById(id)
        clearLiveStats(id)
    }

    suspend fun pauseAll() {
        dao.getAll().forEach { row ->
            if (row.status == DownloadStatus.QUEUED.name || row.status.isActiveStatus()) {
                dao.update(
                    row.copy(
                        status = DownloadStatus.PAUSED.name,
                        autoResume = false,
                        activeConnections = 0,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                clearLiveStats(row.id)
            }
        }
    }

    suspend fun resumeAll(): Int {
        var count = 0
        dao.getAll().forEach { row ->
            if (row.status == DownloadStatus.PAUSED.name || row.status == DownloadStatus.FAILED.name) {
                dao.update(
                    row.copy(
                        status = DownloadStatus.QUEUED.name,
                        autoResume = true,
                        error = null,
                        attempts = 0,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                count++
            }
        }
        return count
    }

    suspend fun retryFailed(): Int {
        var count = 0
        dao.getAll().forEach { row ->
            if (row.status == DownloadStatus.FAILED.name) {
                dao.update(
                    row.copy(
                        status = DownloadStatus.QUEUED.name,
                        autoResume = true,
                        error = null,
                        attempts = 0,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                count++
            }
        }
        return count
    }

    suspend fun deleteFinished() {
        dao.getAll().filter { it.status == DownloadStatus.COMPLETED.name }.forEach { row ->
            if (row.savedUri != null) storageManager.delete(row.storageKind, row.savedUri)
            dao.deleteById(row.id)
        }
    }

    // Schedules ----------------------------------------------------------------

    suspend fun addSchedule(entity: ScheduleEntity): Long = scheduleDao.insert(entity)

    suspend fun updateSchedule(entity: ScheduleEntity) = scheduleDao.update(entity)

    suspend fun deleteSchedule(id: Long) = scheduleDao.delete(id)

    suspend fun schedulesOnce(): List<ScheduleEntity> = scheduleDao.getAll()

    suspend fun scheduleById(id: Long): ScheduleEntity? = scheduleDao.getById(id)

    private fun String.isActiveStatus(): Boolean =
        this == DownloadStatus.QUEUED.name || this == DownloadStatus.CONNECTING.name ||
            this == DownloadStatus.DOWNLOADING.name || this == DownloadStatus.VERIFYING.name

    companion object {
        fun guessMime(fileName: String): String {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "mp4" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "ts" -> "video/mp2t"
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "aac" -> "audio/aac"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "pdf" -> "application/pdf"
                "zip" -> "application/zip"
                "apk" -> "application/vnd.android.package-archive"
                "json" -> "application/json"
                "txt" -> "text/plain"
                else -> "application/octet-stream"
            }
        }
    }
}




