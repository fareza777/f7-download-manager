package com.f7.downloadmanager

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.f7.core.model.DownloadItem
import com.f7.core.model.DownloadStatus
import com.f7.core.model.SegmentInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FilesViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = (app as F7Application).graph

    val completed = graph.repository.downloads
        .map { items -> items.filter { it.status == DownloadStatus.COMPLETED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<DownloadItem>())

    fun delete(id: Long) = viewModelScope.launch {
        graph.repository.delete(id, deleteFile = true)
    }

    fun deleteDuplicates(ids: List<Long>) = viewModelScope.launch {
        ids.forEach { graph.repository.delete(it, deleteFile = true) }
    }
}

sealed interface ChecksumState {
    data object Idle : ChecksumState
    data object Loading : ChecksumState
    data class Result(val sha256: String) : ChecksumState
    data object Error : ChecksumState
}

class DetailViewModel(app: Application, private val downloadId: Long) : AndroidViewModel(app) {

    private val graph = (app as F7Application).graph

    val item: StateFlow<DownloadItem?> = graph.repository.observeDownload(downloadId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val segments: StateFlow<List<SegmentInfo>> = graph.repository.observeSegments(downloadId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _speedSamples = MutableStateFlow<List<Long>>(emptyList())
    val speedSamples: StateFlow<List<Long>> = _speedSamples.asStateFlow()

    private val _checksum = MutableStateFlow<ChecksumState>(ChecksumState.Idle)
    val checksum: StateFlow<ChecksumState> = _checksum.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                val current = item.value ?: continue
                if (current.status.isActive) {
                    _speedSamples.update { (it + current.speedBytesPerSec).takeLast(60) }
                }
            }
        }
    }

    fun pause() = viewModelScope.launch { DownloadActions.pause(getApplication(), downloadId) }
    fun resume() = viewModelScope.launch { DownloadActions.resume(getApplication(), downloadId) }
    fun retry() = viewModelScope.launch { DownloadActions.retry(getApplication(), downloadId) }
    fun cancel() = viewModelScope.launch { DownloadActions.cancel(getApplication(), downloadId) }
    fun delete() = viewModelScope.launch { DownloadActions.delete(getApplication(), downloadId, true) }

    fun verifyChecksum(context: Context) {
        val current = item.value ?: return
        _checksum.value = ChecksumState.Loading
        viewModelScope.launch {
            val digest = computeDigest(context, current)
            _checksum.value = if (digest != null) ChecksumState.Result(digest) else ChecksumState.Error
        }
    }
}
