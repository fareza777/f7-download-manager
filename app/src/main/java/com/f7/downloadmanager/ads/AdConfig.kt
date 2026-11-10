package com.f7.downloadmanager.ads

import com.f7.downloadmanager.BuildConfig

/**
 * Single place that holds every advertising identifier.
 *
 * The values compiled in by default are Google's official **test** ad units, so the
 * app is safe to build and run without an AdMob account. Replace them in
 * `app/build.gradle.kts` (`ADMOB_BANNER_ID`, `ADMOB_INTERSTITIAL_ID`,
 * `ADMOB_REWARDED_ID`, `admobAppId`, `REMOVE_ADS_PRODUCT_ID`) with your own
 * production values before publishing, and set `USE_TEST_ADS = false`.
 */
object AdConfig {
    val testMode: Boolean = BuildConfig.USE_TEST_ADS

    val bannerUnitId: String = BuildConfig.ADMOB_BANNER_ID
    val interstitialUnitId: String = BuildConfig.ADMOB_INTERSTITIAL_ID
    val rewardedUnitId: String = BuildConfig.ADMOB_REWARDED_ID

    /** One-time in-app product created in Play Console with a $4.99 price. */
    val removeAdsProductId: String = BuildConfig.REMOVE_ADS_PRODUCT_ID

    /** Fallback label shown when Play Billing has not returned a localized price yet. */
    const val REMOVE_ADS_FALLBACK_PRICE = "$4.99"

    /** Minimum time between two interstitials. */
    const val INTERSTITIAL_COOLDOWN_MS = 3 * 60 * 1000L

    /** How long one rewarded view keeps the app ad-free. */
    const val REWARD_AD_FREE_MS = 2 * 60 * 60 * 1000L
}
