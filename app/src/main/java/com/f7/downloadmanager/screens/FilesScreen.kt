package com.f7.downloadmanager.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.f7.core.model.AppSettings
import com.f7.core.model.DownloadItem
import com.f7.core.model.FileCategory
import com.f7.core.ui.components.F7Card
import com.f7.core.ui.components.F7ChoiceChip
import com.f7.core.ui.components.F7EmptyState
import com.f7.core.ui.components.F7ScreenBackground
import com.f7.core.ui.components.FileBadge
import com.f7.downloadmanager.FilesViewModel
import com.f7.downloadmanager.R
import com.f7.downloadmanager.categoryColors
import com.f7.downloadmanager.copyToClipboard
import com.f7.downloadmanager.extensionLabel
import com.f7.downloadmanager.formatBytes
import com.f7.downloadmanager.formatDate
import com.f7.downloadmanager.iconRes
import com.f7.downloadmanager.labelRes
import com.f7.downloadmanager.openDownload
import com.f7.downloadmanager.shareDownload
import com.f7.downloadmanager.storageLabel
import kotlinx.coroutines.launch

@Composable
fun FilesScreen(
    settings: AppSettings,
    onOpenSettings: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: FilesViewModel = viewModel()
    val completed by vm.completed.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("all") }
    var showDuplicates by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DownloadItem?>(null) }

    val filtered = completed.filter { item ->
        val matchesQuery = query.isBlank() || item.fileName.contains(query, true)
        val matchesFilter = filter == "all" || item.category.name == filter
        matchesQuery && matchesFilter
    }
    val totalBytes = completed.sumOf { it.totalBytes }
    val duplicateGroups = remember(completed) {
        completed.groupBy { it.fileName.lowercase() to it.totalBytes }
            .filter { it.value.size > 1 }
            .values
            .toList()
    }
    val duplicateIdsToDelete = duplicateGroups.flatMap { group ->
        group.sortedByDescending { it.completedAt ?: 0L }.drop(1).map { it.id }
    }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.files_delete_title)) },
            text = {
                Text(stringResource(R.string.files_delete_message, pendingDelete?.fileName.orEmpty()))
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete?.let { vm.delete(it.id) }
                    pendingDelete = null
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.files_deleted)) }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showDuplicates) {
        AlertDialog(
            onDismissRequest = { showDuplicates = false },
            title = { Text(stringResource(R.string.files_duplicate_title)) },
            text = {
                Column {
                    if (duplicateGroups.isEmpty()) {
                        Text(stringResource(R.string.files_duplicate_none))
                    } else {
                        Text(stringResource(R.string.files_duplicate_found, duplicateGroups.size))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.files_duplicate_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        duplicateGroups.take(5).forEach { group ->
                            Text(
                                text = "${group.first().fileName} · ${group.size}x",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (duplicateGroups.isNotEmpty()) {
                    TextButton(onClick = {
                        showDuplicates = false
                        vm.deleteDuplicates(duplicateIdsToDelete)
                        scope.launch {
                            snackbar.showSnackbar(context.getString(R.string.files_duplicates_removed, duplicateIdsToDelete.size))
                        }
                    }) { Text(stringResource(R.string.files_delete_duplicate)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicates = false }) {
                    Text(stringResource(R.string.action_close))
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
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(modifier = Modifier.statusBarsPadding().padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.files_title),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.files_subtitle, completed.size, formatBytes(totalBytes)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { searchOpen = !searchOpen },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_f7_search),
                                contentDescription = stringResource(R.string.action_search),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { showDuplicates = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_f7_content_copy),
                                contentDescription = stringResource(R.string.files_duplicates),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    AnimatedVisibility(visible = searchOpen) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            placeholder = { Text(stringResource(R.string.files_search_hint)) },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.ic_f7_search), null)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            )
                        )
                    }
                }
            }

            item {
                StorageCard(
                    totalBytes = totalBytes,
                    location = storageLabel(settings, context),
                    onClick = onOpenSettings
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val filters = buildList {
                        add("all" to context.getString(R.string.category_all))
                        FileCategory.entries.forEach { add(it.name to context.getString(it.labelRes())) }
                    }
                    items(filters) { (key, label) ->
                        F7ChoiceChip(text = label, selected = filter == key, onClick = { filter = key })
                    }
                }
            }

            if (completed.isEmpty()) {
                item {
                    F7EmptyState(
                        icon = painterResource(R.drawable.ic_f7_folder_open),
                        title = stringResource(R.string.files_empty_title),
                        message = stringResource(R.string.files_empty_message)
                    )
                }
            } else if (filtered.isEmpty()) {
                item {
                    F7EmptyState(
                        icon = painterResource(R.drawable.ic_f7_search),
                        title = stringResource(R.string.home_no_results),
                        message = stringResource(R.string.home_no_results_desc)
                    )
                }
            } else {
                items(filtered.chunked(2), key = { it.first().id }) { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { item ->
                            FileTile(
                                modifier = Modifier.weight(1f),
                                item = item,
                                onClick = { onOpenDetail(item.id) },
                                onOpen = {
                                    if (!openDownload(context, item)) {
                                        scope.launch { snackbar.showSnackbar(context.getString(R.string.detail_open_failed)) }
                                    }
                                },
                                onShare = {
                                    if (!shareDownload(context, item)) {
                                        scope.launch { snackbar.showSnackbar(context.getString(R.string.detail_open_failed)) }
                                    }
                                },
                                onCopyPath = {
                                    copyToClipboard(context, item.fileName, item.displayPath ?: item.savedUri ?: "")
                                    scope.launch { snackbar.showSnackbar(context.getString(R.string.detail_path_copied)) }
                                },
                                onDelete = { pendingDelete = item }
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FileTile(
    item: DownloadItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onCopyPath: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val (iconColor, iconContainer) = categoryColors(item.category)
    Surface(
        modifier = modifier
            .height(162.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)
        ),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileBadge(
                    icon = painterResource(item.iconRes()),
                    label = item.extensionLabel(),
                    size = 42.dp,
                    container = iconContainer,
                    content = iconColor
                )
                Spacer(Modifier.weight(1f))
                Box {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable { menuOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_f7_more_vert),
                            contentDescription = stringResource(R.string.action_more),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_open)) },
                            onClick = { menuOpen = false; onOpen() },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_f7_open_in_new), null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share)) },
                            onClick = { menuOpen = false; onShare() },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_f7_share), null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.detail_copy_path)) },
                            onClick = { menuOpen = false; onCopyPath() },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_f7_content_copy), null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            onClick = { menuOpen = false; onDelete() },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_f7_delete), null) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = item.fileName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatBytes(item.totalBytes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = formatDate(item.completedAt ?: item.createdAt),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StorageCard(totalBytes: Long, location: String, onClick: () -> Unit) {
    val glow = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = glow.copy(alpha = 0.08f),
                    radius = size.width * 0.5f,
                    center = Offset(size.width, 0f)
                )
            }
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.ic_f7_storage),
                        null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(23.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatBytes(totalBytes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.files_storage_used_short),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                    Text(
                        text = location,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    painterResource(R.drawable.ic_f7_chevron_right),
                    null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun FileRow(
    item: DownloadItem,
    onClick: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onCopyPath: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val (iconColor, iconContainer) = categoryColors(item.category)
    F7Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileBadge(
                icon = painterResource(item.iconRes()),
                label = item.extensionLabel(),
                size = 48.dp,
                container = iconContainer,
                content = iconColor
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatBytes(item.totalBytes)} · ${formatDate(item.completedAt ?: item.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { menuOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.ic_f7_more_vert),
                        contentDescription = stringResource(R.string.action_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_open)) },
                        onClick = { menuOpen = false; onOpen() },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_f7_open_in_new), null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_share)) },
                        onClick = { menuOpen = false; onShare() },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_f7_share), null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.detail_copy_path)) },
                        onClick = { menuOpen = false; onCopyPath() },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_f7_content_copy), null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = { menuOpen = false; onDelete() },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_f7_delete), null) }
                    )
                }
            }
        }
    }
}


