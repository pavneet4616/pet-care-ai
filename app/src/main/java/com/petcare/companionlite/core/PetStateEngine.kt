package com.petcare.companionlite.core

import com.petcare.companionlite.data.model.MoodState
import com.petcare.companionlite.data.model.PetActivity
import com.petcare.companionlite.data.model.PetDashboardItem
import com.petcare.companionlite.data.model.PetProfile
import com.petcare.companionlite.data.model.PetType
import com.petcare.companionlite.data.model.Reminder
import com.petcare.companionlite.data.model.TaskType
import com.petcare.companionlite.data.model.TimelineItem
import com.petcare.companionlite.data.model.TimelineStatus
import com.petcare.companionlite.data.model.WeeklyReport
import com.petcare.companionlite.data.model.EngagementTier
import com.petcare.companionlite.data.model.computeTimeline
import com.petcare.companionlite.data.model.countDailyCareStreak
import com.petcare.companionlite.data.model.nextTriggerAt
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import com.petcare.companionlite.data.model.WaterLog

/**
 * PetStateEngine — Single source of truth for all pet-state computation.
 *
 * The UI layer receives [PetDashboardItem] and only renders — it never
 * computes streak, risk, recommendations, or personality independently.
 *
 * Rules:
 *  - All "today" comparisons use [ZoneId]-aware [LocalDate], never raw millis.
 *  - Streak risk = has existing streak BUT pending/overdue tasks remain today.
 *  - Recommended task = first overdue → first pending (urgency order).
 *  - Personality messages randomised per pet type, with mood-aware sad fallback.
 */
object PetStateEngine {

    fun buildDashboardItem(
        pet: PetProfile,
        activities: List<PetActivity>,
        reminders: List<Reminder>,
        waterLogs: List<WaterLog>,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): PetDashboardItem {
        val timeline      = computeTimeline(reminders, activities, nowMillis, zoneId)
        val streak        = countDailyCareStreak(activities, zoneId)
        val mood          = computeMood(timeline.completedCount, timeline.items.size, timeline.overdueCount)
        val streakAtRisk  = streak > 0 && (timeline.pendingCount > 0 || timeline.overdueCount > 0)

        // Compute hasWaterToday
        val today = java.time.LocalDate.now(zoneId)
        val todayStartMillis = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val hasWaterToday = waterLogs.any { it.timestamp >= todayStartMillis }

        val recommendedTask = timeline.items
            .firstOrNull { it.status == TimelineStatus.OVERDUE }
            ?: timeline.items.firstOrNull { it.status == TimelineStatus.PENDING }

        val recommendedLabel = recommendedTask?.let {
            "\uD83C\uDFAF ${it.reminder.type.label} (Recommended)"
        } ?: ""

        val personalityMsg = resolvePersonalityMessage(mood, pet.petType, pet.name)

        val nextReminderText = buildNextReminderLabel(reminders, nowMillis, zoneId)

        val greeting = getTimeOfDayGreeting(zoneId)
        val emotionalPrompt = getEmotionalTaskPrompt(
            petName = pet.name,
            petType = pet.petType,
            recommendedTask = recommendedTask,
            urgentCount = timeline.pendingCount + timeline.overdueCount,
            completedCount = timeline.completedCount,
            totalCount = timeline.items.size,
        )

        return PetDashboardItem(
            pet               = pet,
            mood              = mood,
            dailyMessage      = buildDailyMessage(mood, pet.name, timeline.completedTypes, timeline.completedCount),
            summaryMessage    = buildSummaryMessage(pet.name, timeline.completedCount, timeline.items.size, streak),
            streakDays        = streak,
            completedCount    = timeline.completedCount,
            pendingCount      = timeline.pendingCount,
            overdueCount      = timeline.overdueCount,
            nextReminderText  = nextReminderText,
            timeline          = timeline.items,
            streakAtRisk      = streakAtRisk,
            personalityMessage = personalityMsg,
            recommendedTaskLabel = recommendedLabel,
            greetingLine      = greeting,
            emotionalTaskPrompt = emotionalPrompt,
            hasWaterToday     = hasWaterToday,
        )
    }

    // ── Mood ─────────────────────────────────────────────────────
    private fun computeMood(completed: Int, total: Int, overdue: Int): MoodState = when {
        total == 0     -> MoodState.NEUTRAL
        overdue > 0    -> MoodState.SAD
        completed == total && total > 0 -> MoodState.HAPPY
        else           -> MoodState.NEUTRAL
    }

    // ── Personality ───────────────────────────────────────────────
    private fun resolvePersonalityMessage(mood: MoodState, petType: PetType, petName: String): String {
        if (mood == MoodState.SAD) {
            return when (petType) {
                PetType.DOG   -> "I missed my walk today \uD83D\uDE22"
                PetType.CAT   -> "I'm still waiting for food \uD83D\uDE3E"
                PetType.BIRD  -> "My feeder is empty \uD83D\uDE14"
                PetType.OTHER -> "$petName needs some attention \uD83E\uDD7A"
            }
        }
        return petType.personalityLines.random()
    }

    // ── Daily message ─────────────────────────────────────────────
    private fun buildDailyMessage(
        mood: MoodState,
        petName: String,
        completedTypes: Set<TaskType>,
        completedCount: Int,
    ): String = when (mood) {
        MoodState.HAPPY   -> completedTypes.randomOrNull()?.moodMessages?.randomOrNull()
            ?: "$petName had an amazing day! You're the best \uD83C\uDF89"
        MoodState.NEUTRAL -> if (completedCount > 0)
            completedTypes.firstOrNull()?.moodMessages?.randomOrNull() ?: "$petName is doing well so far \uD83D\uDC9A"
        else
            "$petName's day hasn't started yet. Say good morning! \uD83C\uDF05"
        MoodState.SAD     -> "$petName is waiting for you... \uD83E\uDD7A"
    }

    // ── Summary message ───────────────────────────────────────────
    private fun buildSummaryMessage(petName: String, completed: Int, total: Int, streak: Int): String = when {
        streak >= 7   -> "$petName loves your dedication! $streak days together \uD83D\uDD25"
        streak >= 2   -> "$streak days of love in a row \u2764\uFE0F"
        completed > 0 -> "$petName: $completed of $total tasks done — keep going! \uD83D\uDC9A"
        total == 0    -> "Start $petName's first routine to build a care streak \uD83D\uDC3E"
        else          -> "$petName is ready for some love today \uD83D\uDC3E"
    }

    // ── Next reminder label ───────────────────────────────────────
    private fun buildNextReminderLabel(
        reminders: List<Reminder>,
        nowMillis: Long,
        zoneId: ZoneId,
    ): String {
        val next = reminders.mapNotNull { reminder ->
            nextTriggerAt(reminder, nowMillis, zoneId)?.let { it to reminder }
        }.minByOrNull { it.first } ?: return ""
        val timeStr = Instant.ofEpochMilli(next.first)
            .atZone(zoneId)
            .format(DateTimeFormatter.ofPattern("EEE h:mm a"))
        return "Next up: ${next.second.type.label} at $timeStr"
    }

    // ── Time-of-day greeting ──────────────────────────────────────
    fun getTimeOfDayGreeting(zoneId: ZoneId = ZoneId.systemDefault()): String {
        val hour = LocalTime.now(zoneId.rules.getOffset(Instant.now())).hour
        return when (hour) {
            in 5..11  -> "Good morning \u2600\uFE0F"
            in 12..16 -> "Good afternoon \uD83C\uDF24\uFE0F"
            in 17..20 -> "Good evening \uD83C\uDF19"
            else      -> "Good night \uD83C\uDF1F"
        }
    }

    // ── Emotional task prompt ─────────────────────────────────────
    fun getEmotionalTaskPrompt(
        petName: String,
        petType: PetType,
        recommendedTask: TimelineItem?,
        urgentCount: Int,
        completedCount: Int,
        totalCount: Int,
    ): String {
        // All done!
        if (totalCount > 0 && completedCount == totalCount) {
            return "$petName is happy and cared for! \uD83C\uDF89"
        }
        // No tasks set up
        if (totalCount == 0) {
            return when (petType) {
                PetType.DOG -> "\uD83D\uDC36 $petName wants to play!"
                PetType.CAT -> "\uD83D\uDC31 $petName is purring for attention"
                PetType.BIRD -> "\uD83D\uDC26 $petName wants to sing for you!"
                PetType.OTHER -> "\uD83D\uDC3E $petName is waiting for you"
            }
        }
        // Has a recommended task
        val task = recommendedTask ?: return "$petName has $urgentCount things to do \uD83D\uDC3E"
        return when (task.reminder.type) {
            TaskType.FEED -> "\uD83C\uDF56 $petName is hungry!"
            TaskType.WALK -> "\uD83D\uDEB6 $petName wants to go outside!"
            TaskType.MEDICINE -> "\uD83D\uDC8A Time for $petName's medicine"
            TaskType.GROOMING -> "\u2702\uFE0F $petName needs grooming"
        }
    }

    // ── Weekly Report ────────────────────────────────────────────
    fun generateWeeklyReport(
        pet: PetProfile,
        activities: List<PetActivity>,
        reminders: List<Reminder>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): WeeklyReport {
        val today = java.time.LocalDate.now(zoneId)
        val weekStart = today.minusDays(6)
        val prevWeekStart = weekStart.minusDays(7)

        val weekStartMillis = weekStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val prevWeekStartMillis = prevWeekStart.atStartOfDay(zoneId).toInstant().toEpochMilli()

        // Current week activities
        val currentWeekActivities = activities.filter { it.timestamp in weekStartMillis until (weekStartMillis + 7 * 24 * 60 * 60 * 1000L) }
        // Previous week activities
        val prevWeekActivities = activities.filter { it.timestamp in prevWeekStartMillis until weekStartMillis }

        // Count active days in current week
        val activeDays = currentWeekActivities
            .map { Instant.ofEpochMilli(it.timestamp).atZone(zoneId).toLocalDate() }
            .filter { !it.isBefore(weekStart) && !it.isAfter(today) }
            .toSet()

        // Generate Consistency Dots (● ● ● ● ○ ○ ○)
        val consistencyDots = (0..6).joinToString(" ") { offset ->
            val date = weekStart.plusDays(offset.toLong())
            if (activeDays.contains(date)) "●" else "○"
        }

        // Expected tasks logic
        val totalExpected = reminders.sumOf { reminder ->
            val days = if (reminder.repeatDays.isEmpty()) 7
            else (0..6L).count { offset ->
                val date = weekStart.plusDays(offset)
                reminder.repeatDays.contains(date.dayOfWeek.value)
            }
            days
        }.coerceAtLeast(1)

        val tasksCompleted = currentWeekActivities.size
        val missedTasks = (totalExpected - tasksCompleted).coerceAtLeast(0)
        val completionPercent = ((tasksCompleted.toFloat() / totalExpected) * 100).toInt().coerceIn(0, 100)

        // Trend calculation (Normalized)
        val prevTasksCompleted = prevWeekActivities.size
        // Assuming reminders didn't drastically change, use the same expected base or a heuristic
        // For accurate comparison, we compare the completion ratios
        val currentScore = tasksCompleted.toFloat() / totalExpected
        val previousScore = prevTasksCompleted.toFloat() / totalExpected

        val trendMessage = when {
            currentScore > previousScore -> "You're doing better than last week 🔥"
            currentScore < previousScore -> "Let's bounce back this week 💚"
            else -> "Steady consistency. Keep it up! 🐾"
        }

        val bestStreak = computeBestStreakInWindow(activeDays, weekStart, today)

        val tier = when {
            completionPercent >= 80 -> EngagementTier.HIGH
            completionPercent >= 40 -> EngagementTier.MEDIUM
            else -> EngagementTier.LOW
        }

        val headline = when (tier) {
            EngagementTier.HIGH -> "🔥 ${pet.name} had an amazing week!\nSee your ${bestStreak}-day streak 💚"
            EngagementTier.MEDIUM -> "🐾 You're getting there!\nCheck your weekly progress"
            EngagementTier.LOW -> "😟 ${pet.name} missed you this week\nLet's restart together 🐾"
        }

        val body = when (tier) {
            EngagementTier.HIGH -> "You cared for ${pet.name} ${activeDays.size} days 💚\n🔥 Best streak: $bestStreak days\n\nShe's getting used to your routine!"
            EngagementTier.MEDIUM -> "You showed up ${activeDays.size} days — let's improve this week 💪\n⚠️ $missedTasks tasks missed\n\nA little more consistency goes a long way!"
            EngagementTier.LOW -> "Only ${activeDays.size} day${if (activeDays.size != 1) "s" else ""} logged\n\nStart small — one task today is enough 🐾"
        }

        val nextWeekGoal = when (tier) {
            EngagementTier.HIGH -> "🎯 Goal: Maintain your perfect streak!"
            EngagementTier.MEDIUM -> "🎯 Goal: Complete ${(activeDays.size + 2).coerceAtMost(7)} days of care"
            EngagementTier.LOW -> "🎯 Goal: Show up 3 days this week"
        }

        val premiumUpsell = when (tier) {
            EngagementTier.HIGH -> "You're doing amazing 🔥\nUnlock advanced insights to go further"
            EngagementTier.MEDIUM -> "You're building momentum 💪\nPremium helps you track deeper habits"
            EngagementTier.LOW -> "Let's improve next week\nPremium helps you track deeper habits"
        }

        return WeeklyReport(
            petName = pet.name,
            petEmoji = pet.petType.emoji,
            daysActive = activeDays.size,
            bestStreak = bestStreak,
            tasksCompleted = tasksCompleted,
            totalTasks = totalExpected,
            missedTasks = missedTasks,
            completionPercent = completionPercent,
            tier = tier,
            headline = headline,
            body = body,
            nextWeekGoal = nextWeekGoal,
            premiumUpsell = premiumUpsell,
            consistencyDots = consistencyDots,
            trendMessage = trendMessage,
        )
    }

    private fun computeBestStreakInWindow(
        activeDays: Set<java.time.LocalDate>,
        start: java.time.LocalDate,
        end: java.time.LocalDate,
    ): Int {
        var best = 0
        var current = 0
        var cursor = start
        while (!cursor.isAfter(end)) {
            if (activeDays.contains(cursor)) {
                current++
                if (current > best) best = current
            } else {
                current = 0
            }
            cursor = cursor.plusDays(1)
        }
        return best
    }
}
