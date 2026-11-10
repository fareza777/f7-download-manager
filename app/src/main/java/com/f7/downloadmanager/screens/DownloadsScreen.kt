package com.f7.downloadmanager.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.f7.core.model.AppSettings
import com.f7.core.model.DownloadItem
import com.f7.core.model.DownloadStatus
import com.f7.core.model.F7Format
import com.f7.core.model.FileCategory
import com.f7.core.ui.components.Eyebrow
import com.f7.core.ui.components.F7Card
import com.f7.core.ui.components.F7ChoiceChip
import com.f7.core.ui.components.F7EmptyState
import com.f7.core.ui.components.F7LogoMark
import com.f7.core.ui.components.F7Pill
import com.f7.core.ui.components.F7ProgressBar
import com.f7.core.ui.components.F7ScreenBackground
import com.f7.core.ui.components.F7SectionHeading
import com.f7.core.ui.components.FileBadge
import com.f7.core.ui.components.GradientIcon
import com.f7.downloadmanager.categoryColors
import com.f7.core.ui.theme.F7Themes
import com.f7.core.ui.theme.LocalF7Colors
import com.f7.downloadmanager.DownloadsViewModel
import com.f7.downloadmanager.R
import com.f7.downloadmanager.extensionLabel
import com.f7.downloadmanager.formatBytes
import com.f7.downloadmanager.formatDate
import com.f7.downloadmanager.formatEta
import com.f7.downloadmanager.formatSpeed
import com.f7.downloadmanager.iconRes
import com.f7.downloadmanager.labelRes
import com.f7.downloadmanager.localizedError
import com.f7.downloadmanager.statusLabelRes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val statusOrder = mapOf(
    DownloadStatus.DOWNLOADING to 0,
    DownloadStatus.CONNECTING to 1,
    DownloadStatus.QUEUED to 2,
    DownloadStatus.PAUSED to 3,
    DownloadStatus.FAILED to 4,
)

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    settings: AppSettings,
    onAdd: (String?) -> Unit,
    onOpenDetail: (Long) -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by viewModel.downloads.collectAsStateWithLifecycle()
    val liveSettings by viewModel.settings.collectAsStateWithLifecycle()

    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }

    val active = items.filter { it.status.isActive }
    val completed = items.filter { it.status == DownloadStatus.COMPLETED }
    val unfinished = items.filter { !it.status.isFinished && it.status != DownloadStatus.COMPLETED }
    val totalSpeed = active.sumOf { it.speedBytesPerSec }
    val totalSaved = completed.sumOf { it.totalBytes }

    fun matches(item: DownloadItem): Boolean {
        val matchesQuery = query.isBlank() ||
            item.fileName.contains(query, true) || item.url.contains(query, true)
        val matchesFilter = when (filter) {
            "all" -> true
            "active" -> !item.status.isFinished && item.status != DownloadStatus.COMPLETED
            else -> item.category.name == filter
        }
        return matchesQuery && matchesFilter
    }

    val filteredQueue = unfinished.filter(::matches).sortedWith(
        compareBy({ statusOrder[it.status] ?: 9 }, { -it.createdAt })
    )
    val filteredCompleted = completed.filter(::matches)

    var samples by remember { mutableStateOf(listOf<Long>()) }
    val currentSpeed by rememberUpdatedState(totalSpeed)
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            if (currentSpeed > 0) samples = (samples + currentSpeed).takeLast(30)
        }
    }

    F7ScreenBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 780.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 0.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        F7LogoMark(size = 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Eyebrow(text = "F7 Download Manager")
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = stringResource(R.string.home_header_title),
                                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp),
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        RoundIconButton(
                            icon = painterResource(R.drawable.ic_f7_add),
                            label = stringResource(R.string.home_quick_add),
                            onClick = { onAdd(null) },
                            filled = true
                        )
                        Spacer(Modifier.width(8.dp))
                        RoundIconButton(
                            icon = painterResource(R.drawable.ic_f7_search),
                            label = stringResource(R.string.action_search),
                            onClick = { searchOpen = !searchOpen }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (active.isNotEmpty()) {
                            "${active.size} downloading · ${formatBytes(totalSaved)} saved"
                        } else {
                            stringResource(R.string.home_subtitle_idle)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AnimatedVisibility(visible = searchOpen) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            placeholder = { Text(stringResource(R.string.home_search_hint)) },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.ic_f7_search), null)
                            },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    Icon(
                                        painterResource(R.drawable.ic_f7_close),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable { query = "" }
                                            .padding(8.dp)
                                    )
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { searchOpen = false }),
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
                PremiumHero(
                    themeId = settings.themeId,
                    totalSpeed = totalSpeed,
                    activeCount = active.size,
                    completedCount = completed.size,
                    savedBytes = totalSaved,
                    samples = samples,
                    maxConnections = liveSettings.maxConnections,
                    onClick = onOpenStats,
                )
            }

            item {
                QuickActionBar(
                    onAdd = { onAdd(null) },
                    onPaste = {
                        val clip = com.f7.downloadmanager.clipboardText(context)
                        if (clip.isNullOrBlank()) {
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.add_clipboard_empty)) }
                        } else {
                            onAdd(clip)
                        }
                    },
                    onBrowser = onOpenBrowser,
                    onSchedule = onOpenSchedules,
                )
            }

            item {
                val filters = listOf(
                    "all" to stringResource(R.string.home_category_all),
                    "active" to stringResource(R.string.home_filter_active),
                    FileCategory.VIDEO.name to stringResource(R.string.category_video),
                    FileCategory.AUDIO.name to stringResource(R.string.category_audio),
                    FileCategory.DOCUMENT.name to stringResource(R.string.category_document),
                    FileCategory.ARCHIVE.name to stringResource(R.string.category_archive),
                    FileCategory.APP.name to stringResource(R.string.category_app),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filters) { (key, label) ->
                        F7ChoiceChip(
                            text = label,
                            selected = filter == key,
                            onClick = { filter = key }
                        )
                    }
                }
            }

            if (items.isEmpty()) {
                item {
                    CompactEmptyState(onAdd = { onAdd(null) })
                }
            } else if (filteredQueue.isEmpty() && filteredCompleted.isEmpty()) {
                item {
                    F7EmptyState(
                        icon = painterResource(R.drawable.ic_f7_search),
                        title = stringResource(R.string.home_no_results),
                        message = stringResource(R.string.home_no_results_desc)
                    )
                }
            }

            if (filteredQueue.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Eyebrow(text = stringResource(R.string.home_active_title))
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ) {
                            Text(
                                text = filteredQueue.size.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                items(filteredQueue, key = { "queue-${it.id}" }) { item ->
                    DownloadRow(
                        item = item,
                        onClick = { onOpenDetail(item.id) },
                        onToggle = {
                            when (item.status) {
                                DownloadStatus.PAUSED, DownloadStatus.FAILED -> viewModel.resume(item.id)
                                else -> viewModel.pause(item.id)
                            }
                        },
                        onCancel = { viewModel.cancel(item.id) },
                        onRetry = { viewModel.retry(item.id) },
                        onDelete = { viewModel.delete(item.id) },
                    )
                }
            }

            if (filteredCompleted.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(2.dp))
                    F7SectionHeading(
                        title = stringResource(R.string.home_recent_title),
                        subtitle = stringResource(R.string.home_completed_subtitle),
                        actionLabel = stringResource(R.string.home_more_completed),
                        onAction = onOpenFiles,
                    )
                }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(end = 4.dp)
                    ) {
                        items(filteredCompleted.take(10), key = { "recent-${it.id}" }) { item ->
                            RecentFileCard(item = item, onClick = { onOpenDetail(item.id) })
                        }
                    }
                }
            }

            item {
                F7Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GradientIcon(
                            icon = painterResource(R.drawable.ic_f7_share),
                            colors = listOf(Color(0xFF7A3BC4), Color(0xFFB07BFF)),
                            size = 46.dp,
                            shape = RoundedCornerShape(15.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.home_tip_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.home_tip_text),
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

@Composable
private fun RoundIconButton(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    filled: Boolean = false,
) {
    Surface(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = if (filled) null else androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)
        ),
        shadowElevation = if (filled) 4.dp else 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = icon,
                contentDescription = label,
                tint = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PremiumHero(
    themeId: String,
    totalSpeed: Long,
    activeCount: Int,
    completedCount: Int,
    savedBytes: Long,
    samples: List<Long>,
    maxConnections: Int,
    onClick: () -> Unit,
) {
    val palette = F7Themes.byId(themeId)
    val gradient = listOf(Color(0xFF10245C), palette.gradientStart, palette.gradientEnd)
    val pulse by rememberInfiniteTransition(label = "live").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulse"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(162.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .background(Brush.linearGradient(gradient))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.07f),
                radius = size.width * 0.62f,
                center = Offset(size.width * 1.02f, -size.height * 0.12f)
            )
            drawCircle(
                color = Color(0xFF3BE6C0).copy(alpha = 0.20f),
                radius = size.width * 0.20f,
                center = Offset(size.width * 0.76f, size.height * 1.08f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = size.width * 0.30f,
                center = Offset(size.width * 0.08f, size.height * 1.18f),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(7.dp)) {
                    drawCircle(color = Color(0xFF7CFFD9).copy(alpha = pulse))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        if (totalSpeed > 0) R.string.home_hero_label else R.string.home_hero_ready
                    ).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.72f)
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = CircleShape
                ) {
                    Text(
                        text = stringResource(R.string.home_hero_storage, formatBytes(savedBytes)),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (totalSpeed > 0) formatBytes(totalSpeed) else stringResource(R.string.home_hero_idle),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = (-0.8).sp
                )
                if (totalSpeed > 0) {
                    Text(
                        text = "/s",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.padding(start = 3.dp, bottom = 4.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                HeroSparkline(samples = samples, modifier = Modifier.width(104.dp).height(44.dp))
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroStat(value = activeCount.toString(), label = stringResource(R.string.home_filter_active))
                HeroDot()
                HeroStat(value = completedCount.toString(), label = stringResource(R.string.status_completed))
                HeroDot()
                HeroStat(value = maxConnections.toString(), label = "MAX CONN")
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.8.sp),
            color = Color.White.copy(alpha = 0.58f)
        )
    }
}

@Composable
private fun HeroDot() {
    Box(
        modifier = Modifier
            .padding(horizontal = 14.dp)
            .size(3.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.25f))
    )
}

@Composable
private fun HeroSparkline(samples: List<Long>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (samples.size < 2) return@Canvas
        val maxValue = (samples.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
        val step = size.width / (samples.size - 1)
        val path = Path()
        samples.forEachIndexed { index, value ->
            val x = index * step
            val y = size.height - (value / maxValue) * size.height * 0.88f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = Color.White.copy(alpha = 0.92f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawCircle(
            color = Color(0xFF7CFFD9),
            radius = 3.dp.toPx(),
            center = Offset(size.width, size.height - (samples.last() / maxValue) * size.height * 0.88f)
        )
    }
}

@Composable
private fun QuickActionBar(
    onAdd: () -> Unit,
    onPaste: () -> Unit,
    onBrowser: () -> Unit,
    onSchedule: () -> Unit,
) {
    val actions = listOf(
        Triple(R.drawable.ic_f7_add, R.string.home_quick_add, onAdd),
        Triple(R.drawable.ic_f7_content_copy, R.string.home_quick_paste, onPaste),
        Triple(R.drawable.ic_f7_public, R.string.home_quick_browser, onBrowser),
        Triple(R.drawable.ic_f7_schedule, R.string.home_quick_schedule, onSchedule),
    )
    val colors = listOf(
        listOf(Color(0xFF1F4BD8), Color(0xFF5B86FF)),
        listOf(Color(0xFF7A3BC4), Color(0xFFB07BFF)),
        listOf(Color(0xFF0C8F7D), Color(0xFF3ECFB4)),
        listOf(Color(0xFFB46A00), Color(0xFFF2AE4C)),
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)
        ),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            actions.forEachIndexed { index, (icon, label, onClick) ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onClick)
                        .padding(vertical = 2.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GradientIcon(
                        icon = painterResource(icon),
                        colors = colors[index],
                        size = 44.dp,
                        shape = RoundedCornerShape(15.dp)
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = stringResource(label),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactEmptyState(onAdd: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)
        ),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GradientIcon(
                icon = painterResource(R.drawable.ic_f7_cloud_download),
                colors = listOf(Color(0xFF1F4BD8), Color(0xFF5B86FF)),
                size = 56.dp,
                shape = RoundedCornerShape(18.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.home_empty_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.home_empty_cta),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onAdd)
                        .padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val (iconColor, iconContainer) = categoryColors(item.category)
    val statusColors = when (item.status) {
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        DownloadStatus.PAUSED -> LocalF7Colors.current.warningContainer to LocalF7Colors.current.warning
        DownloadStatus.COMPLETED -> LocalF7Colors.current.successContainer to LocalF7Colors.current.success
        else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
    }
    val isRunning = item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.CONNECTING

    F7Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            FileBadge(
                icon = painterResource(item.iconRes()),
                label = item.extensionLabel(),
                size = 52.dp,
                container = iconContainer,
                content = iconColor
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    F7Pill(
                        text = stringResource(statusLabelRes(item)),
                        containerColor = statusColors.first,
                        contentColor = statusColors.second
                    )
                    if (item.speedBytesPerSec > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = formatSpeed(item.speedBytesPerSec),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (item.activeConnections > 1) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = " connections",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggle),
                    shape = CircleShape,
                    color = if (item.status == DownloadStatus.DOWNLOADING) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(
                                when (item.status) {
                                    DownloadStatus.DOWNLOADING, DownloadStatus.CONNECTING, DownloadStatus.QUEUED ->
                                        R.drawable.ic_f7_pause
                                    DownloadStatus.FAILED -> R.drawable.ic_f7_refresh
                                    else -> R.drawable.ic_f7_play_arrow
                                }
                            ),
                            contentDescription = null,
                            tint = if (item.status == DownloadStatus.DOWNLOADING) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                Box {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { menuOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_f7_more_vert),
                            contentDescription = stringResource(R.string.action_more),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_retry)) },
                            onClick = { menuOpen = false; onRetry() },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_f7_refresh), null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_cancel)) },
                            onClick = { menuOpen = false; onCancel() },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_f7_close), null) }
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
        Spacer(Modifier.height(12.dp))
        F7ProgressBar(progress = item.progress, height = 7.dp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (item.totalBytes > 0) {
                    "${F7Format.percent(item.progress)} · ${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}"
                } else {
                    formatBytes(item.downloadedBytes)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(Modifier.weight(1f))
            if (isRunning && item.etaSeconds > 0) {
                Text(
                    text = stringResource(R.string.home_eta_left, formatEta(item.etaSeconds)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (item.status == DownloadStatus.FAILED && !item.error.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = localizedError(context, item.error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RecentFileCard(item: DownloadItem, onClick: () -> Unit) {
    val (iconColor, iconContainer) = categoryColors(item.category)
    Surface(
        modifier = Modifier
            .width(184.dp)
            .height(134.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)
        ),
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileBadge(
                    icon = painterResource(item.iconRes()),
                    label = item.extensionLabel(),
                    size = 34.dp,
                    container = iconContainer,
                    content = iconColor
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    painterResource(R.drawable.ic_f7_check_circle),
                    contentDescription = null,
                    tint = LocalF7Colors.current.success,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.fileName,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${formatBytes(item.totalBytes)} · ${formatDate(item.completedAt ?: item.createdAt)}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun NewDownloadButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.Transparent,
        shadowElevation = 10.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            androidx.compose.ui.graphics.lerp(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary,
                                0.32f
                            )
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_f7_add),
                contentDescription = stringResource(R.string.home_quick_add),
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}




