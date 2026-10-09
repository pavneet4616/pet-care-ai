package com.petcare.companionlite.notifications

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.petcare.companionlite.PetCareApplication
import com.petcare.companionlite.R
import com.petcare.companionlite.core.inAppLanguage
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

// Next-morning check-in after a vomit / sick-mood log:
// "How is Bruno feeling today?" -> opens AI chat with yesterday's context.
// This continuity beats generic daily tips for retention.
class AiFollowupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as PetCareApplication
            val settings = app.container.settingsStore.settings.first()
            if (!settings.healthFollowUps) return Result.success()
            val petId = inputData.getLong(KEY_PET_ID, 0L)
            if (petId == 0L) return Result.success()
            if (!NotificationSupport.canPost(applicationContext)) return Result.success()

            val localized = applicationContext.inAppLanguage(settings.appLanguageTag)
            val petName = inputData.getString(KEY_PET_NAME).orEmpty()
                .ifBlank { localized.getString(R.string.notif_pet_fallback) }
            val prefill = localized.getString(R.string.followup_prompt, petName)
            val pending = NotificationSupport.openAppIntent(
                applicationContext,
                petId.toInt(),
                Uri.parse("petcare://open/followup/$petId"),
            ) {
                putExtra(EXTRA_OPEN_AI_CHAT, true)
                putExtra(EXTRA_AI_PET_ID, petId)
                putExtra(EXTRA_AI_PREFILL, prefill)
            }
            val notification = NotificationSupport.builder(applicationContext, NotificationChannels.AI_FOLLOWUP)
                .setContentTitle(localized.getString(R.string.followup_title, petName))
                .setContentText(localized.getString(R.string.followup_body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(applicationContext).notify(petId.toInt() + 50_000, notification)
            } catch (_: SecurityException) {
                // Notification permission was revoked after canPost() checked it.
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("AiFollowupWorker", "follow-up failed", e)
            Result.success()
        }
    }

    companion object {
        const val EXTRA_OPEN_AI_CHAT = "extra_open_ai_chat"
        const val EXTRA_AI_PET_ID = "extra_ai_pet_id"
        const val EXTRA_AI_PREFILL = "extra_ai_prefill"

        private const val KEY_PET_ID = "pet_id"
        private const val KEY_PET_NAME = "pet_name"
        private const val KEY_EVENT = "event"

        /** Schedules a one-time next-morning (9am) check-in, replacing any pending one. */
        fun schedule(context: Context, petId: Long, petName: String, eventLabel: String) {
            val now = ZonedDateTime.now(ZoneId.systemDefault())
            var target = now.toLocalDate().plusDays(1).atTime(LocalTime.of(9, 0)).atZone(ZoneId.systemDefault())
            if (!target.isAfter(now)) target = target.plusDays(1)
            val delayMs = target.toInstant().toEpochMilli() - System.currentTimeMillis()
            try {
                val request = OneTimeWorkRequestBuilder<AiFollowupWorker>()
                    .setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
                    .setInputData(
                        Data.Builder()
                            .putLong(KEY_PET_ID, petId)
                            .putString(KEY_PET_NAME, petName)
                            .putString(KEY_EVENT, eventLabel)
                            .build(),
                    )
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "ai-followup-$petId",
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            } catch (e: Exception) {
                Log.e("AiFollowupWorker", "schedule failed", e)
            }
        }
    }
}
