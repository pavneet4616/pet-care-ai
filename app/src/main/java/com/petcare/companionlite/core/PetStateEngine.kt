package com.petcare.companionlite.core

import android.content.Context
import com.petcare.companionlite.R
import com.petcare.companionlite.data.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Displays facts from care records. Logging cannot establish a pet's health or feelings. */
object PetStateEngine {
    fun buildDashboardItem(
        pet: PetProfile,
        activities: List<PetActivity>,
        reminders: List<Reminder>,
        waterLogs: List<WaterLog>,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        streakOverride: Int? = null,
        context: Context,
    ): PetDashboardItem {
        val strings = context.inAppLanguage()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val timeline = computeTimeline(reminders, activities, nowMillis, zoneId)
        val streak = streakOverride ?: countDailyCareStreak(activities, zoneId, today)
        val recommended = timeline.items.firstOrNull { it.status == TimelineStatus.OVERDUE }
            ?: timeline.items.firstOrNull { it.status == TimelineStatus.PENDING }
        val progress = strings.getString(R.string.care_progress, pet.name, timeline.completedCount, timeline.items.size)
        val next = reminders.mapNotNull { reminder -> nextTriggerAt(reminder, nowMillis, zoneId)?.let { it to reminder } }.minByOrNull { it.first }
        val locale = strings.resources.configuration.locales[0]
        val hour = Instant.ofEpochMilli(nowMillis).atZone(zoneId).hour
        val greeting = when (hour) {
            in 5..11 -> R.string.greeting_morning
            in 12..16 -> R.string.greeting_afternoon
            in 17..20 -> R.string.greeting_evening
            else -> R.string.greeting_night
        }
        return PetDashboardItem(
            pet = pet,
            mood = when {
                timeline.overdueCount > 0 -> MoodState.SAD
                timeline.items.isNotEmpty() && timeline.completedCount == timeline.items.size -> MoodState.HAPPY
                else -> MoodState.NEUTRAL
            },
            dailyMessage = progress,
            summaryMessage = strings.getString(R.string.care_streak, streak),
            streakDays = streak,
            completedCount = timeline.completedCount,
            pendingCount = timeline.pendingCount,
            overdueCount = timeline.overdueCount,
            nextReminderText = next?.let {
                strings.getString(R.string.care_next, strings.getString(it.second.type.labelResource()),
                    Instant.ofEpochMilli(it.first).atZone(zoneId).format(DateTimeFormatter.ofPattern("EEE HH:mm", locale)))
            }.orEmpty(),
            timeline = timeline.items,
            streakAtRisk = streak > 0 && timeline.pendingCount + timeline.overdueCount > 0,
            personalityMessage = strings.getString(R.string.care_record_message, pet.name),
            recommendedTaskLabel = recommended?.let { strings.getString(R.string.care_recommended, strings.getString(it.reminder.type.labelResource())) }.orEmpty(),
            greetingLine = strings.getString(greeting),
            emotionalTaskPrompt = recommended?.let { strings.getString(R.string.care_task_prompt, pet.name, strings.getString(it.reminder.type.labelResource())) } ?: progress,
            hasWaterToday = waterLogs.any { it.timestamp in today.atStartOfDay(zoneId).toInstant().toEpochMilli()..nowMillis },
        )
    }

    fun generateWeeklyReport(
        pet: PetProfile,
        activities: List<PetActivity>,
        reminders: List<Reminder>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): WeeklyReport {
        val strings = context.inAppLanguage()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val start = today.minusDays(6)
        val startMillis = start.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val previousStart = start.minusDays(7).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val current = activities.filter { it.petId == pet.id && it.timestamp in startMillis..nowMillis }
        val previous = activities.count { it.petId == pet.id && it.timestamp in previousStart until startMillis }
        val active = current.map { Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate() }.toSet()
        val occurrences = (0..6).flatMap { offset ->
            computeTimeline(reminders.filter { it.petId == pet.id }, activities, nowMillis, zoneId, start.plusDays(offset.toLong())).items
        }.filter { it.scheduledAt <= nowMillis }
        val completed = occurrences.count { it.status == TimelineStatus.COMPLETED }
        val percent = if (occurrences.isEmpty()) 0 else 100 * completed / occurrences.size
        var run = 0
        var best = 0
        val dots = (0..6).joinToString(" ") { offset ->
            if (start.plusDays(offset.toLong()) in active) {
                run++; best = maxOf(best, run); "\u25cf"
            } else { run = 0; "\u25cb" }
        }
        return WeeklyReport(
            petName = pet.name, petEmoji = pet.petType.emoji,
            daysActive = active.size, bestStreak = best,
            tasksCompleted = completed, totalTasks = occurrences.size,
            missedTasks = occurrences.size - completed, completionPercent = percent,
            tier = when { percent >= 80 -> EngagementTier.HIGH; percent >= 40 -> EngagementTier.MEDIUM; else -> EngagementTier.LOW },
            headline = strings.getString(R.string.weekly_pet_headline, pet.name),
            body = strings.getString(R.string.weekly_pet_body, active.size, current.size, completed, occurrences.size),
            nextWeekGoal = strings.getString(R.string.weekly_goal),
            premiumUpsell = strings.getString(R.string.weekly_history),
            consistencyDots = dots,
            trendMessage = strings.getString(when { current.size > previous -> R.string.weekly_more_logs; current.size < previous -> R.string.weekly_fewer_logs; else -> R.string.weekly_same_logs }),
        )
    }
}

fun TaskType.labelResource(): Int = when (this) {
    TaskType.FEED -> R.string.care_feed
    TaskType.WALK -> R.string.care_walk
    TaskType.MEDICINE -> R.string.care_medicine
    TaskType.GROOMING -> R.string.care_grooming
}

fun com.petcare.companionlite.data.model.HealthCategory.labelResource(): Int = when (this) {
    com.petcare.companionlite.data.model.HealthCategory.WEIGHT -> R.string.health_weight
    com.petcare.companionlite.data.model.HealthCategory.VET_VISIT -> R.string.health_vet_visit
    com.petcare.companionlite.data.model.HealthCategory.SYMPTOM -> R.string.health_symptom
    com.petcare.companionlite.data.model.HealthCategory.GROOMING -> R.string.care_grooming
    com.petcare.companionlite.data.model.HealthCategory.MOOD -> R.string.health_mood
    com.petcare.companionlite.data.model.HealthCategory.VOMIT -> R.string.health_vomit
}
