package com.f7.downloadmanager

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f7.core.download.service.DownloadNotifier
import com.f7.core.model.AppSettings
import com.f7.core.ui.theme.F7Theme
import com.f7.downloadmanager.screens.F7App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var openDownloadId by mutableStateOf<Long?>(null)
    private var sharedCount by mutableIntStateOf(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        requestNotificationPermission()

        val app = application as F7Application
        setContent {
            val settings by app.graph.settingsStore.settings.collectAsStateWithLifecycle(
                initialValue = AppSettings()
            )
            val dark = when (settings.darkMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            F7Theme(
                themeId = settings.themeId,
                darkTheme = dark,
                textScale = settings.textScale,
                dynamicColor = settings.dynamicColor,
            ) {
                val view = LocalView.current
                SideEffect {
                    val window = this@MainActivity.window
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !dark
                    controller.isAppearanceLightNavigationBars = !dark
                }
                F7App(
                    app = app,
                    settings = settings,
                    initialDownloadId = openDownloadId,
                    sharedCount = sharedCount,
                    onDownloadOpened = { openDownloadId = null },
                    onSharedConsumed = { sharedCount = 0 },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        scope.launch {
            val app = application as F7Application
            val hasWork = app.graph.repository.rows().any {
                it.status == "QUEUED" || it.status == "CONNECTING" ||
                    it.status == "DOWNLOADING" || it.status == "VERIFYING"
            }
            if (hasWork) app.graph.startServiceSafely()
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val id = intent.getLongExtra(DownloadNotifier.EXTRA_OPEN_DOWNLOAD, -1L)
        if (id > 0) openDownloadId = id
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                val count = F7Application.extractUrls(text).size
                if (count > 0) sharedCount = count
                (application as F7Application).handleSharedText(text)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

