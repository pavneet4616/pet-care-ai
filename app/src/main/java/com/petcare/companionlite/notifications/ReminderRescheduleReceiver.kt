package com.petcare.companionlite.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RescheduleRemindersWorker.enqueue(context)
    }
}
