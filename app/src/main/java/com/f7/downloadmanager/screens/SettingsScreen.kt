package com.f7.downloadmanager.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.f7.core.ui.components.Eyebrow
import com.f7.core.ui.components.F7Card
import com.f7.core.ui.components.F7ChoiceChip
import com.f7.core.ui.components.F7Divider
import com.f7.core.ui.components.F7LogoMark
import com.f7.core.ui.components.F7ScreenBackground
import com.f7.core.ui.components.F7SectionHeading
import com.f7.core.ui.theme.F7Themes
import com.f7.downloadmanager.BuildConfig
import com.f7.downloadmanager.LocaleHelper
import com.f7.downloadmanager.R
import com.f7.downloadmanager.SettingsViewModel
import com.f7.downloadmanager.storageLabel
import com.f7.downloadmanager.themeNameRes
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class SettingsTab(val labelRes: Int, val iconRes: Int)

private val settingsTabs = listOf(
    SettingsTab(R.string.settings_appearance, R.drawable.ic_f7_tune),
    SettingsTab(R.string.settings_downloads, R.drawable.ic_f7_download),
    SettingsTab(R.string.settings_network, R.drawable.ic_f7_wifi),
    SettingsTab(R.string.settings_storage, R.drawable.ic_f7_storage),
    SettingsTab(R.string.settings_app, R.drawable.ic_f7_settings),
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onOpenSchedules: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenRemoveAds: () -> Unit,
    adFree: Boolean,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: SettingsViewModel = viewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showClearConfirm by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            vm.setStorageTree(uri.toString())
            vm.setStorageMode("saf")
            scope.launch { snackbar.showSnackbar(context.getString(R.string.settings_folder_selected)) }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.settings_clear_completed)) },
            text = { Text(stringResource(R.string.settings_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    vm.clearCompleted {
                        scope.launch { snackbar.showSnackbar(context.getString(R.string.settings_cleared)) }
                    }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    F7ScreenBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 780.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        F7LogoMark(size = 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Eyebrow(text = "Preferences")
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = stringResource(R.string.settings_title),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onOpenAbout),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painterResource(R.drawable.ic_f7_info),
                                    contentDescription = stringResource(R.string.settings_about),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_made_with),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(settingsTabs.size) { index ->
                        val item = settingsTabs[index]
                        F7ChoiceChip(
                            text = stringResource(item.labelRes),
                            selected = tab == index,
                            leadingIcon = painterResource(item.iconRes),
                            onClick = { tab = index }
                        )
                    }
                }
            }

            when (tab) {
                0 -> {
                    item {
                        F7Card {
                            F7SectionHeading(
                                title = stringResource(R.string.settings_theme),
                                subtitle = stringResource(R.string.settings_theme_desc)
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                F7Themes.all.forEach { (id, palette) ->
                                    ThemeSwatch(
                                        id = id,
                                        swatch = palette.swatch,
                                        selected = settings.themeId == id,
                                        onClick = { vm.setTheme(id) }
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            F7Divider()
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.settings_dark),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "system" to R.string.settings_dark_system,
                                    "light" to R.string.settings_dark_light,
                                    "dark" to R.string.settings_dark_dark,
                                ).forEach { (mode, label) ->
                                    F7ChoiceChip(
                                        text = stringResource(label),
                                        selected = settings.darkMode == mode,
                                        onClick = { vm.setDarkMode(mode) }
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            SwitchRow(
                                title = stringResource(R.string.settings_dynamic_color),
                                subtitle = if (android.os.Build.VERSION.SDK_INT >= 31) {
                                    stringResource(R.string.settings_dynamic_color_desc)
                                } else {
                                    stringResource(R.string.settings_dynamic_color_unavailable)
                                },
                                checked = settings.dynamicColor && android.os.Build.VERSION.SDK_INT >= 31,
                                onCheckedChange = {
                                    if (android.os.Build.VERSION.SDK_INT >= 31) vm.setDynamicColor(it)
                                }
                            )
                            SwitchRow(
                                title = stringResource(R.string.settings_animations),
                                subtitle = stringResource(R.string.settings_animations_desc),
                                checked = settings.uiAnimations,
                                onCheckedChange = vm::setUiAnimations
                            )
                        }
                    }
                    item {
                        F7Card {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.settings_text_size),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${(settings.textScale * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = stringResource(R.string.settings_text_preview),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            SettingsSlider(
                                value = settings.textScale,
                                range = 0.9f..1.3f,
                                steps = 4,
                                onCommit = { vm.setTextScale(it) }
                            )
                        }
                    }
                    item {
                        F7Card {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.settings_preview),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "UI",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_f7_description),
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_preview_file),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "12.4 MB Â· 62%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            com.f7.core.ui.components.F7ProgressBar(progress = 0.62f, height = 7.dp)
                        }
                    }
                }
                1 -> {
                    item {
                        F7Card {
                            F7SectionHeading(
                                title = stringResource(R.string.settings_downloads),
                                subtitle = stringResource(R.string.settings_connections_desc)
                            )
                            Spacer(Modifier.height(10.dp))
                            SettingLabelValue(
                                label = stringResource(R.string.settings_connections),
                                value = stringResource(R.string.settings_connections_value, settings.maxConnections)
                            )
                            SettingsSlider(
                                value = settings.maxConnections.toFloat(),
                                range = 1f..16f,
                                steps = 14,
                                onCommit = { vm.setMaxConnections(it.roundToInt()) }
                            )
                            SwitchRow(
                                title = stringResource(R.string.settings_adaptive),
                                subtitle = stringResource(R.string.settings_adaptive_desc),
                                checked = settings.dynamicConnections,
                                onCheckedChange = vm::setDynamicConnections
                            )
                            Spacer(Modifier.height(8.dp))
                            SettingLabelValue(
                                label = stringResource(R.string.settings_concurrent),
                                value = stringResource(R.string.settings_concurrent_value, settings.maxConcurrentDownloads)
                            )
                            SettingsSlider(
                                value = settings.maxConcurrentDownloads.toFloat(),
                                range = 1f..5f,
                                steps = 3,
                                onCommit = { vm.setMaxConcurrent(it.roundToInt()) }
                            )
                        }
                    }
                    item {
                        F7Card {
                            SwitchRow(
                                title = stringResource(R.string.settings_retry),
                                subtitle = stringResource(R.string.settings_retry_desc),
                                checked = settings.autoRetry,
                                onCheckedChange = vm::setAutoRetry
                            )
                            SettingLabelValue(
                                label = stringResource(R.string.settings_max_retry),
                                value = stringResource(R.string.settings_max_retry_value, settings.maxRetries)
                            )
                            SettingsSlider(
                                value = settings.maxRetries.toFloat(),
                                range = 0f..10f,
                                steps = 9,
                                onCommit = { vm.setMaxRetries(it.roundToInt()) }
                            )
                            F7Divider()
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.settings_hls),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    F7ChoiceChip(
                                        text = stringResource(R.string.settings_hls_best),
                                        selected = settings.hlsPreferredQuality == "best",
                                        onClick = { vm.setHlsQuality("best") }
                                    )
                                    F7ChoiceChip(
                                        text = stringResource(R.string.settings_hls_worst),
                                        selected = settings.hlsPreferredQuality == "worst",
                                        onClick = { vm.setHlsQuality("worst") }
                                    )
                                }
                            }
                        }
                    }
                    item {
                        F7Card {
                            SwitchRow(
                                title = stringResource(R.string.settings_notifications),
                                subtitle = stringResource(R.string.settings_notifications_desc),
                                checked = settings.notificationsEnabled,
                                onCheckedChange = vm::setNotifications
                            )
                        }
                    }
                }

                2 -> {
                    item {
                        F7Card {
                            F7SectionHeading(
                                title = stringResource(R.string.settings_network),
                                subtitle = stringResource(R.string.settings_speed_limit_desc)
                            )
                            Spacer(Modifier.height(10.dp))
                            SwitchRow(
                                title = stringResource(R.string.settings_wifi_only),
                                subtitle = stringResource(R.string.settings_wifi_only_desc),
                                checked = settings.wifiOnly,
                                onCheckedChange = vm::setWifiOnly
                            )
                            Spacer(Modifier.height(10.dp))
                            F7Divider()
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.settings_speed_limit),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(10.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val options = listOf(
                                    0 to stringResource(R.string.settings_speed_unlimited),
                                    256 to "256 KB/s",
                                    512 to "512 KB/s",
                                    1024 to "1 MB/s",
                                    2048 to "2 MB/s",
                                    5120 to "5 MB/s",
                                )
                                options.forEach { (kbps, label) ->
                                    F7ChoiceChip(
                                        text = label,
                                        selected = settings.speedLimitKbps == kbps,
                                        onClick = { vm.setSpeedLimit(kbps) }
                                    )
                                }
                            }
                        }
                    }
                    item {
                        F7Card {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    painterResource(R.drawable.ic_f7_speed),
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "The speed limit is enforced with a shared token bucket across every active download, so the total never exceeds it.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                3 -> {
                    item {
                        F7Card {
                            F7SectionHeading(
                                title = stringResource(R.string.settings_storage),
                                subtitle = stringResource(R.string.settings_storage_current, storageLabel(settings, context))
                            )
                            Spacer(Modifier.height(12.dp))
                            StorageModeRow(
                                title = stringResource(R.string.settings_storage_media),
                                subtitle = stringResource(R.string.settings_storage_media_desc),
                                selected = settings.storageMode == "media_store",
                                onClick = { vm.setStorageMode("media_store") }
                            )
                            StorageModeRow(
                                title = stringResource(R.string.settings_storage_app),
                                subtitle = stringResource(R.string.settings_storage_app_desc),
                                selected = settings.storageMode == "app",
                                onClick = { vm.setStorageMode("app") }
                            )
                            StorageModeRow(
                                title = stringResource(R.string.settings_storage_saf),
                                subtitle = stringResource(R.string.settings_storage_saf_desc),
                                selected = settings.storageMode == "saf",
                                onClick = {
                                    if (settings.storageTreeUri == null) folderPicker.launch(null)
                                    else vm.setStorageMode("saf")
                                }
                            )
                            if (settings.storageMode == "saf") {
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { folderPicker.launch(null) },
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(13.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painterResource(R.drawable.ic_f7_folder_open),
                                            null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(19.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = settings.storageTreeUri?.let { uri ->
                                                Uri.parse(uri).lastPathSegment?.substringAfterLast(':')
                                            } ?: stringResource(R.string.settings_storage_no_folder),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = stringResource(R.string.action_choose_folder),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        F7Card {
                            SettingsRow(
                                icon = R.drawable.ic_f7_schedule,
                                title = stringResource(R.string.settings_schedules),
                                subtitle = stringResource(R.string.schedules_subtitle),
                                onClick = onOpenSchedules
                            )
                            F7Divider()
                            SettingsRow(
                                icon = R.drawable.ic_f7_delete_sweep,
                                title = stringResource(R.string.settings_clear_completed),
                                subtitle = stringResource(R.string.settings_clear_completed_desc),
                                onClick = { showClearConfirm = true }
                            )
                        }
                    }
                }

                else -> {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            F7LogoMark(size = 64.dp)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "F7 Download Manager",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "GPL-3.0-or-later",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    item {
                        F7Card {
                            SettingsRow(
                                icon = R.drawable.ic_f7_workspace_premium,
                                title = stringResource(
                                    if (adFree) R.string.ads_remove_active_title
                                    else R.string.ads_remove_title
                                ),
                                subtitle = stringResource(
                                    if (adFree) R.string.ads_remove_active_message
                                    else R.string.ads_settings_row_desc
                                ),
                                onClick = onOpenRemoveAds
                            )
                            F7Divider()
                            SettingsRow(
                                icon = R.drawable.ic_f7_bar_chart,
                                title = stringResource(R.string.settings_stats),
                                subtitle = stringResource(R.string.settings_stats_desc),
                                onClick = onOpenStats
                            )
                            F7Divider()
                            SettingsRow(
                                icon = R.drawable.ic_f7_tune,
                                title = stringResource(R.string.settings_advanced),
                                subtitle = stringResource(R.string.settings_advanced_desc),
                                onClick = onOpenAdvanced
                            )
                            F7Divider()
                            SettingsRow(
                                icon = R.drawable.ic_f7_info,
                                title = stringResource(R.string.settings_about),
                                subtitle = stringResource(R.string.about_tagline),
                                onClick = onOpenAbout
                            )
                            F7Divider()
                            SettingsRow(
                                icon = R.drawable.ic_f7_privacy_tip,
                                title = stringResource(R.string.settings_privacy),
                                subtitle = stringResource(R.string.about_privacy_short),
                                onClick = onOpenPrivacy
                            )
                        }
                    }
                    item {
                        F7Card {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painterResource(R.drawable.ic_f7_shield),
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.about_inspiration),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(id: String, swatch: Color, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(swatch, swatch.copy(alpha = 0.55f))))
                .then(
                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                    else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    painterResource(R.drawable.ic_f7_check),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = stringResource(themeNameRes(id)),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun SettingsSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onCommit: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value.coerceIn(range.start, range.endInclusive)) }
    Slider(
        value = local,
        onValueChange = { local = it },
        onValueChangeFinished = { onCommit(local) },
        valueRange = range,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    )
}

@Composable
private fun SettingLabelValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun StorageModeRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(
                    width = if (selected) 6.dp else 1.5.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                )
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsRow(
    icon: Int,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(icon),
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            painterResource(R.drawable.ic_f7_chevron_right),
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}




