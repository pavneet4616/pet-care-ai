package com.petcare.companionlite.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.petcare.companionlite.PetCareApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Local DB + notification only; no network work on an alarm delivery. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val id = intent.getLongExtra(ReminderWorker.KEY_REMINDER_ID, -1)
                val occurrence = intent.getLongExtra(ReminderWorker.KEY_SCHEDULED_AT, 0)
                if (id <= 0 || occurrence <= 0) return@launch
                ReminderDelivery.deliver(context.applicationContext, id, occurrence)
                if (!intent.getBooleanExtra("snoozed", false)) {
                    val container = (context.applicationContext as PetCareApplication).container
                    val reminder = container.repository.getReminder(id) ?: return@launch
                    val pet = container.repository.getPet(reminder.petId) ?: return@launch
                    container.notificationScheduler.scheduleReminder(reminder, pet.name)
                }
            } catch (e: Exception) {
                Log.e("ReminderAlarmReceiver", "Reminder alarm failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
