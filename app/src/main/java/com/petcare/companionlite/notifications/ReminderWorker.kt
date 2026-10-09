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
import com.petcare.companionlite.data.model.TaskType
import kotlinx.coroutines.flow.first

class ReminderWorker(
    appContext: android.content.Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val reminderId = inputData.getLong(KEY_REMINDER_ID, -1)
        val scheduledAt = inputData.getLong(KEY_SCHEDULED_AT, System.currentTimeMillis())
        if (reminderId == -1L) return Result.success()
        ReminderDelivery.deliver(applicationContext, reminderId, scheduledAt)

        val app = applicationContext as PetCareApplication
        val reminder = app.container.repository.getReminder(reminderId) ?: return Result.success()
        val pet = app.container.repository.getPet(reminder.petId) ?: return Result.success()
        app.container.notificationScheduler.scheduleReminder(reminder, pet.name)
        return Result.success()
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
        const val KEY_SCHEDULED_AT = "scheduled_at"
        const val KEY_PET_NAME = "pet_name"
        const val KEY_TASK_TYPE = "task_type"
    }
}
