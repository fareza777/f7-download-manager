package com.f7.core.download.hls

import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
data class HlsSegmentMeta(
    val url: String,
    val rangeStart: Long? = null,
    val rangeLength: Long? = null,
    val duration: Double = 0.0,
)

@Serializable
data class HlsKeyMeta(
    val method: String,
    val url: String,
    val ivHex: String? = null,
)

@Serializable
data class HlsMeta(
    val mediaUrl: String,
    val segments: List<HlsSegmentMeta>,
    val key: HlsKeyMeta? = null,
    val initSegment: String? = null,
    val initRangeStart: Long? = null,
    val initRangeLength: Long? = null,
    val completedSegments: Int = 0,
    val completedBytes: Long = 0,
    val outputExtension: String = "ts",
    val segmentSequence: Long = 0,
    val qualityLabel: String? = null,
    val totalSegments: Int = 0,
)

data class HlsVariant(
    val url: String,
    val bandwidth: Long,
    val resolution: String?,
)

data class HlsMediaPlaylist(
    val mediaUrl: String,
    val segments: List<HlsSegmentMeta>,
    val key: HlsKeyMeta?,
    val initSegment: String?,
    val initRangeStart: Long?,
    val initRangeLength: Long?,
    val sequence: Long,
    val isLive: Boolean,
)

class HlsPlaylistException(message: String, val code: String) : Exception(message)

object HlsPlaylistParser {

    private fun attributes(line: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val regex = Regex("([A-Z0-9-]+)=(\"[^\"]*\"|[^,]*)")
        regex.findAll(line.substringAfter(':', "")).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2].trim().removeSurrounding("\"")
            result[key] = value
        }
        return result
    }

    private fun resolve(base: String, reference: String): String {
        val baseUrl = base.toHttpUrlOrNull() ?: return reference
        return baseUrl.resolve(reference)?.toString() ?: reference
    }

    fun isMaster(text: String): Boolean =
        text.lineSequence().any { it.startsWith("#EXT-X-STREAM-INF") }

    fun parseMaster(text: String, baseUrl: String): List<HlsVariant> {
        val lines = text.lines()
        val variants = mutableListOf<HlsVariant>()
        var pendingBandwidth = 0L
        var pendingResolution: String? = null
        for (raw in lines) {
            val line = raw.trim()
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val attrs = attributes(line)
                pendingBandwidth = attrs["BANDWIDTH"]?.toLongOrNull() ?: attrs["AVERAGE-BANDWIDTH"]?.toLongOrNull() ?: 0L
                pendingResolution = attrs["RESOLUTION"]
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                if (pendingBandwidth > 0 || pendingResolution != null) {
                    variants += HlsVariant(resolve(baseUrl, line), pendingBandwidth, pendingResolution)
                    pendingBandwidth = 0
                    pendingResolution = null
                }
            }
        }
        return variants
    }

    fun parseMedia(text: String, baseUrl: String): HlsMediaPlaylist {
        if (!text.contains("#EXTM3U")) {
            throw HlsPlaylistException("Not a valid M3U8 playlist", "server")
        }
        val lines = text.lines()
        val segments = mutableListOf<HlsSegmentMeta>()
        var key: HlsKeyMeta? = null
        var initSegment: String? = null
        var initRangeStart: Long? = null
        var initRangeLength: Long? = null
        var sequence = 0L
        var endList = false
        var pendingDuration = 0.0
        var pendingRangeStart: Long? = null
        var pendingRangeLength: Long? = null
        var lastRangeEnd = -1L

        for (raw in lines) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") ->
                    sequence = line.substringAfter(':').trim().toLongOrNull() ?: 0L

                line.startsWith("#EXT-X-KEY:") -> {
                    val attrs = attributes(line)
                    val method = attrs["METHOD"] ?: "NONE"
                    if (method == "NONE") {
                        key = null
                    } else if (method != "AES-128") {
                        throw HlsPlaylistException(
                            "Playlist uses unsupported $method encryption (DRM)",
                            "drm"
                        )
                    } else {
                        val uri = attrs["URI"] ?: throw HlsPlaylistException("AES key without URI", "drm")
                        val newKey = HlsKeyMeta(
                            method = method,
                            url = resolve(baseUrl, uri),
                            ivHex = attrs["IV"]?.removePrefix("0x")?.removePrefix("0X"),
                        )
                        if (key != null && (key!!.url != newKey.url || key!!.ivHex != newKey.ivHex)) {
                            throw HlsPlaylistException("AES key rotation is not supported yet", "drm")
                        }
                        key = newKey
                    }
                }

                line.startsWith("#EXT-X-MAP:") -> {
                    val attrs = attributes(line)
                    val uri = attrs["URI"] ?: throw HlsPlaylistException("EXT-X-MAP without URI", "server")
                    initSegment = resolve(baseUrl, uri)
                    val range = attrs["BYTERANGE"]
                    if (range != null) {
                        val parts = range.split('@')
                        initRangeLength = parts.getOrNull(0)?.toLongOrNull()
                        initRangeStart = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                    }
                }

                line.startsWith("#EXT-X-BYTERANGE:") -> {
                    val range = line.substringAfter(':').trim()
                    val parts = range.split('@')
                    pendingRangeLength = parts.getOrNull(0)?.toLongOrNull()
                    pendingRangeStart = parts.getOrNull(1)?.toLongOrNull()
                        ?: if (lastRangeEnd >= 0) lastRangeEnd + 1 else 0L
                }

                line.startsWith("#EXTINF:") -> {
                    pendingDuration = line.substringAfter(':').substringBefore(',').trim().toDoubleOrNull() ?: 0.0
                }

                line == "#EXT-X-ENDLIST" -> endList = true
                line.startsWith("#") -> Unit
                else -> {
                    val start = pendingRangeStart
                    val length = pendingRangeLength
                    segments += HlsSegmentMeta(
                        url = resolve(baseUrl, line),
                        rangeStart = start,
                        rangeLength = length,
                        duration = pendingDuration,
                    )
                    if (start != null && length != null) lastRangeEnd = start + length - 1
                    pendingDuration = 0.0
                    pendingRangeStart = null
                    pendingRangeLength = null
                }
            }
        }

        if (segments.isEmpty()) throw HlsPlaylistException("Playlist has no segments", "server")
        return HlsMediaPlaylist(
            mediaUrl = baseUrl,
            segments = segments,
            key = key,
            initSegment = initSegment,
            initRangeStart = initRangeStart,
            initRangeLength = initRangeLength,
            sequence = sequence,
            isLive = !endList,
        )
    }
}






