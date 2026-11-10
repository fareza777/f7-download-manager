package com.f7.core.data.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.f7.core.data.prefs.SettingsStore
import com.f7.core.model.F7Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * A destination that supports random-access writes so several segments can be
 * downloaded in parallel and resumed at exact byte offsets.
 */
interface StorageTarget {
    val uri: String
    val displayPath: String
    val kind: String

    /** Opens (or creates) the destination. Returns false when it cannot be opened. */
    suspend fun open(): Boolean

    suspend fun writeAt(position: Long, buffer: ByteArray, length: Int)

    /** Forces buffered bytes to the provider before progress is written to Room. */
    suspend fun flush()

    suspend fun currentSize(): Long

    suspend fun truncate(size: Long): Boolean

    suspend fun close(deletePartial: Boolean)

    suspend fun finalizeSuccess(): String
}

data class CreatedTarget(
    val target: StorageTarget,
    val fileName: String,
)

class StorageManager(
    private val context: Context,
    private val settingsStore: SettingsStore,
) {

    suspend fun create(
        fileName: String,
        mimeType: String,
        settings: com.f7.core.model.AppSettings,
    ): CreatedTarget = withContext(Dispatchers.IO) {
        val safeName = sanitizeWithExtension(fileName, mimeType)
        when {
            settings.storageMode == "saf" && settings.storageTreeUri != null -> {
                val saf = createSaf(safeName, mimeType, settings.storageTreeUri!!)
                if (saf != null) return@withContext saf
                // Fall back to a location that always works rather than failing the download.
                createApp(safeName, mimeType)
            }
            settings.storageMode == "app" || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> createApp(safeName, mimeType)
            else -> createMediaStore(safeName, mimeType) ?: createApp(safeName, mimeType)
        }
    }

    /** Re-opens a target created earlier, using the persisted URI/path. */
    suspend fun reopen(kind: String, uri: String): StorageTarget? = withContext(Dispatchers.IO) {
        val target = when (kind) {
            "app" -> FileStorageTarget(uri.removePrefix("file://"), File(uri.removePrefix("file://")), uri)
            "saf", "media_store" -> UriStorageTarget(context, Uri.parse(uri), kind)
            else -> null
        }
        if (target != null && target.open()) target else null
    }

    suspend fun delete(kind: String, uri: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            when (kind) {
                "app" -> File(uri.removePrefix("file://")).delete()
                else -> context.contentResolver.delete(Uri.parse(uri), null, null) > 0
            }
        }.getOrDefault(false)
    }

    suspend fun openInput(kind: String, uri: String): java.io.InputStream? = withContext(Dispatchers.IO) {
        runCatching {
            if (kind == "app") {
                java.io.FileInputStream(File(uri.removePrefix("file://")))
            } else {
                context.contentResolver.openInputStream(Uri.parse(uri))
            }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private suspend fun createApp(name: String, mime: String): CreatedTarget {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        if (!base.exists()) base.mkdirs()
        val unique = uniqueFileName(name) { File(base, it).exists() }
        val file = File(base, unique)
        if (!file.exists()) file.createNewFile()
        val target = FileStorageTarget(file.absolutePath, file, file.toURI().toString())
        target.open()
        return CreatedTarget(target, unique)
    }

    private suspend fun createMediaStore(name: String, mime: String): CreatedTarget? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val unique = uniqueFileName(name) { candidate ->
                queryMediaStoreName(candidate)
            }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, unique)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/F7")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = context.contentResolver.insert(collection, values) ?: return null
            val target = UriStorageTarget(context, uri, "media_store")
            target.mediaStorePending = true
            if (!target.open()) {
                context.contentResolver.delete(uri, null, null)
                return null
            }
            CreatedTarget(target, unique)
        }.getOrNull()
    }

    private fun queryMediaStoreName(name: String): Boolean {
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
                arrayOf(name, "Download/F7%"),
                null
            )?.use { it.count > 0 } ?: false
        }.getOrDefault(false)
    }

    private suspend fun createSaf(name: String, mime: String, treeUri: String): CreatedTarget? {
        return runCatching {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return null
            if (!tree.canWrite()) return null
            val unique = uniqueFileName(name) { tree.findFile(it) != null }
            val doc = tree.createFile(mime, unique) ?: return null
            val target = UriStorageTarget(context, doc.uri, "saf")
            if (!target.open()) {
                doc.delete()
                return null
            }
            CreatedTarget(target, unique)
        }.getOrNull()
    }

    private fun uniqueFileName(name: String, exists: (String) -> Boolean): String {
        if (!exists(name)) return name
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (i < 1000) {
            val candidate = if (ext.isBlank()) "$base ($i)" else "$base ($i).$ext"
            if (!exists(candidate)) return candidate
            i++
        }
        return "${base}_${System.currentTimeMillis()}${if (ext.isBlank()) "" else ".$ext"}"
    }

    private fun sanitizeWithExtension(name: String, mime: String): String {
        var n = F7Format.sanitizeFileName(name)
        if (n.substringAfterLast('.', "").isBlank()) {
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                ?: F7Format.extensionForMime(mime)
            if (ext != null) n = "$n.$ext"
        }
        return n
    }
}

class FileStorageTarget(
    private val path: String,
    private val file: File,
    override val uri: String,
) : StorageTarget {
    override val displayPath: String = "F7 Download/$path"
    override val kind: String = "app"

    private var raf: RandomAccessFile? = null
    private var channel: FileChannel? = null

    override suspend fun open(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            file.parentFile?.mkdirs()
            val r = RandomAccessFile(file, "rw")
            raf = r
            channel = r.channel
            true
        }.getOrDefault(false)
    }

    override suspend fun writeAt(position: Long, buffer: ByteArray, length: Int) = withContext(Dispatchers.IO) {
        val ch = channel ?: error("Storage target is not open")
        val bb = ByteBuffer.wrap(buffer, 0, length)
        var written = 0
        while (bb.hasRemaining()) {
            val n = ch.write(bb, position + written)
            if (n <= 0) error("Disk write failed (no space or read-only storage)")
            written += n
        }
    }

    override suspend fun close(deletePartial: Boolean) = withContext(Dispatchers.IO) {
        runCatching { channel?.force(true) }
        runCatching { channel?.close() }
        runCatching { raf?.close() }
        channel = null
        raf = null
        if (deletePartial) runCatching { file.delete() }
    }

    override suspend fun currentSize(): Long = withContext(Dispatchers.IO) { file.length() }

    override suspend fun flush() = withContext(Dispatchers.IO) {
        runCatching { channel?.force(false) }
            .onFailure { android.util.Log.w("F7Storage", "flush failed for $path: ${it.message}") }
        Unit
    }

    override suspend fun truncate(size: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            channel?.truncate(size)
            true
        }.getOrDefault(false)
    }

    override suspend fun finalizeSuccess(): String = withContext(Dispatchers.IO) {
        runCatching { channel?.force(true) }
        path
    }
}

class UriStorageTarget(
    private val context: Context,
    private val contentUri: Uri,
    override val kind: String,
) : StorageTarget {
    override val uri: String = contentUri.toString()
    override val displayPath: String =
        runCatching { contentUri.lastPathSegment ?: contentUri.toString() }.getOrDefault(contentUri.toString())

    var mediaStorePending: Boolean = false

    private var pfd: android.os.ParcelFileDescriptor? = null
    private var channel: FileChannel? = null

    override suspend fun open(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val descriptor = context.contentResolver.openFileDescriptor(contentUri, "rw") ?: return@runCatching false
            pfd = descriptor
            channel = FileOutputStream(descriptor.fileDescriptor).channel
            true
        }.getOrDefault(false)
    }

    override suspend fun writeAt(position: Long, buffer: ByteArray, length: Int) = withContext(Dispatchers.IO) {
        val ch = channel ?: error("Storage target is not open")
        val bb = ByteBuffer.wrap(buffer, 0, length)
        var written = 0
        while (bb.hasRemaining()) {
            val n = ch.write(bb, position + written)
            if (n <= 0) error("Provider write failed at offset ${position + written}")
            written += n
        }
    }

    override suspend fun close(deletePartial: Boolean) = withContext(Dispatchers.IO) {
        runCatching { channel?.force(true) }
        runCatching { channel?.close() }
        runCatching { pfd?.close() }
        channel = null
        pfd = null
        if (deletePartial) {
            runCatching { context.contentResolver.delete(contentUri, null, null) }
        }
    }

    override suspend fun currentSize(): Long = withContext(Dispatchers.IO) {
        runCatching { pfd?.statSize ?: -1L }.getOrDefault(-1L).takeIf { it >= 0 } ?: runCatching {
            context.contentResolver.openAssetFileDescriptor(contentUri, "r")?.use { it.length } ?: -1L
        }.getOrDefault(-1L)
    }

    override suspend fun flush() = withContext(Dispatchers.IO) {
        runCatching { channel?.force(false) }
            .onFailure { android.util.Log.w("F7Storage", "provider flush failed for $uri: ${it.message}") }
        Unit
    }

    override suspend fun truncate(size: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            channel?.truncate(size)
            true
        }.getOrDefault(false)
    }

    override suspend fun finalizeSuccess(): String = withContext(Dispatchers.IO) {
        if (kind == "media_store" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
                put(MediaStore.Downloads.DATE_MODIFIED, System.currentTimeMillis() / 1000)
            }
            val rows = runCatching { context.contentResolver.update(contentUri, values, null, null) }
                .getOrElse {
                    android.util.Log.e("F7Storage", "finalize failed for $uri: ${it.message}")
                    -1
                }
            android.util.Log.i("F7Storage", "finalize $uri rows=$rows size=${currentSize()}")
            mediaStorePending = false
        }
        uri
    }
}

