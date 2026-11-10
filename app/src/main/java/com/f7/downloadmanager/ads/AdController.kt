package com.f7.downloadmanager.ads

import android.app.Activity
import android.content.Context
import com.f7.core.data.prefs.SettingsStore
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns every AdMob interaction: SDK initialisation, banner loading, the throttled
 * interstitial, and the rewarded ad that grants temporary ad-free time.
 *
 * Ads are never loaded while the user owns "Remove ads" or has an active rewarded
 * ad-free window, so a paying user does not download ad creatives at all.
 */
class AdController(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
) {

    private val initialised = AtomicBoolean(false)
    private val initialising = AtomicBoolean(false)

    private var interstitial: InterstitialAd? = null
    private var interstitialLoading = false
    private var rewarded: RewardedAd? = null
    private var rewardedLoading = false

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }

    val adFree: StateFlow<Boolean> = combine(settingsStore.settings, ticker) { settings, now ->
        settings.removeAdsPurchased || settings.adFreeUntil > now
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val purchased: StateFlow<Boolean> = settingsStore.settings
        .map { it.removeAdsPurchased }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val _rewardedReady = MutableStateFlow(false)
    val rewardedReady: StateFlow<Boolean> = _rewardedReady

    fun initialize() {
        if (initialised.get() || !initialising.compareAndSet(false, true)) return
        scope.launch {
            runCatching {
                MobileAds.initialize(context) {
                    initialised.set(true)
                    initialising.set(false)
                    // Preloads requested before the SDK was ready were dropped, so
                    // start them now that ads can actually be requested. They are
                    // staggered so a cold start never loads two formats at once.
                    scope.launch {
                        preloadInterstitial()
                        delay(4_000)
                        preloadRewarded()
                    }
                }
            }.onFailure {
                initialising.set(false)
            }
        }
    }

    private fun ready(): Boolean = initialised.get() || run {
        initialize()
        false
    }

    // Interstitial -------------------------------------------------------------

    fun preloadInterstitial() {
        if (adFree.value) return
        if (!ready()) return
        if (interstitial != null || interstitialLoading) return
        interstitialLoading = true
        runCatching {
            InterstitialAd.load(
                context,
                AdConfig.interstitialUnitId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialLoading = false
                        interstitial = ad
                        android.util.Log.i("F7Ads", "interstitial loaded")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitialLoading = false
                        interstitial = null
                        android.util.Log.w("F7Ads", "interstitial failed: ${error.code} ${error.message}")
                    }
                }
            )
        }.onFailure { interstitialLoading = false }
    }

    /**
     * Shows an interstitial only when the user is not ad-free and the cooldown has
     * elapsed. [onFinished] always runs, so callers can continue their flow even when
     * no ad is available.
     */
    fun showInterstitial(activity: Activity?, force: Boolean = false, onFinished: () -> Unit = {}) {
        if (activity == null || activity.isFinishing || adFree.value) {
            onFinished()
            return
        }
        val ad = interstitial
        if (ad == null) {
            android.util.Log.i("F7Ads", "interstitial not ready (adFree=${adFree.value})")
            preloadInterstitial()
            onFinished()
            return
        }
        scope.launch {
            val settings = settingsStore.current()
            val sinceLast = System.currentTimeMillis() - settings.interstitialLastShownAt
            if (!force && sinceLast < AdConfig.INTERSTITIAL_COOLDOWN_MS) {
                onFinished()
                return@launch
            }
            settingsStore.setInterstitialLastShown(System.currentTimeMillis())
            interstitial = null
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    preloadInterstitial()
                    onFinished()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    preloadInterstitial()
                    onFinished()
                }
            }
            runCatching { ad.show(activity) }.onFailure {
                preloadInterstitial()
                onFinished()
            }
        }
    }

    // Rewarded -----------------------------------------------------------------

    fun preloadRewarded() {
        if (adFree.value) return
        if (!ready()) return
        if (rewarded != null || rewardedLoading) return
        rewardedLoading = true
        runCatching {
            RewardedAd.load(
                context,
                AdConfig.rewardedUnitId,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedLoading = false
                        rewarded = ad
                        _rewardedReady.value = true
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        rewardedLoading = false
                        rewarded = null
                        _rewardedReady.value = false
                    }
                }
            )
        }.onFailure {
            rewardedLoading = false
            _rewardedReady.value = false
        }
    }

    fun showRewarded(
        activity: Activity?,
        onReward: () -> Unit,
        onUnavailable: () -> Unit = {},
        onDismissed: () -> Unit = {},
    ) {
        if (activity == null || activity.isFinishing) {
            onUnavailable()
            return
        }
        val ad = rewarded
        if (ad == null) {
            preloadRewarded()
            onUnavailable()
            return
        }
        rewarded = null
        _rewardedReady.value = false
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preloadRewarded()
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                preloadRewarded()
                onDismissed()
            }
        }
        runCatching {
            ad.show(activity) {
                if (!earned) {
                    earned = true
                    onReward()
                }
            }
        }.onFailure { onDismissed() }
    }

    suspend fun grantRewardAdFree() {
        val until = System.currentTimeMillis() + AdConfig.REWARD_AD_FREE_MS
        settingsStore.setAdFreeUntil(until)
    }
}

