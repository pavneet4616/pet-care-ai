package com.petcare.companionlite.notifications

import com.petcare.companionlite.core.inAppLanguage
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.petcare.companionlite.PetCareApplication
import com.petcare.companionlite.R
import com.petcare.companionlite.core.PetStateEngine
import com.petcare.companionlite.data.model.toDomain
import com.petcare.companionlite.data.model.toProfile
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * OneTimeWorkRequest-based weekly report worker.
 *
 * Instead of PeriodicWorkRequest (which has timing flex issues),
 * this worker fires once, sends the notification, then re-schedules
 * itself for the next Sunday at the user's adaptive preferred hour.
 */
class WeeklyReportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as PetCareApplication
            val repo = app.container.repository
            val settingsStore = app.container.settingsStore
            if (!settingsStore.settings.first().weeklySummary) {
                reschedule()
                return Result.success()
            }

            // Check if user recently opened the app (within 2 hours)
            val nowMillis = System.currentTimeMillis()
            val lastOpen = settingsStore.lastAppOpenTimestamp.first()
            if (nowMillis - lastOpen < 2 * 60 * 60 * 1000L) {
                enqueue(applicationContext, delayHours = 2)
                return Result.success()
            }

            // Get all pets and generate reports
            val pets = repo.getAllPets()
            if (pets.isEmpty()) {
                reschedule()
                return Result.success()
            }

            val zoneId = ZoneId.systemDefault()
            val weekStartMillis = LocalDate.now(zoneId).minusDays(6)
                .atStartOfDay(zoneId).toInstant().toEpochMilli()
            val allActivities = repo.getActivitiesSince(LocalDate.now(zoneId).minusDays(13).atStartOfDay(zoneId).toInstant().toEpochMilli()) // Fetch 14 days for trend comparison
            val allReminders = repo.getAllReminders()

            val localized = applicationContext.inAppLanguage(settingsStore.settings.first().appLanguageTag)
            val reports = pets.map { pet ->
                PetStateEngine.generateWeeklyReport(
                    pet = pet.toProfile(),
                    activities = allActivities.filter { it.petId == pet.id }.mapNotNull { it.toDomain() },
                    reminders = allReminders.filter { it.petId == pet.id }.mapNotNull { it.toDomain() },
                    zoneId = zoneId, context = localized,
                )
            }
            val headline = if (reports.size == 1) reports.first().headline
                else localized.getString(R.string.weekly_all_pets_headline, reports.size)
            val body = localized.getString(R.string.weekly_open_report)

            // Update state
            settingsStore.setUnreadWeeklyReport(true)
            settingsStore.setLastReportTimestamp(nowMillis)

            // Send notification
            if (NotificationSupport.canPost(applicationContext)) {
                val pendingIntent = NotificationSupport.openAppIntent(
                    applicationContext,
                    NOTIFICATION_ID,
                    Uri.parse("petcare://open/weekly"),
                ) {
                    putExtra(EXTRA_OPEN_WEEKLY_REPORT, true)
                }

                val notification = NotificationSupport.builder(applicationContext, NotificationChannels.WEEKLY_REPORT)
                    .setContentTitle(headline) // Personalized CTA
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

                try {
                    NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
                } catch (_: SecurityException) {
                    // Notification permission was revoked after canPost() checked it.
                }
            }

            // Self-reschedule for next week
            reschedule()
            Result.success()
        } catch (e: Exception) {
            Log.e("WeeklyWorker", "Failed: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun reschedule() {
        val app = applicationContext as PetCareApplication
        enqueue(applicationContext, app.container.settingsStore.getPreferredReportHour())
    }

    companion object {
        private const val UNIQUE_NAME = "petcare-weekly-report"
        const val EXTRA_OPEN_WEEKLY_REPORT = "open_weekly_report"
        const val NOTIFICATION_ID = 9001

        /**
         * Schedule a one-time work request.
         * If [delayHours] is provided, it simply delays by that amount.
         * Otherwise, it calculates the next Sunday at [preferredHour].
         * [policy] defaults to REPLACE; cold starts pass KEEP so an existing schedule is not reset.
         */
        fun enqueue(
            context: Context,
            preferredHour: Int = 20,
            delayHours: Int = 0,
            policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
        ) {
            val zoneId = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zoneId)

            val delayMillis = if (delayHours > 0) {
                delayHours * 60 * 60 * 1000L
            } else {
                var nextSunday = now.toLocalDate()
                    .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                    .atTime(LocalTime.of(preferredHour, 0))
                    .atZone(zoneId)

                // If we're already past that time this Sunday, push to next week
                if (!nextSunday.isAfter(now)) {
                    nextSunday = now.toLocalDate()
                        .with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
                        .atTime(LocalTime.of(preferredHour, 0))
                        .atZone(zoneId)
                }
                (nextSunday.toInstant().toEpochMilli() - now.toInstant().toEpochMilli())
                    .coerceAtLeast(60_000)
            }

            val request = OneTimeWorkRequestBuilder<WeeklyReportWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                policy,
                request,
            )
        }
    }
}
