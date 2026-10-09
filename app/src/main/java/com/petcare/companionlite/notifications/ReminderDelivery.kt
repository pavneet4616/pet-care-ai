package com.petcare.companionlite.notifications

import android.Manifest
import com.petcare.companionlite.core.inAppLanguage
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.petcare.companionlite.MainActivity
import com.petcare.companionlite.PetCareApplication
import com.petcare.companionlite.R
import com.petcare.companionlite.data.model.TaskType
import kotlinx.coroutines.flow.first

object ReminderDelivery {
    suspend fun deliver(applicationContext: android.content.Context, reminderId: Long, scheduledAt: Long) {
        val app = applicationContext as PetCareApplication
        val reminder = app.container.repository.getReminder(reminderId) ?: return
        val pet = app.container.repository.getPet(reminder.petId) ?: return
        if (app.container.repository.reminderCompleted(reminderId, scheduledAt)) return
        val displayPetName = pet.name
        val settings = app.container.settingsStore.settings.first()
        val localized = applicationContext.inAppLanguage(settings.appLanguageTag)
        val notificationEnabled = when (reminder.type) {
            TaskType.FEED -> settings.feedingNotifications
            TaskType.MEDICINE -> settings.medicineNotifications
            TaskType.WALK -> settings.walkNotifications
            TaskType.GROOMING -> true
        }

        if (notificationEnabled && (android.os.Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) {
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                putExtra("selected_pet_id", reminder.petId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                reminderId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            fun action(action: String): PendingIntent {
                val actionIntent = Intent(applicationContext, ReminderActionReceiver::class.java).apply {
                    this.action = action
                    data = android.net.Uri.parse("petcare://reminder/$reminderId/$scheduledAt/$action")
                    putExtra(ReminderWorker.KEY_REMINDER_ID, reminderId)
                    putExtra(ReminderWorker.KEY_SCHEDULED_AT, scheduledAt)
                }
                return PendingIntent.getBroadcast(applicationContext, 0, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
            val taskLabel = localized.getString(when (reminder.type) {
                TaskType.FEED -> R.string.care_feed
                TaskType.WALK -> R.string.care_walk
                TaskType.MEDICINE -> R.string.care_medicine
                TaskType.GROOMING -> R.string.care_grooming
            })
            val title = localized.getString(R.string.care_reminder_title, displayPetName)
            val body = localized.getString(R.string.care_reminder_body, taskLabel)
            val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.ROUTINE)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .addAction(0, localized.getString(R.string.mark_done), action(ReminderActionReceiver.ACTION_DONE))
                .addAction(0, localized.getString(R.string.snooze_ten_minutes), action(ReminderActionReceiver.ACTION_SNOOZE))
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(applicationContext).notify(reminderId.toInt(), notification)
        }

    }
}
