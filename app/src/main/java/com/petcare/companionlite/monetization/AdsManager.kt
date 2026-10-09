package com.petcare.companionlite.monetization

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.petcare.companionlite.BuildConfig
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class AdsManager(context: Context) {
    private val consent = UserMessagingPlatform.getConsentInformation(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val initializationStarted = AtomicBoolean(false)
    private var initialized = false
    private val _adsAllowed = MutableStateFlow(false)
    val adsAllowed = _adsAllowed.asStateFlow()
    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired = _privacyOptionsRequired.asStateFlow()
    private var rewardedAd: RewardedAd? = null
    private var isLoadingRewarded = false
    private var consentGeneration = 0

    fun gatherConsent(activity: Activity) {
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    updateConsent(activity)
                }
                updatePrivacyOptions()
            },
            { updateConsent(activity) },
        )
        // UMP may permit requests using consent from a previous session.
        updateConsent(activity)
    }

    fun showPrivacyOptions(activity: Activity) {
        if (!_privacyOptionsRequired.value) return
        _adsAllowed.value = false
        consentGeneration++
        rewardedAd = null
        isLoadingRewarded = false
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { updateConsent(activity) }
    }

    private fun updatePrivacyOptions() {
        _privacyOptionsRequired.value = consent.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun updateConsent(context: Context) {
        updatePrivacyOptions()
        if (!consent.canRequestAds()) {
            _adsAllowed.value = false
            consentGeneration++
            rewardedAd = null
            isLoadingRewarded = false
            return
        }
        if (initialized) {
            _adsAllowed.value = true
            return
        }
        if (initializationStarted.compareAndSet(false, true)) {
            scope.launch(Dispatchers.IO) {
                try {
                    MobileAds.initialize(context.applicationContext) {
                        scope.launch {
                            initialized = true
                            _adsAllowed.value = consent.canRequestAds()
                        }
                    }
                } catch (error: Exception) {
                    initializationStarted.set(false)
                    android.util.Log.w("PetCareAds", "Ad initialization failed", error)
                }
            }
        }
    }

    fun loadRewarded(context: Context) {
        if (!_adsAllowed.value || !consent.canRequestAds() || rewardedAd != null || isLoadingRewarded) return
        val generation = consentGeneration
        isLoadingRewarded = true

        RewardedAd.load(
            context.applicationContext,
            BuildConfig.ADMOB_REWARDED_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    if (generation != consentGeneration || !_adsAllowed.value) return
                    isLoadingRewarded = false
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    if (generation != consentGeneration) return
                    isLoadingRewarded = false
                    rewardedAd = null
                }
            },
        )
    }

    fun isRewardedReady(): Boolean = _adsAllowed.value && consent.canRequestAds() && rewardedAd != null

    fun showRewarded(activity: Activity, installationId:String, onRewardEarned: () -> Unit) {
        val ad = rewardedAd
        if (!_adsAllowed.value || !consent.canRequestAds()) return
        if (ad == null) {
            loadRewarded(activity.applicationContext)
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                rewardedAd = null
            }

            override fun onAdDismissedFullScreenContent() {
                loadRewarded(activity.applicationContext)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                loadRewarded(activity.applicationContext)
            }
        }
        ad.setServerSideVerificationOptions(com.google.android.gms.ads.rewarded.ServerSideVerificationOptions.Builder().setUserId(installationId).build())
        rewardedAd=null
        ad.show(activity) { _ -> onRewardEarned() }
    }

    companion object {
        val bannerAdUnitId: String
            get() = BuildConfig.ADMOB_BANNER_ID
    }
}
