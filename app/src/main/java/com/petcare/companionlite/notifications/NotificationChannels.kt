package com.petcare.companionlite.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.petcare.companionlite.R
import com.petcare.companionlite.core.inAppLanguage

object NotificationChannels {
    const val ROUTINE = "routine_reminders"
    const val WEEKLY_REPORT = "weekly_report"
    const val AI_FOLLOWUP = "ai_followup"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val localized = context.inAppLanguage()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                ROUTINE,
                localized.getString(R.string.channel_routine),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = localized.getString(R.string.channel_routine_body)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                WEEKLY_REPORT,
                localized.getString(R.string.channel_weekly),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = localized.getString(R.string.channel_weekly_body)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                AI_FOLLOWUP,
                localized.getString(R.string.channel_followup),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = localized.getString(R.string.channel_followup_body)
            },
        )
    }
}

