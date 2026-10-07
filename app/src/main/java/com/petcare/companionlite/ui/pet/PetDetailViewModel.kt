package com.petcare.companionlite.ui.pet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.petcare.companionlite.PetCareApplication
import com.petcare.companionlite.data.model.HealthCategory
import com.petcare.companionlite.data.model.HealthLog
import com.petcare.companionlite.data.model.MoodState
import com.petcare.companionlite.data.model.PetType
import com.petcare.companionlite.data.model.PetActivity
import com.petcare.companionlite.data.model.PetProfile
import com.petcare.companionlite.data.model.Reminder
import com.petcare.companionlite.data.model.SettingsState
import com.petcare.companionlite.data.model.TaskType
import com.petcare.companionlite.data.model.TimelineItem
import com.petcare.companionlite.data.model.TimelineStatus
import com.petcare.companionlite.data.model.countDailyCareStreak
import com.petcare.companionlite.data.repository.PetRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PetDetailUiState(
    val pet: PetProfile? = null,
    val reminders: List<Reminder> = emptyList(),
    val timeline: List<TimelineItem> = emptyList(),
    val healthLogs: List<HealthLog> = emptyList(),
    val activities: List<PetActivity> = emptyList(),
    val mood: MoodState = MoodState.NEUTRAL,
    val settings: SettingsState = SettingsState(),
    val personalityMessage: String = "",
)

class PetDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val app = application as PetCareApplication
    val petId: Long = checkNotNull(savedStateHandle["petId"]).toString().toLong()
    private val repository = app.container.repository

    val uiState: StateFlow<PetDetailUiState> = combine(
        repository.observePet(petId),
        repository.observeReminders(petId),
        repository.observeTimeline(petId),
        repository.observeHealthLogs(petId),
        repository.observeActivities(petId)
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val pet = args[0] as PetProfile?
        @Suppress("UNCHECKED_CAST")
        val reminders = args[1] as List<Reminder>
        @Suppress("UNCHECKED_CAST")
        val timeline = args[2] as List<TimelineItem>
        @Suppress("UNCHECKED_CAST")
        val logs = args[3] as List<HealthLog>
        @Suppress("UNCHECKED_CAST")
        val activities = args[4] as List<PetActivity>
        
        val mood = when {
            timeline.any { it.status == TimelineStatus.MISSED || it.status == TimelineStatus.OVERDUE } -> MoodState.SAD
            timeline.isNotEmpty() && timeline.all { it.status == TimelineStatus.COMPLETED } -> MoodState.HAPPY
            else -> MoodState.NEUTRAL
        }
        val petType = pet?.let { PetType.fromString(it.type) } ?: PetType.OTHER
        val personalityMsg = if (mood == MoodState.SAD) {
            when (petType) {
                PetType.DOG -> "I missed my walk today \uD83D\uDE22"
                PetType.CAT -> "I'm still waiting for food \uD83D\uDE3E"
                PetType.BIRD -> "My feeder is empty \uD83D\uDE14"
                PetType.OTHER -> "${pet?.name ?: "I"} needs some attention \uD83E\uDD7A"
            }
        } else {
            petType.personalityLines.random()
        }
        PetDetailUiState(
            pet = pet,
            reminders = reminders,
            timeline = timeline,
            healthLogs = logs,
            activities = activities,
            mood = mood,
            settings = repository.settings.first(),
            personalityMessage = personalityMsg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PetDetailUiState())

    fun logTask(taskType: TaskType, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.logActivity(petId, taskType)
            onMessage(result.toastMessage)
        }
    }

    fun addReminder(taskType: TaskType, hour: Int, minute: Int, repeatDays: Set<Int>, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.upsertReminder(
                Reminder(
                    petId = petId,
                    type = taskType,
                    hour = hour,
                    minute = minute,
                    repeatDays = repeatDays,
                ),
            )
            when (result) {
                PetRepository.ReminderSaveResult.Success -> onMessage("${taskType.label} reminder set")
                PetRepository.ReminderSaveResult.FreeLimitReached ->
                    onMessage("Free tier allows ${PetRepository.FREE_REMINDER_LIMIT} reminders per pet. Upgrade to add more.")
            }
        }
    }

    fun deleteReminder(reminderId: Long) {
        viewModelScope.launch {
            repository.deleteReminder(reminderId)
        }
    }

    fun deletePet(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deletePet(petId)
            onDeleted()
        }
    }

    fun deleteHealthLog(logId: Long, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            repository.deleteHealthLog(logId)
            onMessage("Health log removed")
        }
    }

    fun addHealthLog(category: HealthCategory, note: String, value: Double?, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            repository.addHealthLog(petId, category, note, value)
            onMessage("${category.label} saved")
        }
    }

    fun exportPdf(onMessage: (String) -> Unit) {
        viewModelScope.launch {
            val state = uiState.value
            val pet = state.pet ?: return@launch
            val weightPoints = repository.observeWeightPoints(petId).first()
            val streakDays = countDailyCareStreak(state.activities)

            val uri = app.container.pdfExportManager.generateReport(
                pet = pet,
                activities = state.activities,
                healthLogs = state.healthLogs,
                weightPoints = weightPoints,
                streakDays = streakDays,
            )
            if (uri != null) {
                app.container.pdfExportManager.shareReport(uri)
                onMessage("Report generated and ready to share")
            } else {
                onMessage("Could not generate the report")
            }
        }
    }
}
