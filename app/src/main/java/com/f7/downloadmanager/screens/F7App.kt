package com.f7.downloadmanager.screens

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.app.Activity
import com.f7.core.model.AppSettings
import com.f7.downloadmanager.DownloadsViewModel
import com.f7.downloadmanager.ads.AdBannerSlot
import com.f7.downloadmanager.F7Application
import com.f7.downloadmanager.R

private data class NavItem(val route: String, val labelRes: Int, val iconRes: Int)

private val navItems = listOf(
    NavItem("home", R.string.nav_downloads, R.drawable.ic_f7_download),
    NavItem("queue", R.string.nav_queue, R.drawable.ic_f7_list),
    NavItem("files", R.string.nav_files, R.drawable.ic_f7_folder),
    NavItem("browser", R.string.nav_browser, R.drawable.ic_f7_public),
    NavItem("settings", R.string.nav_settings, R.drawable.ic_f7_settings),
)

@Composable
fun F7App(
    app: F7Application,
    settings: AppSettings,
    initialDownloadId: Long?,
    sharedCount: Int,
    onDownloadOpened: () -> Unit,
    onSharedConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val downloadsViewModel: DownloadsViewModel = viewModel()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "home"
    val showBottomBar = currentRoute in navItems.map { it.route }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var addPrefill by rememberSaveable { mutableStateOf<String?>(null) }
    val shareMessage = stringResource(R.string.share_received, sharedCount)
    val localContext = androidx.compose.ui.platform.LocalContext.current
    val currentActivity = localContext as? Activity

    LaunchedEffect(initialDownloadId) {
        if (initialDownloadId != null) {
            navController.navigate("detail/$initialDownloadId")
            onDownloadOpened()
        }
    }

    LaunchedEffect(sharedCount) {
        if (sharedCount > 0) {
            snackbarHostState.showSnackbar(shareMessage)
            onSharedConsumed()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Banner sits between the page content and the app bottom bar.
                    // The bottom bar keeps the system navigation inset, so the ad can
                    // never cover Android navigation buttons.
                    AdBannerSlot(
                        adController = app.graph.adController,
                        visible = !showAddSheet,
                    )
                    F7BottomBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            NavHost(navController = navController, startDestination = "home") {
                composable("home") {
                    DownloadsScreen(
                        viewModel = downloadsViewModel,
                        settings = settings,
                        onAdd = { prefill ->
                            addPrefill = prefill
                            showAddSheet = true
                        },
                        onOpenDetail = { navController.navigate("detail/$it") },
                        onOpenSchedules = { navController.navigate("schedules") },
                        onOpenBrowser = { navController.navigate("browser") },
                        onOpenFiles = { navController.navigate("files") },
                        onOpenSettings = { navController.navigate("settings") },
                        onOpenStats = { navController.navigate("stats") },
                        snackbar = snackbarHostState,
                    )
                }
                composable("queue") {
                    QueueScreen(
                        onBack = { navController.popBackStack() },
                        onOpenDetail = { navController.navigate("detail/$it") },
                        snackbar = snackbarHostState,
                    )
                }
                composable("files") {
                    FilesScreen(
                        settings = settings,
                        onOpenSettings = { navController.navigate("settings") },
                        onOpenDetail = { navController.navigate("detail/$it") },
                        snackbar = snackbarHostState,
                    )
                }
                composable("browser") {
                    BrowserScreen(snackbar = snackbarHostState)
                }
                composable("settings") {
                    SettingsScreen(
                        onOpenSchedules = { navController.navigate("schedules") },
                        onOpenAbout = { navController.navigate("about") },
                        onOpenPrivacy = { navController.navigate("privacy") },
                        onOpenStats = { navController.navigate("stats") },
                        onOpenAdvanced = { navController.navigate("advanced") },
                        onOpenRemoveAds = { navController.navigate("remove_ads") },
                        adFree = app.graph.adController.adFree.collectAsStateWithLifecycle().value,
                        snackbar = snackbarHostState,
                    )
                }
                composable("detail/{id}") { entry ->
                    val id = entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                    DownloadDetailScreen(
                        downloadId = id,
                        onBack = { navController.popBackStack() },
                        snackbar = snackbarHostState,
                    )
                }
                composable("schedules") {
                    SchedulesScreen(
                        onBack = { navController.popBackStack() },
                        snackbar = snackbarHostState,
                    )
                }
                composable("stats") {
                    StatsScreen(onBack = { navController.popBackStack() })
                }
                composable("advanced") {
                    AdvancedSettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenStats = { navController.navigate("stats") },
                        snackbar = snackbarHostState,
                    )
                }
                composable("remove_ads") {
                    RemoveAdsScreen(
                        onBack = { navController.popBackStack() },
                        snackbar = snackbarHostState,
                    )
                }
                composable("about") {
                    AboutScreen(onBack = { navController.popBackStack() })
                }
                composable("privacy") {
                    PrivacyScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }

    if (showAddSheet) {
        AddDownloadSheet(
            settings = settings,
            initialText = addPrefill,
            viewModel = downloadsViewModel,
            onDismiss = {
                showAddSheet = false
                addPrefill = null
            },
            onEnqueued = { count ->
                showAddSheet = false
                addPrefill = null
                val activity = currentActivity
                if (activity != null) {
                    // Let the sheet close and the list settle before covering the
                    // screen, so the ad never interrupts an in-flight animation.
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        app.graph.adController.showInterstitial(activity)
                    }, 900)
                }
                count
            },
            snackbar = snackbarHostState,
        )
    }
}

@Composable
private fun F7BottomBar(currentRoute: String, onNavigate: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 18.dp,
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEach { item ->
                    BottomBarItem(
                        modifier = Modifier.weight(1f),
                        item = item,
                        selected = currentRoute == item.route,
                        onClick = { onNavigate(item.route) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    modifier: Modifier = Modifier,
    item: NavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .padding(horizontal = 1.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    if (selected) {
                        Brush.linearGradient(
                            listOf(primary, primary.copy(alpha = 0.72f))
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(item.iconRes),
                contentDescription = stringResource(item.labelRes),
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = stringResource(item.labelRes),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun unusedIcons(): Painter = painterResource(R.drawable.ic_f7_download)

@Suppress("unused")
private val ignore = Configuration.ORIENTATION_UNDEFINED












