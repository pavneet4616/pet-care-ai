package com.petcare.companionlite.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.petcare.companionlite.PetCareApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val id = intent.getLongExtra(ReminderWorker.KEY_REMINDER_ID, -1)
                val occurrence = intent.getLongExtra(ReminderWorker.KEY_SCHEDULED_AT, 0)
                if (id <= 0 || occurrence <= 0 || occurrence > System.currentTimeMillis()) return@launch
                val app = (context.applicationContext as PetCareApplication).container
                when (intent.action) {
                    ACTION_DONE -> app.repository.completeReminder(id, occurrence)
                    ACTION_SNOOZE -> if (!app.repository.reminderCompleted(id, occurrence)) app.notificationScheduler.snooze(id, occurrence)
                    else -> return@launch
                }
                NotificationManagerCompat.from(context).cancel(id.toInt())
            } finally { pending.finish() }
        }
    }
    companion object {
        const val ACTION_DONE = "com.petcare.companionlite.DONE"
        const val ACTION_SNOOZE = "com.petcare.companionlite.SNOOZE"
    }
}
