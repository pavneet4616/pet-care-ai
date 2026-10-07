package com.petcare.companionlite.monetization

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.petcare.companionlite.BuildConfig

class AdsManager {
    private var interstitialAd: InterstitialAd? = null
    private var isLoadingInterstitial = false

    private var rewardedAd: RewardedAd? = null
    private var isLoadingRewarded = false

    // ── Smart pacing: show interstitial every N user actions ──
    private var actionCounter = 0
    private val interstitialFrequency = 3  // Show after every 3rd log action

    fun loadInterstitial(context: Context) {
        if (interstitialAd != null || isLoadingInterstitial) return
        isLoadingInterstitial = true

        InterstitialAd.load(
            context.applicationContext,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoadingInterstitial = false
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoadingInterstitial = false
                    interstitialAd = null
                }
            },
        )
    }

    fun showInterstitialIfReady(activity: Activity) {
        val ad = interstitialAd
        if (ad == null) {
            loadInterstitial(activity.applicationContext)
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                interstitialAd = null
            }

            override fun onAdDismissedFullScreenContent() {
                loadInterstitial(activity.applicationContext)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialAd = null
                loadInterstitial(activity.applicationContext)
            }
        }
        ad.show(activity)
    }

    /**
     * Tracks user actions and shows interstitial at smart intervals.
     * Returns true if an ad was shown.
     */
    fun trackActionAndMaybeShowInterstitial(activity: Activity): Boolean {
        actionCounter++
        if (actionCounter >= interstitialFrequency) {
            actionCounter = 0
            val ad = interstitialAd
            if (ad != null) {
                showInterstitialIfReady(activity)
                return true
            } else {
                loadInterstitial(activity.applicationContext)
            }
        }
        return false
    }

    // ── Rewarded Ads ─────────────────────────────────────────────
    fun loadRewarded(context: Context) {
        if (rewardedAd != null || isLoadingRewarded) return
        isLoadingRewarded = true

        RewardedAd.load(
            context.applicationContext,
            BuildConfig.ADMOB_REWARDED_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    isLoadingRewarded = false
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoadingRewarded = false
                    rewardedAd = null
                }
            },
        )
    }

    fun isRewardedReady(): Boolean = rewardedAd != null

    fun showRewarded(activity: Activity, onRewardEarned: () -> Unit) {
        val ad = rewardedAd
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
        ad.show(activity) { _ -> onRewardEarned() }
    }

    companion object {
        val bannerAdUnitId: String
            get() = BuildConfig.ADMOB_BANNER_ID
    }
}
