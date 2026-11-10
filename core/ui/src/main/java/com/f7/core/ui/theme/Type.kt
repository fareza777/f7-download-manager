@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.f7.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.f7.core.ui.R

private fun manrope(weight: Int): Font = Font(
    resId = R.font.manrope,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val ManropeFamily = FontFamily(
    manrope(400),
    manrope(500),
    manrope(600),
    manrope(700),
    manrope(800),
)

fun f7Typography(scale: Float): Typography {
    val s = scale.coerceIn(0.9f, 1.3f)
    fun style(
        size: Float,
        height: Float,
        weight: FontWeight,
        tracking: Float = 0f,
    ) = TextStyle(
        fontFamily = ManropeFamily,
        fontWeight = weight,
        fontSize = (size * s).sp,
        lineHeight = (height * s).sp,
        letterSpacing = tracking.sp,
    )

    return Typography(
        displayLarge = style(44f, 50f, FontWeight.Bold, (-1.0f)),
        displayMedium = style(38f, 44f, FontWeight.Bold, (-0.8f)),
        displaySmall = style(32f, 38f, FontWeight.Bold, (-0.6f)),
        headlineLarge = style(30f, 36f, FontWeight.Bold, (-0.5f)),
        headlineMedium = style(26f, 32f, FontWeight.Bold, (-0.4f)),
        headlineSmall = style(23f, 29f, FontWeight.SemiBold, (-0.3f)),
        titleLarge = style(20f, 26f, FontWeight.SemiBold, (-0.2f)),
        titleMedium = style(17f, 23f, FontWeight.SemiBold),
        titleSmall = style(15f, 20f, FontWeight.SemiBold),
        bodyLarge = style(16.5f, 24f, FontWeight.Normal),
        bodyMedium = style(15f, 22f, FontWeight.Normal),
        bodySmall = style(13.5f, 19f, FontWeight.Normal),
        labelLarge = style(15f, 20f, FontWeight.SemiBold),
        labelMedium = style(13f, 17f, FontWeight.Medium),
        labelSmall = style(11.5f, 15f, FontWeight.Medium),
    )
}
