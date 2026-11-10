package com.f7.downloadmanager

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.f7.core.model.AppSettings
import com.f7.core.model.ScheduleItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = (app as F7Application).graph

    val settings = graph.settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setTheme(id: String) = viewModelScope.launch { graph.settingsStore.setTheme(id) }
    fun setDarkMode(mode: String) = viewModelScope.launch { graph.settingsStore.setDarkMode(mode) }
    fun setTextScale(scale: Float) = viewModelScope.launch { graph.settingsStore.setTextScale(scale) }
    fun setLanguage(language: String) = viewModelScope.launch { graph.settingsStore.setLanguage(language) }
    fun setMaxConnections(value: Int) = viewModelScope.launch { graph.settingsStore.setMaxConnections(value) }
    fun setMaxConcurrent(value: Int) = viewModelScope.launch { graph.settingsStore.setMaxConcurrent(value) }
    fun setSpeedLimit(kbps: Int) = viewModelScope.launch { graph.settingsStore.setSpeedLimitKbps(kbps) }
    fun setWifiOnly(value: Boolean) = viewModelScope.launch { graph.settingsStore.setWifiOnly(value) }
    fun setAutoRetry(value: Boolean) = viewModelScope.launch { graph.settingsStore.setAutoRetry(value) }
    fun setMaxRetries(value: Int) = viewModelScope.launch { graph.settingsStore.setMaxRetries(value) }
    fun setDynamicConnections(value: Boolean) = viewModelScope.launch { graph.settingsStore.setDynamicConnections(value) }
    fun setNotifications(value: Boolean) = viewModelScope.launch { graph.settingsStore.setNotifications(value) }
    fun setHlsQuality(value: String) = viewModelScope.launch { graph.settingsStore.setHlsQuality(value) }
    fun setStorageMode(mode: String) = viewModelScope.launch { graph.settingsStore.setStorageMode(mode) }
    fun setStorageTree(uri: String?) = viewModelScope.launch { graph.settingsStore.setStorageTree(uri) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { graph.settingsStore.setDynamicColor(value) }
    fun setUiAnimations(value: Boolean) = viewModelScope.launch { graph.settingsStore.setUiAnimations(value) }
    fun setConnectTimeout(seconds: Int) = viewModelScope.launch { graph.settingsStore.setConnectTimeout(seconds) }
    fun setReadTimeout(seconds: Int) = viewModelScope.launch { graph.settingsStore.setReadTimeout(seconds) }
    fun setChunkSize(kb: Int) = viewModelScope.launch { graph.settingsStore.setChunkSize(kb) }
    fun setUserAgent(value: String) = viewModelScope.launch { graph.settingsStore.setUserAgent(value) }
    fun setAutoChecksum(value: Boolean) = viewModelScope.launch { graph.settingsStore.setAutoChecksum(value) }
    fun setSkipDuplicates(value: Boolean) = viewModelScope.launch { graph.settingsStore.setSkipDuplicates(value) }
    fun setAutoDeleteDays(days: Int) = viewModelScope.launch { graph.settingsStore.setAutoDeleteDays(days) }
    fun setPauseOnMetered(value: Boolean) = viewModelScope.launch { graph.settingsStore.setPauseOnMetered(value) }
    fun setPauseOnBatterySaver(value: Boolean) = viewModelScope.launch { graph.settingsStore.setPauseOnBatterySaver(value) }
    fun setChargingOnly(value: Boolean) = viewModelScope.launch { graph.settingsStore.setChargingOnly(value) }
    fun setPauseOnLowBattery(value: Boolean) = viewModelScope.launch { graph.settingsStore.setPauseOnLowBattery(value) }
    fun setBrowserJavaScript(value: Boolean) = viewModelScope.launch { graph.settingsStore.setBrowserJavaScript(value) }
    fun setBrowserCookies(value: Boolean) = viewModelScope.launch { graph.settingsStore.setBrowserCookies(value) }
    fun setBrowserDesktopMode(value: Boolean) = viewModelScope.launch { graph.settingsStore.setBrowserDesktopMode(value) }
    fun setBrowserSearchEngine(value: String) = viewModelScope.launch { graph.settingsStore.setBrowserSearchEngine(value) }
    fun setBrowserHomepage(value: String) = viewModelScope.launch { graph.settingsStore.setBrowserHomepage(value) }
    fun setNotificationComplete(value: Boolean) = viewModelScope.launch { graph.settingsStore.setNotificationComplete(value) }
    fun resetSettings() = viewModelScope.launch { graph.settingsStore.reset() }
    fun cleanupNow(days: Int, onDone: (Int) -> Unit) = viewModelScope.launch {
        onDone(graph.repository.cleanupOldCompleted(days))
    }
    fun clearBrowserData(onDone: () -> Unit) = viewModelScope.launch {
        graph.repository.clearHistory()
        graph.repository.clearBookmarks()
        onDone()
    }
    fun clearStats() = viewModelScope.launch { graph.repository.clearStatistics() }
    fun clearCompleted(onDone: () -> Unit) = viewModelScope.launch {
        graph.repository.deleteFinished()
        onDone()
    }
}

class SchedulesViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = (app as F7Application).graph

    val schedules = graph.repository.schedules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<ScheduleItem>())

    fun add(url: String, fileName: String?, triggerAt: Long, repeat: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val id = graph.repository.addSchedule(
                com.f7.core.data.db.ScheduleEntity(
                    url = url,
                    fileName = fileName?.takeIf { it.isNotBlank() },
                    triggerAt = triggerAt,
                    repeatRule = repeat,
                    enabled = true,
                    nextRunAt = triggerAt,
                )
            )
            graph.repository.scheduleById(id)?.let { graph.scheduleManager.scheduleNext(it) }
            onDone()
        }
    }

    fun setEnabled(item: ScheduleItem, enabled: Boolean) {
        viewModelScope.launch {
            val entity = graph.repository.scheduleById(item.id) ?: return@launch
            val next = if (enabled) {
                if (entity.nextRunAt != null && entity.nextRunAt!! > System.currentTimeMillis()) entity.nextRunAt
                else System.currentTimeMillis() + 60 * 60 * 1000L
            } else null
            val updated = entity.copy(enabled = enabled, nextRunAt = next)
            graph.repository.updateSchedule(updated)
            if (enabled) graph.scheduleManager.scheduleNext(updated) else graph.scheduleManager.cancel(item.id)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            graph.scheduleManager.cancel(id)
            graph.repository.deleteSchedule(id)
        }
    }
}
