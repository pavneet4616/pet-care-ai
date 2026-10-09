package com.petcare.companionlite.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.petcare.companionlite.data.model.AiLanguageMode
import com.petcare.companionlite.data.model.AiResponseStyle
import com.petcare.companionlite.data.model.SettingsState
import com.petcare.companionlite.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "petcare_settings")

class SettingsStore(private val context: Context) {
    private val identity = com.petcare.companionlite.core.InstallationIdentity(context)
    private val identityInitializedKey = booleanPreferencesKey("identity_initialized")
    private val premiumKey = booleanPreferencesKey("premium_enabled")
    private val adsRemovedKey = booleanPreferencesKey("ads_removed")
    private val notificationPromptShownKey = booleanPreferencesKey("notification_prompt_shown")
    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
    private val installationIdKey = stringPreferencesKey("installation_id")
    private val selectedPetKey = androidx.datastore.preferences.core.longPreferencesKey("selected_pet_id")
    private val lifetimeTokenKey = stringPreferencesKey("lifetime_purchase_token")
    private val weightUnitKey = stringPreferencesKey("weight_unit")
    private val analyticsConsentKey = booleanPreferencesKey("analytics_consent")
    private val crashConsentKey = booleanPreferencesKey("crash_consent")
    private val deletionPendingKey = booleanPreferencesKey("server_deletion_pending")
    private val reviewAttemptKey = androidx.datastore.preferences.core.longPreferencesKey("review_last_attempt")
    private val aiWeeklyProKey = booleanPreferencesKey("ai_weekly_pro")
    private val aiProExpiryKey = androidx.datastore.preferences.core.longPreferencesKey("ai_pro_expiry")
    private val aiPurchaseTokenKey = stringPreferencesKey("ai_purchase_token")
    private val aiSubscriptionIdKey = stringPreferencesKey("ai_subscription_id")
    private val appOpenHoursKey = stringPreferencesKey("app_open_hours")
    private val hasUnreadWeeklyReportKey = booleanPreferencesKey("has_unread_weekly_report")
    private val lastReportTimestampKey = androidx.datastore.preferences.core.longPreferencesKey("last_report_timestamp")
    private val lastAppOpenTimestampKey = androidx.datastore.preferences.core.longPreferencesKey("last_app_open_timestamp")
    private val lastBannerShownTimestampKey = androidx.datastore.preferences.core.longPreferencesKey("last_banner_shown_timestamp")

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val appLanguageKey = stringPreferencesKey("app_language_tag")
    private val aiLanguageModeKey = stringPreferencesKey("ai_language_mode")
    private val aiUseCareHistoryKey = booleanPreferencesKey("ai_use_care_history")
    private val aiUseProfileKey = booleanPreferencesKey("ai_use_profile")
    private val aiUseActivityKey = booleanPreferencesKey("ai_use_activity")
    private val aiUseHealthKey = booleanPreferencesKey("ai_use_health")
    private val aiUseRemindersKey = booleanPreferencesKey("ai_use_reminders")
    private val aiResponseStyleKey = stringPreferencesKey("ai_response_style")
    private val feedingNotificationsKey = booleanPreferencesKey("notif_feeding")
    private val waterNotificationsKey = booleanPreferencesKey("notif_water")
    private val medicineNotificationsKey = booleanPreferencesKey("notif_medicine")
    private val walkNotificationsKey = booleanPreferencesKey("notif_walk")
    private val healthFollowUpsKey = booleanPreferencesKey("notif_health_followups")
    private val weeklySummaryKey = booleanPreferencesKey("notif_weekly_summary")
    private val streakRemindersKey = booleanPreferencesKey("notif_streak")
    private val dailyAiTipsKey = booleanPreferencesKey("notif_daily_ai_tips")

    val settings: Flow<SettingsState> = context.dataStore.data.map { prefs ->
        SettingsState(
            isPremium = prefs[premiumKey] ?: false,
            selectedPetId = prefs[selectedPetKey],
            analyticsConsent = prefs[analyticsConsentKey] ?: false,
            crashConsent = prefs[crashConsentKey] ?: false,
            serverDeletionPending = prefs[deletionPendingKey] ?: false,
            weightUnit = prefs[weightUnitKey] ?: if (android.content.res.Resources.getSystem().configuration.locales[0].country == "US") "lb" else "kg",
            currencyCode = runCatching { java.util.Currency.getInstance(android.content.res.Resources.getSystem().configuration.locales[0]).currencyCode }.getOrDefault("XXX"),
            adsRemoved = prefs[adsRemovedKey] ?: false,
            notificationPromptShown = prefs[notificationPromptShownKey] ?: false,
            onboardingCompleted = prefs[onboardingCompletedKey] ?: false,
            isAiWeeklyPro = prefs[aiWeeklyProKey] ?: false,
            aiProExpiryMillis = prefs[aiProExpiryKey] ?: 0L,
            themeMode = prefs[themeModeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
            appLanguageTag = prefs[appLanguageKey],
            aiLanguageMode = prefs[aiLanguageModeKey]?.let { runCatching { AiLanguageMode.valueOf(it) }.getOrNull() }
                ?: AiLanguageMode.AUTO,
            aiUseCareHistory = prefs[aiUseCareHistoryKey] ?: true,
            aiUseProfile = prefs[aiUseProfileKey] ?: true,
            aiUseActivity = prefs[aiUseActivityKey] ?: true,
            aiUseHealth = prefs[aiUseHealthKey] ?: true,
            aiUseReminders = prefs[aiUseRemindersKey] ?: true,
            aiResponseStyle = prefs[aiResponseStyleKey]?.let { runCatching { AiResponseStyle.valueOf(it) }.getOrNull() }
                ?: AiResponseStyle.BALANCED,
            feedingNotifications = prefs[feedingNotificationsKey] ?: true,
            waterNotifications = prefs[waterNotificationsKey] ?: true,
            medicineNotifications = prefs[medicineNotificationsKey] ?: true,
            walkNotifications = prefs[walkNotificationsKey] ?: true,
            healthFollowUps = prefs[healthFollowUpsKey] ?: true,
            weeklySummary = prefs[weeklySummaryKey] ?: true,
            streakReminders = prefs[streakRemindersKey] ?: true,
            dailyAiTips = prefs[dailyAiTipsKey] ?: false,
        )
    }

    val hasUnreadWeeklyReport: Flow<Boolean> = context.dataStore.data.map { it[hasUnreadWeeklyReportKey] ?: false }
    val lastReportTimestamp: Flow<Long> = context.dataStore.data.map { it[lastReportTimestampKey] ?: 0L }
    val lastBannerShownTimestamp: Flow<Long> = context.dataStore.data.map { it[lastBannerShownTimestampKey] ?: 0L }
    val lastAppOpenTimestamp: Flow<Long> = context.dataStore.data.map { it[lastAppOpenTimestampKey] ?: 0L }

    suspend fun setPremium(enabled: Boolean) {
        context.dataStore.edit { it[premiumKey] = enabled }
    }

    suspend fun setAdsRemoved(enabled: Boolean) {
        context.dataStore.edit { it[adsRemovedKey] = enabled }
    }

    suspend fun setNotificationPromptShown(shown: Boolean) {
        context.dataStore.edit { it[notificationPromptShownKey] = shown }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[onboardingCompletedKey] = completed }
    }

    suspend fun getOrCreateInstallationId(): String {
        val current=context.dataStore.data.first()
        val actual=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { identity.get(current[installationIdKey], current[identityInitializedKey] ?: false).first }
        val updated = context.dataStore.edit {
            it[installationIdKey] = actual
            it[identityInitializedKey] = true
        }
        return requireNotNull(updated[installationIdKey])
    }
    suspend fun installationSecret(): String {
        val id=getOrCreateInstallationId()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { identity.get(id,true).second }
    }

    suspend fun selectPet(id: Long) { context.dataStore.edit { it[selectedPetKey] = id } }
    suspend fun setAnalyticsConsent(enabled: Boolean) { context.dataStore.edit { it[analyticsConsentKey] = enabled } }
    suspend fun setCrashConsent(enabled: Boolean) { context.dataStore.edit { it[crashConsentKey] = enabled } }
    suspend fun setDeletionPending(pending:Boolean) { context.dataStore.edit { it[deletionPendingKey]=pending } }
    suspend fun claimReviewAttempt(): Boolean {
        var claimed=false
        context.dataStore.edit {
            val last=it[reviewAttemptKey]
            val now=System.currentTimeMillis()
            if (last == null || now-last >= 90L*24*60*60*1000) { it[reviewAttemptKey]=now;claimed=true }
        }
        return claimed
    }
    suspend fun setWeightUnit(unit: String) { require(unit in setOf("kg", "lb")); context.dataStore.edit { it[weightUnitKey] = unit } }
    suspend fun setLifetimeToken(token: String?) { context.dataStore.edit { if (token == null) it.remove(lifetimeTokenKey) else it[lifetimeTokenKey] = token } }
    suspend fun getLifetimeToken(): String? = context.dataStore.data.first()[lifetimeTokenKey]
    suspend fun clearPersonalData() {
        context.dataStore.edit {
            val installation = it[installationIdKey]
            it.clear()
            // Retain only the anonymous anti-abuse identity. Deleting care data must not reset quotas.
            if (installation != null) it[installationIdKey] = installation
            it[identityInitializedKey] = true
            it[deletionPendingKey] = true
        }
    }

    /** Publish a whole Play snapshot atomically; ignore a superseded query. */
    internal suspend fun applyBillingSnapshot(
        owned: com.petcare.companionlite.monetization.BillingEntitlements,
        stillCurrent: () -> Boolean,
    ) {
        context.dataStore.edit { prefs ->
            if (!stillCurrent()) return@edit
            prefs[premiumKey] = owned.lifetimeToken != null
            prefs[adsRemovedKey] = owned.adsRemoved
            if (owned.lifetimeToken == null) prefs.remove(lifetimeTokenKey)
            else prefs[lifetimeTokenKey] = owned.lifetimeToken
            val subscription = owned.subscription
            prefs[aiWeeklyProKey] = subscription != null
            if (subscription == null) {
                prefs.remove(aiPurchaseTokenKey)
                prefs.remove(aiSubscriptionIdKey)
                prefs[aiProExpiryKey] = 0L
            } else {
                if (prefs[aiPurchaseTokenKey] != subscription.token) prefs[aiProExpiryKey] = 0L
                prefs[aiPurchaseTokenKey] = subscription.token
                prefs[aiSubscriptionIdKey] = requireNotNull(owned.subscriptionId)
            }
        }
    }

    suspend fun applyVerifiedExpiry(token: String, expiryMillis: Long): Boolean {
        var applied = false
        context.dataStore.edit {
            if (it[aiPurchaseTokenKey] == token) {
                it[aiWeeklyProKey] = true
                it[aiProExpiryKey] = expiryMillis
                applied = true
            }
        }
        return applied
    }

    suspend fun setAiWeeklyPro(enabled: Boolean, expiryMillis: Long = 0L) {
        context.dataStore.edit {
            it[aiWeeklyProKey] = enabled
            it[aiProExpiryKey] = expiryMillis
        }
    }

    suspend fun getAiProExpiry(): Long = context.dataStore.data.first()[aiProExpiryKey] ?: 0L

    suspend fun setAiPurchase(token: String, subscriptionId: String) {
        context.dataStore.edit {
            it[aiPurchaseTokenKey] = token
            it[aiSubscriptionIdKey] = subscriptionId
        }
    }

    suspend fun clearAiPurchase() {
        context.dataStore.edit {
            it.remove(aiPurchaseTokenKey)
            it.remove(aiSubscriptionIdKey)
        }
    }

    suspend fun getAiPurchaseToken(): String? =
        context.dataStore.data.first()[aiPurchaseTokenKey]?.takeIf { it.isNotBlank() }

    suspend fun getAiSubscriptionId(): String? =
        context.dataStore.data.first()[aiSubscriptionIdKey]?.takeIf { it.isNotBlank() }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setAppLanguageTag(tag: String?) {
        context.dataStore.edit {
            if (tag.isNullOrBlank()) it.remove(appLanguageKey) else it[appLanguageKey] = tag
        }
    }

    suspend fun setAiLanguageMode(mode: AiLanguageMode) {
        context.dataStore.edit { it[aiLanguageModeKey] = mode.name }
    }

    suspend fun setAiUseCareHistory(enabled: Boolean) {
        context.dataStore.edit { it[aiUseCareHistoryKey] = enabled }
    }

    suspend fun setAiUseProfile(enabled: Boolean) {
        context.dataStore.edit { it[aiUseProfileKey] = enabled }
    }

    suspend fun setAiUseActivity(enabled: Boolean) {
        context.dataStore.edit { it[aiUseActivityKey] = enabled }
    }

    suspend fun setAiUseHealth(enabled: Boolean) {
        context.dataStore.edit { it[aiUseHealthKey] = enabled }
    }

    suspend fun setAiUseReminders(enabled: Boolean) {
        context.dataStore.edit { it[aiUseRemindersKey] = enabled }
    }

    suspend fun setAiResponseStyle(style: AiResponseStyle) {
        context.dataStore.edit { it[aiResponseStyleKey] = style.name }
    }

    suspend fun setFeedingNotifications(enabled: Boolean) {
        context.dataStore.edit { it[feedingNotificationsKey] = enabled }
    }

    suspend fun setWaterNotifications(enabled: Boolean) {
        context.dataStore.edit { it[waterNotificationsKey] = enabled }
    }

    suspend fun setMedicineNotifications(enabled: Boolean) {
        context.dataStore.edit { it[medicineNotificationsKey] = enabled }
    }

    suspend fun setWalkNotifications(enabled: Boolean) {
        context.dataStore.edit { it[walkNotificationsKey] = enabled }
    }

    suspend fun setHealthFollowUps(enabled: Boolean) {
        context.dataStore.edit { it[healthFollowUpsKey] = enabled }
    }

    suspend fun setWeeklySummary(enabled: Boolean) {
        context.dataStore.edit { it[weeklySummaryKey] = enabled }
    }

    suspend fun setStreakReminders(enabled: Boolean) {
        context.dataStore.edit { it[streakRemindersKey] = enabled }
    }

    suspend fun setDailyAiTips(enabled: Boolean) {
        context.dataStore.edit { it[dailyAiTipsKey] = enabled }
    }

    suspend fun setUnreadWeeklyReport(unread: Boolean) {
        context.dataStore.edit { it[hasUnreadWeeklyReportKey] = unread }
    }

    suspend fun setLastReportTimestamp(timestamp: Long) {
        context.dataStore.edit { it[lastReportTimestampKey] = timestamp }
    }

    suspend fun setLastBannerShownTimestamp(timestamp: Long) {
        context.dataStore.edit { it[lastBannerShownTimestampKey] = timestamp }
    }

    suspend fun recordAppOpen(hour: Int, timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[lastAppOpenTimestampKey] = timestamp
            val existing = prefs[appOpenHoursKey]?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?: emptyList()
            val updated = (existing + hour).takeLast(20)
            prefs[appOpenHoursKey] = updated.joinToString(",")
        }
    }

    suspend fun getPreferredReportHour(): Int {
        val prefs = context.dataStore.data.first()
        val hours = prefs[appOpenHoursKey]?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?: return 20
        val eveningOpens = hours.filter { it in 18..23 }
        if (eveningOpens.isEmpty()) return 20
        return eveningOpens.groupBy { it }.maxByOrNull { it.value.size }?.key ?: 20
    }
}
