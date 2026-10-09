package com.petcare.companionlite.core

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.petcare.companionlite.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class Diagnostics(context:Context, private val settings:SettingsStore) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val analytics=FirebaseAnalytics.getInstance(context)
    private val crash=FirebaseCrashlytics.getInstance()
    init {
        scope.launch {
            settings.settings.map { it.analyticsConsent to it.crashConsent }.distinctUntilChanged().collect { (analyticsEnabled,crashEnabled) ->
                analytics.setConsent(mapOf(
                    FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to if (analyticsEnabled) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED,
                    FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                    FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                    FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED,
                ))
                analytics.setAnalyticsCollectionEnabled(analyticsEnabled)
                if (!analyticsEnabled) analytics.resetAnalyticsData()
                crash.setCrashlyticsCollectionEnabled(crashEnabled)
                if (!crashEnabled) crash.deleteUnsentReports()
            }
        }
    }
    fun event(name:String, value:String?=null) {
        require(name in setOf("onboarding_complete","reminder_created","care_logged","ai_message_sent","ai_error","ai_helpful","paywall_view","subscription_purchase","notif_permission_result"))
        scope.launch {
            if (!settings.settings.first().analyticsConsent) return@launch
            // Only enums, not user text, pet names, health history, receipt tokens or installation IDs.
            val allowed=value?.takeIf { it in setOf("FEED","WALK","MEDICINE","GROOMING","ai_weekly_pass","ai_monthly_pass","granted","denied","network","quota","other") }
            analytics.logEvent(name,Bundle().apply { allowed?.let { putString("result",it) } })
        }
    }
}
