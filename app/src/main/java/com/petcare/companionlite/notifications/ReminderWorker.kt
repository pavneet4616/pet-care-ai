package com.petcare.companionlite.notifications

import android.Manifest
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

class ReminderWorker(
    appContext: android.content.Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val reminderId = inputData.getLong(KEY_REMINDER_ID, -1)
        val petName = inputData.getString(KEY_PET_NAME).orEmpty()
        val taskType = inputData.getString(KEY_TASK_TYPE).orEmpty()
        if (reminderId == -1L) return Result.success()

        val app = applicationContext as PetCareApplication
        val reminder = app.container.repository.getReminder(reminderId) ?: return Result.success()
        val pet = app.container.repository.getPet(reminder.petId) ?: return Result.success()
        val displayPetName = petName.ifBlank { pet.name }

        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                reminderId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val petEmoji = com.petcare.companionlite.data.model.PetType.entries.firstOrNull { it.label == pet.type }?.emoji ?: "🐾"
            val (title, body) = when (taskType.lowercase()) {
                "feed" -> "🐱 $displayPetName is waiting for dinner ❤️" to "Tap to show $displayPetName some love 💚"
                "walk" -> "🚶 $displayPetName wants to go outside! 🐾" to "Tap to show $displayPetName some love 💚"
                "medicine" -> "💊 Time for $displayPetName's medicine ❤️" to "Tap to show $displayPetName some love 💚"
                "grooming" -> "✂️ $displayPetName needs a little grooming love" to "Tap to show $displayPetName some love 💚"
                else -> "$petEmoji $displayPetName has a care reminder ❤️" to "Tap to show $displayPetName some love 💚"
            }
            val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.ROUTINE)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(applicationContext).notify(reminderId.toInt(), notification)
        }

        app.container.notificationScheduler.scheduleReminder(reminder, pet.name)
        return Result.success()
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
        const val KEY_PET_NAME = "pet_name"
        const val KEY_TASK_TYPE = "task_type"
    }
}
