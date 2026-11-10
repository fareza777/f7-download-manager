package com.f7.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.f7.core.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "f7_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_id")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val LANGUAGE = stringPreferencesKey("language")
        val MAX_CONNECTIONS = intPreferencesKey("max_connections")
        val SPEED_LIMIT = intPreferencesKey("speed_limit_kbps")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val AUTO_RETRY = booleanPreferencesKey("auto_retry")
        val MAX_RETRIES = intPreferencesKey("max_retries")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val STORAGE_TREE = stringPreferencesKey("storage_tree_uri")
        val STORAGE_MODE = stringPreferencesKey("storage_mode")
        val DYNAMIC_CONNECTIONS = booleanPreferencesKey("dynamic_connections")
        val HLS_QUALITY = stringPreferencesKey("hls_quality")
        val ASK_BEFORE_DOWNLOAD = booleanPreferencesKey("ask_before_download")
        val MAX_CONCURRENT = intPreferencesKey("max_concurrent")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val UI_ANIMATIONS = booleanPreferencesKey("ui_animations")
        val CONNECT_TIMEOUT = intPreferencesKey("connect_timeout")
        val READ_TIMEOUT = intPreferencesKey("read_timeout")
        val CHUNK_SIZE = intPreferencesKey("chunk_size_kb")
        val USER_AGENT = stringPreferencesKey("user_agent")
        val AUTO_CHECKSUM = booleanPreferencesKey("auto_checksum")
        val SKIP_DUPLICATES = booleanPreferencesKey("skip_duplicates")
        val AUTO_DELETE_DAYS = intPreferencesKey("auto_delete_days")
        val PAUSE_METERED = booleanPreferencesKey("pause_metered")
        val PAUSE_BATTERY_SAVER = booleanPreferencesKey("pause_battery_saver")
        val CHARGING_ONLY = booleanPreferencesKey("charging_only")
        val PAUSE_LOW_BATTERY = booleanPreferencesKey("pause_low_battery")
        val BROWSER_JS = booleanPreferencesKey("browser_js")
        val BROWSER_COOKIES = booleanPreferencesKey("browser_cookies")
        val BROWSER_DESKTOP = booleanPreferencesKey("browser_desktop")
        val BROWSER_SEARCH = stringPreferencesKey("browser_search")
        val BROWSER_HOME = stringPreferencesKey("browser_home")
        val NOTIF_COMPLETE = booleanPreferencesKey("notif_complete")
        val CLOSE_SHEET_ON_SHARE = booleanPreferencesKey("close_sheet_on_share")
        val REMOVE_ADS = booleanPreferencesKey("remove_ads_purchased")
        val AD_FREE_UNTIL = longPreferencesKey("ad_free_until")
        val INTERSTITIAL_LAST = longPreferencesKey("interstitial_last_shown")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeId = p[Keys.THEME] ?: "aurora",
            darkMode = p[Keys.DARK_MODE] ?: "light",
            textScale = p[Keys.TEXT_SCALE] ?: 1.0f,
            language = p[Keys.LANGUAGE] ?: "en",
            maxConnections = p[Keys.MAX_CONNECTIONS] ?: 8,
            speedLimitKbps = p[Keys.SPEED_LIMIT] ?: 0,
            wifiOnly = p[Keys.WIFI_ONLY] ?: false,
            autoRetry = p[Keys.AUTO_RETRY] ?: true,
            maxRetries = p[Keys.MAX_RETRIES] ?: 5,
            notificationsEnabled = p[Keys.NOTIFICATIONS] ?: true,
            storageTreeUri = p[Keys.STORAGE_TREE],
            storageMode = p[Keys.STORAGE_MODE] ?: "media_store",
            dynamicConnections = p[Keys.DYNAMIC_CONNECTIONS] ?: true,
            hlsPreferredQuality = p[Keys.HLS_QUALITY] ?: "best",
            askBeforeDownload = p[Keys.ASK_BEFORE_DOWNLOAD] ?: false,
            maxConcurrentDownloads = p[Keys.MAX_CONCURRENT] ?: 2,
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: false,
            uiAnimations = p[Keys.UI_ANIMATIONS] ?: true,
            connectTimeoutSec = p[Keys.CONNECT_TIMEOUT] ?: 20,
            readTimeoutSec = p[Keys.READ_TIMEOUT] ?: 45,
            chunkSizeKb = p[Keys.CHUNK_SIZE] ?: 64,
            userAgent = p[Keys.USER_AGENT] ?: "",
            autoChecksum = p[Keys.AUTO_CHECKSUM] ?: false,
            skipDuplicateUrls = p[Keys.SKIP_DUPLICATES] ?: true,
            autoDeleteDays = p[Keys.AUTO_DELETE_DAYS] ?: 0,
            pauseOnMetered = p[Keys.PAUSE_METERED] ?: false,
            pauseOnBatterySaver = p[Keys.PAUSE_BATTERY_SAVER] ?: false,
            chargingOnly = p[Keys.CHARGING_ONLY] ?: false,
            pauseOnLowBattery = p[Keys.PAUSE_LOW_BATTERY] ?: false,
            browserJavaScript = p[Keys.BROWSER_JS] ?: true,
            browserCookies = p[Keys.BROWSER_COOKIES] ?: true,
            browserDesktopMode = p[Keys.BROWSER_DESKTOP] ?: false,
            browserSearchEngine = p[Keys.BROWSER_SEARCH] ?: "google",
            browserHomepage = p[Keys.BROWSER_HOME] ?: "https://www.google.com",
            notificationComplete = p[Keys.NOTIF_COMPLETE] ?: true,
            closeAddSheetOnShare = p[Keys.CLOSE_SHEET_ON_SHARE] ?: true,
            removeAdsPurchased = p[Keys.REMOVE_ADS] ?: false,
            adFreeUntil = p[Keys.AD_FREE_UNTIL] ?: 0L,
            interstitialLastShownAt = p[Keys.INTERSTITIAL_LAST] ?: 0L,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTheme(id: String) = put { it[Keys.THEME] = id }
    suspend fun setDarkMode(mode: String) = put { it[Keys.DARK_MODE] = mode }
    suspend fun setTextScale(scale: Float) = put { it[Keys.TEXT_SCALE] = scale.coerceIn(0.9f, 1.3f) }
    suspend fun setLanguage(lang: String) = put { it[Keys.LANGUAGE] = lang }
    suspend fun setMaxConnections(value: Int) = put { it[Keys.MAX_CONNECTIONS] = value.coerceIn(1, 16) }
    suspend fun setSpeedLimitKbps(value: Int) = put { it[Keys.SPEED_LIMIT] = value.coerceAtLeast(0) }
    suspend fun setWifiOnly(value: Boolean) = put { it[Keys.WIFI_ONLY] = value }
    suspend fun setAutoRetry(value: Boolean) = put { it[Keys.AUTO_RETRY] = value }
    suspend fun setMaxRetries(value: Int) = put { it[Keys.MAX_RETRIES] = value.coerceIn(0, 10) }
    suspend fun setNotifications(value: Boolean) = put { it[Keys.NOTIFICATIONS] = value }
    suspend fun setStorageTree(uri: String?) = put {
        if (uri == null) it.remove(Keys.STORAGE_TREE) else it[Keys.STORAGE_TREE] = uri
    }
    suspend fun setStorageMode(mode: String) = put { it[Keys.STORAGE_MODE] = mode }
    suspend fun setDynamicConnections(value: Boolean) = put { it[Keys.DYNAMIC_CONNECTIONS] = value }
    suspend fun setHlsQuality(value: String) = put { it[Keys.HLS_QUALITY] = value }
    suspend fun setAskBeforeDownload(value: Boolean) = put { it[Keys.ASK_BEFORE_DOWNLOAD] = value }
    suspend fun setMaxConcurrent(value: Int) = put { it[Keys.MAX_CONCURRENT] = value.coerceIn(1, 5) }
    suspend fun setDynamicColor(value: Boolean) = put { it[Keys.DYNAMIC_COLOR] = value }
    suspend fun setUiAnimations(value: Boolean) = put { it[Keys.UI_ANIMATIONS] = value }
    suspend fun setConnectTimeout(seconds: Int) = put { it[Keys.CONNECT_TIMEOUT] = seconds.coerceIn(5, 120) }
    suspend fun setReadTimeout(seconds: Int) = put { it[Keys.READ_TIMEOUT] = seconds.coerceIn(10, 300) }
    suspend fun setChunkSize(kb: Int) = put { it[Keys.CHUNK_SIZE] = kb.coerceIn(16, 512) }
    suspend fun setUserAgent(value: String) = put { it[Keys.USER_AGENT] = value.trim() }
    suspend fun setAutoChecksum(value: Boolean) = put { it[Keys.AUTO_CHECKSUM] = value }
    suspend fun setSkipDuplicates(value: Boolean) = put { it[Keys.SKIP_DUPLICATES] = value }
    suspend fun setAutoDeleteDays(days: Int) = put { it[Keys.AUTO_DELETE_DAYS] = days.coerceIn(0, 365) }
    suspend fun setPauseOnMetered(value: Boolean) = put { it[Keys.PAUSE_METERED] = value }
    suspend fun setPauseOnBatterySaver(value: Boolean) = put { it[Keys.PAUSE_BATTERY_SAVER] = value }
    suspend fun setChargingOnly(value: Boolean) = put { it[Keys.CHARGING_ONLY] = value }
    suspend fun setPauseOnLowBattery(value: Boolean) = put { it[Keys.PAUSE_LOW_BATTERY] = value }
    suspend fun setBrowserJavaScript(value: Boolean) = put { it[Keys.BROWSER_JS] = value }
    suspend fun setBrowserCookies(value: Boolean) = put { it[Keys.BROWSER_COOKIES] = value }
    suspend fun setBrowserDesktopMode(value: Boolean) = put { it[Keys.BROWSER_DESKTOP] = value }
    suspend fun setBrowserSearchEngine(value: String) = put { it[Keys.BROWSER_SEARCH] = value }
    suspend fun setBrowserHomepage(value: String) = put { it[Keys.BROWSER_HOME] = value.trim() }
    suspend fun setNotificationComplete(value: Boolean) = put { it[Keys.NOTIF_COMPLETE] = value }
    suspend fun setCloseAddSheetOnShare(value: Boolean) = put { it[Keys.CLOSE_SHEET_ON_SHARE] = value }
    suspend fun setRemoveAdsPurchased(value: Boolean) = put { it[Keys.REMOVE_ADS] = value }
    suspend fun setAdFreeUntil(value: Long) = put { it[Keys.AD_FREE_UNTIL] = value }
    suspend fun setInterstitialLastShown(value: Long) = put { it[Keys.INTERSTITIAL_LAST] = value }

    suspend fun reset() {
        context.dataStore.edit { it.clear() }
    }

    private suspend fun put(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}


