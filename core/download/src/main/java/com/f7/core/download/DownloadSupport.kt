package com.f7.core.download

import com.f7.core.data.db.DownloadEntity
import com.f7.core.data.db.SegmentEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import kotlin.math.max

/**
 * Process-wide token bucket. Every socket read pays for the bytes it brings in,
 * so several downloads together never exceed the configured global limit.
 */
object SpeedGovernor {
    @Volatile
    private var limitBytesPerSec: Long = 0

    private val mutex = Mutex()
    private var nextFreeNanos: Long = System.nanoTime()

    fun setLimitKbps(kbps: Int) {
        val newLimit = kbps.coerceAtLeast(0) * 1024L
        if (newLimit != limitBytesPerSec) {
            limitBytesPerSec = newLimit
            if (newLimit == 0L) nextFreeNanos = System.nanoTime()
        }
    }

    suspend fun acquire(bytes: Int) {
        val limit = limitBytesPerSec
        if (limit <= 0 || bytes <= 0) return
        val waitMs = mutex.withLock {
            val now = System.nanoTime()
            val start = max(now, nextFreeNanos)
            nextFreeNanos = start + (bytes.toLong() * 1_000_000_000L / limit)
            ((start - now) / 1_000_000L).coerceAtLeast(0)
        }
        if (waitMs > 0) delay(waitMs)
    }
}

/** Cooperative pause/cancel signal checked between every chunk. */
class DownloadControl {
    @Volatile var paused: Boolean = false
    @Volatile var cancelled: Boolean = false
    @Volatile var job: Job? = null

    val stopped: Boolean get() = paused || cancelled
}

object DownloadErrorCode {
    const val NETWORK = "network"
    const val TIMEOUT = "timeout"
    const val DNS = "dns"
    const val SSL = "ssl"
    const val STORAGE = "storage"
    const val RANGE = "range"
    const val SERVER = "server"
    const val DRM = "drm"
    const val LIVE = "live"
    const val HTTP = "http"
    const val UNKNOWN = "unknown"
}

class DownloadFailure(
    val code: String,
    val detail: String? = null,
    val retryable: Boolean = true,
    val httpCode: Int = 0,
) : IOException(detail ?: code) {
    companion object {
        fun http(code: Int): DownloadFailure = DownloadFailure(
            code = DownloadErrorCode.HTTP,
            detail = "HTTP $code",
            retryable = code >= 500 || code == 408 || code == 429,
            httpCode = code,
        )
    }
}

class RangeUnsupportedException : IOException("Server ignores Range requests")

fun DownloadFailure.stored(): String = if (detail.isNullOrBlank()) code else "$code|$detail"

fun Throwable.toStoredDownloadError(): String = when (this) {
    is DownloadFailure -> stored()
    is UnknownHostException -> DownloadErrorCode.DNS
    is SocketTimeoutException -> DownloadErrorCode.TIMEOUT
    is SSLException, is CertificateException -> DownloadErrorCode.SSL
    is OutOfMemoryError -> "${DownloadErrorCode.STORAGE}|Not enough memory"
    is IOException -> "${DownloadErrorCode.NETWORK}|${message?.take(160) ?: "Connection failed"}"
    else -> "${DownloadErrorCode.UNKNOWN}|${message?.take(160) ?: this::class.java.simpleName}"
}

fun planSegments(
    downloadId: Long,
    totalBytes: Long,
    maxConnections: Int,
): List<SegmentEntity> {
    if (totalBytes <= 0) return emptyList()
    val minSegment = 1L * 1024 * 1024
    val bySize = (totalBytes / minSegment).toInt().coerceAtLeast(1)
    val count = minOf(maxConnections.coerceAtLeast(1), bySize, 16)
    val chunk = totalBytes / count
    return (0 until count).map { index ->
        val start = index * chunk
        val end = if (index == count - 1) totalBytes - 1 else ((index + 1) * chunk) - 1
        SegmentEntity(downloadId = downloadId, index = index, start = start, end = end, downloaded = 0)
    }
}

fun DownloadEntity.httpHeaders(): Map<String, String> {
    val json = headersJson ?: return emptyMap()
    return runCatching {
        val obj = org.json.JSONObject(json)
        buildMap {
            obj.keys().forEach { key ->
                val value = obj.optString(key, "")
                if (key.isNotBlank() && value.isNotBlank()) put(key, value)
            }
        }
    }.getOrDefault(emptyMap())
}

internal fun backoffMillis(attempt: Int): Long =
    (500L * (1L shl attempt.coerceIn(0, 5))).coerceAtMost(15_000L)

internal fun CancellationException.isUserStop(): Boolean = true


