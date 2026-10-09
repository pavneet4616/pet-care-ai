package com.petcare.companionlite.monetization

import org.junit.Assert.*
import org.junit.Test

class BillingReconciliationTest {
    private fun owned(id: String, token: String = id, pending: Boolean = false) =
        OwnedSubscription(listOf(id), token, purchased = !pending, pending = pending)

    @Test fun subscriptionExpiryPreservesHistoricalLifetimeAndAdRemoval() {
        val legacy = listOf(owned("premium_unlock"), owned("remove_ads"))
        assertNotNull(billingEntitlements(legacy + owned("ai_weekly_pass")).subscription)
        val expired = billingEntitlements(legacy)
        assertNull(expired.subscription)
        assertEquals("premium_unlock", expired.lifetimeToken)
        assertTrue(expired.adsRemoved)
    }

    @Test fun refundClearsAllGrantsFromAnEmptySnapshot() {
        assertEquals(BillingEntitlements(null, null, null, false), billingEntitlements(emptyList()))
    }

    @Test fun pendingPaymentsAndUnknownProductsDoNotGrantFeatures() {
        assertEquals(BillingEntitlements(null, null, null, false),
            billingEntitlements(listOf(owned("ai_weekly_pass", pending = true), owned("annual"))))
    }

    @Test fun monthlyIsSelectedConsistentlyWhenOldClientsHaveTwoPlans() {
        val weekly = owned("ai_weekly_pass", "weekly-token")
        val monthly = owned("ai_monthly_pass", "monthly-token")
        for (list in listOf(listOf(weekly, monthly), listOf(monthly, weekly))) {
            val result = billingEntitlements(list)
            assertEquals("monthly-token", result.subscription?.token)
            assertEquals("ai_monthly_pass", result.subscriptionId)
        }
    }
}
