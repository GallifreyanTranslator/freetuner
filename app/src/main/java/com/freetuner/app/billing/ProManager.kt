package com.freetuner.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One-time "FreeTuner Pro" unlock through Google Play Billing (non-consumable in-app product).
 * The unlock state is cached locally so Pro keeps working offline; it is re-verified with Play
 * every time the app connects, so a refund removes it.
 */
class ProManager(context: Context) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("freetuner_pro", Context.MODE_PRIVATE)

    private val _isPro = MutableStateFlow(prefs.getBoolean(KEY_PRO, false))
    val isPro: StateFlow<Boolean> = _isPro

    private val _price = MutableStateFlow<String?>(null)
    val price: StateFlow<String?> = _price

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private var client: BillingClient? = null
    private var details: ProductDetails? = null

    fun connect() {
        if (client?.isReady == true) {
            refresh()
            return
        }
        val newClient = BillingClient.newBuilder(appContext)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        client = newClient
        newClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
            }

            override fun onBillingServiceDisconnected() {
                // Reconnected on the next connect(); cached state stays valid meanwhile.
            }
        })
    }

    fun disconnect() {
        client?.endConnection()
        client = null
    }

    fun restore() {
        _message.value = null
        if (client?.isReady == true) refresh() else connect()
    }

    fun buy(activity: Activity) {
        _message.value = null
        val productDetails = details
        val billing = client
        if (billing == null || !billing.isReady || productDetails == null) {
            _message.value = "Google Play is not available right now. Please try again in a moment."
            connect()
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(productDetails).build()),
            )
            .build()
        billing.launchBillingFlow(activity, params)
    }

    private fun refresh() {
        val billing = client ?: return
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billing.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build(),
        ) { result, productDetailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                details = productDetailsResult.productDetailsList.firstOrNull()
                _price.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
            }
        }
        billing.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val owned = purchases.any { it.isProPurchased() }
                setPro(owned)
                purchases.filter { it.isProPurchased() }.forEach(::acknowledgeIfNeeded)
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val bought = purchases.orEmpty().filter { it.isProPurchased() }
                if (bought.isNotEmpty()) {
                    setPro(true)
                    bought.forEach(::acknowledgeIfNeeded)
                } else if (purchases.orEmpty().any { it.products.contains(PRODUCT_ID) }) {
                    _message.value = "Your purchase is pending. Pro unlocks as soon as it completes."
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> _message.value = "Purchase could not be completed (code ${result.responseCode})."
        }
    }

    private fun Purchase.isProPurchased(): Boolean =
        products.contains(PRODUCT_ID) && purchaseState == Purchase.PurchaseState.PURCHASED

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        client?.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
        ) { /* Play refunds unacknowledged purchases after 3 days; retried on next connect. */ }
    }

    private fun setPro(value: Boolean) {
        _isPro.value = value
        prefs.edit().putBoolean(KEY_PRO, value).apply()
    }

    companion object {
        const val PRODUCT_ID = "pro_unlock"
        private const val KEY_PRO = "pro"
    }
}