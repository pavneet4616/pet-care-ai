package com.petcare.companionlite.core

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory
import com.petcare.companionlite.data.settings.SettingsStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ReviewPrompter(private val settings:SettingsStore) {
    private val triggers=MutableSharedFlow<Unit>(extraBufferCapacity=1)
    val positiveMoments=triggers.asSharedFlow()
    fun positiveMoment() { triggers.tryEmit(Unit) }
    suspend fun request(activity:Activity) {
        if (activity.isFinishing || activity.isDestroyed || !settings.claimReviewAttempt()) return
        val manager=ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnSuccessListener { info ->
            if (!activity.isFinishing && !activity.isDestroyed) manager.launchReviewFlow(activity,info)
        }
        // Play decides whether a dialog is shown; record attempts, never claim a review happened.
    }
}
