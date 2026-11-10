package com.f7.core.download.hls

import com.f7.core.data.DownloadsRepository
import com.f7.core.data.storage.StorageTarget
import com.f7.core.download.DownloadControl
import com.f7.core.download.DownloadErrorCode
import com.f7.core.download.DownloadFailure
import com.f7.core.download.SpeedGovernor
import com.f7.core.download.backoffMillis
import com.f7.core.download.net.Http
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Downloads a VOD HLS (M3U8) rendition by concatenating its media segments.
 * AES-128 segment encryption is supported; SAMPLE-AES/DRM and live playlists are
 * rejected explicitly rather than producing a broken file.
 */
class HlsDownloader(
    private val client: OkHttpClient,
    private val repository: DownloadsRepository,
    private val target: StorageTarget,
    private val downloadId: Long,
    private val meta: HlsMeta,
    private val headers: Map<String, String>,
    private val maxRetries: Int,
    private val bufferSize: Int,
    private val control: DownloadControl,
    private val onProgress: (downloaded: Long) -> Unit,
    private val persistMeta: suspend (HlsMeta) -> Unit,
) {

    private val keyCache = mutableMapOf<String, ByteArray>()

    suspend fun run(): HlsMeta {
        var current = meta
        val existingSize = target.currentSize()
        if (existingSize > current.completedBytes) {
            target.truncate(current.completedBytes)
        } else if (existingSize >= 0 && existingSize < current.completedBytes) {
            // The partial file was removed or truncated behind our back: start clean.
            current = current.copy(completedSegments = 0, completedBytes = 0)
            target.truncate(0)
            persistMeta(current)
        }

        var offset = current.completedBytes
        if (current.initSegment != null && current.completedSegments == 0 && offset == 0L) {
            val initBytes = downloadPart(
                url = current.initSegment,
                rangeStart = current.initRangeStart,
                rangeLength = current.initRangeLength,
            )
            target.writeAt(0, initBytes, initBytes.size)
            offset = initBytes.size.toLong()
            onProgress(offset)
        }

        val sequence = current.segmentSequence
        for (index in current.completedSegments until current.segments.size) {
            if (control.stopped) break
            val segment = current.segments[index]
            val bytesWritten = downloadSegment(segment, current.key, sequence + index, offset)
            if (control.stopped || bytesWritten <= 0) break
            offset += bytesWritten
            target.flush()
            current = current.copy(
                completedSegments = index + 1,
                completedBytes = offset,
            )
            persistMeta(current)
            repository.progress(downloadId, offset, 0, 1, false)
            onProgress(offset)
        }
        return current
    }

    private suspend fun downloadSegment(
        segment: HlsSegmentMeta,
        key: HlsKeyMeta?,
        mediaSequence: Long,
        outputOffset: Long,
    ): Long {
        var attempt = 0
        while (!control.stopped) {
            try {
                if (key == null) {
                    return streamToTarget(segment, outputOffset)
                }
                val encrypted = downloadPart(segment.url, segment.rangeStart, segment.rangeLength)
                if (encrypted.size > 64 * 1024 * 1024) {
                    throw DownloadFailure(DownloadErrorCode.STORAGE, "Encrypted segment is too large")
                }
                val plain = decrypt(encrypted, key, mediaSequence)
                target.writeAt(outputOffset, plain, plain.size)
                return plain.size.toLong()
            } catch (e: CancellationException) {
                throw e
            } catch (e: DownloadFailure) {
                if (!e.retryable) throw e
                attempt++
                if (attempt > maxRetries) throw e
                delay(backoffMillis(attempt))
            } catch (e: IOException) {
                if (control.stopped) return 0
                attempt++
                if (attempt > maxRetries) {
                    throw DownloadFailure(DownloadErrorCode.NETWORK, e.message?.take(160))
                }
                delay(backoffMillis(attempt))
            }
        }
        return 0
    }

    private suspend fun streamToTarget(segment: HlsSegmentMeta, outputOffset: Long): Long {
        val builder = Http.baseRequest(segment.url, headers)
        if (segment.rangeStart != null && segment.rangeLength != null) {
            builder.header("Range", "bytes=${segment.rangeStart}-${segment.rangeStart + segment.rangeLength - 1}")
        }
        val call = client.newCall(builder.build())
        val cancelHandle = kotlin.coroutines.coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
        var written = 0L
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw DownloadFailure.http(response.code)
                val body = response.body ?: throw DownloadFailure(DownloadErrorCode.SERVER, "Empty segment")
                val input = body.byteStream()
                if (response.code == 200 && segment.rangeStart != null && segment.rangeStart > 0) {
                    var toSkip = segment.rangeStart
                    while (toSkip > 0) {
                        val skipped = input.skip(toSkip)
                        if (skipped <= 0) break
                        toSkip -= skipped
                    }
                }
                val limit = segment.rangeLength ?: Long.MAX_VALUE
                val buffer = ByteArray(bufferSize.coerceIn(8 * 1024, 512 * 1024))
                while (!control.stopped && written < limit) {
                    val want = minOf(buffer.size.toLong(), limit - written).toInt()
                    val read = input.read(buffer, 0, want)
                    if (read <= 0) break
                    SpeedGovernor.acquire(read)
                    target.writeAt(outputOffset + written, buffer, read)
                    written += read
                    onProgress(outputOffset + written)
                }
            }
        } finally {
            cancelHandle?.dispose()
        }
        if (segment.rangeLength != null && written != segment.rangeLength) {
            throw IOException("Incomplete segment ($written/${segment.rangeLength})")
        }
        return written
    }

    private suspend fun downloadPart(url: String, rangeStart: Long?, rangeLength: Long?): ByteArray {
        val builder = Http.baseRequest(url, headers)
        if (rangeStart != null && rangeLength != null) {
            builder.header("Range", "bytes=$rangeStart-${rangeStart + rangeLength - 1}")
        }
        val call = client.newCall(builder.build())
        val cancelHandle = kotlin.coroutines.coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw DownloadFailure.http(response.code)
                val body = response.body ?: throw DownloadFailure(DownloadErrorCode.SERVER, "Respons kosong")
                val bytes = body.bytes()
                if (response.code == 200 && rangeStart != null && rangeStart > 0) {
                    val from = rangeStart.toInt().coerceAtMost(bytes.size)
                    val to = if (rangeLength != null) (from + rangeLength.toInt()).coerceAtMost(bytes.size) else bytes.size
                    return bytes.copyOfRange(from, to)
                }
                if (rangeLength != null) {
                    val to = rangeLength.toInt().coerceAtMost(bytes.size)
                    return bytes.copyOfRange(0, to)
                }
                return bytes
            }
        } finally {
            cancelHandle?.dispose()
        }
    }

    private fun decrypt(encrypted: ByteArray, key: HlsKeyMeta, mediaSequence: Long): ByteArray {
        val keyBytes = keyCache.getOrPut(key.url) { fetchKey(key.url) }
        val iv = key.ivHex?.let { hexToBytes(it) } ?: run {
            val raw = ByteArray(16)
            var value = mediaSequence
            for (i in 15 downTo 0) {
                raw[i] = (value and 0xFF).toByte()
                value = value shr 8
            }
            raw
        }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(encrypted)
    }

    private fun fetchKey(url: String): ByteArray {
        val call = client.newCall(Http.baseRequest(url, headers).get().build())
        return call.execute().use { response ->
            if (!response.isSuccessful) throw DownloadFailure(DownloadErrorCode.DRM, "Failed to download the AES key")
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (bytes.size != 16) throw DownloadFailure(DownloadErrorCode.DRM, "Invalid AES key length")
            bytes
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.removePrefix("0x").removePrefix("0X")
        if (clean.length != 32) throw DownloadFailure(DownloadErrorCode.DRM, "Invalid AES IV")
        return ByteArray(16) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}








