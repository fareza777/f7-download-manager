package com.f7.core.download

import com.f7.core.data.DownloadsRepository
import com.f7.core.data.storage.StorageTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * Sequential download for servers without Range support or responses whose size
 * is unknown. It cannot resume from a byte offset (the server offers no way to
 * validate one), so a resume restarts cleanly from zero and overwrites the file.
 */
class StreamingHttpDownloader(
    private val client: OkHttpClient,
    private val repository: DownloadsRepository,
    private val target: StorageTarget,
    private val url: String,
    private val headers: Map<String, String>,
    private val downloadId: Long,
    private val expectedTotal: Long,
    private val maxRetries: Int,
    private val bufferSize: Int,
    private val control: DownloadControl,
    private val onProgress: (downloaded: Long) -> Unit,
) {

    suspend fun run(): Long {
        var attempt = 0
        while (!control.stopped) {
            try {
                return attemptStream()
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

    private suspend fun attemptStream(): Long {
        target.truncate(0)
        val call = client.newCall(com.f7.core.download.net.Http.baseRequest(url, headers).get().build())
        val cancelHandle = kotlin.coroutines.coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
        var written = 0L
        var lastPersist = 0L
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw DownloadFailure.http(response.code)
                val body = response.body ?: throw DownloadFailure(DownloadErrorCode.SERVER, "Respons kosong")
                val input = body.byteStream()
                val buffer = ByteArray(bufferSize.coerceIn(8 * 1024, 512 * 1024))
                while (!control.stopped) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    SpeedGovernor.acquire(read)
                    target.writeAt(written, buffer, read)
                    written += read
                    onProgress(written)
                    val now = System.currentTimeMillis()
                    if (now - lastPersist > 1_200) {
                        target.flush()
                        repository.progress(downloadId, written, expectedTotal, 1, false)
                        lastPersist = now
                    }
                }
            }
        } finally {
            cancelHandle?.dispose()
        }
        if (control.stopped) return written
        if (expectedTotal > 0 && written != expectedTotal) {
            throw IOException("File size mismatch (${written}/${expectedTotal} bytes)")
        }
        return written
    }
}


