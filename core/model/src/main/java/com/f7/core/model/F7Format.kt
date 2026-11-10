package com.f7.core.model

import java.net.URLDecoder
import java.util.Locale

object F7Format {

    fun bytes(value: Long): String {
        if (value < 0) return "—"
        if (value < 1024) return "$value B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var size = value.toDouble()
        var unit = -1
        while (size >= 1024 && unit < units.lastIndex) {
            size /= 1024
            unit++
        }
        return if (size >= 100) {
            String.format(Locale.US, "%.0f %s", size, units[unit])
        } else {
            String.format(Locale.US, "%.1f %s", size, units[unit])
        }
    }

    fun speed(bytesPerSec: Long): String =
        if (bytesPerSec <= 0) "—" else bytes(bytesPerSec) + "/s"

    fun eta(seconds: Long): String {
        if (seconds <= 0) return "—"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    fun percent(progress: Float): String = "${(progress * 100).toInt()}%"

    fun sanitizeFileName(raw: String): String {
        var name = raw.trim().replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_")
        name = name.replace(Regex("\\s+"), " ").trim().trimEnd('.')
        if (name.isEmpty()) name = "download"
        if (name.length > 180) {
            val dot = name.lastIndexOf('.')
            name = if (dot > 20) {
                name.substring(0, 150) + name.substring(dot)
            } else {
                name.take(180)
            }
        }
        return name
    }

    fun guessFileName(
        url: String,
        contentDisposition: String? = null,
        mimeType: String? = null,
    ): String {
        contentDisposition?.let { cd ->
            val encoded = Regex("filename\\*\\s*=\\s*UTF-8''([^;]+)", RegexOption.IGNORE_CASE)
                .find(cd)?.groupValues?.get(1)
            if (!encoded.isNullOrBlank()) {
                val decoded = runCatching { URLDecoder.decode(encoded.trim(), "UTF-8") }.getOrNull()
                if (!decoded.isNullOrBlank()) return sanitizeFileName(decoded)
            }
            val plain = Regex("filename\\s*=\\s*\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)
                .find(cd)?.groupValues?.get(1)
            if (!plain.isNullOrBlank()) {
                val decoded = runCatching { URLDecoder.decode(plain.trim(), "UTF-8") }.getOrNull()
                return sanitizeFileName(decoded ?: plain)
            }
        }

        val withoutQuery = url.substringBefore('#').substringBefore('?')
        val lastSegment = withoutQuery.trimEnd('/').substringAfterLast('/')
        var candidate = runCatching { URLDecoder.decode(lastSegment, "UTF-8") }.getOrDefault(lastSegment)
        candidate = sanitizeFileName(candidate)

        val hasExtension = candidate.substringAfterLast('.', "").length in 1..5
        if (candidate.isBlank() || candidate == "download" || !hasExtension) {
            val ext = extensionForMime(mimeType)
            if (candidate.isBlank() || candidate == "download") {
                candidate = "F7_${System.currentTimeMillis()}"
            }
            if (ext != null && !candidate.lowercase().endsWith(".$ext")) {
                candidate = "$candidate.$ext"
            }
        }
        return candidate
    }

    fun extensionForMime(mime: String?): String? = when (mime?.lowercase()?.substringBefore(';')?.trim()) {
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/x-matroska" -> "mkv"
        "video/mp2t" -> "ts"
        "audio/mpeg" -> "mp3"
        "audio/mp4", "audio/x-m4a" -> "m4a"
        "audio/aac" -> "aac"
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "application/pdf" -> "pdf"
        "application/zip" -> "zip"
        "application/vnd.android.package-archive" -> "apk"
        "application/json" -> "json"
        "text/plain" -> "txt"
        "application/octet-stream" -> null
        else -> null
    }

    fun isPlaylistUrl(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return path.endsWith(".m3u8") || path.endsWith(".m3u")
    }

    fun displayPathFromName(name: String): String = "Download/F7/$name"
}

