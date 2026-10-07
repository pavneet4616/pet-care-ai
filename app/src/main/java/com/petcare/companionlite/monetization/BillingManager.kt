package com.petcare.companionlite.monetization

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import com.petcare.companionlite.R
import com.petcare.companionlite.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class StoreProduct(
    val id: String,
    val title: String,
    val description: String,
    val priceText: String,
)

data class BillingUiState(
    val isReady: Boolean = false,
    val isLoading: Boolean = true,
    val products: List<StoreProduct> = emptyList(),
    val weeklyReady: Boolean = false,
    val monthlyReady: Boolean = false,
    val message: String? = null,
)

class BillingManager(
    context: Context,
    private val settingsStore: SettingsStore,
) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _uiState = MutableStateFlow(BillingUiState())
    private val productDetailsById = ConcurrentHashMap<String, ProductDetails>()

    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build(),
        )
        .enableAutoServiceReconnection()
        .build()

    fun startConnection() {
        if (billingClient.isReady) {
            queryProducts()
            queryPurchases()
            return
        }

        _uiState.update { it.copy(isLoading = true) }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Play Billing disconnected")
                _uiState.update {
                    it.copy(
                        isReady = false,
                        isLoading = false,
                        message = "Play Billing disconnected. We'll retry when the app resumes.",
                    )
                }
            }

            override fun onBillingSetupFinished(result: BillingResult) {
                Log.i(TAG, "Billing setup: ${result.responseCode} ${result.debugMessage}")
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _uiState.update { it.copy(isReady = true, message = null) }
                    queryProducts()
                    queryPurchases()
                } else {
                    _uiState.update {
                        it.copy(
                            isReady = false,
                            isLoading = false,
                            message = "Could not connect to Google Play billing right now.",
                        )
                    }
                }
            }
        })
    }

    fun refreshPurchases() {
        startConnection()
    }

    /** Manual "Restore purchases" tap: silent refresh + a result message once Play answers. */
    fun restorePurchases() {
        pendingManualRestore = true
        startConnection()
    }

    fun launchPurchase(activity: Activity, productId: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { launchPurchase(activity, productId) }
            return
        }

        if (!billingClient.isReady) {
            startConnection()
            _uiState.update { it.copy(message = "Store is still connecting. Try again in a moment.") }
            return
        }

        val productDetails = productDetailsById[productId]
        if (productDetails == null) {
            Log.w(TAG, "No ProductDetails for $productId. Loaded=${productDetailsById.keys}")
            queryProducts()
            _uiState.update {
                it.copy(
                    message = "This Play product isn’t available yet. Open the app from Google Play, then try again.",
                )
            }
            return
        }

        val params = try {
            productDetailsParams(productDetails)
        } catch (error: Exception) {
            Log.e(TAG, "Could not build billing params for $productId", error)
            _uiState.update {
                it.copy(message = "Purchase could not start: ${error.message ?: "invalid offer"}")
            }
            return
        }

        val result = try {
            billingClient.launchBillingFlow(
                activity,
                BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(params))
                    .build(),
            )
        } catch (error: Exception) {
            Log.e(TAG, "launchBillingFlow crashed for $productId", error)
            _uiState.update {
                it.copy(message = "Purchase could not start: ${error.message ?: "Play Billing error"}")
            }
            return
        }

        Log.i(TAG, "launchBillingFlow $productId -> ${result.responseCode} ${result.debugMessage}")
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _uiState.update {
                it.copy(
                    message = "Purchase could not start: ${result.debugMessage.ifBlank { "error ${result.responseCode}" }}",
                )
            }
        }
    }

    fun launchAiWeekly(activity: Activity) = launchPurchase(activity, aiWeeklyProductId)

    fun launchAiMonthly(activity: Activity) = launchPurchase(activity, aiMonthlyProductId)

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?,
    ) {
        Log.i(TAG, "onPurchasesUpdated ${billingResult.responseCode} count=${purchases?.size ?: 0}")
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases.isNullOrEmpty()) {
                    _uiState.update { it.copy(message = "Purchase completed but no items were returned.") }
                } else {
                    handlePurchases(purchases)
                    // Refresh canonical Play lists so reconcile grants immediately.
                    queryPurchases()
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _uiState.update { it.copy(message = "Purchase cancelled.") }
            }
            else -> {
                _uiState.update {
                    it.copy(message = "Purchase failed: ${billingResult.debugMessage.orEmpty()}")
                }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun productDetailsParams(productDetails: ProductDetails): BillingFlowParams.ProductDetailsParams {
        val offerToken = offerTokenFor(productDetails)
        val builder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
        if (offerToken != null) {
            builder.setOfferToken(offerToken)
        } else if (productDetails.productType == BillingClient.ProductType.SUBS) {
            throw IllegalStateException("No Google Play offer is available for ${productDetails.productId}. Activate a base plan in Play Console.")
        }
        return builder.build()
    }

    private fun offerTokenFor(productDetails: ProductDetails): String? {
        val offers = productDetails.subscriptionOfferDetails.orEmpty()
        if (offers.isEmpty()) {
            val oneTime = productDetails.oneTimePurchaseOfferDetailsList
                ?.firstOrNull { !it.offerToken.isNullOrBlank() }
                ?.offerToken
            return oneTime?.takeIf { it.isNotBlank() }
        }
        val trial = offers.firstOrNull { offer ->
            offer.pricingPhases.pricingPhaseList.firstOrNull()?.priceAmountMicros == 0L &&
                offer.offerToken.isNotBlank()
        }
        val base = offers.firstOrNull { it.offerId.isNullOrEmpty() && it.offerToken.isNotBlank() }
        return (trial ?: base ?: offers.firstOrNull { it.offerToken.isNotBlank() })?.offerToken
    }

    private fun queryProducts() {
        if (!billingClient.isReady) return
        queryInAppProducts()
        querySubscriptionProducts()
    }

    private fun queryInAppProducts() {
        val inAppParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(premiumProductId, removeAdsProductId).map { productId ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                },
            )
            .build()

        billingClient.queryProductDetailsAsync(inAppParams) { result, queryProductDetailsResult ->
            Log.i(
                TAG,
                "INAPP query ${result.responseCode} fetched=${queryProductDetailsResult.productDetailsList.size} " +
                    "unfetched=${queryProductDetailsResult.unfetchedProductList.map { it.productId }}",
            )
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                queryProductDetailsResult.productDetailsList.forEach { detail ->
                    productDetailsById[detail.productId] = detail
                }
            }
            publishProducts(
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    null
                } else {
                    "Could not load one-time Play products: ${result.debugMessage}"
                },
            )
        }
    }

    private fun querySubscriptionProducts() {
        val subParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(aiWeeklyProductId, aiMonthlyProductId).map { productId ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                },
            )
            .build()

        billingClient.queryProductDetailsAsync(subParams) { result, queryProductDetailsResult ->
            val fetched = queryProductDetailsResult.productDetailsList
            val unfetched = queryProductDetailsResult.unfetchedProductList.map { it.productId }
            Log.i(
                TAG,
                "SUBS query ${result.responseCode} ${result.debugMessage} fetched=${fetched.map { it.productId }} unfetched=$unfetched",
            )
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                fetched.forEach { detail ->
                    productDetailsById[detail.productId] = detail
                    Log.i(
                        TAG,
                        "${detail.productId} offers=${detail.subscriptionOfferDetails?.map { "${it.basePlanId}/${it.offerId}" }}",
                    )
                }
            }
            val missingSubs = listOf(aiWeeklyProductId, aiMonthlyProductId)
                .filter { !productDetailsById.containsKey(it) }
            val message = when {
                result.responseCode != BillingClient.BillingResponseCode.OK ->
                    "Could not load Pro plans from Google Play."
                missingSubs.isNotEmpty() ->
                    "Pro plans aren’t available on this install yet. Use a Play tester account and a build uploaded to Play Console."
                else -> null
            }
            publishProducts(message)
        }
    }

    private fun publishProducts(message: String?) {
        _uiState.update {
            it.copy(
                isLoading = false,
                weeklyReady = productDetailsById.containsKey(aiWeeklyProductId),
                monthlyReady = productDetailsById.containsKey(aiMonthlyProductId),
                products = productDetailsById.values.map { detail -> toStoreProduct(detail) },
                message = message ?: it.message,
            )
        }
    }

    private fun toStoreProduct(detail: ProductDetails): StoreProduct {
        val isSub = detail.subscriptionOfferDetails?.isNotEmpty() == true
        val price = if (isSub) {
            val offers = detail.subscriptionOfferDetails.orEmpty()
            val paidPhase = offers
                .flatMap { it.pricingPhases.pricingPhaseList }
                .lastOrNull { it.priceAmountMicros > 0 }
                ?: offers.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()
            paidPhase?.formattedPrice
        } else {
            detail.oneTimePurchaseOfferDetails?.formattedPrice
                ?: detail.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
        }
        return StoreProduct(
            id = detail.productId,
            title = detail.title.substringBefore("(").trim(),
            description = detail.description,
            priceText = price ?: "Available in Google Play",
        )
    }

    // Latest Play purchase lists. Local flags are DERIVED from these on every
    // refresh (reconcile), so cancel / refund / expiry automatically returns
    // the user to the free version. Flags are never only set-true anymore.
    @Volatile private var inAppBatchGen = -1
    @Volatile private var subsBatchGen = -1
    @Volatile private var inAppBatch: List<Purchase> = emptyList()
    @Volatile private var subsBatch: List<Purchase> = emptyList()
    private val purchaseQueryGen = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile private var pendingManualRestore = false
    private val verifyingTokens = ConcurrentHashMap.newKeySet<String>()

    private fun queryPurchases() {
        if (!billingClient.isReady) return
        val gen = purchaseQueryGen.incrementAndGet()
        val inAppParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(inAppParams) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                inAppBatch = purchases.toList()
                inAppBatchGen = gen
                handlePurchases(purchases)
                tryReconcile(gen)
            }
        }
        val subParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(subParams) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                subsBatch = purchases.toList()
                subsBatchGen = gen
                handlePurchases(purchases)
                tryReconcile(gen)
            }
        }
    }

    /** Reconcile only when BOTH lists of the same query generation arrived,
     * so a half-answered refresh can never briefly revoke Pro. */
    private fun tryReconcile(gen: Int) {
        if (inAppBatchGen != gen || subsBatchGen != gen) return
        val all = inAppBatch + subsBatch
        val active = all.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        val products = active.flatMap { it.products }.toSet()
        val hasAi = products.contains(aiWeeklyProductId) || products.contains(aiMonthlyProductId)
        val hasPremium = products.contains(premiumProductId)
        val hasRemoveAds = products.contains(removeAdsProductId)
        val manualRestore = pendingManualRestore
        pendingManualRestore = false
        Log.i(
            TAG,
            "Reconcile gen=$gen active=$products hasAi=$hasAi premium=$hasPremium noAds=$hasRemoveAds",
        )
        scope.launch {
            if (hasAi) {
                val aiPurchase = active.firstOrNull { purchase ->
                    purchase.products.contains(aiWeeklyProductId) ||
                        purchase.products.contains(aiMonthlyProductId)
                }
                val sku = when {
                    aiPurchase?.products?.contains(aiMonthlyProductId) == true -> aiMonthlyProductId
                    else -> aiWeeklyProductId
                }
                if (aiPurchase != null) {
                    settingsStore.setAiPurchase(aiPurchase.purchaseToken, sku)
                }
                // Preserve server expiry; verify refreshes it when it answers.
                val expiry = settingsStore.getAiProExpiry()
                settingsStore.setAiWeeklyPro(true, expiry)
                // AI Pro includes every premium feature + no ads while active.
                settingsStore.setPremium(true)
                settingsStore.setAdsRemoved(true)
            } else {
                settingsStore.clearAiPurchase()
                settingsStore.setAiWeeklyPro(false, 0L)
                settingsStore.setPremium(hasPremium)
                settingsStore.setAdsRemoved(hasRemoveAds)
            }
            if (manualRestore) {
                val text = if (hasAi || hasPremium || hasRemoveAds) {
                    appContext.getString(R.string.purchases_restored)
                } else {
                    appContext.getString(R.string.no_purchases_found)
                }
                _uiState.update { it.copy(message = text) }
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        purchases.forEach { purchase ->
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PENDING -> {
                    _uiState.update {
                        it.copy(message = "Purchase pending. We'll unlock it automatically when Google Play confirms payment.")
                    }
                }
                Purchase.PurchaseState.PURCHASED -> {
                    if (purchase.isAcknowledged) {
                        // Already acknowledged (e.g. app restart/resume): the old code
                        // stopped here and NEVER ran server verification, leaving the
                        // server quota on free tier. Always verify AI subs.
                        verifyAiServerIfNeeded(purchase)
                    } else {
                        acknowledge(purchase)
                    }
                }
                else -> Unit
            }
        }
    }

    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.i(TAG, "Acknowledged ${purchase.products}")
                verifyAiServerIfNeeded(purchase)
                // Refresh canonical lists so reconcile grants immediately.
                queryPurchases()
                _uiState.update { it.copy(message = appContext.getString(R.string.purchase_unlocked)) }
            } else {
                Log.w(TAG, "Acknowledge failed: ${result.responseCode} ${result.debugMessage}")
                _uiState.update {
                    it.copy(message = "Purchase received but acknowledgement failed. Please retry opening the app.")
                }
            }
        }
    }

    /** Server-side Play verification for AI subs (pro quota + expiry).
     * Deduped per purchase token so resume-refreshes don't spam the server,
     * but every NEW token is always verified. */
    private fun verifyAiServerIfNeeded(purchase: Purchase) {
        val subId = when {
            purchase.products.contains(aiWeeklyProductId) -> aiWeeklyProductId
            purchase.products.contains(aiMonthlyProductId) -> aiMonthlyProductId
            else -> return
        }
        scope.launch {
            settingsStore.setAiPurchase(purchase.purchaseToken, subId)
        }
        if (!verifyingTokens.add(purchase.purchaseToken)) return
        scope.launch {
            try {
                onAiWeeklyPurchased?.invoke(purchase.purchaseToken, subId)
            } finally {
                // Allow re-verify on next resume so expiry stays fresh.
                scope.launch {
                    kotlinx.coroutines.delay(30_000)
                    verifyingTokens.remove(purchase.purchaseToken)
                }
            }
        }
    }

    companion object {
        private const val TAG = "PetCareBilling"
        const val premiumProductId = "premium_unlock"
        const val removeAdsProductId = "remove_ads"
        const val aiWeeklyProductId = "ai_weekly_pass"
        const val aiMonthlyProductId = "ai_monthly_pass"
    }

    var onAiWeeklyPurchased: ((purchaseToken: String, subscriptionId: String) -> Unit)? = null
}
