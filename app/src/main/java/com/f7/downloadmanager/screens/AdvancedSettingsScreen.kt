package com.f7.downloadmanager.screens

import android.content.Intent
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.f7.core.model.AppSettings
import com.f7.core.ui.components.F7Card
import com.f7.core.ui.components.F7ChoiceChip
import com.f7.core.ui.components.F7Divider
import com.f7.core.ui.components.F7ScreenBackground
import com.f7.core.ui.components.F7SectionHeading
import com.f7.core.ui.components.F7TopBar
import com.f7.downloadmanager.LocaleHelper
import com.f7.downloadmanager.R
import com.f7.downloadmanager.SettingsViewModel
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun AdvancedSettingsScreen(
    onBack: () -> Unit,
    onOpenStats: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: SettingsViewModel = viewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showUserAgent by remember { mutableStateOf(false) }
    var showHomepage by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var userAgentText by remember(settings.userAgent) { mutableStateOf(settings.userAgent) }
    var homepageText by remember(settings.browserHomepage) { mutableStateOf(settings.browserHomepage) }

    if (showUserAgent) {
        InputDialog(
            title = stringResource(R.string.settings_user_agent),
            hint = stringResource(R.string.settings_user_agent_hint),
            value = userAgentText,
            onValueChange = { userAgentText = it },
            onDismiss = { showUserAgent = false },
            onSave = {
                vm.setUserAgent(userAgentText)
                showUserAgent = false
            }
        )
    }
    if (showHomepage) {
        InputDialog(
            title = stringResource(R.string.settings_browser_home),
            hint = "https://www.google.com",
            value = homepageText,
            onValueChange = { homepageText = it },
            onDismiss = { showHomepage = false },
            onSave = { vm.setBrowserHomepage(homepageText); showHomepage = false }
        )
    }
    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text(stringResource(R.string.settings_reset)) },
            text = { Text(stringResource(R.string.settings_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showReset = false
                    vm.resetSettings()
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.settings_reset_done)) }
                }) { Text(stringResource(R.string.action_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showReset = false }) {
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
                .navigationBarsPadding()
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(modifier = Modifier.statusBarsPadding()) {
                    F7TopBar(
                        title = stringResource(R.string.settings_advanced),
                        subtitle = stringResource(R.string.settings_advanced_desc),
                        onBack = onBack,
                        backIcon = painterResource(R.drawable.ic_f7_arrow_back)
                    )
                }
            }

            item {
                F7Card {
                    F7SectionHeading(title = stringResource(R.string.settings_advanced_performance))
                    AdvancedSwitch(
                        title = stringResource(R.string.settings_auto_checksum),
                        subtitle = stringResource(R.string.settings_auto_checksum_desc),
                        checked = settings.autoChecksum,
                        onChange = vm::setAutoChecksum
                    )
                    AdvancedDivider()
                    AdvancedSwitch(
                        title = stringResource(R.string.settings_skip_duplicates),
                        subtitle = stringResource(R.string.settings_skip_duplicates_desc),
                        checked = settings.skipDuplicateUrls,
                        onChange = vm::setSkipDuplicates
                    )
                    AdvancedDivider()
                    AdvancedSwitch(
                        title = stringResource(R.string.settings_notification_complete),
                        subtitle = stringResource(R.string.settings_notification_complete_desc),
                        checked = settings.notificationComplete,
                        onChange = vm::setNotificationComplete
                    )
                    Spacer(Modifier.height(12.dp))
                    ValueRow(
                        label = stringResource(R.string.settings_http_timeout),
                        value = stringResource(R.string.settings_timeout_value, settings.connectTimeoutSec)
                    )
                    ChoiceRow(
                        values = listOf(10, 20, 30, 60),
                        selected = settings.connectTimeoutSec,
                        label = { stringResource(R.string.settings_timeout_value, it) },
                        onSelect = vm::setConnectTimeout
                    )
                    Spacer(Modifier.height(10.dp))
                    ValueRow(
                        label = stringResource(R.string.settings_read_timeout),
                        value = stringResource(R.string.settings_timeout_value, settings.readTimeoutSec)
                    )
                    ChoiceRow(
                        values = listOf(30, 45, 90, 120),
                        selected = settings.readTimeoutSec,
                        label = { stringResource(R.string.settings_timeout_value, it) },
                        onSelect = vm::setReadTimeout
                    )
                    Spacer(Modifier.height(10.dp))
                    ValueRow(
                        label = stringResource(R.string.settings_chunk_size),
                        value = stringResource(R.string.settings_chunk_value, settings.chunkSizeKb)
                    )
                    ChoiceRow(
                        values = listOf(32, 64, 128, 256),
                        selected = settings.chunkSizeKb,
                        label = { stringResource(R.string.settings_chunk_value, it) },
                        onSelect = vm::setChunkSize
                    )
                    Spacer(Modifier.height(10.dp))
                    ActionRow(
                        icon = R.drawable.ic_f7_info,
                        title = stringResource(R.string.settings_user_agent),
                        subtitle = settings.userAgent.ifBlank { stringResource(R.string.settings_user_agent_hint) },
                        onClick = {
                            userAgentText = settings.userAgent
                            showUserAgent = true
                        }
                    )
                }
            }

            item {
                F7Card {
                    F7SectionHeading(
                        title = stringResource(R.string.settings_battery_network),
                        subtitle = stringResource(R.string.settings_advanced_automation_desc)
                    )
                    AdvancedSwitch(
                        title = stringResource(R.string.settings_metered),
                        subtitle = stringResource(R.string.settings_metered_desc),
                        checked = settings.pauseOnMetered,
                        onChange = vm::setPauseOnMetered
                    )
                    AdvancedDivider()
                    AdvancedSwitch(
                        title = stringResource(R.string.settings_battery_saver),
                        subtitle = stringResource(R.string.settings_battery_saver_desc),
                        checked = settings.pauseOnBatterySaver,
                        onChange = vm::setPauseOnBatterySaver
                    )
                    AdvancedDivider()
                    AdvancedSwitch(
                        title = stringResource(R.string.settings_charging_only),
                        subtitle = stringResource(R.string.settings_charging_only_desc),
                        checked = settings.chargingOnly,
                        onChange = vm::setChargingOnly
                    )
                    AdvancedDivider()
                    AdvancedSwitch(
                        title = stringResource(R.string.settings_low_battery),
                        subtitle = stringResource(R.string.settings_low_battery_desc),
                        checked = settings.pauseOnLowBattery,
                        onChange = vm::setPauseOnLowBattery
                    )
                    AdvancedDivider()
                    ValueRow(
                        label = stringResource(R.string.settings_auto_delete),
                        value = if (settings.autoDeleteDays == 0) {
                            stringResource(R.string.settings_auto_delete_off)
                        } else {
                            stringResource(R.string.settings_auto_delete_value, settings.autoDeleteDays)
                        }
                    )
                    ChoiceRow(
                        values = listOf(0, 7, 30, 90),
                        selected = settings.autoDeleteDays,
                        label = {
                            if (it == 0) stringResource(R.string.settings_auto_delete_off)
                            else stringResource(R.string.settings_auto_delete_value, it)
                        },
                        onSelect = vm::setAutoDeleteDays
                    )
                    if (settings.autoDeleteDays > 0) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = {
                            vm.cleanupNow(settings.autoDeleteDays) { count ->
                                scope.launch {
                                    snackbar.showSnackbar(
                                        context.getString(R.string.settings_cleanup_done, count)
                                    )
                                }
                            }
                        }) { Text(stringResource(R.string.settings_cleanup_now)) }
                    }
                }
            }

            item {
                F7Card {
                    F7SectionHeading(title = stringResource(R.string.settings_browser))
                    AdvancedSwitch(
                        title = stringResource(R.string.settings_browser_js),
                        subtitle = stringResource(R.string.settings_browser_js_desc),
                        checked = settings.browserJavaScript,
                        onChange = vm::setBrowserJavaScript
                    )
                    AdvancedDivider()
                    AdvancedSwitch(
                        title = stringResource(R.string.settings_browser_cookies),
                        subtitle = stringResource(R.string.settings_browser_cookies_desc),
                        checked = settings.browserCookies,
                        onChange = vm::setBrowserCookies
                    )
                    AdvancedDivider()
                    AdvancedSwitch(
                        title = stringResource(R.string.settings_browser_desktop),
                        subtitle = stringResource(R.string.settings_browser_desktop_desc),
                        checked = settings.browserDesktopMode,
                        onChange = vm::setBrowserDesktopMode
                    )
                    AdvancedDivider()
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.settings_browser_search),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "google" to "Google",
                            "duckduckgo" to "DuckDuckGo",
                            "bing" to "Bing",
                        ).forEach { (id, label) ->
                            F7ChoiceChip(
                                text = label,
                                selected = settings.browserSearchEngine == id,
                                onClick = { vm.setBrowserSearchEngine(id) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ActionRow(
                        icon = R.drawable.ic_f7_home,
                        title = stringResource(R.string.settings_browser_home),
                        subtitle = settings.browserHomepage,
                        onClick = {
                            homepageText = settings.browserHomepage
                            showHomepage = true
                        }
                    )
                    AdvancedDivider()
                    ActionRow(
                        icon = R.drawable.ic_f7_delete_sweep,
                        title = stringResource(R.string.settings_browser_clear),
                        subtitle = stringResource(R.string.settings_browser_clear_desc),
                        onClick = {
                            CookieManager.getInstance().removeAllCookies(null)
                            WebStorage.getInstance().deleteAllData()
                            vm.clearBrowserData {
                                scope.launch {
                                    snackbar.showSnackbar(context.getString(R.string.settings_browser_cleared))
                                }
                            }
                        }
                    )
                }
            }

            item {
                F7Card {
                    F7SectionHeading(title = stringResource(R.string.settings_advanced_data))
                    ActionRow(
                        icon = R.drawable.ic_f7_bar_chart,
                        title = stringResource(R.string.settings_stats),
                        subtitle = stringResource(R.string.settings_stats_desc),
                        onClick = onOpenStats
                    )
                    AdvancedDivider()
                    ActionRow(
                        icon = R.drawable.ic_f7_delete_sweep,
                        title = stringResource(R.string.stats_clear_title),
                        subtitle = stringResource(R.string.stats_clear_message),
                        onClick = { vm.clearStats() }
                    )
                    AdvancedDivider()
                    ActionRow(
                        icon = R.drawable.ic_f7_code,
                        title = stringResource(R.string.settings_export),
                        subtitle = stringResource(R.string.settings_export_desc),
                        onClick = {
                            val json = settingsToJson(settings)
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, json)
                            }
                            runCatching {
                                context.startActivity(
                                    Intent.createChooser(share, context.getString(R.string.settings_export_title))
                                )
                            }
                        }
                    )
                    AdvancedDivider()
                    ActionRow(
                        icon = R.drawable.ic_f7_refresh,
                        title = stringResource(R.string.settings_reset),
                        subtitle = stringResource(R.string.settings_reset_desc),
                        onClick = { showReset = true }
                    )
                }
            }

            item {
                Text(
                    text = "F7 Â· Android ${Build.VERSION.RELEASE} Â· ${LocaleHelper.savedLanguage(context).uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun AdvancedSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
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
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun AdvancedDivider() {
    F7Divider(modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
private fun ChoiceRow(
    values: List<Int>,
    selected: Int,
    label: @Composable (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        values.forEach { value ->
            F7ChoiceChip(
                text = label(value),
                selected = selected == value,
                onClick = { onSelect(value) }
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp),
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
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            painterResource(R.drawable.ic_f7_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun InputDialog(
    title: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(hint) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

private fun settingsToJson(settings: AppSettings): String {
    return JSONObject().apply {
        put("theme", settings.themeId)
        put("darkMode", settings.darkMode)
        put("textScale", settings.textScale)
        put("dynamicColor", settings.dynamicColor)
        put("maxConnections", settings.maxConnections)
        put("maxConcurrent", settings.maxConcurrentDownloads)
        put("speedLimitKbps", settings.speedLimitKbps)
        put("wifiOnly", settings.wifiOnly)
        put("pauseOnMetered", settings.pauseOnMetered)
        put("chargingOnly", settings.chargingOnly)
        put("autoChecksum", settings.autoChecksum)
        put("autoDeleteDays", settings.autoDeleteDays)
        put("connectTimeoutSec", settings.connectTimeoutSec)
        put("readTimeoutSec", settings.readTimeoutSec)
        put("chunkSizeKb", settings.chunkSizeKb)
        put("userAgent", settings.userAgent)
        put("browserJavaScript", settings.browserJavaScript)
        put("browserCookies", settings.browserCookies)
        put("browserDesktopMode", settings.browserDesktopMode)
        put("browserSearchEngine", settings.browserSearchEngine)
        put("browserHomepage", settings.browserHomepage)
    }.toString(2)
}

