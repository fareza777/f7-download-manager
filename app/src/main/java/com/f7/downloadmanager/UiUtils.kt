package com.f7.downloadmanager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.f7.core.download.DownloadErrorCode
import com.f7.core.model.AppSettings
import com.f7.core.model.DownloadItem
import com.f7.core.model.FileCategory
import com.f7.downloadmanager.R
import java.io.File
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun DownloadItem.iconRes(): Int = when (category) {
    FileCategory.VIDEO -> R.drawable.ic_f7_movie
    FileCategory.AUDIO -> R.drawable.ic_f7_music_note
    FileCategory.IMAGE -> R.drawable.ic_f7_image
    FileCategory.DOCUMENT -> R.drawable.ic_f7_description
    FileCategory.ARCHIVE -> R.drawable.ic_f7_archive
    FileCategory.APP -> R.drawable.ic_f7_android
    FileCategory.OTHER -> R.drawable.ic_f7_file_open
}

fun FileCategory.iconRes(): Int = when (this) {
    FileCategory.VIDEO -> R.drawable.ic_f7_movie
    FileCategory.AUDIO -> R.drawable.ic_f7_music_note
    FileCategory.IMAGE -> R.drawable.ic_f7_image
    FileCategory.DOCUMENT -> R.drawable.ic_f7_description
    FileCategory.ARCHIVE -> R.drawable.ic_f7_archive
    FileCategory.APP -> R.drawable.ic_f7_android
    FileCategory.OTHER -> R.drawable.ic_f7_file_open
}

fun FileCategory.labelRes(): Int = when (this) {
    FileCategory.VIDEO -> R.string.category_video
    FileCategory.AUDIO -> R.string.category_audio
    FileCategory.IMAGE -> R.string.category_image
    FileCategory.DOCUMENT -> R.string.category_document
    FileCategory.ARCHIVE -> R.string.category_archive
    FileCategory.APP -> R.string.category_app
    FileCategory.OTHER -> R.string.category_other
}

fun DownloadItem.extensionLabel(): String {
    val ext = fileName.substringAfterLast('.', "").uppercase(Locale.US)
    return if (ext.length in 1..4) ext else "FILE"
}

@Composable
fun categoryColors(category: FileCategory): Pair<Color, Color> {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.4f
    val video = Color(0xFF2457E6) to Color(0xFFE8EEFF)
    val audio = Color(0xFF7A3BC4) to Color(0xFFF3E9FF)
    val image = Color(0xFFB06A00) to Color(0xFFFFF1DC)
    val document = Color(0xFF2C5F7A) to Color(0xFFE8F0F6)
    val archive = Color(0xFF0F7A5E) to Color(0xFFE3F5EF)
    val app = Color(0xFF2C7A3F) to Color(0xFFE7F6E9)
    val other = Color(0xFF5D6577) to Color(0xFFF0F1F4)
    val pair = when (category) {
        FileCategory.VIDEO -> video
        FileCategory.AUDIO -> audio
        FileCategory.IMAGE -> image
        FileCategory.DOCUMENT -> document
        FileCategory.ARCHIVE -> archive
        FileCategory.APP -> app
        FileCategory.OTHER -> other
    }
    return if (dark) {
        val tint = when (category) {
            FileCategory.VIDEO -> Color(0xFF9DB8FF)
            FileCategory.AUDIO -> Color(0xFFCFB0FF)
            FileCategory.IMAGE -> Color(0xFFFFC978)
            FileCategory.DOCUMENT -> Color(0xFFA6CBE0)
            FileCategory.ARCHIVE -> Color(0xFF7FDCC2)
            FileCategory.APP -> Color(0xFF9EDBA9)
            FileCategory.OTHER -> Color(0xFFB9C0CE)
        }
        tint to tint.copy(alpha = 0.14f)
    } else {
        pair.first to pair.second
    }
}

fun themeNameRes(id: String): Int = when (id) {
    "emerald" -> R.string.settings_theme_emerald
    "sunset" -> R.string.settings_theme_sunset
    "rose" -> R.string.settings_theme_rose
    "graphite" -> R.string.settings_theme_graphite
    else -> R.string.settings_theme_aurora
}

fun statusLabelRes(item: DownloadItem): Int = when (item.status) {
    com.f7.core.model.DownloadStatus.QUEUED -> R.string.status_queued
    com.f7.core.model.DownloadStatus.CONNECTING -> R.string.status_connecting
    com.f7.core.model.DownloadStatus.DOWNLOADING -> R.string.status_downloading
    com.f7.core.model.DownloadStatus.PAUSED ->
        when (item.error) {
            "waiting_network", "waiting_wifi", "wifi_only" -> R.string.status_waiting_wifi
            "waiting_unmetered" -> R.string.waiting_unmetered
            "waiting_power" -> R.string.waiting_power
            else -> R.string.status_paused
        }
    com.f7.core.model.DownloadStatus.COMPLETED -> R.string.status_completed
    com.f7.core.model.DownloadStatus.FAILED -> R.string.status_failed
    com.f7.core.model.DownloadStatus.VERIFYING -> R.string.status_verifying
    com.f7.core.model.DownloadStatus.SCHEDULED -> R.string.status_scheduled
    com.f7.core.model.DownloadStatus.CANCELLED -> R.string.status_cancelled
}

fun errorCodeOf(error: String?): String = error?.substringBefore('|').orEmpty()
fun errorDetailOf(error: String?): String? = error?.substringAfter('|', "")?.takeIf { it.isNotBlank() }

fun localizedError(context: Context, error: String?): String {
    val code = errorCodeOf(error)
    if (code.isBlank()) return context.getString(R.string.error_unknown)
    return when (code) {
        DownloadErrorCode.NETWORK -> context.getString(R.string.error_network)
        DownloadErrorCode.TIMEOUT -> context.getString(R.string.error_timeout)
        DownloadErrorCode.DNS -> context.getString(R.string.error_dns)
        DownloadErrorCode.SSL -> context.getString(R.string.error_ssl)
        DownloadErrorCode.STORAGE -> context.getString(R.string.error_storage)
        DownloadErrorCode.RANGE -> context.getString(R.string.error_range)
        DownloadErrorCode.SERVER -> context.getString(R.string.error_server)
        DownloadErrorCode.DRM -> context.getString(R.string.error_drm)
        DownloadErrorCode.LIVE -> context.getString(R.string.error_live)
        DownloadErrorCode.HTTP -> {
            val httpCode = Regex("(\\d{3})").find(errorDetailOf(error).orEmpty())?.groupValues?.get(1)?.toIntOrNull()
            when {
                httpCode == 403 -> context.getString(R.string.error_http_403)
                httpCode == 404 -> context.getString(R.string.error_http_404)
                httpCode == 429 -> context.getString(R.string.error_http_429)
                httpCode != null && httpCode >= 500 -> context.getString(R.string.error_http_5xx)
                httpCode != null -> context.getString(R.string.error_http, httpCode.toString())
                else -> context.getString(R.string.error_server)
            }
        }
        "waiting_network" -> context.getString(R.string.error_waiting_network)
        "waiting_wifi" -> context.getString(R.string.error_wifi_only)
        "waiting_unmetered" -> context.getString(R.string.waiting_unmetered)
        "waiting_power" -> context.getString(R.string.waiting_power)
        "wifi_only" -> context.getString(R.string.error_wifi_only)
        else -> context.getString(R.string.error_unknown)
    }
}

fun storageLabel(settings: AppSettings, context: Context): String = when {
    settings.storageMode == "saf" && settings.storageTreeUri != null ->
        Uri.parse(settings.storageTreeUri).lastPathSegment?.substringAfterLast(':')?.takeIf { it.isNotBlank() }
            ?.let { "SAF/$it" } ?: context.getString(R.string.storage_default)
    settings.storageMode == "app" -> context.getString(R.string.settings_storage_app)
    else -> context.getString(R.string.storage_default)
}

fun formatBytes(value: Long): String = com.f7.core.model.F7Format.bytes(value)
fun formatSpeed(value: Long): String = com.f7.core.model.F7Format.speed(value)
fun formatEta(seconds: Long): String = com.f7.core.model.F7Format.eta(seconds)

fun formatDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
        .format(Date(millis))

fun formatTime(millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(millis))

fun formatDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(millis))

fun clipboardText(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = manager.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context).toString().takeIf { it.isNotBlank() }
}

fun copyToClipboard(context: Context, label: String, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun fileUriFor(context: Context, item: DownloadItem): Uri? {
    val saved = item.savedUri ?: return null
    return if (item.storageKind == "app") {
        val path = if (saved.startsWith("file://")) Uri.parse(saved).path ?: return null else saved
        val file = File(path)
        if (!file.exists()) return null
        runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }.getOrNull()
    } else {
        Uri.parse(saved)
    }
}

fun openDownload(context: Context, item: DownloadItem): Boolean {
    val uri = fileUriFor(context, item) ?: return false
    val mime = item.mimeType.takeIf { it.isNotBlank() } ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(intent) }.isSuccess
}

fun shareDownload(context: Context, item: DownloadItem): Boolean {
    val uri = fileUriFor(context, item) ?: return false
    val mime = item.mimeType.takeIf { it.isNotBlank() } ?: "*/*"
    val intent = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(Intent.createChooser(intent, item.fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}

suspend fun computeDigest(context: Context, item: DownloadItem, algorithm: String = "SHA-256"): String? =
    withContext(Dispatchers.IO) {
        val app = context.applicationContext as? F7Application ?: return@withContext null
        val stream = app.graph.storageManager.openInput(item.storageKind, item.savedUri ?: return@withContext null)
            ?: return@withContext null
        stream.use { input ->
            val digest = MessageDigest.getInstance(algorithm)
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

