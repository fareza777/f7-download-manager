package com.f7.downloadmanager

import android.app.Activity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.f7.downloadmanager.ads.AdConfig
import kotlinx.coroutines.launch

class AdsViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val graph = (app as F7Application).graph
    private val ads = graph.adController
    private val billing = graph.billing

    val billingState = billing.state
    val adFree = ads.adFree
    val purchased = ads.purchased
    val rewardedReady = ads.rewardedReady

    fun refresh() {
        billing.refreshEntitlement()
        ads.preloadRewarded()
    }

    fun purchase(activity: Activity?) = billing.purchase(activity)

    fun restore() = billing.restore()

    fun dismissMessage() = billing.dismissMessage()

    fun watchRewarded(activity: Activity?, onResult: (granted: Boolean) -> Unit) {
        ads.showRewarded(
            activity = activity,
            onReward = {
                viewModelScope.launch {
                    ads.grantRewardAdFree()
                    onResult(true)
                }
            },
            onUnavailable = { onResult(false) },
        )
    }

    val rewardDurationLabel: String
        get() = "${AdConfig.REWARD_AD_FREE_MS / (60 * 60 * 1000)} hours"
}
