package com.petcare.companionlite.monetization

import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionPolicyTest {
    private val weekly = "ai_weekly_pass"
    private val monthly = "ai_monthly_pass"
    private val active = setOf(weekly, monthly)
    private fun purchased(id: String, token: String = "old-token") =
        OwnedSubscription(listOf(id), token, purchased = true, pending = false)

    @Test fun firstPurchaseHasNoReplacement() {
        assertEquals(SubscriptionCheckout.NewPurchase, subscriptionCheckout(monthly, active, emptyList()))
    }

    @Test fun weeklyAndMonthlyChangesAlwaysReplaceTheOldToken() {
        assertEquals(SubscriptionCheckout.Replace("old-token", weekly), subscriptionCheckout(monthly, active, listOf(purchased(weekly))))
        assertEquals(SubscriptionCheckout.Replace("old-token", monthly), subscriptionCheckout(weekly, active, listOf(purchased(monthly))))
    }

    @Test fun subscribingToOwnedPlanIsBlocked() {
        assertEquals(SubscriptionCheckout.AlreadyActive, subscriptionCheckout(monthly, active, listOf(purchased(monthly))))
    }

    @Test fun pendingPaymentPreventsASecondPurchase() {
        val pending = OwnedSubscription(listOf(weekly), "pending", purchased = false, pending = true)
        assertEquals(SubscriptionCheckout.Pending, subscriptionCheckout(monthly, active, listOf(pending)))
    }

    @Test fun existingDoubleSubscriptionsRequirePlayManagement() {
        assertEquals(SubscriptionCheckout.MultipleActive, subscriptionCheckout(monthly, active,
            listOf(purchased(weekly, "a"), purchased(monthly, "b"))))
    }

    @Test fun duplicatePlayRowsDoNotCountAsTwoPurchases() {
        val purchase = purchased(weekly)
        assertEquals(SubscriptionCheckout.Replace("old-token", weekly), subscriptionCheckout(monthly, active, listOf(purchase, purchase)))
    }

    @Test fun lifetimeAnnualAndRemoveAdsCannotBeSold() {
        listOf("premium_unlock", "remove_ads", "ai_annual_pass").forEach {
            assertEquals(SubscriptionCheckout.Unsupported, subscriptionCheckout(it, active, emptyList()))
        }
    }

    @Test fun historicalOneTimePurchasesDoNotBlockSubscriptions() {
        assertEquals(SubscriptionCheckout.NewPurchase, subscriptionCheckout(monthly, active, listOf(purchased("premium_unlock"))))
    }
}
