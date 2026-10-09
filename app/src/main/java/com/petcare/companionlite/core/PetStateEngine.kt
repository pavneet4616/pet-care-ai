package com.petcare.companionlite.core

import android.content.Context
import com.petcare.companionlite.R
import com.petcare.companionlite.data.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle

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
        // The most urgent open task: overdue first, then upcoming, then missed.
        val focus = timeline.items.firstOrNull { it.status == TimelineStatus.OVERDUE }
            ?: timeline.items.firstOrNull { it.status == TimelineStatus.PENDING }
            ?: timeline.items.firstOrNull { it.status == TimelineStatus.MISSED }
        val allDone = timeline.items.isNotEmpty() && timeline.completedCount == timeline.items.size
        val prompt = when {
            reminders.isEmpty() -> strings.getString(R.string.dash_hero_no_routine, pet.name)
            timeline.items.isEmpty() -> strings.getString(R.string.dash_hero_free_day, pet.name)
            allDone -> strings.getString(R.string.dash_hero_done, pet.name)
            focus != null -> strings.getString(
                R.string.care_task_prompt,
                pet.name,
                strings.getString(focus.reminder.type.actionLabelResource()),
            )
            else -> strings.getString(R.string.dash_hero_free_day, pet.name)
        }
        val next = reminders.mapNotNull { reminder -> nextTriggerAt(reminder, nowMillis, zoneId)?.let { it to reminder } }.minByOrNull { it.first }
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
            dailyMessage = prompt,
            summaryMessage = strings.getString(R.string.care_streak, streak),
            streakDays = streak,
            completedCount = timeline.completedCount,
            pendingCount = timeline.pendingCount,
            overdueCount = timeline.overdueCount,
            nextReminderText = next?.let { (triggerAt, reminder) ->
                strings.getString(
                    R.string.dash_next_format,
                    strings.getString(reminder.type.actionLabelResource()),
                    whenLabel(strings, triggerAt, nowMillis, zoneId),
                )
            }.orEmpty(),
            timeline = timeline.items,
            streakAtRisk = streak > 0 && timeline.pendingCount + timeline.overdueCount > 0,
            personalityMessage = strings.getString(R.string.care_record_message, pet.name),
            recommendedTaskLabel = focus?.let {
                strings.getString(R.string.care_recommended, strings.getString(it.reminder.type.actionLabelResource()))
            }.orEmpty(),
            greetingLine = strings.getString(greeting),
            emotionalTaskPrompt = prompt,
            hasWaterToday = waterLogs.any { it.timestamp in today.atStartOfDay(zoneId).toInstant().toEpochMilli()..nowMillis },
            reminderCount = reminders.size,
        )
    }

    /** "Today 8:00 AM", "Tomorrow 8:00 AM" or "Sat 8:00 AM", with the clock time from the device settings. */
    private fun whenLabel(context: Context, triggerAt: Long, nowMillis: Long, zoneId: ZoneId): String {
        val locale = context.resources.configuration.locales[0]
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val day = Instant.ofEpochMilli(triggerAt).atZone(zoneId).toLocalDate()
        val time = CareFormats.clockText(context, triggerAt, zoneId)
        return when (day) {
            today -> context.getString(R.string.dash_when_today, time)
            today.plusDays(1) -> context.getString(R.string.dash_when_tomorrow, time)
            else -> context.getString(
                R.string.dash_when_weekday,
                day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                time,
            )
        }
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
                run++; best = maxOf(best, run); "●"
            } else { run = 0; "○" }
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

/** Sentence fragment for running text only (e.g. "feeding"). Use [actionLabelResource] for labels. */
fun TaskType.labelResource(): Int = when (this) {
    TaskType.FEED -> R.string.care_feed
    TaskType.WALK -> R.string.care_walk
    TaskType.MEDICINE -> R.string.care_medicine
    TaskType.GROOMING -> R.string.care_grooming
}

/** Short, capitalized action label for buttons, chips and titles (e.g. "Feed", "Medicine"). */
fun TaskType.actionLabelResource(): Int = when (this) {
    TaskType.FEED -> R.string.dash_action_feed
    TaskType.WALK -> R.string.dash_action_walk
    TaskType.MEDICINE -> R.string.dash_action_medicine
    TaskType.GROOMING -> R.string.dash_action_grooming
}

fun com.petcare.companionlite.data.model.HealthCategory.labelResource(): Int = when (this) {
    com.petcare.companionlite.data.model.HealthCategory.WEIGHT -> R.string.health_weight
    com.petcare.companionlite.data.model.HealthCategory.VET_VISIT -> R.string.health_vet_visit
    com.petcare.companionlite.data.model.HealthCategory.SYMPTOM -> R.string.health_symptom
    com.petcare.companionlite.data.model.HealthCategory.GROOMING -> R.string.care_grooming
    com.petcare.companionlite.data.model.HealthCategory.MOOD -> R.string.health_mood
    com.petcare.companionlite.data.model.HealthCategory.VOMIT -> R.string.health_vomit
}
