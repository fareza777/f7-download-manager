package com.f7.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "downloads",
    indices = [Index("status"), Index("createdAt"), Index("category")]
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val category: String,
    val kind: String,
    val status: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val etag: String? = null,
    val lastModified: String? = null,
    val supportsResume: Boolean = false,
    val requestedConnections: Int = 6,
    val activeConnections: Int = 0,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val savedUri: String? = null,
    val displayPath: String? = null,
    val storageKind: String = "media_store",
    val sizeIsExact: Boolean = false,
    val headersJson: String? = null,
    val hlsMetaJson: String? = null,
    val checksum: String? = null,
    val checksumType: String? = null,
    val priority: Int = 0,
    val attempts: Int = 0,
    val speedLimitKbps: Int = 0,
    val originalUrl: String? = null,
    val autoResume: Boolean = true,
)

@Entity(
    tableName = "segments",
    primaryKeys = ["downloadId", "index"],
    foreignKeys = [
        ForeignKey(
            entity = DownloadEntity::class,
            parentColumns = ["id"],
            childColumns = ["downloadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("downloadId")]
)
data class SegmentEntity(
    val downloadId: Long,
    val index: Int,
    val start: Long,
    val end: Long,
    val downloaded: Long = 0,
)

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String? = null,
    val category: String? = null,
    val triggerAt: Long,
    val repeatRule: String = "ONCE",
    val enabled: Boolean = true,
    val lastRunAt: Long? = null,
    val nextRunAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "download_events",
    indices = [Index("createdAt"), Index("success")]
)
data class DownloadEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val downloadId: Long = 0,
    val fileName: String,
    val category: String,
    val bytes: Long,
    val durationMs: Long,
    val averageSpeed: Long,
    val success: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "browser_history",
    indices = [Index("visitedAt")]
)
data class BrowserHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String? = null,
    val visitedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "browser_bookmarks",
    indices = [Index(value = ["url"], unique = true)]
)
data class BrowserBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)


