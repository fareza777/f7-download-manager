package com.f7.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import androidx.compose.ui.unit.dp

@Immutable
data class F7StatusColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val info: Color,
)

val LocalF7Colors = staticCompositionLocalOf {
    F7StatusColors(
        success = Color(0xFF11845B),
        onSuccess = Color.White,
        successContainer = Color(0xFFDEF6EA),
        warning = Color(0xFFB0660A),
        warningContainer = Color(0xFFFFF1D8),
        info = Color(0xFF2457E6),
    )
}

val F7Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun F7Theme(
    themeId: String = "aurora",
    darkTheme: Boolean = isSystemInDarkTheme(),
    textScale: Float = 1f,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = F7Themes.byId(themeId)
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> palette.dark
        else -> palette.light
    }
    val status = if (darkTheme) {
        F7StatusColors(
            success = Color(0xFF6FE0B0),
            onSuccess = Color(0xFF00382A),
            successContainer = Color(0xFF14483A),
            warning = Color(0xFFFFC266),
            warningContainer = Color(0xFF4A3410),
            info = Color(0xFF9BB6FF),
        )
    } else {
        F7StatusColors(
            success = Color(0xFF11845B),
            onSuccess = Color.White,
            successContainer = Color(0xFFDDF5EA),
            warning = Color(0xFFB0660A),
            warningContainer = Color(0xFFFFF0D5),
            info = colors.primary,
        )
    }

    CompositionLocalProvider(LocalF7Colors provides status) {
        MaterialTheme(
            colorScheme = colors,
            typography = f7Typography(textScale),
            shapes = F7Shapes,
            content = content,
        )
    }
}

