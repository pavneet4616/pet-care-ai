package com.petcare.companionlite.data.repository

import com.petcare.companionlite.R

import com.petcare.companionlite.core.PetStateEngine
import com.petcare.companionlite.core.inAppLanguage
import com.petcare.companionlite.core.labelResource
import com.petcare.companionlite.data.local.ActivityDao
import com.petcare.companionlite.data.local.ExpenseDao
import com.petcare.companionlite.data.local.HealthLogDao
import com.petcare.companionlite.data.local.NoteDao
import com.petcare.companionlite.data.local.PetDao
import com.petcare.companionlite.data.local.ReminderDao
import com.petcare.companionlite.data.local.WaterLogDao
import com.petcare.companionlite.data.model.ActivityEntity
import com.petcare.companionlite.data.model.Expense
import com.petcare.companionlite.data.model.HealthCategory
import com.petcare.companionlite.data.model.HealthLog
import com.petcare.companionlite.data.model.LogActivityResult
import com.petcare.companionlite.data.model.Note
import com.petcare.companionlite.data.model.PetDashboardItem
import com.petcare.companionlite.data.model.PetProfile
import com.petcare.companionlite.data.model.PetSaveResult
import com.petcare.companionlite.data.model.TimelineItem
import com.petcare.companionlite.data.model.Reminder
import com.petcare.companionlite.data.model.TaskType
import com.petcare.companionlite.data.model.WaterLog
import com.petcare.companionlite.data.model.WeightPoint
import com.petcare.companionlite.data.model.computeTimeline
import com.petcare.companionlite.data.model.nowMillis
import com.petcare.companionlite.data.model.toDomain
import com.petcare.companionlite.data.model.toEntity
import com.petcare.companionlite.data.model.toProfile
import com.petcare.companionlite.notifications.NotificationScheduler
import com.petcare.companionlite.data.settings.SettingsStore
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import com.petcare.companionlite.data.model.countCareStreakFromDays
import com.petcare.companionlite.core.PetPhotoStore

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PetRepository(
    private val context: android.content.Context,
    private val petDao: PetDao,
    private val activityDao: ActivityDao,
    private val reminderDao: ReminderDao,
    private val healthLogDao: HealthLogDao,
    private val waterLogDao: WaterLogDao,
    private val noteDao: NoteDao,
    private val expenseDao: ExpenseDao,
    private val settingsStore: SettingsStore,
    private val notificationScheduler: NotificationScheduler,
    private val photoStore: PetPhotoStore,
) {
    private val zoneId: ZoneId
        get() = ZoneId.systemDefault()

    val settings = settingsStore.settings
    val hasReminders = reminderDao.observeCount().map { it > 0 }

    fun observePets(): Flow<List<PetProfile>> = petDao.observeAll().map { pets -> pets.map { it.toProfile() } }

    fun observePet(petId: Long): Flow<PetProfile?> = petDao.observeById(petId).map { it?.toProfile() }

    fun observeReminders(petId: Long): Flow<List<Reminder>> =
        reminderDao.observeForPet(petId).map { reminders -> reminders.mapNotNull { it.toDomain() } }

    fun observeActivities(petId: Long): Flow<List<com.petcare.companionlite.data.model.PetActivity>> =
        activityDao.observeForPet(petId).map { items -> items.mapNotNull { it.toDomain() } }

    fun observeHealthLogs(petId: Long): Flow<List<HealthLog>> =
        healthLogDao.observeForPet(petId).map { items -> items.mapNotNull { it.toDomain() } }

    fun observeWaterLogs(petId: Long): Flow<List<WaterLog>> =
        waterLogDao.observeForPet(petId).map { items -> items.map { it.toDomain() } }

    fun observeNotes(petId: Long): Flow<List<Note>> =
        noteDao.observeForPet(petId).map { items -> items.map { it.toDomain() } }

    fun observeExpenses(petId: Long): Flow<List<Expense>> =
        expenseDao.observeForPet(petId).map { items -> items.map { it.toDomain() } }

    fun observeTimeline(petId: Long): Flow<List<TimelineItem>> = dates.flatMapLatest { date -> combine(
        observeReminders(petId),
        activityDao.observeForPetSince(petId, date.minusDays(2).atStartOfDay(zoneId).toInstant().toEpochMilli()),
        clock,
    ) { reminders, activities, now ->
        computeTimeline(reminders, activities.mapNotNull { it.toDomain() }, now, zoneId).items
    } }

    private val clock = flow { while (true) { emit(nowMillis()); delay(60_000) } }
    private val dates = clock.map { java.time.Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }.distinctUntilChanged()

    fun observeDashboard(): Flow<List<PetDashboardItem>> = dates.flatMapLatest { date -> combine(
        petDao.observeAll(),
        activityDao.observeSince(date.minusDays(30).atStartOfDay(zoneId).toInstant().toEpochMilli()),
        reminderDao.observeAll(),
        waterLogDao.observeSince(date.minusDays(30).atStartOfDay(zoneId).toInstant().toEpochMilli()),
        combine(activityDao.observeCareDays(), clock) { days, now -> days to now },
    ) { pets, activities, reminders, waterLogs, daysAndNow ->
        val (days, now) = daysAndNow
        pets.map { pet ->
            val petActivities = activities.filter { it.petId == pet.id }.mapNotNull { it.toDomain() }
            val petReminders  = reminders.filter  { it.petId == pet.id }.mapNotNull { it.toDomain() }
            val petWaterLogs  = waterLogs.filter  { it.petId == pet.id }.map { it.toDomain() }
            PetStateEngine.buildDashboardItem(
                pet        = pet.toProfile(),
                activities = petActivities,
                reminders  = petReminders,
                waterLogs  = petWaterLogs,
                nowMillis  = now,
                zoneId     = zoneId,
                streakOverride = countCareStreakFromDays(days.filter { it.petId == pet.id }.map { LocalDate.parse(it.day) }.toSet(), java.time.Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()),
                context = context,
            )
        }
    } }

    private val saveMutex = kotlinx.coroutines.sync.Mutex()
    suspend fun savePet(profile: PetProfile): PetSaveResult = saveMutex.withLock { savePetInternal(profile) }

    private suspend fun savePetInternal(profile: PetProfile): PetSaveResult {
        var entity = profile.toEntity() ?: return PetSaveResult.InvalidInput
        if (entity.id == 0L) {
            val canAddMore = settings.first().hasFullAccess || petDao.count() < 2
            if (!canAddMore) return PetSaveResult.FreeLimitReached
            entity = entity.copy(photoUri = try { photoStore.importPhoto(entity.photoUri) } catch (_: Exception) { return PetSaveResult.PhotoError })
            val id = petDao.insert(entity)
            return PetSaveResult.Success(id)
        }
        val oldPhoto = petDao.getById(entity.id)?.photoUri
        entity = entity.copy(photoUri = try { photoStore.importPhoto(entity.photoUri) } catch (_: Exception) { return PetSaveResult.PhotoError })
        petDao.update(entity)
        if (oldPhoto != entity.photoUri) photoStore.delete(oldPhoto)
        return PetSaveResult.Success(entity.id)
    }

    suspend fun deletePet(petId: Long) {
        // FK cascade will remove activities, reminders, health_logs
        val petReminders = reminderDao.getAll().filter { it.petId == petId }
        petReminders.forEach { notificationScheduler.cancelReminder(it.id) }
        val photo = petDao.getById(petId)?.photoUri
        petDao.deleteById(petId)
        photoStore.delete(photo)
    }

    suspend fun logActivity(petId: Long, type: TaskType): LogActivityResult {
        val currentTime = nowMillis()
        val latest = activityDao.getLatestForType(petId, type.name)
        if (latest != null && currentTime - latest.timestamp < DUPLICATE_TAP_GUARD_MILLIS) {
            return LogActivityResult(
                duplicateDetected = true,
                toastMessage = context.inAppLanguage().getString(R.string.ui_6e37fc3377),
            )
        }


        activityDao.insert(
            ActivityEntity(
                petId = petId,
                type = type.name,
                timestamp = currentTime,
            ),
        )

        return LogActivityResult(
            duplicateDetected = false,
            toastMessage = context.inAppLanguage().getString(com.petcare.companionlite.R.string.care_logged, context.inAppLanguage().getString(type.labelResource())),
        )
    }

    sealed interface ReminderSaveResult {
        data object Success : ReminderSaveResult
        data object FreeLimitReached : ReminderSaveResult
    }

    suspend fun upsertReminder(reminder: Reminder): ReminderSaveResult {
        val pet = petDao.getById(reminder.petId) ?: return ReminderSaveResult.Success

        // Free tier: max 3 reminders per pet
        if (reminder.id == 0L) {
            val hasAccess = settings.first().hasFullAccess
            if (!hasAccess) {
                val currentCount = reminderDao.countForPet(reminder.petId)
                if (currentCount >= FREE_REMINDER_LIMIT) {
                    return ReminderSaveResult.FreeLimitReached
                }
            }
        }

        val persisted = if (reminder.id == 0L) {
            val fresh = reminder.copy(createdAt = nowMillis())
            val id = reminderDao.insert(fresh.toEntity())
            fresh.copy(id = id)
        } else {
            val existing = reminderDao.getAll().firstOrNull { it.id == reminder.id }
            val edited = reminder.copy(createdAt = existing?.createdAt ?: reminder.createdAt)
            reminderDao.update(edited.toEntity())
            edited
        }
        notificationScheduler.scheduleReminder(persisted, pet.name)
        return ReminderSaveResult.Success
    }

    suspend fun deleteReminder(reminderId: Long) {
        reminderDao.deleteById(reminderId)
        notificationScheduler.cancelReminder(reminderId)
    }

    suspend fun deleteHealthLog(logId: Long) {
        healthLogDao.deleteById(logId)
    }

    suspend fun addHealthLog(
        petId: Long,
        category: HealthCategory,
        note: String,
        value: Double? = null,
    ) {
        healthLogDao.insert(
            HealthLog(
                id = 0,
                petId = petId,
                category = category,
                value = value,
                note = note,
                timestamp = nowMillis(),
            ).toEntity(),
        )
    }

    suspend fun setPremium(enabled: Boolean) = settingsStore.setPremium(enabled)

    suspend fun setAdsRemoved(enabled: Boolean) = settingsStore.setAdsRemoved(enabled)

    suspend fun setNotificationPromptShown(shown: Boolean) = settingsStore.setNotificationPromptShown(shown)



    suspend fun getReminder(reminderId: Long): Reminder? = reminderDao.getById(reminderId)?.toDomain()

    suspend fun getPet(petId: Long): PetProfile? = petDao.getById(petId)?.toProfile()

    suspend fun reminderCompleted(reminderId: Long, scheduledAt: Long): Boolean {
        val reminder = getReminder(reminderId) ?: return true
        val activities = activityDao.getActivitiesSince(scheduledAt - 90 * 60 * 1000L).mapNotNull { it.toDomain() }
        val reminders = reminderDao.getAll().mapNotNull { it.toDomain() }
        return computeTimeline(reminders, activities, nowMillis(), zoneId, java.time.Instant.ofEpochMilli(scheduledAt).atZone(zoneId).toLocalDate()).items
            .any { it.reminder.id == reminderId && it.scheduledAt == scheduledAt && it.matchedActivity != null }
    }

    suspend fun completeReminder(reminderId: Long, scheduledAt: Long) {
        val reminder = getReminder(reminderId) ?: return
        if (reminderCompleted(reminderId, scheduledAt)) return
        activityDao.insert(ActivityEntity(petId = reminder.petId, type = reminder.type.name,
            timestamp = nowMillis(), reminderId = reminderId, scheduledAt = scheduledAt))
    }

    suspend fun migratePhotos() {
        petDao.getAll().filter { it.photoUri?.startsWith("content:") == true }.forEach { pet ->
            runCatching { photoStore.importPhoto(pet.photoUri) }.getOrNull()?.let { petDao.update(pet.copy(photoUri = it)) }
        }
    }

    suspend fun rescheduleAllReminders() {
        val petsById = petDao.getAll().associateBy { it.id }
        reminderDao.getAll()
            .mapNotNull { entity ->
                val reminder = entity.toDomain() ?: return@mapNotNull null
                val pet = petsById[reminder.petId] ?: return@mapNotNull null
                reminder to pet.name
            }
            .forEach { (reminder, petName) ->
                notificationScheduler.scheduleReminder(reminder, petName)
            }
    }

    fun observeWeightPoints(petId: Long): Flow<List<WeightPoint>> = observeHealthLogs(petId).map { logs ->
        logs.filter { it.category == HealthCategory.WEIGHT && it.value != null }
            .sortedBy { it.timestamp }
            .map { WeightPoint(timestamp = it.timestamp, value = it.value ?: 0.0) }
    }

    // ── Weekly Report helpers ──────────────────────────────────────
    suspend fun getAllPets() = petDao.getAll()

    suspend fun getActivitiesSince(sinceMillis: Long) = activityDao.getActivitiesSince(sinceMillis)

    suspend fun getAllReminders() = reminderDao.getAll()

    suspend fun saveWaterLog(log: WaterLog) = waterLogDao.insert(log.toEntity())
    suspend fun deleteWaterLog(id: Long) = waterLogDao.deleteById(id)

    suspend fun saveNote(note: Note) = noteDao.insert(note.toEntity())
    suspend fun updateNote(note: Note) = noteDao.update(note.toEntity())
    suspend fun deleteNote(id: Long) = noteDao.deleteById(id)

    suspend fun saveExpense(expense: Expense) = expenseDao.insert(expense.toEntity())
    suspend fun deleteExpense(id: Long) = expenseDao.deleteById(id)


    companion object {
        private const val DUPLICATE_TAP_GUARD_MILLIS = 15_000L
        const val FREE_REMINDER_LIMIT = 3
    }
}
