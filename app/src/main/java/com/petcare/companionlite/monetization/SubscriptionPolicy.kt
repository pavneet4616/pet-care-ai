package com.petcare.companionlite.monetization

internal data class OwnedSubscription(
    val productIds: List<String>,
    val token: String,
    val purchased: Boolean,
    val pending: Boolean,
)

internal sealed interface SubscriptionCheckout {
    data object NewPurchase : SubscriptionCheckout
    data class Replace(val oldToken: String, val oldProductId: String) : SubscriptionCheckout
    data object AlreadyActive : SubscriptionCheckout
    data object Pending : SubscriptionCheckout
    data object MultipleActive : SubscriptionCheckout
    data object Unsupported : SubscriptionCheckout
}

internal fun subscriptionCheckout(
    requestedId: String,
    activeIds: Set<String>,
    owned: List<OwnedSubscription>,
): SubscriptionCheckout {
    if (requestedId !in activeIds) return SubscriptionCheckout.Unsupported
    val relevant = owned.filter { purchase -> purchase.productIds.any { it in activeIds } }
    if (relevant.any { it.pending }) return SubscriptionCheckout.Pending
    val active = relevant.filter { it.purchased }.distinctBy { it.token }
    if (active.size > 1) return SubscriptionCheckout.MultipleActive
    val previous = active.singleOrNull() ?: return SubscriptionCheckout.NewPurchase
    if (requestedId in previous.productIds) return SubscriptionCheckout.AlreadyActive
    return SubscriptionCheckout.Replace(previous.token, previous.productIds.first { it in activeIds })
}

/** Entitlements from a complete canonical Play snapshot, independent of callbacks. */
internal data class BillingEntitlements(
    val subscription: OwnedSubscription?,
    val subscriptionId: String?,
    val lifetimeToken: String?,
    val adsRemoved: Boolean,
)
internal fun billingEntitlements(owned: List<OwnedSubscription>): BillingEntitlements {
    val purchased = owned.filter { it.purchased && !it.pending }
    val subscription = purchased.firstOrNull { "ai_monthly_pass" in it.productIds }
        ?: purchased.firstOrNull { "ai_weekly_pass" in it.productIds }
    return BillingEntitlements(
        subscription,
        subscription?.let { if ("ai_monthly_pass" in it.productIds) "ai_monthly_pass" else "ai_weekly_pass" },
        purchased.firstOrNull { "premium_unlock" in it.productIds }?.token,
        purchased.any { "remove_ads" in it.productIds },
    )
}
