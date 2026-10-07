package com.petcare.companionlite.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val ROUTINE = "routine_reminders"
    const val WEEKLY_REPORT = "weekly_report"
    const val AI_FOLLOWUP = "ai_followup"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                ROUTINE,
                "Routine reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Feeding, walk, medicine, and grooming reminders"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                WEEKLY_REPORT,
                "Weekly report",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Your pet's weekly care summary"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                AI_FOLLOWUP,
                "Pet check-ins",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Follow-ups after health events, with quick AI help"
            },
        )
    }
}

