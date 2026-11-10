package com.f7.downloadmanager.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.f7.core.model.DownloadItem
import com.f7.core.model.DownloadStatus
import com.f7.core.model.F7Format
import com.f7.core.ui.components.F7Card
import com.f7.core.ui.components.F7EmptyState
import com.f7.core.ui.components.F7Pill
import com.f7.core.ui.components.F7ProgressBar
import com.f7.core.ui.components.F7ScreenBackground
import com.f7.core.ui.components.F7TopBar
import com.f7.core.ui.components.FileBadge
import com.f7.core.ui.theme.LocalF7Colors
import com.f7.downloadmanager.DownloadsViewModel
import com.f7.downloadmanager.R
import com.f7.downloadmanager.categoryColors
import com.f7.downloadmanager.extensionLabel
import com.f7.downloadmanager.formatBytes
import com.f7.downloadmanager.formatSpeed
import com.f7.downloadmanager.iconRes
import com.f7.downloadmanager.statusLabelRes
import kotlinx.coroutines.launch

@Composable
fun QueueScreen(
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: DownloadsViewModel = viewModel()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()

    val queue = remember(downloads) {
        downloads
            .filter { it.status != DownloadStatus.COMPLETED && it.status != DownloadStatus.CANCELLED }
            .sortedWith(compareByDescending<DownloadItem> { it.priority }.thenBy { it.createdAt })
    }
    val failedCount = queue.count { it.status == DownloadStatus.FAILED }

    F7ScreenBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 780.dp)
                .navigationBarsPadding()
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(modifier = Modifier.statusBarsPadding()) {
                    F7TopBar(
                        title = stringResource(R.string.queue_title),
                        subtitle = stringResource(R.string.queue_subtitle, queue.size),
                        onBack = onBack,
                        backIcon = painterResource(R.drawable.ic_f7_arrow_back)
                    )
                }
            }

            if (queue.isEmpty()) {
                item {
                    F7EmptyState(
                        icon = painterResource(R.drawable.ic_f7_downloading),
                        title = stringResource(R.string.queue_empty),
                        message = stringResource(R.string.queue_empty_message)
                    )
                }
            } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QueueAction(
                            modifier = Modifier.weight(1f),
                            icon = painterResource(R.drawable.ic_f7_pause),
                            label = stringResource(R.string.queue_pause_all),
                            onClick = {
                                viewModel.pauseAll()
                                scope.launch { snackbar.showSnackbar(context.getString(R.string.queue_paused_all)) }
                            }
                        )
                        QueueAction(
                            modifier = Modifier.weight(1f),
                            icon = painterResource(R.drawable.ic_f7_play_arrow),
                            label = stringResource(R.string.queue_resume_all),
                            emphasized = true,
                            onClick = {
                                viewModel.resumeAll()
                                scope.launch { snackbar.showSnackbar(context.getString(R.string.queue_resumed_all)) }
                            }
                        )
                        QueueAction(
                            modifier = Modifier.weight(1f),
                            icon = painterResource(R.drawable.ic_f7_refresh),
                            label = stringResource(R.string.queue_retry_failed),
                            enabled = failedCount > 0,
                            onClick = {
                                viewModel.retryFailed()
                                scope.launch { snackbar.showSnackbar(context.getString(R.string.queue_retrying)) }
                            }
                        )
                    }
                }

                items(queue, key = { it.id }) { item ->
                    QueueCard(
                        item = item,
                        canMoveUp = queue.indexOf(item) > 0,
                        canMoveDown = queue.indexOf(item) < queue.lastIndex,
                        onClick = { onOpenDetail(item.id) },
                        onMoveUp = { viewModel.moveQueue(item.id, -1) },
                        onMoveDown = { viewModel.moveQueue(item.id, 1) },
                        onToggle = {
                            when (item.status) {
                                DownloadStatus.PAUSED, DownloadStatus.FAILED -> viewModel.resume(item.id)
                                else -> viewModel.pause(item.id)
                            }
                        },
                        onCancel = { viewModel.cancel(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueAction(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.painter.Painter,
    label: String,
    onClick: () -> Unit,
    emphasized: Boolean = false,
    enabled: Boolean = true,
) {
    val container = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainer
        emphasized -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        emphasized -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = container,
        border = if (!emphasized) androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant
        ) else null,
        shadowElevation = if (emphasized) 3.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = content, modifier = Modifier.size(20.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QueueCard(
    item: DownloadItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggle: () -> Unit,
    onCancel: () -> Unit,
) {
    val (iconColor, iconContainer) = categoryColors(item.category)
    val statusColor = when (item.status) {
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        DownloadStatus.PAUSED -> LocalF7Colors.current.warningContainer to LocalF7Colors.current.warning
        else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
    }
    F7Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileBadge(
                icon = painterResource(item.iconRes()),
                label = item.extensionLabel(),
                size = 46.dp,
                container = iconContainer,
                content = iconColor
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    F7Pill(
                        text = stringResource(statusLabelRes(item)),
                        containerColor = statusColor.first,
                        contentColor = statusColor.second
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (item.speedBytesPerSec > 0) formatSpeed(item.speedBytesPerSec)
                        else F7Format.percent(item.progress),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                PriorityButton(
                    icon = R.drawable.ic_f7_arrow_upward,
                    label = stringResource(R.string.queue_priority_up),
                    enabled = canMoveUp,
                    onClick = onMoveUp
                )
                PriorityButton(
                    icon = R.drawable.ic_f7_arrow_downward,
                    label = stringResource(R.string.queue_priority_down),
                    enabled = canMoveDown,
                    onClick = onMoveDown
                )
                PriorityButton(
                    icon = if (item.status == DownloadStatus.PAUSED || item.status == DownloadStatus.FAILED) {
                        R.drawable.ic_f7_play_arrow
                    } else {
                        R.drawable.ic_f7_pause
                    },
                    label = stringResource(R.string.action_pause),
                    enabled = true,
                    onClick = onToggle
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        F7ProgressBar(progress = item.progress, height = 6.dp)
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (item.totalBytes > 0) {
                    "${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}"
                } else {
                    formatBytes(item.downloadedBytes)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.action_cancel),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun PriorityButton(
    icon: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(icon),
            contentDescription = label,
            tint = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
            modifier = Modifier.size(17.dp)
        )
    }
}
