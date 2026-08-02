package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Loads and shows the "watch an ad for stars" rewarded ad, behind a small
 * callback API so the rest of the app never touches AdMob types directly.
 *
 * [adUnitId] defaults to the real AdMob rewarded ad unit for this app. The
 * manifest's APPLICATION_ID meta-data must likewise be the real AdMob app id
 * (it is) - Google flags production traffic served against test ad units as
 * a policy violation, and no test ad ever generates real revenue.
 */
class RewardedAdManager(
    private val context: Context,
    private val adUnitId: String = PRODUCTION_AD_UNIT_ID
) {
    companion object {
        /** This app's real AdMob rewarded ad unit id. */
        const val PRODUCTION_AD_UNIT_ID = "ca-app-pub-8250547585089024/7027261120"

        /** Google's public sample rewarded-ad unit id - only for local experimentation. */
        const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
        private const val TAG = "RewardedAdManager"
    }

    private var rewardedAd: RewardedAd? = null
    private var loading = false

    /** Starts the SDK and fetches the first ad so one is ready by the time the player asks. */
    fun initialize() {
        MobileAds.initialize(context)
        load()
    }

    /** Fetches the next ad in the background, if one isn't already loaded or loading. */
    fun load() {
        if (loading || rewardedAd != null) return
        loading = true
        RewardedAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loading = false
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    rewardedAd = null
                    Log.w(TAG, "Rewarded ad failed to load: ${error.message}")
                }
            }
        )
    }

    /**
     * Shows the loaded ad, if one is ready. [onReward] fires only once the player
     * watches to completion - dismissing early grants nothing. Always tries to
     * preload the next ad afterwards (win, lose, or not-ready), so the button
     * recovers on its own without the caller managing ad lifecycle state.
     */
    fun show(activity: Activity, onReward: () -> Unit, onDismissedWithoutReward: () -> Unit = {}) {
        val ad = rewardedAd
        if (ad == null) {
            onDismissedWithoutReward()
            load()
            return
        }
        rewardedAd = null
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                if (!earned) onDismissedWithoutReward()
                load()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                onDismissedWithoutReward()
                load()
            }
        }
        ad.show(activity) {
            earned = true
            onReward()
        }
    }
}
