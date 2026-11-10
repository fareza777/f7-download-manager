package com.f7.downloadmanager.ads

import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * Anchored adaptive banner rendered **above** the app bottom bar, which itself keeps
 * the system navigation inset. The Scaffold lays page content out around this slot,
 * so no native Android control (system navigation, app tabs) is ever covered.
 *
 * The AdView instance is created once and reused, and the slot is removed entirely
 * while a modal sheet is open, so typing in a dialog never competes with a banner
 * reload. Renders nothing for ad-free users and collapses when the ad fails.
 */
@Composable
fun AdBannerSlot(
    adController: AdController,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val adFree by adController.adFree.collectAsStateWithLifecycle()
    if (adFree || !visible) return

    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val density = context.resources.displayMetrics.density
    var failed by remember { mutableStateOf(false) }
    if (failed) return

    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceArgb = surfaceColor.toArgb()

    val adaptiveSize = remember(widthDp) {
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
            context,
            widthDp.coerceAtLeast(320)
        ) ?: AdSize.BANNER
    }
    val slotHeight = remember(adaptiveSize, density) {
        val px = if (adaptiveSize.height > 0) {
            adaptiveSize.height
        } else {
            adaptiveSize.getHeightInPixels(context)
        }
        (px / density).toInt().coerceIn(50, 120)
    }

    // One AdView for the whole slot lifetime; recomposition never reloads it.
    val adView = remember {
        AdView(context).apply {
            adUnitId = AdConfig.bannerUnitId
            setAdSize(adaptiveSize)
            setBackgroundColor(surfaceArgb)
            adListener = object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    failed = true
                }
            }
            runCatching { loadAd(AdRequest.Builder().build()) }
                .onFailure { failed = true }
        }
    }

    DisposableEffect(adView) {
        onDispose { runCatching { adView.destroy() } }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(surfaceColor)
            .padding(top = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(slotHeight.dp),
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    setBackgroundColor(surfaceArgb)
                    if (adView.parent != null) {
                        (adView.parent as? FrameLayout)?.removeView(adView)
                    }
                    addView(adView)
                }
            },
            update = { }
        )
    }
}
