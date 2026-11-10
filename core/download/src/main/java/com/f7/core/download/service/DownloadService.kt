package com.f7.core.download.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.f7.core.data.DataCore
import com.f7.core.data.DownloadsRepository
import com.f7.core.download.DownloadEngine
import com.f7.core.model.DownloadItem
import com.f7.core.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: DownloadsRepository
    private lateinit var engine: DownloadEngine
    private lateinit var notifier: DownloadNotifier

    private var collector: Job? = null
    private var ticker: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockAcquiredAt = 0L
    private var lastForegroundId = -1L
    private var serviceStartedAt = 0L
    private var idleSeconds = 0
    private var noNetworkSeconds = 0
    private var notificationsEnabled = true
    private var notificationComplete = true
    private val seenActive = HashSet<Long>()

    override fun onCreate() {
        super.onCreate()
        serviceStartedAt = System.currentTimeMillis()
        DataCore.init(this)
        repository = DataCore.repository
        notifier = DownloadNotifier(this)
        startForegroundCompat(notifier.serviceNotification(null, 0, 0))
        engine = DownloadEngine(applicationContext, repository, DataCore.settingsStore)
        engine.start()

        collector = scope.launch {
            repository.downloads.collectLatest { items -> render(items) }
        }
        scope.launch {
            DataCore.settingsStore.settings.collectLatest { settings ->
                notificationsEnabled = settings.notificationsEnabled
                notificationComplete = settings.notificationComplete
            }
        }
        ticker = scope.launch {
            while (isActive) {
                delay(1_000)
                checkIdle()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every startForegroundService() call must be matched by startForeground(),
        // including commands that arrive while the service is already running.
        startForegroundCompat(notifier.serviceNotification(null, 0, 0))
        val action = intent?.action
        val id = intent?.getLongExtra(EXTRA_ID, -1L) ?: -1L
        when (action) {
            ACTION_PAUSE -> if (id > 0) scope.launch { engine.pause(id) }
            ACTION_RESUME -> if (id > 0) scope.launch { engine.resume(id) }
            ACTION_CANCEL -> if (id > 0) scope.launch { engine.cancel(id) }
            ACTION_RETRY -> if (id > 0) scope.launch { engine.retry(id) }
            ACTION_PAUSE_ALL -> scope.launch { engine.pauseAll() }
            ACTION_RESUME_ALL -> scope.launch { engine.resumeAll() }
            ACTION_RETRY_FAILED -> scope.launch { engine.retryAllFailed() }
            ACTION_START, null -> {
                engine.start()
            }
        }
        return START_STICKY
    }

    private fun render(items: List<DownloadItem>) {
        val active = items.filter { it.status.isActive }
        val primary = active.firstOrNull()
        val totalSpeed = active.sumOf { it.speedBytesPerSec }

        if (primary != null) {
            val notification = notifier.serviceNotification(primary, active.size, totalSpeed)
            if (primary.id != lastForegroundId) {
                lastForegroundId = primary.id
                startForegroundCompat(notification)
            } else {
                notifier.updateService(notification)
            }
            if (notificationsEnabled) {
                active.drop(1).forEach { notifier.notifyProgress(it) }
            }
        } else {
            notifier.updateService(notifier.serviceNotification(null, 0, 0))
        }

        // Completion / failure notifications only for downloads we saw running.
        for (item in items) {
            if (item.status.isActive) {
                seenActive.add(item.id)
                continue
            }
            if (item.id !in seenActive) continue
            seenActive.remove(item.id)
            if (!notificationsEnabled) {
                notifier.cancelProgress(item.id)
                continue
            }
            when (item.status) {
                DownloadStatus.COMPLETED -> {
                    notifier.cancelProgress(item.id)
                    val completedAt = item.completedAt ?: 0L
                    if (notificationComplete && completedAt >= serviceStartedAt - 5_000) {
                        notifier.notifyCompleted(item)
                    }
                }
                DownloadStatus.FAILED -> {
                    notifier.cancelProgress(item.id)
                    notifier.notifyFailed(item)
                }
                DownloadStatus.PAUSED -> notifier.cancelProgress(item.id)
                else -> notifier.cancelProgress(item.id)
            }
        }

        updateWakeLock(active.isNotEmpty())
    }

    private fun checkIdle() {
        val online = isOnline()
        if (!online) {
            noNetworkSeconds++
        } else {
            noNetworkSeconds = 0
        }
        val hasRunning = engine.activeCount.value > 0
        if (!hasRunning) idleSeconds++ else idleSeconds = 0
        val onlineWaiting = !online && noNetworkSeconds > 5
        if ((!hasRunning && idleSeconds >= 4) || onlineWaiting) {
            stopSelfSafely()
        }
    }

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                DownloadNotifier.SERVICE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(DownloadNotifier.SERVICE_NOTIFICATION_ID, notification)
        }
    }

    private fun updateWakeLock(active: Boolean) {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (wakeLock == null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "F7Download:engine").apply {
                setReferenceCounted(false)
            }
        }
        val lock = wakeLock ?: return
        val now = SystemClock.elapsedRealtime()
        if (active) {
            if (lock.isHeld && now - wakeLockAcquiredAt > 15 * 60 * 1000L) {
                runCatching { lock.release() }
            }
            if (!lock.isHeld) {
                runCatching { lock.acquire(30 * 60 * 1000L) }
                wakeLockAcquiredAt = now
            }
        } else if (lock.isHeld) {
            runCatching { lock.release() }
        }
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onTimeout(startId: Int) {
        handleSystemTimeout()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        handleSystemTimeout()
    }

    private fun handleSystemTimeout() {
        scope.launch {
            repository.rows().filter { it.status.isActiveStatusName() }.forEach {
                repository.markForNetworkWait(it.id)
            }
            notifier.updateService(notifier.timeoutNotification())
            stopSelfSafely()
        }
    }

    private fun String.isActiveStatusName(): Boolean =
        this == DownloadStatus.QUEUED.name || this == DownloadStatus.CONNECTING.name ||
            this == DownloadStatus.DOWNLOADING.name || this == DownloadStatus.VERIFYING.name

    override fun onDestroy() {
        collector?.cancel()
        ticker?.cancel()
        runCatching { engine.shutdown() }
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.f7.download.action.START"
        const val ACTION_PAUSE = "com.f7.download.action.PAUSE"
        const val ACTION_RESUME = "com.f7.download.action.RESUME"
        const val ACTION_CANCEL = "com.f7.download.action.CANCEL"
        const val ACTION_RETRY = "com.f7.download.action.RETRY"
        const val ACTION_PAUSE_ALL = "com.f7.download.action.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "com.f7.download.action.RESUME_ALL"
        const val ACTION_RETRY_FAILED = "com.f7.download.action.RETRY_FAILED"
        const val EXTRA_ID = "download_id"

        fun intent(context: Context, action: String): Intent =
            Intent(context, DownloadService::class.java).setAction(action)

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, intent(context, ACTION_START))
        }
    }
}



