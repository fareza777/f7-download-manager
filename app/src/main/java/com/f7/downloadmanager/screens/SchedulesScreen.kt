package com.f7.downloadmanager.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.f7.core.model.RepeatRule
import com.f7.core.model.ScheduleItem
import com.f7.core.ui.components.F7Card
import com.f7.core.ui.components.F7ChoiceChip
import com.f7.core.ui.components.F7EmptyState
import com.f7.core.ui.components.F7PrimaryButton
import com.f7.core.ui.components.F7ScreenBackground
import com.f7.core.ui.components.F7TopBar
import com.f7.downloadmanager.DownloadsViewModel
import com.f7.downloadmanager.R
import com.f7.downloadmanager.SchedulesViewModel
import com.f7.downloadmanager.formatDate
import com.f7.downloadmanager.formatDateTime
import com.f7.downloadmanager.formatTime
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun SchedulesScreen(onBack: () -> Unit, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: SchedulesViewModel = viewModel()
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ScheduleItem?>(null) }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.schedules_delete_title)) },
            text = { Text(stringResource(R.string.schedules_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete?.let { vm.delete(it.id) }
                    pendingDelete = null
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.schedules_deleted)) }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showSheet) {
        AddScheduleSheet(
            onDismiss = { showSheet = false },
            onSave = { url, name, millis, repeat ->
                vm.add(url, name, millis, repeat) {
                    showSheet = false
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.schedules_created)) }
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
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(modifier = Modifier.statusBarsPadding()) {
                    F7TopBar(
                        title = stringResource(R.string.schedules_title),
                        subtitle = stringResource(R.string.schedules_subtitle),
                        onBack = onBack,
                        backIcon = painterResource(R.drawable.ic_f7_arrow_back)
                    )
                }
            }
            item {
                F7Card {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            painterResource(R.drawable.ic_f7_timer),
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.schedules_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (schedules.isEmpty()) {
                item {
                    F7EmptyState(
                        icon = painterResource(R.drawable.ic_f7_schedule),
                        title = stringResource(R.string.schedules_empty_title),
                        message = stringResource(R.string.schedules_empty_message),
                        actionLabel = stringResource(R.string.schedules_new),
                        onAction = { showSheet = true }
                    )
                }
            } else {
                items(schedules, key = { it.id }) { schedule ->
                    ScheduleCard(
                        item = schedule,
                        onToggle = { vm.setEnabled(schedule, it) },
                        onDelete = { pendingDelete = schedule }
                    )
                }
            }
            if (schedules.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    F7PrimaryButton(
                        text = stringResource(R.string.schedules_new),
                        icon = painterResource(R.drawable.ic_f7_add),
                        onClick = { showSheet = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleCard(item: ScheduleItem, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val dateLabel = when {
        isSameDay(item.triggerAt, System.currentTimeMillis()) -> stringResource(R.string.schedules_today)
        isSameDay(item.triggerAt, System.currentTimeMillis() + 24 * 60 * 60 * 1000L) ->
            stringResource(R.string.schedules_tomorrow)
        else -> formatDate(item.triggerAt)
    }
    val repeatLabel = when (item.repeatRule) {
        RepeatRule.ONCE -> stringResource(R.string.schedules_once)
        RepeatRule.DAILY -> stringResource(R.string.schedules_daily)
        RepeatRule.WEEKLY -> stringResource(R.string.schedules_weekly)
    }
    F7Card(
        modifier = Modifier.fillMaxWidth().alpha(if (item.enabled) 1f else 0.55f),
        contentPadding = PaddingValues(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = formatTime(item.triggerAt),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName ?: item.url.substringAfterLast('/').take(48),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.f7.core.ui.components.F7Pill(
                        text = repeatLabel,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.repeatRule != RepeatRule.ONCE && item.nextRunAt != null && item.enabled) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.schedules_next, formatDateTime(item.nextRunAt ?: item.triggerAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(
                    checked = item.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.ic_f7_delete),
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun isSameDay(first: Long, second: Long): Boolean {
    val a = Calendar.getInstance().apply { timeInMillis = first }
    val b = Calendar.getInstance().apply { timeInMillis = second }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

private fun pickDateTime(context: android.content.Context, initial: Long, onPicked: (Long) -> Unit) {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = if (initial > System.currentTimeMillis()) initial else System.currentTimeMillis() + 3600_000
    }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            val chosen = Calendar.getInstance().apply {
                timeInMillis = calendar.timeInMillis
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
            }
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    chosen.set(Calendar.HOUR_OF_DAY, hour)
                    chosen.set(Calendar.MINUTE, minute)
                    chosen.set(Calendar.SECOND, 0)
                    chosen.set(Calendar.MILLISECOND, 0)
                    onPicked(chosen.timeInMillis)
                },
                chosen.get(Calendar.HOUR_OF_DAY),
                chosen.get(Calendar.MINUTE),
                true
            ).show()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddScheduleSheet(
    onDismiss: () -> Unit,
    onSave: (String, String?, Long, String) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var url by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var triggerAt by remember { mutableLongStateOf(System.currentTimeMillis() + 3600_000) }
    var repeat by remember { mutableStateOf(RepeatRule.ONCE.name) }
    var invalid by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .size(width = 44.dp, height = 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.schedules_new),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; invalid = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.add_url_label)) },
                placeholder = { Text(stringResource(R.string.schedules_url_hint)) },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                )
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.add_name_label)) },
                placeholder = { Text(stringResource(R.string.schedules_name_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                )
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.schedules_time),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { pickDateTime(context, triggerAt) { triggerAt = it } },
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painterResource(R.drawable.ic_f7_calendar_month),
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = formatDateTime(triggerAt),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        painterResource(R.drawable.ic_f7_chevron_right),
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.schedules_repeat),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    RepeatRule.ONCE to R.string.schedules_once,
                    RepeatRule.DAILY to R.string.schedules_daily,
                    RepeatRule.WEEKLY to R.string.schedules_weekly,
                ).forEach { (rule, label) ->
                    F7ChoiceChip(
                        text = stringResource(label),
                        selected = repeat == rule.name,
                        onClick = { repeat = rule.name }
                    )
                }
            }
            if (invalid) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.schedules_invalid_time),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(20.dp))
            F7PrimaryButton(
                text = stringResource(R.string.schedules_save),
                icon = painterResource(R.drawable.ic_f7_schedule),
                onClick = {
                    val urls = DownloadsViewModel.parseUrls(url)
                    if (urls.isEmpty() || triggerAt <= System.currentTimeMillis()) {
                        invalid = true
                    } else {
                        onSave(urls.first(), fileName, triggerAt, repeat)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
