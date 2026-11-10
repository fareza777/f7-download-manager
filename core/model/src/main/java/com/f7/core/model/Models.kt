package com.f7.core.model

/**
 * Lifecycle of a download. Persisted by name, so never rename existing constants.
 */
enum class DownloadStatus {
    QUEUED,
    CONNECTING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    VERIFYING,
    SCHEDULED,
    CANCELLED;

    val isActive: Boolean
        get() = this == QUEUED || this == CONNECTING || this == DOWNLOADING || this == VERIFYING

    val isFinished: Boolean
        get() = this == COMPLETED || this == CANCELLED
}

enum class DownloadKind {
    HTTP,
    HLS;

    val label: String
        get() = when (this) {
            HTTP -> "HTTP"
            HLS -> "HLS"
        }
}

enum class FileCategory {
    VIDEO,
    AUDIO,
    IMAGE,
    DOCUMENT,
    ARCHIVE,
    APP,
    OTHER;

    companion object {
        fun fromMime(mime: String?, fileName: String?): FileCategory {
            val m = mime?.lowercase().orEmpty()
            val ext = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
            return when {
                m.startsWith("video/") || ext in setOf("mp4", "mkv", "webm", "avi", "mov", "ts", "m3u8", "flv", "3gp") -> VIDEO
                m.startsWith("audio/") || ext in setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus") -> AUDIO
                m.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "bmp", "svg") -> IMAGE
                ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub", "csv") -> DOCUMENT
                ext in setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso") -> ARCHIVE
                ext in setOf("apk", "xapk", "apks", "aab") -> APP
                else -> OTHER
            }
        }
    }
}

/** A download row as exposed to the UI. Kept immutable. */
data class DownloadItem(
    val id: Long,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val category: FileCategory,
    val kind: DownloadKind,
    val status: DownloadStatus,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val connections: Int,
    val activeConnections: Int,
    val speedBytesPerSec: Long,
    val etaSeconds: Long,
    val error: String?,
    val etag: String?,
    val lastModified: String?,
    val supportsResume: Boolean,
    val createdAt: Long,
    val startedAt: Long? = null,
    val completedAt: Long?,
    val checksum: String?,
    val checksumType: String?,
    val savedUri: String?,
    val displayPath: String?,
    val sizeIsExact: Boolean,
    val storageKind: String,
    val priority: Int = 0,
) {
    val progress: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val isActive: Boolean get() = status.isActive

    val fileNameWithoutExtension: String
        get() = fileName.substringBeforeLast('.', fileName)
}

data class SegmentInfo(
    val index: Int,
    val start: Long,
    val end: Long,
    val downloaded: Long,
) {
    val length: Long get() = end - start + 1
    val progress: Float get() = if (length > 0) (downloaded.toFloat() / length).coerceIn(0f, 1f) else 0f
    val isComplete: Boolean get() = downloaded >= length
}

data class DownloadStats(
    val activeCount: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
    val totalCount: Int = 0,
    val totalBytesDownloaded: Long = 0,
    val currentSpeed: Long = 0,
)

data class ScheduleItem(
    val id: Long,
    val url: String,
    val fileName: String?,
    val category: FileCategory?,
    val triggerAt: Long,
    val repeatRule: RepeatRule,
    val enabled: Boolean,
    val lastRunAt: Long?,
    val nextRunAt: Long?,
)

enum class RepeatRule { ONCE, DAILY, WEEKLY }

data class AppSettings(
    val themeId: String = "aurora",
    val darkMode: String = "light", // system | light | dark
    val textScale: Float = 1.0f,
    val language: String = "en", // en | id | system
    val maxConnections: Int = 8,
    val speedLimitKbps: Int = 0, // 0 = unlimited
    val wifiOnly: Boolean = false,
    val autoRetry: Boolean = true,
    val maxRetries: Int = 5,
    val notificationsEnabled: Boolean = true,
    val storageTreeUri: String? = null,
    val storageMode: String = "media_store", // media_store | app | saf
    val dynamicConnections: Boolean = true,
    val hlsPreferredQuality: String = "best", // best | worst
    val askBeforeDownload: Boolean = false,
    val maxConcurrentDownloads: Int = 2,
    val dynamicColor: Boolean = false,
    val uiAnimations: Boolean = true,
    val connectTimeoutSec: Int = 20,
    val readTimeoutSec: Int = 45,
    val chunkSizeKb: Int = 64,
    val userAgent: String = "",
    val autoChecksum: Boolean = false,
    val skipDuplicateUrls: Boolean = true,
    val autoDeleteDays: Int = 0,
    val pauseOnMetered: Boolean = false,
    val pauseOnBatterySaver: Boolean = false,
    val chargingOnly: Boolean = false,
    val pauseOnLowBattery: Boolean = false,
    val browserJavaScript: Boolean = true,
    val browserCookies: Boolean = true,
    val browserDesktopMode: Boolean = false,
    val browserSearchEngine: String = "google",
    val browserHomepage: String = "https://www.google.com",
    val notificationComplete: Boolean = true,
    val closeAddSheetOnShare: Boolean = true,
)





