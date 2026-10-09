package com.petcare.companionlite.notifications

import android.content.Context
import androidx.work.*
import com.petcare.companionlite.PetCareApplication

class DataDeletionWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val container=(applicationContext as PetCareApplication).container
        val installation=inputData.getString("installation_id") ?: return Result.failure()
        if (!container.aiApi.deleteData(installation)) return Result.retry()
        container.settingsStore.setDeletionPending(false)
        return Result.success()
    }
    companion object {
        fun enqueue(context:Context,installationId:String) {
            val request=OneTimeWorkRequestBuilder<DataDeletionWorker>()
                .setInputData(workDataOf("installation_id" to installationId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("petcare-server-deletion",ExistingWorkPolicy.REPLACE,request)
        }
    }
}
