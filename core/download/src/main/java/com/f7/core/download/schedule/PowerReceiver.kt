package com.f7.core.download.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.PowerManager
import com.f7.core.data.DataCore
import com.f7.core.download.service.DownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restarts the download service when a device condition changes, so downloads paused
 * for charging, battery saver, low battery, or metered networks can resume without
 * the user reopening the app. It only starts the service; the engine decides.
 */
class PowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in SUPPORTED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DataCore.init(context)
                val rows = DataCore.repository.rows()
                val shouldStart = rows.any {
                    it.status == "QUEUED" || it.status == "CONNECTING" ||
                        it.status == "DOWNLOADING" || it.status == "VERIFYING" ||
                        (it.status == "PAUSED" && it.autoResume)
                }
                if (shouldStart) runCatching { DownloadService.start(context) }
            } catch (_: Throwable) {
                // Never crash on a system broadcast.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        val SUPPORTED = setOf(
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED,
            Intent.ACTION_BATTERY_LOW,
            Intent.ACTION_BATTERY_OKAY,
            PowerManager.ACTION_POWER_SAVE_MODE_CHANGED,
            BatteryManager.ACTION_CHARGING,
            BatteryManager.ACTION_DISCHARGING,
        )
    }
}
