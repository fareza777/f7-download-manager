package com.f7.downloadmanager

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.f7.core.data.db.DownloadEventEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StatsViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as F7Application).graph

    val events = graph.repository.events()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<DownloadEventEntity>())

    fun clearStats() = viewModelScope.launch {
        graph.repository.clearStatistics()
    }
}
