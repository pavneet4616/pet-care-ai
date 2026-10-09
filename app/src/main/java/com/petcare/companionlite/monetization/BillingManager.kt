package com.petcare.companionlite.monetization

import com.petcare.companionlite.core.inAppLanguage

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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.security.MessageDigest
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
    private var purchaseFlowInProgress = false

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
                        message = appContext.inAppLanguage().getString(R.string.ui_c29ae41e17),
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
                            message = appContext.inAppLanguage().getString(R.string.ui_63a02de04c),
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

        if (productId !in activeSubscriptionIds) {
            _uiState.update { it.copy(message = appContext.inAppLanguage().getString(R.string.ui_4e728e6c43)) }
            return
        }
        if (purchaseFlowInProgress) return

        if (!billingClient.isReady) {
            startConnection()
            _uiState.update { it.copy(message = appContext.inAppLanguage().getString(R.string.ui_0c08d00176)) }
            return
        }

        val productDetails = productDetailsById[productId]
        if (productDetails == null) {
            Log.w(TAG, "No ProductDetails for $productId. Loaded=${productDetailsById.keys}")
            queryProducts()
            _uiState.update {
                it.copy(
                    message = appContext.inAppLanguage().getString(R.string.ui_323d4f329e),
                )
            }
            return
        }

        val params = try {
            productDetailsParams(productDetails)
        } catch (error: Exception) {
            Log.e(TAG, "Could not build billing params for $productId", error)
            _uiState.update {
                it.copy(message = appContext.inAppLanguage().getString(R.string.billing_offer_unavailable))
            }
            return
        }

        purchaseFlowInProgress = true
        scope.launch(Dispatchers.Main) {
            var launched = false
            try {
                // Query Play immediately before checkout; cached flags can be stale.
                val (queryResult, purchases) = suspendCancellableCoroutine<Pair<BillingResult, List<Purchase>>> { continuation ->
                    billingClient.queryPurchasesAsync(
                        QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
                    ) { result, owned ->
                        if (continuation.isActive) continuation.resume(result to owned.toList())
                    }
                }
                if (queryResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    _uiState.update { it.copy(message = appContext.inAppLanguage().getString(R.string.ui_add2a4feb7)) }
                    return@launch
                }
                val checkout = subscriptionCheckout(productId, activeSubscriptionIds, purchases.map { purchase ->
                    OwnedSubscription(
                        purchase.products, purchase.purchaseToken,
                        purchased = purchase.purchaseState == Purchase.PurchaseState.PURCHASED,
                        pending = purchase.purchaseState == Purchase.PurchaseState.PENDING,
                    )
                })
                val blockedMessage = when (checkout) {
                    SubscriptionCheckout.Pending -> appContext.inAppLanguage().getString(R.string.billing_pending)
                    SubscriptionCheckout.MultipleActive -> appContext.inAppLanguage().getString(R.string.billing_multiple)
                    SubscriptionCheckout.AlreadyActive -> appContext.inAppLanguage().getString(R.string.billing_already_active)
                    SubscriptionCheckout.Unsupported -> appContext.inAppLanguage().getString(R.string.billing_supported_plans)
                    else -> null
                }
                if (blockedMessage != null) {
                    _uiState.update { it.copy(message = blockedMessage) }
                    return@launch
                }
                val installationId = settingsStore.getOrCreateInstallationId()
                val accountId = MessageDigest.getInstance("SHA-256")
                    .digest(installationId.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
                val flow = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(if (checkout is SubscriptionCheckout.Replace) productDetailsParams(productDetails,checkout.oldProductId) else params))
                    .setObfuscatedAccountId(accountId)
                if (checkout is SubscriptionCheckout.Replace) {
                    flow.setSubscriptionUpdateParams(
                        BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                            .setOldPurchaseToken(checkout.oldToken)
                            .build(),
                    )
                }
                if (activity.isFinishing || activity.isDestroyed) return@launch
                val result = billingClient.launchBillingFlow(activity, flow.build())
                launched = result.responseCode == BillingClient.BillingResponseCode.OK
                if (!launched) {
                    _uiState.update { it.copy(message = appContext.inAppLanguage().getString(R.string.ui_3bae8a8178, result.debugMessage)) }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Purchase launch failed", error)
                _uiState.update { it.copy(message = appContext.inAppLanguage().getString(R.string.ui_473a9ef576)) }
            } finally {
                if (!launched) purchaseFlowInProgress = false
            }
        }
    }

    fun launchAiWeekly(activity: Activity) = launchPurchase(activity, aiWeeklyProductId)

    fun launchAiMonthly(activity: Activity) = launchPurchase(activity, aiMonthlyProductId)

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?,
    ) {
        purchaseFlowInProgress = false
        Log.i(TAG, "onPurchasesUpdated ${billingResult.responseCode} count=${purchases?.size ?: 0}")
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases.isNullOrEmpty()) {
                    _uiState.update { it.copy(message = appContext.inAppLanguage().getString(R.string.ui_aa559fff3b)) }
                } else {
                    handlePurchases(purchases)
                    // Refresh canonical Play lists so reconcile grants immediately.
                    queryPurchases()
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _uiState.update { it.copy(message = appContext.inAppLanguage().getString(R.string.ui_1eb379e30a)) }
            }
            else -> {
                _uiState.update {
                    it.copy(message = appContext.inAppLanguage().getString(R.string.ui_884960b553, billingResult.debugMessage.orEmpty()))
                }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun productDetailsParams(productDetails: ProductDetails, oldProductId: String? = null): BillingFlowParams.ProductDetailsParams {
        val offerToken = offerTokenFor(productDetails)
        val builder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
        if (oldProductId != null) builder.setSubscriptionProductReplacementParams(
            BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.newBuilder()
                .setOldProductId(oldProductId)
                .setReplacementMode(BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams.ReplacementMode.WITH_TIME_PRORATION)
                .build(),
        )
        if (offerToken != null) {
            builder.setOfferToken(offerToken)
        } else if (productDetails.productType == BillingClient.ProductType.SUBS) {
            throw IllegalStateException("No Google Play offer is available for ${productDetails.productId}. Activate a base plan in Play Console.")
        }
        return builder.build()
    }

    private fun offerTokenFor(productDetails: ProductDetails): String? {
        val expectedPeriod = if (productDetails.productId == aiWeeklyProductId) "P1W" else "P1M"
        val offers = productDetails.subscriptionOfferDetails.orEmpty().filter {
            it.pricingPhases.pricingPhaseList.lastOrNull()?.billingPeriod == expectedPeriod
        }
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
        querySubscriptionProducts()
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
                productDetailsById.clear()
                fetched.forEach { detail ->
                    if (detail.productId in activeSubscriptionIds && offerTokenFor(detail) != null) {
                        productDetailsById[detail.productId] = detail
                    }
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
                    appContext.inAppLanguage().getString(R.string.billing_load_failed)
                missingSubs.isNotEmpty() ->
                    appContext.inAppLanguage().getString(R.string.billing_plans_unavailable)
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
            val offer = detail.subscriptionOfferDetails.orEmpty().firstOrNull { it.offerToken == offerTokenFor(detail) }
            val paidPhase = offer?.pricingPhases?.pricingPhaseList?.lastOrNull()
            paidPhase?.let { recurring ->
                val strings = appContext.inAppLanguage()
                var terms = strings.getString(R.string.plan_recurring_price, recurring.formattedPrice, billingPeriodLabel(recurring.billingPeriod))
                offer?.pricingPhases?.pricingPhaseList.orEmpty().dropLast(1).asReversed().forEach { phase ->
                    val period = billingPeriodLabel(phase.billingPeriod,phase.billingCycleCount.coerceAtLeast(1))
                    terms = if (phase.priceAmountMicros == 0L) strings.getString(R.string.plan_free_period,period,terms)
                        else strings.getString(R.string.plan_intro_price,phase.formattedPrice,period,terms)
                }
                terms
            }
        } else {
            detail.oneTimePurchaseOfferDetails?.formattedPrice
                ?: detail.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
        }
        return StoreProduct(
            id = detail.productId,
            title = detail.title.substringBefore("(").trim(),
            description = detail.description,
            priceText = price ?: appContext.inAppLanguage().getString(R.string.plan_price_in_play),
        )
    }

    private fun billingPeriodLabel(period: String, cycles: Int = 1): String {
        val match = Regex("P(\\d+)([DWMY])").matchEntire(period) ?: return period
        val count = match.groupValues[1].toInt() * cycles
        val resource = when(match.groupValues[2]) {
            "D" -> R.plurals.billing_days
            "W" -> R.plurals.billing_weeks
            "M" -> R.plurals.billing_months
            else -> R.plurals.billing_years
        }
        return appContext.inAppLanguage().resources.getQuantityString(resource,count,count)
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
    var onOwnedPurchasesReady: (() -> Unit)? = null
    private val verifyingTokens = ConcurrentHashMap.newKeySet<String>()

    private fun queryPurchases() {
        if (!billingClient.isReady) return
        val gen = purchaseQueryGen.incrementAndGet()
        val inAppParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(inAppParams) { result, purchases ->
            if (purchaseQueryGen.get() != gen) return@queryPurchasesAsync
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
            if (purchaseQueryGen.get() != gen) return@queryPurchasesAsync
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
        val owned = billingEntitlements((inAppBatch + subsBatch).map {
            OwnedSubscription(it.products, it.purchaseToken,
                it.purchaseState == Purchase.PurchaseState.PURCHASED,
                it.purchaseState == Purchase.PurchaseState.PENDING)
        })
        val hasAi = owned.subscription != null
        val hasPremium = owned.lifetimeToken != null
        val hasRemoveAds = owned.adsRemoved
        val manualRestore = pendingManualRestore
        pendingManualRestore = false
        scope.launch {
            settingsStore.applyBillingSnapshot(owned) { purchaseQueryGen.get() == gen }
            if (purchaseQueryGen.get() != gen) return@launch
            // Verify only after the canonical tokens are stored, including lifetime restores.
            owned.subscription?.let { subscription ->
                (inAppBatch + subsBatch).firstOrNull { it.purchaseToken == subscription.token }
                    ?.let { verifyAiServerIfNeeded(it) }
            }
            onOwnedPurchasesReady?.invoke()
            if (manualRestore) {
                val text = if (hasAi || hasPremium || hasRemoveAds) {
                    appContext.inAppLanguage().getString(R.string.purchases_restored)
                } else {
                    appContext.inAppLanguage().getString(R.string.no_purchases_found)
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
                        it.copy(message = appContext.inAppLanguage().getString(R.string.ui_672d825998))
                    }
                }
                Purchase.PurchaseState.PURCHASED -> {
                    if (purchase.isAcknowledged) {
                        // Already acknowledged (e.g. app restart/resume): the old code
                        // stopped here and NEVER ran server verification, leaving the
                        // server quota on free tier. Always verify AI subs.
                        // Canonical reconciliation schedules verification after token storage.
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
                // Refresh canonical lists so reconcile grants immediately.
                queryPurchases()
                _uiState.update { it.copy(message = appContext.inAppLanguage().getString(R.string.purchase_unlocked)) }
            } else {
                Log.w(TAG, "Acknowledge failed: ${result.responseCode} ${result.debugMessage}")
                _uiState.update {
                    it.copy(message = appContext.inAppLanguage().getString(R.string.ui_6e4975afd1))
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
        val activeSubscriptionIds = setOf(aiWeeklyProductId, aiMonthlyProductId)
    }

    var onAiWeeklyPurchased: ((purchaseToken: String, subscriptionId: String) -> Unit)? = null
}
