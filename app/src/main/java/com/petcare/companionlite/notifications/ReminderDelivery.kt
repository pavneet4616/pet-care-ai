package com.petcare.companionlite.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.petcare.companionlite.PetCareApplication
import com.petcare.companionlite.R
import com.petcare.companionlite.core.inAppLanguage
import com.petcare.companionlite.data.model.TaskType
import kotlinx.coroutines.flow.first

object ReminderDelivery {
    suspend fun deliver(applicationContext: Context, reminderId: Long, scheduledAt: Long) {
        val app = applicationContext as PetCareApplication
        val reminder = app.container.repository.getReminder(reminderId) ?: return
        val pet = app.container.repository.getPet(reminder.petId) ?: return
        if (app.container.repository.reminderCompleted(reminderId, scheduledAt)) return
        val settings = app.container.settingsStore.settings.first()
        val localized = applicationContext.inAppLanguage(settings.appLanguageTag)
        val notificationEnabled = when (reminder.type) {
            TaskType.FEED -> settings.feedingNotifications
            TaskType.MEDICINE -> settings.medicineNotifications
            TaskType.WALK -> settings.walkNotifications
            TaskType.GROOMING -> true
        }
        if (!notificationEnabled || !NotificationSupport.canPost(applicationContext)) return

        val contentIntent = NotificationSupport.openAppIntent(
            applicationContext,
            reminderId.toInt(),
            Uri.parse("petcare://open/reminder/$reminderId"),
        ) {
            putExtra("selected_pet_id", reminder.petId)
        }

        fun action(action: String): PendingIntent {
            val actionIntent = Intent(applicationContext, ReminderActionReceiver::class.java).apply {
                this.action = action
                data = Uri.parse("petcare://reminder/$reminderId/$scheduledAt/$action")
                putExtra(ReminderWorker.KEY_REMINDER_ID, reminderId)
                putExtra(ReminderWorker.KEY_SCHEDULED_AT, scheduledAt)
            }
            return PendingIntent.getBroadcast(
                applicationContext,
                0,
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val isMedicine = reminder.type == TaskType.MEDICINE
        val channelId = if (isMedicine) NotificationChannels.MEDICATION else NotificationChannels.ROUTINE
        val title = localized.getString(titleRes(reminder.type), pet.name)
        val body = localized.getString(R.string.notif_body_reminder)
        val notification = NotificationSupport.builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .addAction(0, localized.getString(R.string.mark_done), action(ReminderActionReceiver.ACTION_DONE))
            .addAction(0, localized.getString(R.string.snooze_ten_minutes), action(ReminderActionReceiver.ACTION_SNOOZE))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(reminderId.toInt(), notification)
        } catch (_: SecurityException) {
            // Notification permission was revoked after canPost() checked it.
        }
    }

    /** Title with a task emoji and the pet name, e.g. "💊 Time for Buddy’s medicine". */
    private fun titleRes(type: TaskType): Int = when (type) {
        TaskType.FEED -> R.string.notif_title_feed
        TaskType.WALK -> R.string.notif_title_walk
        TaskType.MEDICINE -> R.string.notif_title_medicine
        TaskType.GROOMING -> R.string.notif_title_grooming
    }
}
