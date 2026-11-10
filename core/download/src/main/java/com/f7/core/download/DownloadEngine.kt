package com.f7.core.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import com.f7.core.data.DownloadsRepository
import com.f7.core.data.db.DownloadEntity
import com.f7.core.data.db.SegmentEntity
import com.f7.core.data.prefs.SettingsStore
import com.f7.core.data.storage.StorageTarget
import com.f7.core.download.hls.HlsDownloader
import com.f7.core.download.hls.HlsLoader
import com.f7.core.download.hls.HlsMeta
import com.f7.core.download.net.Http
import com.f7.core.download.net.Probe
import com.f7.core.model.AppSettings
import com.f7.core.model.DownloadKind
import com.f7.core.model.DownloadStatus
import com.f7.core.model.F7Format
import com.f7.core.model.FileCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the download queue. The database is the source of truth: any row left in an
 * active state (because the process died) is resumed when the service starts again.
 * The engine deliberately keeps only runnable jobs in memory; paused/failed rows stay
 * untouched until the user resumes them (or the network pause flag is set).
 */
class DownloadEngine(
    private val context: Context,
    private val repository: DownloadsRepository,
    private val settingsStore: SettingsStore,
) {

    private data class RunningJob(
        val control: DownloadControl,
        val job: Job,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Long, RunningJob>()
    private val _activeCount = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = _activeCount

    private var monitor: Job? = null
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun start() {
        if (monitor?.isActive == true) return
        monitor = scope.launch {
            while (isActive) {
                runCatching { tick() }
                delay(700)
            }
        }
    }

    fun shutdown() {
        scope.cancel()
    }

    private suspend fun tick() {
        val settings = repository.settings()
        SpeedGovernor.setLimitKbps(settings.speedLimitKbps)

        val blockReason = waitReason(settings)
        if (blockReason != null) {
            val running = jobs.keys.toList()
            for (id in running) {
                val control = jobs[id]?.control ?: continue
                control.paused = true
                jobs[id]?.job?.cancel()
                repository.pauseAuto(id, blockReason)
            }
            // Queued rows are not running yet, but must show why they are waiting and
            // stay auto-resumable when the network/power condition changes.
            repository.runnable()
                .filter { it.status == DownloadStatus.QUEUED.name }
                .forEach { repository.pauseAuto(it.id, blockReason) }
            return
        }

        // Rows paused automatically for network/power reasons come back on their own.
        repository.rows().forEach { row ->
            if (row.status == DownloadStatus.PAUSED.name && row.autoResume && !jobs.containsKey(row.id)) {
                repository.queue(row.id)
            }
        }

        val rows = repository.runnable().sortedWith(
            compareByDescending<DownloadEntity> { it.priority }.thenBy { it.createdAt }
        )
        val maxActive = settings.maxConcurrentDownloads.coerceIn(1, 5)
        var slots = maxActive - jobs.size
        for (row in rows) {
            if (slots <= 0) break
            if (jobs.containsKey(row.id)) continue
            if (row.status == DownloadStatus.QUEUED.name || row.status == DownloadStatus.CONNECTING.name ||
                row.status == DownloadStatus.DOWNLOADING.name || row.status == DownloadStatus.VERIFYING.name
            ) {
                slots--
                startJob(row, settings)
            }
        }
    }

    private fun waitReason(settings: AppSettings): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return "waiting_network"
        }
        val onWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        if (settings.wifiOnly && !onWifi) return "waiting_wifi"
        val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        if (settings.pauseOnMetered && !unmetered) return "waiting_unmetered"

        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (settings.pauseOnBatterySaver && power?.isPowerSaveMode == true) return "waiting_power"

        val battery = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        val charging = battery?.isCharging == true
        if (settings.chargingOnly && !charging) return "waiting_power"
        if (settings.pauseOnLowBattery && level in 0..15 && !charging) return "waiting_power"
        return null
    }

    private fun startJob(row: DownloadEntity, settings: AppSettings) {
        val control = DownloadControl()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                if (repository.get(row.id) == null) return@launch
                if (row.status == DownloadStatus.QUEUED.name) {
                    repository.setStatus(row.id, DownloadStatus.CONNECTING)
                }
                repository.markStarted(row.id)
                if (row.kind == DownloadKind.HLS.name) {
                    runHlsTask(row, settings, control)
                } else {
                    runHttpTask(row, settings, control)
                }
            } catch (e: CancellationException) {
                // Pause / network pause / cancel path already persisted the right status.
            } catch (e: DownloadFailure) {
                if (!control.stopped) {
                    repository.fail(row.id, e.stored())
                    recordFailure(row.id)
                }
            } catch (e: Throwable) {
                if (!control.stopped) {
                    repository.fail(row.id, e.toStoredDownloadError())
                    recordFailure(row.id)
                }
            } finally {
                jobs.remove(row.id)
                repository.clearLiveStats(row.id)
                _activeCount.value = jobs.size
            }
        }
        control.job = job
        jobs[row.id] = RunningJob(control, job)
        _activeCount.value = jobs.size
        job.start()
    }

    // Public controls ---------------------------------------------------------

    suspend fun pause(id: Long) {
        jobs[id]?.let {
            it.control.paused = true
            it.job.cancel()
        }
        repository.pause(id)
    }

    suspend fun resume(id: Long) {
        repository.queue(id)
    }

    suspend fun cancel(id: Long) {
        jobs[id]?.let {
            it.control.cancelled = true
            it.job.cancel()
        }
        repository.delete(id, deleteFile = true)
    }

    suspend fun retry(id: Long) {
        repository.queue(id)
    }

    suspend fun pauseAll() {
        jobs.keys.toList().forEach { pause(it) }
        repository.pauseAll()
    }

    suspend fun resumeAll() {
        repository.resumeAll()
    }

    suspend fun retryAllFailed() {
        repository.retryFailed()
    }

    suspend fun deleteFinished() {
        repository.deleteFinished()
    }

    // HTTP --------------------------------------------------------------------

    private suspend fun runHttpTask(row0: DownloadEntity, settings: AppSettings, control: DownloadControl) {
        var row = row0
        val headers = row.httpHeaders()
        val maxRetries = if (settings.autoRetry) settings.maxRetries else 0
        val client = Http.client(settings)
        val probe = Probe(client).probe(row.url, headers)
        if (control.stopped) return

        val total = probe.totalBytes.coerceAtLeast(0)
        val supportsRanges = probe.supportsRanges && total > 0
        android.util.Log.i(
            "F7Engine",
            "probe id=${row.id} total=$total supports=$supportsRanges etag=${probe.etag} name=${probe.fileName}"
        )
        val etagChanged = !row.etag.isNullOrBlank() && !probe.etag.isNullOrBlank() && row.etag != probe.etag
        val sizeChanged = row.totalBytes > 0 && total > 0 && row.totalBytes != total
        val mustReset = (row.downloadedBytes > 0 || row.savedUri != null) && (etagChanged || sizeChanged)

        if (mustReset) {
            row.savedUri?.let { repository.storageManager.delete(row.storageKind, it) }
            repository.replaceSegments(row.id, emptyList())
            row = row.copy(savedUri = null, displayPath = null, downloadedBytes = 0)
        }

        val fileName = if (row.savedUri == null) probe.fileName else row.fileName
        val mime = if (probe.mimeType == "application/octet-stream") row.mimeType else probe.mimeType
        row = row.copy(
            url = probe.finalUrl,
            fileName = fileName,
            mimeType = mime,
            category = FileCategory.fromMime(mime, fileName).name,
            totalBytes = total,
            supportsResume = supportsRanges,
            etag = probe.etag ?: row.etag,
            lastModified = probe.lastModified ?: row.lastModified,
            sizeIsExact = total > 0,
            status = DownloadStatus.DOWNLOADING.name,
            error = null,
            attempts = 0,
            updatedAt = System.currentTimeMillis(),
        )
        repository.update(row)

        if (control.stopped) return

        val reopened: StorageTarget? = row.savedUri?.let {
            repository.storageManager.reopen(row.storageKind, it)
        }
        android.util.Log.i(
            "F7Engine",
            "reopen id=${row.id} savedUri=${row.savedUri} ok=${reopened != null} segments=${repository.segments(row.id).size}"
        )
        if (reopened == null && row.savedUri != null) {
            row.savedUri?.let { repository.storageManager.delete(row.storageKind, it) }
            repository.replaceSegments(row.id, emptyList())
            row = row.copy(savedUri = null, displayPath = null, downloadedBytes = 0)
            repository.update(row)
        }
        val target: StorageTarget = reopened ?: run {
            val created = repository.storageManager.create(row.fileName, row.mimeType, settings)
            android.util.Log.i("F7Engine", "new target id=${row.id} uri=${created.target.uri} name=${created.fileName}")
            row = row.copy(
                fileName = created.fileName,
                savedUri = created.target.uri,
                displayPath = created.target.displayPath,
                storageKind = created.target.kind,
                downloadedBytes = 0,
            )
            repository.update(row)
            repository.location(row.id, created.target.uri, created.target.displayPath)
            repository.replaceSegments(row.id, emptyList())
            created.target
        }

        val tracker = JobProgress(repository, row.id)
        var segments: List<SegmentEntity> = repository.segments(row.id)

        if (!supportsRanges) {
            target.truncate(0)
            if (segments.isNotEmpty() || row.downloadedBytes != 0L) {
                repository.replaceSegments(row.id, emptyList())
                repository.progress(row.id, 0, total, 0, total > 0)
                row = row.copy(downloadedBytes = 0)
                repository.update(row)
            }
            segments = emptyList()
        } else if (segments.isEmpty()) {
            segments = planSegments(row.id, total, row.requestedConnections)
            repository.replaceSegments(row.id, segments)
        }

        var downloaded = if (supportsRanges) segments.sumOf { it.downloaded } else 0L
        repository.progress(row.id, downloaded, total, 0, total > 0)

        try {
            if (!supportsRanges || total <= 0) {
                val streamer = StreamingHttpDownloader(
                    client = client,
                    repository = repository,
                    target = target,
                    url = row.url,
                    headers = headers,
                    downloadId = row.id,
                    expectedTotal = total,
                    maxRetries = maxRetries,
                    bufferSize = settings.chunkSizeKb * 1024,
                    control = control,
                    onProgress = { written ->
                        tracker.update(written, total, 1)
                    },
                )
                downloaded = streamer.run()
                if (control.stopped) {
                    target.close(false)
                    return
                }
            } else {
                val downloader = SegmentedHttpDownloader(
                    client = client,
                    repository = repository,
                    target = target,
                    url = row.url,
                    headers = headers,
                    etag = row.etag,
                    downloadId = row.id,
                    segments = segments,
                    totalBytes = total,
                    supportsRanges = supportsRanges,
                    maxConnections = row.requestedConnections.coerceAtMost(settings.maxConnections),
                    dynamicConnections = settings.dynamicConnections && settings.speedLimitKbps == 0,
                    maxRetries = maxRetries,
                    bufferSize = settings.chunkSizeKb * 1024,
                    control = control,
                    onProgress = { bytes, active -> tracker.update(bytes, total, active) },
                )
                try {
                    downloader.run()
                    downloaded = downloader.downloadedBytes
                    downloader.currentSegments.forEach { segment ->
                        repository.updateSegment(row.id, segment.index, segment.downloaded)
                    }
                } catch (e: RangeUnsupportedException) {
                    // The server advertised Range but answered with 200: restart as a stream.
                    if (control.stopped) {
                        target.close(false)
                        return
                    }
                    target.truncate(0)
                    repository.replaceSegments(row.id, emptyList())
                    row = (repository.get(row.id) ?: row).copy(
                        supportsResume = false,
                        downloadedBytes = 0,
                        sizeIsExact = total > 0,
                    )
                    repository.update(row)
                    repository.progress(row.id, 0, total, 0, total > 0)
                    val streamer = StreamingHttpDownloader(
                        client = client,
                        repository = repository,
                        target = target,
                        url = row.url,
                        headers = headers,
                        downloadId = row.id,
                        expectedTotal = total,
                        maxRetries = maxRetries,
                        bufferSize = settings.chunkSizeKb * 1024,
                        control = control,
                        onProgress = { written -> tracker.update(written, total, 1) },
                    )
                    downloaded = streamer.run()
                    if (control.stopped) {
                        target.close(false)
                        return
                    }
                }
            }

            if (control.stopped) {
                target.close(false)
                return
            }

            val actualSize = target.currentSize()
            android.util.Log.i(
                "F7Engine",
                "complete id=${row.id} file=${row.fileName} total=$total downloaded=$downloaded " +
                    "actualSize=$actualSize uri=${row.savedUri}"
            )
            if (actualSize in 0 until downloaded) {
                throw DownloadFailure(
                    DownloadErrorCode.STORAGE,
                    "File on disk is shorter than progress ($actualSize/$downloaded)",
                    retryable = false,
                )
            }
            target.close(false)
            val uri = target.finalizeSuccess()
            android.util.Log.i("F7Engine", "finalized id=${row.id} uri=$uri")
            repository.location(row.id, uri, target.displayPath)
            finishSuccess(row.id, total = if (total > 0) total else downloaded, downloaded = downloaded, settings = settings)
        } catch (e: Throwable) {
            target.close(false)
            repository.clearLiveStats(row.id)
            throw e
        }
    }

    // HLS ---------------------------------------------------------------------

    private suspend fun runHlsTask(row0: DownloadEntity, settings: AppSettings, control: DownloadControl) {
        var row = row0
        val headers = row.httpHeaders()
        val maxRetries = if (settings.autoRetry) settings.maxRetries else 0
        val client = Http.client(settings)
        val storedMeta: HlsMeta? = row.hlsMetaJson?.let {
            runCatching { json.decodeFromString<HlsMeta>(it) }.getOrNull()
        }

        var hlsMeta: HlsMeta = storedMeta ?: run {
            val loaded = HlsLoader(client).load(row.url, headers, settings.hlsPreferredQuality)
            if (control.stopped) return
            val extension = loaded.outputExtension
            val mime = if (extension == "mp4") "video/mp4" else "video/mp2t"
            row = row.copy(
                kind = DownloadKind.HLS.name,
                fileName = hlsFileName(loaded.mediaUrl, extension),
                mimeType = mime,
                category = FileCategory.VIDEO.name,
                totalBytes = 0,
                supportsResume = true,
                sizeIsExact = false,
                hlsMetaJson = json.encodeToString(loaded),
                status = DownloadStatus.DOWNLOADING.name,
                error = null,
            )
            repository.update(row)
            loaded
        }

        val reopened: StorageTarget? = row.savedUri?.let {
            repository.storageManager.reopen(row.storageKind, it)
        }
        val target: StorageTarget = if (reopened != null) {
            reopened
        } else {
            row.savedUri?.let { repository.storageManager.delete(row.storageKind, it) }
            val created = repository.storageManager.create(row.fileName, row.mimeType, settings)
            hlsMeta = hlsMeta.copy(completedSegments = 0, completedBytes = 0)
            row = row.copy(
                fileName = created.fileName,
                savedUri = created.target.uri,
                displayPath = created.target.displayPath,
                storageKind = created.target.kind,
                downloadedBytes = 0,
                hlsMetaJson = json.encodeToString(hlsMeta),
            )
            repository.update(row)
            repository.location(row.id, created.target.uri, created.target.displayPath)
            created.target
        }

        repository.setStatus(row.id, DownloadStatus.DOWNLOADING)
        repository.progress(row.id, hlsMeta.completedBytes, 0, 1, false)
        val tracker = JobProgress(repository, row.id)

        try {
            val downloader = HlsDownloader(
                client = client,
                repository = repository,
                target = target,
                downloadId = row.id,
                meta = hlsMeta,
                headers = headers,
                maxRetries = maxRetries,
                bufferSize = settings.chunkSizeKb * 1024,
                control = control,
                onProgress = { offset -> tracker.update(offset, 0, 1) },
                persistMeta = { updated ->
                    val current = repository.get(row.id) ?: row
                    repository.update(
                        current.copy(
                            hlsMetaJson = json.encodeToString(updated),
                            downloadedBytes = updated.completedBytes,
                            totalBytes = 0,
                        )
                    )
                },
            )
            val finalMeta = downloader.run()
            if (control.stopped) {
                target.close(false)
                repository.progress(row.id, finalMeta.completedBytes, 0, 0, false)
                return
            }
            if (finalMeta.completedSegments < finalMeta.segments.size) {
                throw DownloadFailure(DownloadErrorCode.NETWORK, "HLS video is incomplete")
            }
            target.close(false)
            val uri = target.finalizeSuccess()
            repository.location(row.id, uri, target.displayPath)
            val current = repository.get(row.id) ?: row
            repository.update(
                current.copy(
                    totalBytes = finalMeta.completedBytes,
                    downloadedBytes = finalMeta.completedBytes,
                    status = DownloadStatus.COMPLETED.name,
                    completedAt = System.currentTimeMillis(),
                    sizeIsExact = true,
                    hlsMetaJson = json.encodeToString(finalMeta),
                    error = null,
                    activeConnections = 0,
                )
            )
            repository.completed(row.id)
            recordCompletion(row.id, settings)
        } catch (e: Throwable) {
            target.close(false)
            repository.clearLiveStats(row.id)
            throw e
        }
    }

    private suspend fun finishSuccess(id: Long, total: Long, downloaded: Long, settings: AppSettings) {
        val current = repository.get(id) ?: return
        repository.update(
            current.copy(
                totalBytes = total.coerceAtLeast(downloaded),
                downloadedBytes = downloaded,
                status = DownloadStatus.COMPLETED.name,
                completedAt = System.currentTimeMillis(),
                sizeIsExact = true,
                error = null,
                activeConnections = 0,
            )
        )
        repository.completed(id)
        recordCompletion(id, settings)
    }

    private suspend fun recordFailure(id: Long) {
        val row = repository.get(id) ?: return
        repository.recordEvent(
            downloadId = id,
            fileName = row.fileName,
            category = row.category,
            bytes = row.downloadedBytes,
            durationMs = (System.currentTimeMillis() - row.createdAt).coerceAtLeast(1),
            success = false,
        )
    }

    private suspend fun recordCompletion(id: Long, settings: AppSettings) {
        val row = repository.get(id) ?: return
        val completedAt = row.completedAt ?: System.currentTimeMillis()
        repository.recordEvent(
            downloadId = id,
            fileName = row.fileName,
            category = row.category,
            bytes = row.downloadedBytes,
            durationMs = (completedAt - (row.startedAt ?: row.createdAt)).coerceAtLeast(1),
            success = true,
        )
        if (settings.autoChecksum) {
            runCatching {
                val digest = repository.storageManager.openInput(row.storageKind, row.savedUri ?: return)
                    ?.use { input ->
                        val md = java.security.MessageDigest.getInstance("SHA-256")
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            md.update(buffer, 0, read)
                        }
                        md.digest().joinToString("") { "%02x".format(it) }
                    }
                if (digest != null) repository.checksum(id, digest, "SHA-256")
            }
        }
    }

    // Helpers -----------------------------------------------------------------

    private fun hlsFileName(mediaUrl: String, extension: String): String {
        val path = mediaUrl.substringBefore('?').substringBefore('#')
        val parts = path.split('/').filter { it.isNotBlank() }
        var base = parts.lastOrNull().orEmpty()
        base = runCatching { URLDecoder.decode(base, "UTF-8") }.getOrDefault(base)
        base = base.removeSuffix(".m3u8").removeSuffix(".m3u").removeSuffix(".M3U8")
        val generic = setOf("index", "master", "playlist", "manifest", "media", "video", "prog_index")
        if (base.isBlank() || base.lowercase() in generic) {
            val parent = parts.dropLast(1).lastOrNull()?.lowercase()
            base = if (parent != null && parent.length in 3..32 && parent !in generic) {
                "$parent-video"
            } else {
                "F7 Video"
            }
        }
        return F7Format.sanitizeFileName("$base.$extension")
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private class JobProgress(
        private val repository: DownloadsRepository,
        private val id: Long,
    ) {
        private var lastBytes = -1L
        private var lastTime = 0L
        private var smoothed = 0.0

        fun update(downloaded: Long, total: Long, active: Int) {
            val now = SystemClock.elapsedRealtime()
            if (lastTime == 0L) {
                lastTime = now
                lastBytes = downloaded
                return
            }
            val elapsed = now - lastTime
            if (elapsed < 500) return
            val delta = downloaded - lastBytes
            if (delta >= 0) {
                val instant = delta * 1000.0 / elapsed
                smoothed = if (smoothed <= 0.0) instant else smoothed * 0.65 + instant * 0.35
            }
            val eta = if (total > 0 && smoothed > 1) ((total - downloaded) / smoothed).toLong().coerceAtLeast(0) else 0
            repository.setLiveStats(id, smoothed.toLong(), active, eta)
            lastBytes = downloaded
            lastTime = now
        }
    }
}











