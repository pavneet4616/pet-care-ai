package com.petcare.companionlite.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.petcare.companionlite.PetCareApplication

class RescheduleRemindersWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PetCareApplication
        app.container.repository.rescheduleAllReminders()
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "petcare-reschedule-reminders"

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<RescheduleRemindersWorker>().build(),
            )
        }
    }
}

