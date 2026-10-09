package com.petcare.companionlite.notifications

import android.Manifest
import com.petcare.companionlite.core.inAppLanguage
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.petcare.companionlite.PetCareApplication
import kotlinx.coroutines.flow.first
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.petcare.companionlite.MainActivity
import com.petcare.companionlite.R
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
        val app = applicationContext as PetCareApplication
        if (!app.container.settingsStore.settings.first().healthFollowUps) return Result.success()
        return try {
            val petId = inputData.getLong(KEY_PET_ID, 0L)
            val petName = inputData.getString(KEY_PET_NAME).orEmpty().ifBlank { "your pet" }
            val eventLabel = inputData.getString(KEY_EVENT).orEmpty().ifBlank { "health event" }
            if (petId == 0L) return Result.success()

            if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return Result.success()
            }

            val localized = applicationContext.inAppLanguage(app.container.settingsStore.settings.first().appLanguageTag)
            val prefill = localized.getString(R.string.followup_prompt, petName)
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                putExtra(EXTRA_OPEN_AI_CHAT, true)
                putExtra(EXTRA_AI_PET_ID, petId)
                putExtra(EXTRA_AI_PREFILL, prefill)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                applicationContext,
                petId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.AI_FOLLOWUP)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(localized.getString(R.string.followup_title, petName))
                .setContentText(localized.getString(R.string.followup_body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(applicationContext).notify(petId.toInt() + 50_000, notification)
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
