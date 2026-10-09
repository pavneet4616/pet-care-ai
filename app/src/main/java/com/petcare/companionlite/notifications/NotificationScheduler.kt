package com.petcare.companionlite.notifications

import android.content.Context
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.petcare.companionlite.data.model.Reminder
import com.petcare.companionlite.data.model.nextTriggerAt
import com.petcare.companionlite.data.model.TaskType
import java.util.concurrent.TimeUnit

class NotificationScheduler(private val context: Context) {
    private val workManager = WorkManager.getInstance(context)
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun exactAlarmsAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    fun scheduleReminder(reminder: Reminder, petName: String) {
        val now = System.currentTimeMillis()
        val nextTrigger = nextTriggerAt(reminder, now) ?: run {
            cancelReminder(reminder.id)
            return
        }
        val delay = (nextTrigger - now).coerceAtLeast(0)
        if (reminder.type == TaskType.MEDICINE) {
            workManager.cancelUniqueWork(uniqueName(reminder.id))
            scheduleAlarm(reminder.id, nextTrigger, nextTrigger, snooze = false)
            return
        }
        try {
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder()
                        .putLong(ReminderWorker.KEY_REMINDER_ID, reminder.id)
                        .putLong(ReminderWorker.KEY_SCHEDULED_AT, nextTrigger)
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
        listOf(false, true).forEach { snooze ->
            alarms.cancel(alarmIntent(reminderId, 0, snooze))
        }
        androidx.core.app.NotificationManagerCompat.from(context).cancel(reminderId.toInt())
    }

    fun snooze(reminderId: Long, scheduledAt: Long) =
        scheduleAlarm(reminderId, System.currentTimeMillis() + 10 * 60 * 1000, scheduledAt, true)

    private fun alarmIntent(id: Long, scheduledAt: Long, snooze: Boolean): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            data = Uri.parse("petcare://reminder/$id/${if (snooze) "snooze" else "alarm"}")
            putExtra(ReminderWorker.KEY_REMINDER_ID, id)
            putExtra(ReminderWorker.KEY_SCHEDULED_AT, scheduledAt)
            putExtra("snoozed", snooze)
        }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun scheduleAlarm(id: Long, triggerAt: Long, scheduledAt: Long, snooze: Boolean) {
        val pending = alarmIntent(id, scheduledAt, snooze)
        try {
            if (exactAlarmsAllowed()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            else alarms.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10 * 60 * 1000, pending)
        } catch (_: SecurityException) {
            // Permission may change between checking and scheduling. Never crash or silently drop it.
            alarms.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10 * 60 * 1000, pending)
        }
    }

    companion object {
        fun uniqueName(reminderId: Long): String = "petcare-reminder-$reminderId"
    }
}
