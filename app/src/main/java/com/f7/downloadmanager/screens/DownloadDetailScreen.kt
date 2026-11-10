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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.f7.core.model.DownloadItem
import com.f7.core.model.DownloadStatus
import com.f7.core.model.F7Format
import com.f7.core.model.SegmentInfo
import com.f7.core.ui.components.Eyebrow
import com.f7.core.ui.components.F7Card
import com.f7.core.ui.components.F7Pill
import com.f7.core.ui.components.F7ProgressBar
import com.f7.core.ui.components.F7ScreenBackground
import com.f7.core.ui.components.F7SectionHeading
import com.f7.core.ui.components.F7TopBar
import com.f7.core.ui.components.FileBadge
import com.f7.core.ui.components.RingProgress
import com.f7.core.ui.components.SegmentProgressBars
import com.f7.core.ui.components.SpeedChart
import com.f7.core.ui.theme.LocalF7Colors
import com.f7.downloadmanager.ChecksumState
import com.f7.downloadmanager.DetailViewModel
import com.f7.downloadmanager.F7Application
import com.f7.downloadmanager.R
import com.f7.downloadmanager.categoryColors
import com.f7.downloadmanager.computeDigest
import com.f7.downloadmanager.copyToClipboard
import com.f7.downloadmanager.errorDetailOf
import com.f7.downloadmanager.extensionLabel
import com.f7.downloadmanager.formatBytes
import com.f7.downloadmanager.formatDateTime
import com.f7.downloadmanager.formatEta
import com.f7.downloadmanager.formatSpeed
import com.f7.downloadmanager.iconRes
import com.f7.downloadmanager.labelRes
import com.f7.downloadmanager.localizedError
import com.f7.downloadmanager.openDownload
import com.f7.downloadmanager.shareDownload
import com.f7.downloadmanager.statusLabelRes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadDetailScreen(
    downloadId: Long,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val app = context.applicationContext as F7Application
    val scope = rememberCoroutineScope()
    val factory = remember(downloadId) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DetailViewModel(app, downloadId) as T
            }
        }
    }
    val vm: DetailViewModel = viewModel(key = "detail-$downloadId", factory = factory)
    val item by vm.item.collectAsStateWithLifecycle()
    val segments by vm.segments.collectAsStateWithLifecycle()
    val samples by vm.speedSamples.collectAsStateWithLifecycle()
    val checksum by vm.checksum.collectAsStateWithLifecycle()

    var showDelete by remember { mutableStateOf(false) }
    var showChecksum by remember { mutableStateOf(false) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val current = item

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.detail_delete_title)) },
            text = { Text(stringResource(R.string.detail_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    vm.delete()
                    onBack()
                }) { Text(stringResource(R.string.detail_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showChecksum && current != null) {
        ChecksumDialog(
            state = checksum,
            fileName = current.fileName,
            onDismiss = { showChecksum = false },
            onVerify = { vm.verifyChecksum(context) },
            onCopy = { value ->
                copyToClipboard(context, "SHA-256", value)
                scope.launch { snackbar.showSnackbar(context.getString(R.string.detail_checksum_copied)) }
            }
        )
    }

    F7ScreenBackground {
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.detail_file_missing),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@F7ScreenBackground
        }

        val download = current
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 780.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(modifier = Modifier.statusBarsPadding()) {
                    F7TopBar(
                        title = stringResource(R.string.detail_title),
                        subtitle = download.fileName,
                        onBack = onBack,
                        backIcon = painterResource(R.drawable.ic_f7_arrow_back),
                        actions = {
                            Box {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .clickable { menuOpen = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_f7_more_vert),
                                        contentDescription = stringResource(R.string.action_more),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    if (download.savedUri != null && download.status == DownloadStatus.COMPLETED) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_open)) },
                                            onClick = {
                                                menuOpen = false
                                                if (!openDownload(context, download)) {
                                                    scope.launch { snackbar.showSnackbar(context.getString(R.string.detail_open_failed)) }
                                                }
                                            },
                                            leadingIcon = { Icon(painterResource(R.drawable.ic_f7_open_in_new), null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_share)) },
                                            onClick = {
                                                menuOpen = false
                                                if (!shareDownload(context, download)) {
                                                    scope.launch { snackbar.showSnackbar(context.getString(R.string.detail_open_failed)) }
                                                }
                                            },
                                            leadingIcon = { Icon(painterResource(R.drawable.ic_f7_share), null) }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_delete)) },
                                        onClick = { menuOpen = false; showDelete = true },
                                        leadingIcon = { Icon(painterResource(R.drawable.ic_f7_delete), null) }
                                    )
                                }
                            }
                        }
                    )
                }
            }

            item { FileHeaderCard(download) }

            item {
                ProgressCard(
                    item = download,
                    segments = segments,
                    onPrimary = {
                        when (download.status) {
                            DownloadStatus.COMPLETED -> {
                                if (!openDownload(context, download)) {
                                    scope.launch { snackbar.showSnackbar(context.getString(R.string.detail_open_failed)) }
                                }
                            }
                            DownloadStatus.PAUSED, DownloadStatus.FAILED -> vm.resume()
                            else -> vm.pause()
                        }
                    },
                    onCancel = { showDelete = true },
                )
            }

            if (download.status == DownloadStatus.FAILED && !download.error.isNullOrBlank()) {
                item {
                    ErrorCard(
                        message = localizedError(context, download.error),
                        detail = errorDetailOf(download.error),
                        onRetry = { vm.resume() }
                    )
                }
            }

            if (download.status.isActive || samples.isNotEmpty()) {
                item {
                    F7Card {
                        F7SectionHeading(
                            title = stringResource(R.string.detail_speed_chart),
                            subtitle = if (download.speedBytesPerSec > 0) formatSpeed(download.speedBytesPerSec) else null
                        )
                        Spacer(Modifier.height(12.dp))
                        if (samples.size < 2) {
                            Text(
                                text = stringResource(R.string.speed_chart_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            SpeedChart(samples = samples)
                        }
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showAdvanced = !showAdvanced },
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_f7_tune),
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                if (showAdvanced) R.string.detail_hide_advanced else R.string.detail_show_advanced
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            painterResource(R.drawable.ic_f7_expand_more),
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (showAdvanced && segments.isNotEmpty()) {
                item {
                    SegmentsCard(segments)
                }
            }

            if (showAdvanced) {
                item {
                    F7SectionHeading(title = stringResource(R.string.detail_file_info))
                }
                item {
                    F7Card {
                        InfoRow(stringResource(R.string.detail_status), stringResource(statusLabelRes(download)))
                        InfoRow(stringResource(R.string.detail_size), formatBytes(if (download.totalBytes > 0) download.totalBytes else download.downloadedBytes))
                        InfoRow(stringResource(R.string.detail_type), "${download.mimeType} Â· ${download.kind.label}")
                        InfoRow(
                            stringResource(R.string.detail_connections),
                            "${download.activeConnections} / ${download.connections}"
                        )
                        InfoRow(stringResource(R.string.detail_started), formatDateTime(download.createdAt))
                        download.completedAt?.let {
                            InfoRow(stringResource(R.string.detail_completed), formatDateTime(it))
                        }
                        InfoRow(stringResource(R.string.detail_location), download.displayPath ?: download.savedUri ?: "â€”")
                        val etag = download.etag
                        if (!etag.isNullOrBlank()) {
                            InfoRow(stringResource(R.string.detail_etag), etag, maxLines = 2)
                        }
                    }
                }
                item {
                    F7Card {
                        F7SectionHeading(
                            title = stringResource(R.string.detail_checksum),
                            subtitle = stringResource(R.string.detail_checksum_desc)
                        )
                        Spacer(Modifier.height(12.dp))
                        val savedChecksum = download.checksum
                        if (!savedChecksum.isNullOrBlank()) {
                            InfoRow(
                                label = download.checksumType ?: "SHA-256",
                                value = savedChecksum,
                                maxLines = 3
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        com.f7.core.ui.components.F7PrimaryButton(
                            text = stringResource(
                                if (savedChecksum.isNullOrBlank()) R.string.detail_verify
                                else R.string.detail_recompute
                            ),
                            icon = painterResource(R.drawable.ic_f7_verified),
                            onClick = { showChecksum = true; vm.verifyChecksum(context) }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DetailAction(
                        modifier = Modifier.weight(1f),
                        icon = painterResource(R.drawable.ic_f7_open_in_new),
                        label = stringResource(R.string.action_open),
                        enabled = download.savedUri != null,
                        onClick = {
                            if (!openDownload(context, download)) {
                                scope.launch { snackbar.showSnackbar(context.getString(R.string.detail_open_failed)) }
                            }
                        }
                    )
                    DetailAction(
                        modifier = Modifier.weight(1f),
                        icon = painterResource(R.drawable.ic_f7_share),
                        label = stringResource(R.string.action_share),
                        enabled = download.savedUri != null,
                        onClick = {
                            if (!shareDownload(context, download)) {
                                scope.launch { snackbar.showSnackbar(context.getString(R.string.detail_open_failed)) }
                            }
                        }
                    )
                    DetailAction(
                        modifier = Modifier.weight(1f),
                        icon = painterResource(R.drawable.ic_f7_delete),
                        label = stringResource(R.string.action_delete),
                        enabled = true,
                        onClick = { showDelete = true }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) { }
    }
}

@Composable
private fun FileHeaderCard(item: DownloadItem) {
    val (iconColor, iconContainer) = categoryColors(item.category)
    F7Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileBadge(
                icon = painterResource(item.iconRes()),
                label = item.extensionLabel(),
                size = 62.dp,
                container = iconContainer,
                content = iconColor
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    F7Pill(text = stringResource(item.category.labelRes()))
                    F7Pill(
                        text = stringResource(statusLabelRes(item)),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = item.url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProgressCard(
    item: DownloadItem,
    segments: List<SegmentInfo>,
    onPrimary: () -> Unit,
    onCancel: () -> Unit,
) {
    val active = item.status.isActive
    val indeterminate = item.totalBytes <= 0
    F7Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingProgress(
                progress = if (indeterminate) 0f else item.progress,
                size = 94.dp,
                stroke = 9.dp,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (indeterminate) formatBytes(item.downloadedBytes) else F7Format.percent(item.progress),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (indeterminate) {
                            "HLS"
                        } else {
                            when (item.status) {
                                DownloadStatus.DOWNLOADING, DownloadStatus.CONNECTING -> "LIVE"
                                DownloadStatus.PAUSED -> "PAUSED"
                                DownloadStatus.FAILED -> "FAILED"
                                DownloadStatus.COMPLETED -> "DONE"
                                DownloadStatus.QUEUED -> "QUEUED"
                                else -> "â€¦"
                            }
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Eyebrow(
                    text = when (item.status) {
                        DownloadStatus.PAUSED -> stringResource(R.string.status_paused)
                        DownloadStatus.FAILED -> stringResource(R.string.status_failed)
                        DownloadStatus.COMPLETED -> stringResource(R.string.status_completed)
                        DownloadStatus.QUEUED -> stringResource(R.string.status_queued)
                        else -> stringResource(R.string.status_downloading)
                    },
                    color = when (item.status) {
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (indeterminate) {
                        formatBytes(item.downloadedBytes)
                    } else {
                        "${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.speedBytesPerSec > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ) {
                            Text(
                                text = formatSpeed(item.speedBytesPerSec),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    if (item.etaSeconds > 0) {
                        Text(
                            text = formatEta(item.etaSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(
                            R.string.home_connections,
                            if (item.status.isActive) item.activeConnections else item.connections
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (segments.isNotEmpty()) {
            SegmentProgressBars(
                segmentProgress = segments.map { it.progress },
                overall = item.progress,
                height = 10.dp
            )
        } else {
            F7ProgressBar(progress = item.progress, height = 10.dp)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.f7.core.ui.components.F7PrimaryButton(
                text = stringResource(
                    when (item.status) {
                        DownloadStatus.COMPLETED -> R.string.action_open
                        DownloadStatus.PAUSED, DownloadStatus.FAILED -> R.string.action_resume
                        else -> R.string.action_pause
                    }
                ),
                icon = painterResource(
                    when (item.status) {
                        DownloadStatus.COMPLETED -> R.drawable.ic_f7_open_in_new
                        DownloadStatus.PAUSED, DownloadStatus.FAILED -> R.drawable.ic_f7_play_arrow
                        else -> R.drawable.ic_f7_pause
                    }
                ),
                onClick = onPrimary,
                modifier = Modifier.weight(1f)
            )
            if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED ||
                item.status == DownloadStatus.CONNECTING || item.status == DownloadStatus.PAUSED
            ) {
                Surface(
                    modifier = Modifier
                        .height(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(onClick = onCancel),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.action_cancel),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = when {
                item.status == DownloadStatus.PAUSED -> stringResource(R.string.detail_paused_note)
                item.status == DownloadStatus.FAILED -> stringResource(R.string.detail_paused_note)
                item.status == DownloadStatus.COMPLETED -> stringResource(R.string.detail_completed_note)
                active -> stringResource(R.string.detail_active_note, item.activeConnections)
                else -> stringResource(R.string.detail_paused_note)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SegmentsCard(segments: List<SegmentInfo>) {
    F7Card {
        F7SectionHeading(
            title = stringResource(R.string.detail_segments),
            subtitle = stringResource(R.string.detail_segment_summary, segments.count { it.isComplete }, segments.size)
        )
        Spacer(Modifier.height(12.dp))
        segments.forEach { segment ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#${segment.index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp)
                )
                Box(modifier = Modifier.weight(1f)) {
                    F7ProgressBar(progress = segment.progress, height = 6.dp)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = F7Format.percent(segment.progress),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (segment.isComplete) LocalF7Colors.current.success else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(36.dp)
                )
                Text(
                    text = formatBytes(segment.length),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ErrorCard(message: String, detail: String?, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.detail_error_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            if (!detail.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.action_retry),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, maxLines: Int = 1) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(112.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DetailAction(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.painter.Painter,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(21.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChecksumDialog(
    state: ChecksumState,
    fileName: String,
    onDismiss: () -> Unit,
    onVerify: () -> Unit,
    onCopy: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_checksum)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.detail_checksum_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                when (state) {
                    ChecksumState.Idle -> Text(
                        text = stringResource(R.string.detail_verify),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ChecksumState.Loading -> Text(
                        text = stringResource(R.string.detail_checksum_computing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    is ChecksumState.Result -> {
                        Text(
                            text = "SHA-256",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = state.sha256,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .clickable { onCopy(state.sha256) }
                            )
                        }
                    }
                    ChecksumState.Error -> Text(
                        text = stringResource(R.string.detail_file_missing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (state is ChecksumState.Result) onCopy(state.sha256) else onVerify()
            }) {
                Text(
                    stringResource(
                        if (state is ChecksumState.Result) R.string.action_copy else R.string.detail_verify
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}



