package com.f7.core.download

import com.f7.core.data.DownloadsRepository
import com.f7.core.data.db.SegmentEntity
import com.f7.core.data.storage.StorageTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/**
 * Downloads one HTTP/HTTPS resource over several concurrent Range connections.
 *
 * Design notes:
 *  - Segment offsets and byte counts are the unit of resume; they are written to
 *    Room while downloading, so a process death loses at most ~1 second of work.
 *  - Workers write with positional FileChannel writes; the file is never held in RAM.
 *  - When the server ignores Range requests the task throws [RangeUnsupportedException]
 *    and the engine restarts the download as a single stream.
 *  - Connection count adapts to measured throughput between 2 and the configured
 *    maximum, only while several segments are still pending.
 */
class SegmentedHttpDownloader(
    private val client: OkHttpClient,
    private val repository: DownloadsRepository,
    private val target: StorageTarget,
    private val url: String,
    private val headers: Map<String, String>,
    private val etag: String?,
    private val downloadId: Long,
    segments: List<SegmentEntity>,
    private val totalBytes: Long,
    private val supportsRanges: Boolean,
    private val maxConnections: Int,
    private val dynamicConnections: Boolean,
    private val maxRetries: Int,
    private val bufferSize: Int,
    private val control: DownloadControl,
    private val onProgress: (downloaded: Long, active: Int) -> Unit,
) {

    private class SegmentState(
        val index: Int,
        val start: Long,
        val end: Long,
        @Volatile var downloaded: Long,
        var persisted: Long,
    ) {
        val length: Long get() = (end - start + 1).coerceAtLeast(0)
        val complete: Boolean get() = downloaded >= length && length > 0
        val claimed = AtomicBoolean(false)
    }

    private val states: MutableList<SegmentState> =
        segments.sortedBy { it.index }.map { entity ->
            SegmentState(
                index = entity.index,
                start = entity.start,
                end = entity.end,
                downloaded = entity.downloaded.coerceIn(0, (entity.end - entity.start + 1).coerceAtLeast(0)),
                persisted = entity.downloaded,
            )
        }.toMutableList()

    private val doneBytes = AtomicLong(states.sumOf { it.downloaded })
    private val activeWorkers = AtomicInteger(0)
    private val desiredWorkers = AtomicInteger(
        if (dynamicConnections) minOf(4, maxConnections, max(1, states.size)) else minOf(maxConnections, max(1, states.size))
    )
    private var nextWorkerId = 0
    private var lastPersistAt = 0L

    val downloadedBytes: Long get() = doneBytes.get()
    val currentSegments: List<SegmentEntity>
        get() = states.map { SegmentEntity(downloadId, it.index, it.start, it.end, it.downloaded) }

    suspend fun run() {
        if (states.isEmpty()) throw DownloadFailure(DownloadErrorCode.SERVER, "No segments")
        if (states.all { it.complete }) return

        var lastTickBytes = doneBytes.get()
        var lastTickTime = System.currentTimeMillis()

        coroutineScope {
            val workers = mutableListOf<Job>()

            fun spawn() {
                if (control.stopped) return
                val id = nextWorkerId++
                workers += launch {
                    workerLoop(id)
                }
            }

            repeat(desiredWorkers.get().coerceAtMost(states.size)) { spawn() }

            val monitor = launch {
                var lastTuneAt = System.currentTimeMillis()
                var lastTuneBytes = doneBytes.get()
                while (isActive && !control.stopped) {
                    delay(700)
                    val now = System.currentTimeMillis()
                    val downloaded = doneBytes.get()
                    onProgress(downloaded, activeWorkers.get())

                    if (now - lastPersistAt >= 1_200) {
                        persist(downloaded)
                        lastPersistAt = now
                    }

                    // Adaptive connection count: only grow when every connection is fast,
                    // only shrink when per-connection throughput collapses.
                    if (dynamicConnections && now - lastTuneAt >= 5_000) {
                        val elapsed = (now - lastTuneAt).coerceAtLeast(1)
                        val speed = (downloaded - lastTuneBytes) * 1000 / elapsed
                        val active = max(1, activeWorkers.get())
                        val perConnection = speed / active
                        val pending = states.any { !it.complete && !it.claimed.get() }
                        val desired = desiredWorkers.get()
                        when {
                            pending && perConnection > 1_500_000 && desired < min(maxConnections, states.size) -> {
                                desiredWorkers.incrementAndGet()
                                spawn()
                            }
                            perConnection in 1..300_000 && desired > 2 -> desiredWorkers.decrementAndGet()
                        }
                        lastTuneAt = now
                        lastTuneBytes = downloaded
                    }
                    if (workers.none { it.isActive } && states.none { it.complete }) {
                        // All workers stopped unexpectedly; let the exception surface.
                        break
                    }
                }
            }

            workers.joinAll()
            monitor.cancel()
            if (control.stopped) return@coroutineScope
            if (states.any { !it.complete }) {
                throw DownloadFailure(DownloadErrorCode.NETWORK, "Download stopped before completing")
            }
            persist(doneBytes.get())
        }
    }

    private suspend fun workerLoop(workerId: Int) {
        while (!control.stopped) {
            if (workerId >= desiredWorkers.get() && states.any { !it.complete && !it.claimed.get() }) return
            val segment = claimNext() ?: return
            activeWorkers.incrementAndGet()
            try {
                downloadSegment(segment)
            } finally {
                activeWorkers.decrementAndGet()
            }
        }
    }

    private fun claimNext(): SegmentState? {
        synchronized(states) {
            for (state in states) {
                if (!state.complete && state.claimed.compareAndSet(false, true)) return state
            }
        }
        return null
    }

    private suspend fun downloadSegment(segment: SegmentState) {
        var attempt = 0
        while (!control.stopped && !segment.complete) {
            try {
                fetchRange(segment)
                if (!segment.complete && !control.stopped) {
                    throw IOException("Connection dropped before the segment finished")
                }
            } catch (e: RangeUnsupportedException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: DownloadFailure) {
                if (!e.retryable) throw e
                attempt++
                if (attempt > maxRetries) throw e
                delay(backoffMillis(attempt))
            } catch (e: IOException) {
                if (control.stopped) return
                attempt++
                if (attempt > maxRetries) {
                    throw DownloadFailure(
                        code = when (e) {
                            is java.net.SocketTimeoutException -> DownloadErrorCode.TIMEOUT
                            else -> DownloadErrorCode.NETWORK
                        },
                        detail = e.message?.take(160),
                    )
                }
                repository.attempts(downloadId, attempt, "${DownloadErrorCode.NETWORK}|${e.message?.take(120)}")
                delay(backoffMillis(attempt))
            }
        }
    }

    private suspend fun fetchRange(segment: SegmentState) {
        val remaining = segment.length - segment.downloaded
        if (remaining <= 0) return
        val from = segment.start + segment.downloaded
        val requestBuilder = com.f7.core.download.net.Http.baseRequest(url, headers)
            .header("Range", "bytes=$from-${segment.end}")
        if (!etag.isNullOrBlank()) requestBuilder.header("If-Range", etag)

        val call = client.newCall(requestBuilder.build())
        val parentJob = kotlin.coroutines.coroutineContext[Job]
        val cancelHandle = parentJob?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                when {
                    response.code == 200 -> throw RangeUnsupportedException()
                    response.code == 416 -> {
                        if (segment.downloaded >= segment.length) return
                        throw DownloadFailure(DownloadErrorCode.RANGE, "Server rejected the byte range (416)", retryable = false)
                    }
                    !response.isSuccessful -> throw DownloadFailure.http(response.code)
                }
                val contentRange = response.header("Content-Range")
                if (response.code == 206 && !contentRange.isNullOrBlank()) {
                    val expectedStart = contentRange.removePrefix("bytes ").substringBefore('-').trim().toLongOrNull()
                    if (expectedStart != null && expectedStart != from) {
                        throw DownloadFailure(
                            DownloadErrorCode.RANGE,
                            "Server returned the wrong byte range ($expectedStart instead of $from)",
                            retryable = false,
                        )
                    }
                }
                val body = response.body ?: throw DownloadFailure(DownloadErrorCode.SERVER, "Respons kosong")
                val input = body.byteStream()
                val buffer = ByteArray(bufferSize.coerceIn(8 * 1024, 512 * 1024))
                while (!control.stopped) {
                    kotlin.coroutines.coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read <= 0) break
                    val room = (segment.length - segment.downloaded).coerceAtMost(read.toLong()).toInt()
                    if (room <= 0) break
                    SpeedGovernor.acquire(room)
                    val position = segment.start + segment.downloaded
                    target.writeAt(position, buffer, room)
                    segment.downloaded += room
                    doneBytes.addAndGet(room.toLong())
                }
            }
        } finally {
            cancelHandle?.dispose()
        }
    }

    private suspend fun persist(downloaded: Long) {
        target.flush()
        repository.progress(
            id = downloadId,
            downloaded = downloaded,
            total = totalBytes,
            active = activeWorkers.get(),
            exact = totalBytes > 0 && supportsRanges,
        )
        val changed = states.filter { it.downloaded != it.persisted }
        for (state in changed) {
            repository.updateSegment(downloadId, state.index, state.downloaded)
            state.persisted = state.downloaded
        }
    }
}






