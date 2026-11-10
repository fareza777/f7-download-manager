package com.f7.downloadmanager

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.f7.core.data.NewDownload
import com.f7.core.model.AppSettings
import com.f7.core.model.DownloadItem
import com.f7.core.model.DownloadKind
import com.f7.core.model.FileCategory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = (app as F7Application).graph

    val settings = graph.settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val downloads = graph.repository.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<DownloadItem>())

    fun enqueue(
        rawText: String,
        fileName: String? = null,
        category: FileCategory? = null,
        connections: Int = 0,
        onResult: (added: Int, skipped: Int) -> Unit,
    ) {
        viewModelScope.launch {
            val urls = parseUrls(rawText)
            if (urls.isEmpty()) {
                onResult(0, 0)
                return@launch
            }
            val settingsNow = graph.settingsStore.current()
            var added = 0
            var skipped = 0
            urls.take(30).forEach { url ->
                if (settingsNow.skipDuplicateUrls && graph.repository.findByUrl(url) != null) {
                    skipped++
                    return@forEach
                }
                graph.repository.enqueue(
                    NewDownload(
                        url = url,
                        fileName = if (urls.size == 1) fileName?.trim()?.takeIf { it.isNotBlank() } else null,
                        category = category ?: FileCategory.fromMime(null, url.substringAfterLast('/').substringBefore('?')),
                        connections = if (connections > 0) connections else settingsNow.maxConnections,
                        kind = if (url.substringBefore('?').lowercase().endsWith(".m3u8")) DownloadKind.HLS else DownloadKind.HTTP,
                    )
                )
                added++
            }
            if (added > 0) graph.startServiceSafely()
            onResult(added, skipped)
        }
    }

    fun schedule(url: String, fileName: String?, atMillis: Long, repeat: String) {
        viewModelScope.launch {
            val id = graph.repository.addSchedule(
                com.f7.core.data.db.ScheduleEntity(
                    url = url,
                    fileName = fileName?.takeIf { it.isNotBlank() },
                    triggerAt = atMillis,
                    repeatRule = repeat,
                    enabled = true,
                    nextRunAt = atMillis,
                )
            )
            val entity = graph.repository.scheduleById(id)
            if (entity != null) graph.scheduleManager.scheduleNext(entity)
        }
    }

    fun pause(id: Long) = viewModelScope.launch { DownloadActions.pause(getApplication(), id) }

    fun resume(id: Long) = viewModelScope.launch { DownloadActions.resume(getApplication(), id) }

    fun retry(id: Long) = viewModelScope.launch { DownloadActions.retry(getApplication(), id) }

    fun cancel(id: Long) = viewModelScope.launch { DownloadActions.cancel(getApplication(), id) }

    fun delete(id: Long) = viewModelScope.launch { DownloadActions.delete(getApplication(), id, true) }

    fun pauseAll() = viewModelScope.launch { DownloadActions.pauseAll(getApplication()) }

    fun resumeAll() = viewModelScope.launch { DownloadActions.resumeAll(getApplication()) }

    fun retryFailed() = viewModelScope.launch { DownloadActions.retryFailed(getApplication()) }

    fun moveQueue(id: Long, delta: Int) = viewModelScope.launch {
        graph.repository.moveQueueItem(id, delta)
    }

    fun clearCompleted() = viewModelScope.launch {
        graph.repository.deleteFinished()
    }

    companion object {
        fun parseUrls(raw: String): List<String> {
            val explicit = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
                .findAll(raw)
                .map { it.value.trim().trimEnd('.', ',', ')', ']', '}') }
                .toList()
            val candidates = if (explicit.isNotEmpty()) explicit else {
                raw.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.contains(' ') && it.contains('.') }
                    .map { if (it.startsWith("http", true)) it else "https://$it" }
                    .toList()
            }
            return candidates
                .filter { url ->
                    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return@filter false
                    (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
                }
                .distinct()
        }
    }
}
