package com.f7.downloadmanager.ads

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.f7.core.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Google Play Billing for the one-time "Remove ads" product.
 *
 * The price itself is configured in Play Console; the app only displays the
 * localized price that Play returns. When Billing is unavailable (no Play Store,
 * no product created yet) the UI stays usable and reports the problem instead of
 * pretending the purchase worked.
 */
class BillingManager(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
) {

    data class State(
        val connected: Boolean = false,
        val priceText: String = AdConfig.REMOVE_ADS_FALLBACK_PRICE,
        val purchased: Boolean = false,
        val busy: Boolean = false,
        val message: String? = null,
        val billingAvailable: Boolean = true,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var client: BillingClient? = null
    private var productDetails: ProductDetails? = null
    private var connecting = false

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
                if (purchases.isNullOrEmpty()) {
                    _state.value = _state.value.copy(busy = false)
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _state.value = _state.value.copy(busy = false, message = "Purchase canceled")
            }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                restore()
            }

            else -> {
                _state.value = _state.value.copy(
                    busy = false,
                    message = "Purchase failed (${result.responseCode})",
                )
            }
        }
    }

    fun start() {
        if (client != null || connecting) return
        connecting = true
        val billing = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()
        client = billing
        billing.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.value = _state.value.copy(connected = true, billingAvailable = true)
                    queryProduct()
                    restore()
                } else {
                    _state.value = _state.value.copy(
                        connected = false,
                        billingAvailable = false,
                        message = "Play Billing unavailable (${result.responseCode})",
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                connecting = false
                _state.value = _state.value.copy(connected = false)
            }
        })
    }

    private fun queryProduct() {
        val billing = client ?: return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(AdConfig.removeAdsProductId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        runCatching {
            billing.queryProductDetailsAsync(
                params
            ) { result, details ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    val product = details.firstOrNull()
                    productDetails = product
                    val price = product?.oneTimePurchaseOfferDetails?.formattedPrice
                    _state.value = _state.value.copy(
                        priceText = price ?: AdConfig.REMOVE_ADS_FALLBACK_PRICE
                    )
                } else {
                    _state.value = _state.value.copy(
                        message = "Remove Ads product not configured in Play Console"
                    )
                }
            }
        }
    }

    fun purchase(activity: Activity?) {
        if (activity == null) return
        val billing = client
        val details = productDetails
        if (billing == null || details == null) {
            _state.value = _state.value.copy(
                busy = false,
                message = "Purchase is not available yet. Check your connection and try again.",
            )
            start()
            return
        }
        _state.value = _state.value.copy(busy = true, message = null)
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        val result = runCatching { billing.launchBillingFlow(activity, params) }.getOrNull()
        if (result == null || result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.value = _state.value.copy(
                busy = false,
                message = "Could not open the Play purchase sheet",
            )
        }
    }

    fun restore() {
        val billing = client ?: run {
            start()
            return
        }
        runCatching {
            billing.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            ) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    val owned = purchases.any { purchase ->
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                            purchase.products.contains(AdConfig.removeAdsProductId)
                    }
                    if (owned) {
                        purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                            .forEach { handlePurchase(it) }
                    } else {
                        _state.value = _state.value.copy(purchased = false)
                    }
                }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!purchase.products.contains(AdConfig.removeAdsProductId)) return

        val billing = client
        if (!purchase.isAcknowledged && billing != null) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            runCatching {
                billing.acknowledgePurchase(params) { }
            }
        }
        scope.launch {
            settingsStore.setRemoveAdsPurchased(true)
            _state.value = _state.value.copy(
                purchased = true,
                busy = false,
                message = "Ads removed. Thank you!",
            )
        }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun refreshEntitlement() {
        scope.launch {
            val purchasedNow = settingsStore.current().removeAdsPurchased
            _state.value = _state.value.copy(purchased = purchasedNow)
        }
    }
}


