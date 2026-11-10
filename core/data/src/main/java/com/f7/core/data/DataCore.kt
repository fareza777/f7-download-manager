package com.f7.core.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.f7.core.data.db.F7Database
import com.f7.core.data.prefs.SettingsStore
import com.f7.core.data.storage.StorageManager

/**
 * Process-wide service locator for the data layer. Initialised once from Application.
 */
object DataCore {
    @Volatile
    private var initialized = false

    lateinit var database: F7Database
        private set
    lateinit var repository: DownloadsRepository
        private set
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var storageManager: StorageManager
        private set

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            settingsStore = SettingsStore(app)
            database = F7Database.get(app)
            storageManager = StorageManager(app, settingsStore)
            repository = DownloadsRepository(
                database.downloadDao(),
                database.scheduleDao(),
                database.statsDao(),
                database.browserDao(),
                storageManager,
                settingsStore,
            )
            initialized = true
        }
    }
}
