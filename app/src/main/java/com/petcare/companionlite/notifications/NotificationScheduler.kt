package com.petcare.companionlite.notifications

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.petcare.companionlite.data.model.Reminder
import com.petcare.companionlite.data.model.nextTriggerAt
import java.util.concurrent.TimeUnit

class NotificationScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun scheduleReminder(reminder: Reminder, petName: String) {
        val now = System.currentTimeMillis()
        val nextTrigger = nextTriggerAt(reminder, now) ?: run {
            cancelReminder(reminder.id)
            return
        }
        val delay = (nextTrigger - now).coerceAtLeast(0)
        try {
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder()
                        .putLong(ReminderWorker.KEY_REMINDER_ID, reminder.id)
                        .putString(ReminderWorker.KEY_PET_NAME, petName)
                        .putString(ReminderWorker.KEY_TASK_TYPE, reminder.type.name)
                        .build(),
                )
                .build()
            workManager.enqueueUniqueWork(uniqueName(reminder.id), ExistingWorkPolicy.REPLACE, request)
        } catch (e: Exception) {
            Log.e("NotificationScheduler", "Failed to schedule reminder ${reminder.id}", e)
        }
    }

    fun cancelReminder(reminderId: Long) {
        workManager.cancelUniqueWork(uniqueName(reminderId))
    }

    companion object {
        fun uniqueName(reminderId: Long): String = "petcare-reminder-$reminderId"
    }
}
