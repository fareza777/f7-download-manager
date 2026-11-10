package com.f7.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
fun F7ScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val glow = MaterialTheme.colorScheme.primary
    val glow2 = MaterialTheme.colorScheme.secondary
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to background,
                    0.55f to background,
                    1f to MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
                )
            )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(460.dp)
        ) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(glow.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.92f, 0f),
                    radius = size.width * 0.92f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(glow2.copy(alpha = 0.045f), Color.Transparent),
                    center = Offset(size.width * 0.02f, size.height * 0.32f),
                    radius = size.width * 0.7f
                )
            )
        }
        content()
    }
}

@Composable
fun F7TopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backIcon: Painter? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null && backIcon != null) {
            F7IconButton(icon = backIcon, contentDescription = "Back", onClick = onBack)
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), content = actions)
    }
}

@Composable
fun F7IconButton(
    icon: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    container: Color = MaterialTheme.colorScheme.surface,
    size: Dp = 42.dp,
    showBorder: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .then(
                if (showBorder) Modifier
                    .background(Color.Transparent)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.46f)
        )
    }
}

@Composable
fun F7ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: Painter? = null,
) {
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = container,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        ),
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    painter = leadingIcon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelMedium, color = content)
        }
    }
}

@Composable
fun SpeedChart(
    samples: List<Long>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val secondary = MaterialTheme.colorScheme.secondary
    Canvas(modifier = modifier.fillMaxWidth().height(96.dp)) {
        val w = size.width
        val h = size.height
        for (i in 0..3) {
            val y = h * i / 3f
            drawLine(color = grid.copy(alpha = 0.55f), start = Offset(0f, y), end = Offset(w, y), strokeWidth = 1f)
        }
        if (samples.size < 2) return@Canvas
        val maxValue = max(1L, samples.maxOrNull() ?: 1L).toFloat()
        val step = w / (samples.size - 1)
        val line = Path()
        val area = Path()
        samples.forEachIndexed { index, value ->
            val x = index * step
            val y = h - (value / maxValue) * (h * 0.86f) - h * 0.06f
            if (index == 0) {
                line.moveTo(x, y)
                area.moveTo(x, h)
                area.lineTo(x, y)
            } else {
                val prevX = (index - 1) * step
                val prevValue = samples[index - 1].toFloat()
                val prevY = h - (prevValue / maxValue) * (h * 0.86f) - h * 0.06f
                val midX = (prevX + x) / 2
                line.cubicTo(midX, prevY, midX, y, x, y)
                area.cubicTo(midX, prevY, midX, y, x, y)
            }
        }
        area.lineTo(w, h)
        area.close()
        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.02f))
            )
        )
        drawPath(
            path = line,
            brush = Brush.horizontalGradient(listOf(lineColor, secondary)),
            style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
        )
        val lastIndex = samples.size - 1
        val lastX = lastIndex * step
        val lastY = h - (samples.last() / maxValue) * (h * 0.86f) - h * 0.06f
        drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(lastX, lastY))
        drawCircle(color = Color.White, radius = 1.8.dp.toPx(), center = Offset(lastX, lastY))
    }
}

