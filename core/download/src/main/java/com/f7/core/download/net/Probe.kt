package com.f7.core.download.net

import com.f7.core.model.F7Format
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class ProbeResult(
    val finalUrl: String,
    val fileName: String,
    val mimeType: String,
    val totalBytes: Long,
    val supportsRanges: Boolean,
    val etag: String?,
    val lastModified: String?,
    val contentDisposition: String?,
)

/**
 * Inspects a URL once (HEAD, then a one-byte ranged GET when HEAD is unhelpful)
 * to learn size, filename, MIME type and whether byte ranges are honoured.
 */
class Probe(private val client: OkHttpClient) {

    fun probe(url: String, headers: Map<String, String>): ProbeResult {
        val head = runCatching {
            client.newCall(Http.baseRequest(url, headers).head().build()).execute().use { response ->
                if (!response.isSuccessful) null else read(response.request.url.toString(), response)
            }
        }.getOrNull()

        if (head != null && head.totalBytes > 0 && head.supportsRanges) return head

        // Many servers reject HEAD or omit Content-Length. Ask for the first byte.
        val rangedAttempt = runCatching {
            client.newCall(
                Http.baseRequest(url, headers)
                    .header("Range", "bytes=0-0")
                    .get()
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    throw com.f7.core.download.DownloadFailure.http(response.code)
                }
                read(response.request.url.toString(), response)
            }
        }
        rangedAttempt.exceptionOrNull()?.let { error ->
            if (error is com.f7.core.download.DownloadFailure) throw error
        }
        return rangedAttempt.getOrNull() ?: head ?: throw IOException("Server did not respond")
    }

    private fun read(finalUrl: String, response: okhttp3.Response): ProbeResult {
        val contentRange = response.header("Content-Range")
        val rangeTotal = contentRange?.substringAfterLast('/', "0")?.toLongOrNull() ?: 0L
        val length = response.header("Content-Length")?.toLongOrNull() ?: 0L
        val total = if (rangeTotal > 0) rangeTotal else if (response.code == 206) rangeTotal else length
        val mime = response.header("Content-Type")?.substringBefore(';')?.trim()
            ?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
        val disposition = response.header("Content-Disposition")
        val name = F7Format.guessFileName(finalUrl, disposition, mime)
        val acceptRanges = response.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
        val supports = (response.code == 206 && rangeTotal > 0) ||
            (acceptRanges && total > 0) ||
            (length > 0 && acceptRanges)

        return ProbeResult(
            finalUrl = finalUrl,
            fileName = name,
            mimeType = mime,
            totalBytes = total,
            supportsRanges = supports,
            etag = response.header("ETag"),
            lastModified = response.header("Last-Modified"),
            contentDisposition = disposition,
        )
    }
}

