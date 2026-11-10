package com.f7.downloadmanager.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.f7.core.data.db.DownloadEventEntity
import com.f7.core.model.F7Format
import com.f7.core.model.FileCategory
import com.f7.core.ui.components.F7Card
import com.f7.core.ui.components.F7Pill
import com.f7.core.ui.components.F7ProgressBar
import com.f7.core.ui.components.F7ScreenBackground
import com.f7.core.ui.components.F7SectionHeading
import com.f7.core.ui.components.F7TopBar
import com.f7.core.ui.components.FileBadge
import com.f7.core.ui.theme.F7Themes
import com.f7.core.ui.theme.LocalF7Colors
import com.f7.downloadmanager.R
import com.f7.downloadmanager.StatsViewModel
import com.f7.downloadmanager.categoryColors
import com.f7.downloadmanager.formatBytes
import com.f7.downloadmanager.formatDateTime
import com.f7.downloadmanager.formatSpeed
import com.f7.downloadmanager.iconRes
import com.f7.downloadmanager.labelRes
import java.util.Calendar

@Composable
fun StatsScreen(onBack: () -> Unit) {
    val vm: StatsViewModel = viewModel()
    val events by vm.events.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.stats_clear_title)) },
            text = { Text(stringResource(R.string.stats_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearStats()
                }) { Text(stringResource(R.string.action_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    val success = remember(events) { events.filter { it.success } }
    val failed = remember(events) { events.count { !it.success } }
    val totalBytes = remember(success) { success.sumOf { it.bytes } }
    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val todayBytes = remember(success) { success.filter { it.createdAt >= todayStart }.sumOf { it.bytes } }
    val successRate = if (events.isEmpty()) 0 else (success.size * 100 / events.size)
    val avgSpeed = if (success.isEmpty()) 0 else success.sumOf { it.averageSpeed } / success.size
    val days = remember(events) { lastSevenDays(success) }
    val categories = remember(success) { categoryTotals(success) }

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
                        title = stringResource(R.string.stats_title),
                        subtitle = stringResource(R.string.stats_subtitle),
                        onBack = onBack,
                        backIcon = painterResource(R.drawable.ic_f7_arrow_back),
                        actions = {
                            com.f7.core.ui.components.F7IconButton(
                                icon = painterResource(R.drawable.ic_f7_delete_sweep),
                                contentDescription = stringResource(R.string.stats_clear_title),
                                onClick = { confirmClear = true }
                            )
                        }
                    )
                }
            }

            item {
                StatsHero(
                    totalBytes = totalBytes,
                    todayBytes = todayBytes,
                    successRate = successRate,
                    completed = success.size,
                    failed = failed,
                    avgSpeed = avgSpeed
                )
            }

            item {
                F7Card {
                    F7SectionHeading(
                        title = stringResource(R.string.stats_last_7_days),
                        subtitle = stringResource(R.string.stats_data_saved, formatBytes(days.sumOf { it.second }))
                    )
                    Spacer(Modifier.height(14.dp))
                    WeeklyChart(days)
                }
            }

            item {
                F7Card {
                    F7SectionHeading(title = stringResource(R.string.stats_by_category))
                    Spacer(Modifier.height(10.dp))
                    if (categories.isEmpty()) {
                        Text(
                            text = stringResource(R.string.stats_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val max = categories.values.maxOrNull()?.coerceAtLeast(1) ?: 1
                        categories.entries.sortedByDescending { it.value }.forEach { (category, bytes) ->
                            CategoryStatRow(category, bytes, bytes.toFloat() / max)
                        }
                    }
                }
            }

            item {
                F7Card {
                    F7SectionHeading(title = stringResource(R.string.stats_recent))
                    Spacer(Modifier.height(8.dp))
                    if (events.isEmpty()) {
                        Text(
                            text = stringResource(R.string.stats_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        events.take(20).forEach { event ->
                            EventRow(event)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsHero(
    totalBytes: Long,
    todayBytes: Long,
    successRate: Int,
    completed: Int,
    failed: Int,
    avgSpeed: Long,
) {
    val palette = F7Themes.aurora
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF10245C), palette.gradientStart, palette.gradientEnd)
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = size.width * 0.55f,
                center = Offset(size.width * 0.95f, size.height * 0.05f)
            )
            drawCircle(
                color = Color(0xFF3BE6C0).copy(alpha = 0.18f),
                radius = size.width * 0.22f,
                center = Offset(size.width * 0.82f, size.height * 1.05f)
            )
        }
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                F7Pill(
                    text = stringResource(R.string.stats_success_rate, successRate),
                    containerColor = Color.White.copy(alpha = 0.14f),
                    contentColor = Color.White,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.stats_today, formatBytes(todayBytes)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = formatBytes(totalBytes),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Text(
                text = stringResource(R.string.stats_total_downloaded),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                MiniStat(completed.toString(), stringResource(R.string.stats_completed))
                MiniStat(failed.toString(), stringResource(R.string.stats_failed))
                MiniStat(formatSpeed(avgSpeed), stringResource(R.string.stats_avg_speed))
            }
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = Color.White.copy(alpha = 0.62f)
        )
    }
}

@Composable
private fun WeeklyChart(days: List<Pair<Long, Long>>) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val labels = remember(days) {
        days.map {
            val cal = Calendar.getInstance().apply { timeInMillis = it.first }
            when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "M"
                Calendar.TUESDAY -> "T"
                Calendar.WEDNESDAY -> "W"
                Calendar.THURSDAY -> "T"
                Calendar.FRIDAY -> "F"
                Calendar.SATURDAY -> "S"
                else -> "S"
            }
        }
    }
    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(132.dp)) {
            val max = days.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
            val gap = 8.dp.toPx()
            val barWidth = (size.width - gap * (days.size - 1)) / days.size
            days.forEachIndexed { index, day ->
                val x = index * (barWidth + gap)
                val height = if (max == 0L) 0f else (day.second.toFloat() / max) * (size.height - 12.dp.toPx())
                drawRoundRect(
                    color = track,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height - 8.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                )
                if (height > 0f) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(primary, secondary)),
                        topLeft = Offset(x, (size.height - 8.dp.toPx()) - height),
                        size = Size(barWidth, height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CategoryStatRow(category: FileCategory, bytes: Long, fraction: Float) {
    val (iconColor, iconContainer) = categoryColors(category)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(iconContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(category.iconRes()),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(
                    text = stringResource(category.labelRes()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatBytes(bytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            F7ProgressBar(progress = fraction, height = 5.dp)
        }
    }
}

@Composable
private fun EventRow(event: DownloadEventEntity) {
    val category = runCatching { FileCategory.valueOf(event.category) }.getOrDefault(FileCategory.OTHER)
    val (iconColor, iconContainer) = categoryColors(category)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileBadge(
            icon = painterResource(category.iconRes()),
            label = event.fileName.substringAfterLast('.', "FILE").take(4),
            size = 38.dp,
            container = iconContainer,
            content = iconColor
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.fileName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatDateTime(event.createdAt)} · ${formatBytes(event.bytes)}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Icon(
            painterResource(if (event.success) R.drawable.ic_f7_check_circle else R.drawable.ic_f7_error),
            contentDescription = null,
            tint = if (event.success) LocalF7Colors.current.success else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(17.dp)
        )
    }
}

private fun lastSevenDays(success: List<DownloadEventEntity>): List<Pair<Long, Long>> {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val days = mutableListOf<Pair<Long, Long>>()
    repeat(7) {
        val start = cal.timeInMillis
        val end = start + 24L * 60L * 60L * 1000L
        days.add(0, start to success.filter { it.createdAt in start until end }.sumOf { it.bytes })
        cal.add(Calendar.DAY_OF_YEAR, -1)
    }
    return days
}

private fun categoryTotals(success: List<DownloadEventEntity>): Map<FileCategory, Long> {
    val map = linkedMapOf<FileCategory, Long>()
    success.forEach { event ->
        val category = runCatching { FileCategory.valueOf(event.category) }.getOrDefault(FileCategory.OTHER)
        map[category] = (map[category] ?: 0) + event.bytes
    }
    return map
}
