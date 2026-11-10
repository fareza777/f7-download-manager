package com.f7.downloadmanager

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.f7.core.data.DataCore
import com.f7.core.download.schedule.ScheduleManager
import com.f7.downloadmanager.ads.AdController
import com.f7.downloadmanager.ads.BillingManager
import com.f7.core.download.service.DownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppGraph(val context: Context) {
    val repository = DataCore.repository
    val settingsStore = DataCore.settingsStore
    val storageManager = DataCore.storageManager
    val scheduleManager = ScheduleManager(context, repository)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val adController = AdController(context, settingsStore, scope)
    val billing = BillingManager(context, settingsStore, scope)

    init {
        // Only initialise the SDK here; each format is preloaded from the init
        // callback so requests are never made before the SDK is ready.
        adController.initialize()
        billing.start()
    }

    fun startServiceSafely() {
        runCatching { DownloadService.start(context) }
    }

    fun findActivity(): android.app.Activity? {
        if (context is android.app.Activity) return context
        if (context is android.content.ContextWrapper) {
            var base: android.content.Context? = context
            while (base is android.content.ContextWrapper) {
                if (base is android.app.Activity) return base
                base = base.baseContext
            }
        }
        return null
    }
}

class F7Application : android.app.Application() {

    lateinit var graph: AppGraph
        private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        DataCore.init(this)
        graph = AppGraph(this)
        scope.launch {
            val settings = graph.settingsStore.current()
            if (settings.autoDeleteDays > 0) {
                runCatching { graph.repository.cleanupOldCompleted(settings.autoDeleteDays) }
            }
            val rows = graph.repository.rows()
            val needsService = rows.any {
                it.status == "QUEUED" || it.status == "CONNECTING" ||
                    it.status == "DOWNLOADING" || it.status == "VERIFYING" ||
                    (it.status == "PAUSED" && it.autoResume)
            }
            if (needsService) graph.startServiceSafely()
        }
        registerNetworkCallback()
    }

    private fun registerNetworkCallback() {
        runCatching {
            val cm = getSystemService(ConnectivityManager::class.java)
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch {
                        val rows = graph.repository.rows()
                        val pending = rows.any {
                            it.status == "QUEUED" || it.status == "CONNECTING" ||
                                it.status == "DOWNLOADING" || it.status == "VERIFYING" ||
                                (it.status == "PAUSED" && it.autoResume)
                        }
                        if (pending) graph.startServiceSafely()
                    }
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        onAvailable(network)
                    }
                }
            })
        }
    }

    fun handleSharedText(text: String?) {
        if (text.isNullOrBlank()) return
        val urls = extractUrls(text)
        if (urls.isEmpty()) return
        scope.launch {
            val settings = graph.settingsStore.current()
            urls.take(20).forEach { url ->
                if (settings.skipDuplicateUrls && graph.repository.findByUrl(url) != null) return@forEach
                graph.repository.enqueue(com.f7.core.data.NewDownload(url = url))
            }
            graph.startServiceSafely()
        }
    }

    companion object {
        fun extractUrls(text: String): List<String> {
            val regex = Regex("""(https?://[^\s"'<>]+)""", RegexOption.IGNORE_CASE)
            val explicit = regex.findAll(text).map { it.value.trim().trimEnd('.', ',', ')', ']', '}') }.toList()
            if (explicit.isNotEmpty()) return explicit.distinct()
            return text.lineSequence()
                .map { it.trim() }
                .filter { it.contains('.') && !it.contains(' ') && it.length in 4..2048 }
                .map { if (it.startsWith("http", true)) it else "https://$it" }
                .toList()
        }
    }
}



