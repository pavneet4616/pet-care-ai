package com.petcare.companionlite.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class PetType(val label: String, val emoji: String) {
    DOG("Dog", "🐕"),
    CAT("Cat", "🐱"),
    BIRD("Bird", "🐦"),
    OTHER("Other", "🐾"),
    ;

    companion object {
        fun fromString(value: String): PetType =
            entries.firstOrNull { it.label.equals(value, ignoreCase = true) } ?: OTHER
    }
}

enum class TaskType(val label: String) {
    FEED("Feed"), WALK("Walk"), MEDICINE("Medicine"), GROOMING("Grooming"),
}

enum class MoodState {
    HAPPY,
    NEUTRAL,
    SAD,
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AiLanguageMode { AUTO, APP_LANGUAGE }

enum class AiResponseStyle { SHORT, BALANCED, DETAILED }

enum class TimelineStatus {
    COMPLETED,
    MISSED,
    OVERDUE,
    PENDING,
}

enum class HealthCategory(val label: String) {
    WEIGHT("Weight"),
    VET_VISIT("Vet Visit"),
    SYMPTOM("Symptom"),
    GROOMING("Grooming"),
    MOOD("Mood"),
    VOMIT("Vomit"),
}

@Entity(tableName = "pets")
data class PetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val breed: String?,
    val weightKg: Double?,
    val ageYears: Int?,
    val photoUri: String?,
    val birthDate: String? = null,
)

@Entity(
    tableName = "activities",
    foreignKeys = [
        ForeignKey(
            entity = PetEntity::class,
            parentColumns = ["id"],
            childColumns = ["petId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["petId"]),
        Index(value = ["petId", "type", "timestamp"], unique = true),
        Index(value = ["reminderId", "scheduledAt"], unique = true),
        Index(value = ["timestamp"]),
    ],
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val petId: Long,
    val type: String,
    val timestamp: Long,
    val note: String? = null,
    val reminderId: Long? = null,
    val scheduledAt: Long? = null,
)

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = PetEntity::class,
            parentColumns = ["id"],
            childColumns = ["petId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["petId"]),
    ],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val petId: Long,
    val type: String,
    val hour: Int,
    val minute: Int,
    val repeatDays: String,
    @androidx.room.ColumnInfo(defaultValue = "0") val createdAt: Long = 0,
)

@Entity(
    tableName = "health_logs",
    foreignKeys = [
        ForeignKey(
            entity = PetEntity::class,
            parentColumns = ["id"],
            childColumns = ["petId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["petId"]),
    ],
)
data class HealthLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val petId: Long,
    val category: String,
    val value: Double? = null,
    val note: String,
    val timestamp: Long,
)

@Entity(
    tableName = "water_logs",
    foreignKeys = [
        ForeignKey(
            entity = PetEntity::class,
            parentColumns = ["id"],
            childColumns = ["petId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["petId"]), Index(value = ["timestamp"])],
)
data class WaterLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val petId: Long,
    val amountMl: Int,
    val timestamp: Long,
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = PetEntity::class,
            parentColumns = ["id"],
            childColumns = ["petId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["petId"])],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val petId: Long,
    val content: String,
    val isTodo: Boolean = false,
    val isCompleted: Boolean = false,
    val timestamp: Long,
)

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = PetEntity::class,
            parentColumns = ["id"],
            childColumns = ["petId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["petId"])],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val petId: Long,
    val amount: Double,
    val category: String,
    val note: String,
    val timestamp: Long,
)

data class PetProfile(
    val id: Long = 0,
    val name: String = "",
    val type: String = "Dog",
    val breed: String = "",
    val weightKg: String = "",
    val ageYears: String = "",
    val photoUri: String? = null,
    val birthDate: String = "",
) {
    val petType: PetType get() = PetType.fromString(type)
    fun ageMonths(today: LocalDate = LocalDate.now()): Long? = birthDate.takeIf { it.isNotBlank() }
        ?.let { runCatching { java.time.temporal.ChronoUnit.MONTHS.between(LocalDate.parse(it), today) }.getOrNull() }
        ?.takeIf { it >= 0 }
}

data class PetActivity(
    val id: Long,
    val petId: Long,
    val type: TaskType,
    val timestamp: Long,
    val note: String?,
    val reminderId: Long? = null,
    val scheduledAt: Long? = null,
)

data class Reminder(
    val id: Long = 0,
    val petId: Long,
    val type: TaskType,
    val hour: Int,
    val minute: Int,
    val repeatDays: Set<Int>,
    val createdAt: Long = 0,
) {
    fun timeLabel(): String = LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("hh:mm a"))
}

data class HealthLog(
    val id: Long,
    val petId: Long,
    val category: HealthCategory,
    val value: Double?,
    val note: String,
    val timestamp: Long,
)

data class WaterLog(
    val id: Long,
    val petId: Long,
    val amountMl: Int,
    val timestamp: Long,
)

data class Note(
    val id: Long,
    val petId: Long,
    val content: String,
    val isTodo: Boolean,
    val isCompleted: Boolean,
    val timestamp: Long,
)

data class Expense(
    val id: Long,
    val petId: Long,
    val amount: Double,
    val category: String,
    val note: String,
    val timestamp: Long,
)

data class TimelineItem(
    val reminder: Reminder,
    val scheduledAt: Long,
    val status: TimelineStatus,
    val matchedActivity: PetActivity?,
)

data class PetDashboardItem(
    val pet: PetProfile,
    val mood: MoodState,
    val dailyMessage: String,
    val summaryMessage: String,
    val streakDays: Int,
    val completedCount: Int,
    val pendingCount: Int,
    val overdueCount: Int,
    val nextReminderText: String,
    val timeline: List<TimelineItem> = emptyList(),
    val streakAtRisk: Boolean = false,
    val personalityMessage: String = "",
    val recommendedTaskLabel: String = "",
    val greetingLine: String = "",
    val emotionalTaskPrompt: String = "",
    val hasWaterToday: Boolean = false,
)

enum class EngagementTier { HIGH, MEDIUM, LOW }

data class WeeklyReport(
    val petName: String,
    val petEmoji: String,
    val daysActive: Int,
    val totalDays: Int = 7,
    val bestStreak: Int,
    val tasksCompleted: Int,
    val totalTasks: Int,
    val missedTasks: Int,
    val completionPercent: Int,
    val tier: EngagementTier,
    val headline: String,
    val body: String,
    val nextWeekGoal: String,
    val premiumUpsell: String,
    val consistencyDots: String,
    val trendMessage: String,
)

data class WeightPoint(
    val timestamp: Long,
    val value: Double,
)

data class SettingsState(
    val selectedPetId: Long? = null,
    val analyticsConsent: Boolean = false,
    val crashConsent: Boolean = false,
    val serverDeletionPending: Boolean = false,
    val weightUnit: String = if (java.util.Locale.getDefault().country == "US") "lb" else "kg",
    val currencyCode: String = runCatching { java.util.Currency.getInstance(java.util.Locale.getDefault()).currencyCode }.getOrDefault("XXX"),
    val isPremium: Boolean = false,
    val adsRemoved: Boolean = false,
    val notificationPromptShown: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val isAiWeeklyPro: Boolean = false,
    val aiProExpiryMillis: Long = 0L,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val appLanguageTag: String? = null,
    val aiLanguageMode: AiLanguageMode = AiLanguageMode.AUTO,
    val aiUseCareHistory: Boolean = true,
    val aiUseProfile: Boolean = true,
    val aiUseActivity: Boolean = true,
    val aiUseHealth: Boolean = true,
    val aiUseReminders: Boolean = true,
    val aiResponseStyle: AiResponseStyle = AiResponseStyle.BALANCED,
    val feedingNotifications: Boolean = true,
    val waterNotifications: Boolean = true,
    val medicineNotifications: Boolean = true,
    val walkNotifications: Boolean = true,
    val healthFollowUps: Boolean = true,
    val weeklySummary: Boolean = true,
    val streakReminders: Boolean = true,
    val dailyAiTips: Boolean = false,
) {
    /**
     * Single source of truth for subscription state.
     * expiry==0 means "verified/optimistic, no expiry known yet" -> treated as active.
     * Cancel / refund / expiry removes the purchase from Play's list, and the
     * BillingManager reconcile then clears these flags (free version again).
     */
    val aiProActive: Boolean
        get() = isAiWeeklyPro &&
            (aiProExpiryMillis == 0L || aiProExpiryMillis > System.currentTimeMillis())

    /** AI Pro includes every premium feature. */
    val hasFullAccess: Boolean
        get() = isPremium || aiProActive

    /** No ads while Pro is active (paywall promise). */
    val hasNoAds: Boolean
        get() = adsRemoved || aiProActive
}

data class LogActivityResult(
    val duplicateDetected: Boolean,
    val toastMessage: String,
)

data class TimelineComputation(
    val items: List<TimelineItem>,
    val completedCount: Int,
    val pendingCount: Int,
    val overdueCount: Int,
    val completedTypes: Set<TaskType>,
)

sealed interface PetSaveResult {
    data class Success(val petId: Long) : PetSaveResult
    data object FreeLimitReached : PetSaveResult
    data object PhotoError : PetSaveResult
    data object InvalidInput : PetSaveResult
}

fun ReminderEntity.toDomain(): Reminder? {
    val taskType = type.toTaskTypeOrNull() ?: return null
    return Reminder(
        id = id,
        petId = petId,
        type = taskType,
        hour = hour.coerceIn(0, 23),
        minute = minute.coerceIn(0, 59),
        repeatDays = repeatDays.parseDays(),
        createdAt = createdAt,
    )
}

fun ActivityEntity.toDomain(): PetActivity? {
    val taskType = type.toTaskTypeOrNull() ?: return null
    return PetActivity(
        id = id,
        petId = petId,
        type = taskType,
        timestamp = timestamp,
        note = note,
        reminderId = reminderId,
        scheduledAt = scheduledAt,
    )
}

fun HealthLogEntity.toDomain(): HealthLog? {
    val categoryValue = category.toHealthCategoryOrNull() ?: return null
    return HealthLog(
        id = id,
        petId = petId,
        category = categoryValue,
        value = value,
        note = note,
        timestamp = timestamp,
    )
}

fun PetEntity.toProfile(): PetProfile = PetProfile(
    id = id,
    name = name,
    type = type,
    breed = breed.orEmpty(),
    weightKg = weightKg?.toString().orEmpty(),
    ageYears = ageYears?.toString().orEmpty(),
    photoUri = photoUri,
    birthDate = birthDate.orEmpty(),
)

fun PetProfile.toEntity(): PetEntity? {
    val weight = weightKg.takeIf { it.isNotBlank() }?.toDoubleOrNull()
    val age = ageYears.takeIf { it.isNotBlank() }?.toIntOrNull()
    if (weightKg.isNotBlank() && (weight == null || !weight.isFinite() || weight <= 0)) return null
    if (ageYears.isNotBlank() && (age == null || age < 0 || age > 100)) return null
    val birthday = birthDate.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (birthDate.isNotBlank() && (birthday == null || birthday.isAfter(LocalDate.now()))) return null
    if (name.isBlank()) return null
    return PetEntity(
        id = id,
        name = name.trim(),
        type = type.trim().ifBlank { "Dog" },
        breed = breed.trim().ifBlank { null },
        weightKg = weight,
        ageYears = age,
        photoUri = photoUri,
        birthDate = birthday?.toString(),
    )
}

fun Reminder.toEntity(): ReminderEntity = ReminderEntity(
    id = id,
    petId = petId,
    type = type.name,
    hour = hour,
    minute = minute,
    repeatDays = repeatDays.sorted().joinToString(","),
    createdAt = createdAt,
)

fun HealthLog.toEntity(): HealthLogEntity = HealthLogEntity(
    id = id,
    petId = petId,
    category = category.name,
    value = value,
    note = note,
    timestamp = timestamp,
)

fun WaterLogEntity.toDomain() = WaterLog(id, petId, amountMl, timestamp)
fun NoteEntity.toDomain() = Note(id, petId, content, isTodo, isCompleted, timestamp)
fun ExpenseEntity.toDomain() = Expense(id, petId, amount, category, note, timestamp)

fun WaterLog.toEntity() = WaterLogEntity(id, petId, amountMl, timestamp)
fun Note.toEntity() = NoteEntity(id, petId, content, isTodo, isCompleted, timestamp)
fun Expense.toEntity() = ExpenseEntity(id, petId, amount, category, note, timestamp)

fun nowMillis(): Long = System.currentTimeMillis()

fun todayAt(hour: Int, minute: Int, zoneId: ZoneId = ZoneId.systemDefault()): Long = LocalDate.now(zoneId)
    .atTime(hour, minute)
    .atZone(zoneId)
    .toInstant()
    .toEpochMilli()

fun Long.toDateTimeLabel(): String = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd MMM, hh:mm a"))

fun Long.toTimeLabel(): String = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("hh:mm a"))

fun Long.toDateLabel(): String = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd MMM yyyy"))

fun Long.toLocalDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()

fun nextTriggerAt(reminder: Reminder, fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
    val start = Instant.ofEpochMilli(fromMillis).atZone(zoneId).toLocalDate()
    val days = if (reminder.repeatDays.isEmpty()) DayOfWeek.entries.map { it.value }.toSet() else reminder.repeatDays
    repeat(8) { offset ->
        val date = start.plusDays(offset.toLong())
        if (days.contains(date.dayOfWeek.value)) {
            val candidate = LocalDateTime.of(date, LocalTime.of(reminder.hour, reminder.minute))
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
            if (candidate > fromMillis) return candidate
        }
    }
    return null
}

fun computeTimeline(
    reminders: List<Reminder>,
    activities: List<PetActivity>,
    now: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    date: LocalDate? = null,
): TimelineComputation {
    val today = date ?: Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
    val earlyWindow = 90 * 60 * 1000L
    // Include neighbouring dates so an early midnight log cannot also complete yesterday's task.
    val occurrences = (-1..1).flatMap { offset ->
        val date = today.plusDays(offset.toLong())
        reminders.filter { it.repeatDays.isEmpty() || date.dayOfWeek.value in it.repeatDays }
            .map { it to date.atTime(it.hour, it.minute).atZone(zoneId).toInstant().toEpochMilli() }
            .filter { (reminder, scheduledAt) -> scheduledAt >= reminder.createdAt }
    }.sortedWith(compareBy({ it.second }, { it.first.id }))
    val matched = mutableMapOf<Pair<Long, Long>, PetActivity>()
    val explicit = activities.filter { it.timestamp <= now && it.reminderId != null && it.scheduledAt != null }
    explicit.forEach { activity ->
        occurrences.firstOrNull { (reminder, time) ->
            reminder.id == activity.reminderId && time == activity.scheduledAt &&
                reminder.petId == activity.petId && reminder.type == activity.type
        }?.let { matched[it.first.id to it.second] = activity }
    }
    activities.filter { it.timestamp <= now && it.reminderId == null }.sortedBy { it.timestamp }.forEach { activity ->
        val candidates = occurrences.filter { (reminder, time) ->
            val next = occurrences.firstOrNull { (other, nextTime) ->
                other.petId == reminder.petId && other.type == reminder.type && nextTime > time
            }?.second
            val end = minOf(time + 24 * 60 * 60 * 1000L, next?.minus(earlyWindow) ?: Long.MAX_VALUE)
            reminder.petId == activity.petId && reminder.type == activity.type &&
                activity.timestamp in (time - earlyWindow) until end && (reminder.id to time) !in matched
        }
        candidates.minByOrNull { kotlin.math.abs(activity.timestamp - it.second) }
            ?.let { matched[it.first.id to it.second] = activity }
    }
    val timeline = occurrences.filter { (_, time) -> time.toLocalDate(zoneId) == today }
        .map { (reminder, scheduledAt) ->
            val completion = matched[reminder.id to scheduledAt]
            TimelineItem(
                reminder = reminder,
                scheduledAt = scheduledAt,
                status = statusFor(scheduledAt, completion != null, now),
                matchedActivity = completion,
            )
        }
        .sortedBy { it.scheduledAt }

    val completedTypes = timeline
        .filter { it.status == TimelineStatus.COMPLETED }
        .map { it.reminder.type }
        .toSet()

    return TimelineComputation(
        items = timeline,
        completedCount = timeline.count { it.status == TimelineStatus.COMPLETED },
        pendingCount = timeline.count { it.status == TimelineStatus.PENDING },
        overdueCount = timeline.count { it.status == TimelineStatus.OVERDUE || it.status == TimelineStatus.MISSED },
        completedTypes = completedTypes,
    )
}

fun countDailyCareStreak(activities: List<PetActivity>, zoneId: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zoneId)): Int =
    countCareStreakFromDays(activities.map { it.timestamp.toLocalDate(zoneId) }.toSet(), today)

fun countCareStreakFromDays(activeDays: Set<LocalDate>, today: LocalDate = LocalDate.now()): Int {
    var streak = 0
    // Start from yesterday if today has no activity yet (don't penalize early morning)
    var cursor = if (activeDays.contains(today)) today else today.minusDays(1)
    while (activeDays.contains(cursor)) {
        streak += 1
        cursor = cursor.minusDays(1)
    }
    return streak
}

private fun statusFor(scheduledAt: Long, completed: Boolean, now: Long): TimelineStatus {
    if (completed) return TimelineStatus.COMPLETED
    return when {
        now < scheduledAt -> TimelineStatus.PENDING
        now <= scheduledAt + 2 * 60 * 60 * 1000 -> TimelineStatus.OVERDUE
        else -> TimelineStatus.MISSED
    }
}

private fun String.parseDays(): Set<Int> = split(",")
    .mapNotNull { it.trim().toIntOrNull()?.takeIf { day -> day in 1..7 } }
    .toSet()

private fun String.toTaskTypeOrNull(): TaskType? = runCatching { TaskType.valueOf(this) }.getOrNull()

private fun String.toHealthCategoryOrNull(): HealthCategory? = runCatching { HealthCategory.valueOf(this) }.getOrNull()
