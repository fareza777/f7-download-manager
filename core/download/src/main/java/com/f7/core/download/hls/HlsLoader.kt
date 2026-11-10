package com.f7.core.download.hls

import com.f7.core.download.DownloadErrorCode
import com.f7.core.download.DownloadFailure
import com.f7.core.download.backoffMillis
import com.f7.core.download.net.Http
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.io.IOException

class HlsLoader(private val client: OkHttpClient) {

    suspend fun load(playlistUrl: String, headers: Map<String, String>, quality: String): HlsMeta {
        var attempt = 0
        while (true) {
            try {
                val (masterText, masterUrl) = fetchText(playlistUrl, headers)
                if (HlsPlaylistParser.isMaster(masterText)) {
                    val variants = HlsPlaylistParser.parseMaster(masterText, masterUrl)
                    if (variants.isEmpty()) {
                        throw DownloadFailure(DownloadErrorCode.SERVER, "Master playlist has no variants")
                    }
                    val chosen = if (quality == "worst") {
                        variants.minByOrNull { it.bandwidth } ?: variants.first()
                    } else {
                        variants.maxByOrNull { it.bandwidth } ?: variants.first()
                    }
                    val (mediaText, mediaUrl) = fetchText(chosen.url, headers)
                    return toMeta(mediaText, mediaUrl, chosen)
                }
                return toMeta(masterText, masterUrl, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: DownloadFailure) {
                if (!e.retryable || ++attempt > 2) throw e
                delay(backoffMillis(attempt))
            } catch (e: IOException) {
                if (++attempt > 2) {
                    throw DownloadFailure(DownloadErrorCode.NETWORK, e.message?.take(160))
                }
                delay(backoffMillis(attempt))
            }
        }
    }

    private fun toMeta(text: String, baseUrl: String, variant: HlsVariant?): HlsMeta {
        val playlist = try {
            HlsPlaylistParser.parseMedia(text, baseUrl)
        } catch (e: HlsPlaylistException) {
            val code = when (e.code) {
                "drm" -> DownloadErrorCode.DRM
                "live" -> DownloadErrorCode.LIVE
                else -> DownloadErrorCode.SERVER
            }
            throw DownloadFailure(code, e.message, retryable = false)
        }
        if (playlist.isLive) {
            throw DownloadFailure(
                DownloadErrorCode.LIVE,
                "Live playlists are not supported. Use an on-demand (VOD) video.",
                retryable = false
            )
        }
        val extension = if (playlist.initSegment != null) "mp4" else "m2ts"
        return HlsMeta(
            mediaUrl = baseUrl,
            segments = playlist.segments,
            key = playlist.key,
            initSegment = playlist.initSegment,
            initRangeStart = playlist.initRangeStart,
            initRangeLength = playlist.initRangeLength,
            outputExtension = extension,
            segmentSequence = playlist.sequence,
            qualityLabel = variant?.resolution ?: variant?.bandwidth?.let { "${it / 1000} kbps" },
            totalSegments = playlist.segments.size,
        )
    }

    private fun fetchText(url: String, headers: Map<String, String>): Pair<String, String> {
        val call = client.newCall(Http.baseRequest(url, headers).get().build())
        call.execute().use { response ->
            if (!response.isSuccessful) throw DownloadFailure.http(response.code)
            val body = response.body ?: throw DownloadFailure(DownloadErrorCode.SERVER, "Empty playlist")
            if (body.contentLength() > 8L * 1024 * 1024) {
                throw DownloadFailure(DownloadErrorCode.SERVER, "Playlist is too large")
            }
            val text = body.string()
            return text to response.request.url.toString()
        }
    }
}




