package com.f7.downloadmanager.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.f7.core.model.AppSettings
import com.f7.core.model.FileCategory
import com.f7.downloadmanager.DownloadsViewModel
import com.f7.downloadmanager.R
import com.f7.downloadmanager.clipboardText
import com.f7.downloadmanager.formatBytes
import com.f7.downloadmanager.labelRes
import com.f7.downloadmanager.storageLabel
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AddDownloadSheet(
    settings: AppSettings,
    initialText: String?,
    viewModel: DownloadsViewModel,
    onDismiss: () -> Unit,
    onEnqueued: (Int) -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf(initialText.orEmpty()) }
    var fileName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<FileCategory?>(null) }
    var connections by remember { mutableFloatStateOf(settings.maxConnections.toFloat().coerceIn(1f, 16f)) }
    var showHttpWarning by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    val urls = remember(text) { DownloadsViewModel.parseUrls(text) }
    val isHls = urls.any { it.substringBefore('?').lowercase().endsWith(".m3u8") }
    val hasHttp = urls.any { it.startsWith("http://", ignoreCase = true) }

    fun enqueueDownload() {
        viewModel.enqueue(
            rawText = text,
            fileName = fileName,
            category = category,
            connections = connections.toInt(),
        ) { added, skipped ->
            scope.launch {
                when {
                    added > 0 && skipped > 0 ->
                        snackbar.showSnackbar(context.getString(R.string.add_success_mixed, added, skipped))
                    added > 0 ->
                        snackbar.showSnackbar(context.getString(R.string.add_success, added))
                    skipped > 0 ->
                        snackbar.showSnackbar(context.getString(R.string.add_duplicates_skipped, skipped))
                }
            }
            if (added > 0) onEnqueued(added)
        }
    }

    fun submit() {
        if (urls.isEmpty()) {
            showError = true
            return
        }
        if (hasHttp) {
            showHttpWarning = true
            return
        }
        enqueueDownload()
    }

    if (showHttpWarning) {
        AlertDialog(
            onDismissRequest = { showHttpWarning = false },
            title = { Text(stringResource(R.string.add_cleartext_title)) },
            text = { Text(stringResource(R.string.add_cleartext_message)) },
            confirmButton = {
                TextButton(onClick = { showHttpWarning = false; enqueueDownload() }) {
                    Text(stringResource(R.string.action_start))
                }
            },
            dismissButton = {
                TextButton(onClick = { showHttpWarning = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

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
                .widthIn(max = 780.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 22.dp)
        ) {
            Text(
                text = stringResource(R.string.add_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.add_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it; showError = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.add_url_label)) },
                placeholder = { Text(stringResource(R.string.add_url_hint)) },
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                trailingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_f7_content_copy),
                        contentDescription = stringResource(R.string.action_paste),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val clip = clipboardText(context)
                                if (clip.isNullOrBlank()) {
                                    scope.launch { snackbar.showSnackbar(context.getString(R.string.add_clipboard_empty)) }
                                } else {
                                    text = clip
                                    showError = false
                                }
                            }
                            .padding(8.dp)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                )
            )

            if (showError) {
                Text(
                    text = stringResource(R.string.add_invalid_url),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                )
            }

            if (urls.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painterResource(R.drawable.ic_f7_link),
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (urls.size > 1) {
                                    stringResource(R.string.add_batch_count, urls.size)
                                } else {
                                    urls.first().substringAfterLast('/').take(52)
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isHls) {
                            Spacer(Modifier.height(8.dp))
                            com.f7.core.ui.components.F7Pill(
                                text = stringResource(R.string.add_hls_badge),
                                icon = painterResource(R.drawable.ic_f7_movie)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.add_saved_location, storageLabel(settings, context)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (hasHttp) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = com.f7.core.ui.theme.LocalF7Colors.current.warningContainer,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_f7_shield),
                            null,
                            tint = com.f7.core.ui.theme.LocalF7Colors.current.warning,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.add_http_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = com.f7.core.ui.theme.LocalF7Colors.current.warning
                        )
                    }
                }
            }

            if (urls.size <= 1) {
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.add_name_label)) },
                    placeholder = { Text(stringResource(R.string.add_name_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.add_category),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.f7.core.ui.components.F7ChoiceChip(
                    text = stringResource(R.string.add_category_auto),
                    selected = category == null,
                    onClick = { category = null }
                )
                FileCategory.entries.forEach { fileCategory ->
                    com.f7.core.ui.components.F7ChoiceChip(
                        text = stringResource(fileCategory.labelRes()),
                        selected = category == fileCategory,
                        onClick = { category = fileCategory }
                    )
                }
            }

            if (urls.size <= 1) {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_f7_speed),
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.add_connections),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.add_connections_value, connections.toInt()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = connections,
                    onValueChange = { connections = it },
                    valueRange = 1f..16f,
                    steps = 14
                )
                Text(
                    text = stringResource(R.string.add_connections_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_f7_bolt),
                    null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.add_queue_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(20.dp))
            com.f7.core.ui.components.F7PrimaryButton(
                text = stringResource(R.string.add_start),
                icon = painterResource(R.drawable.ic_f7_download),
                onClick = { submit() },
                modifier = Modifier.fillMaxWidth(),
                enabled = text.isNotBlank()
            )
        }
    }
}

