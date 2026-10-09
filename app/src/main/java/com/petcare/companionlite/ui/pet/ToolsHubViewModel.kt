package com.petcare.companionlite.ui.pet

import com.petcare.companionlite.core.inAppLanguage

import com.petcare.companionlite.R

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.petcare.companionlite.data.model.Expense
import com.petcare.companionlite.data.model.HealthCategory
import com.petcare.companionlite.data.model.HealthLog
import com.petcare.companionlite.data.model.Note
import com.petcare.companionlite.data.model.WaterLog
import com.petcare.companionlite.data.repository.PetRepository
import com.petcare.companionlite.notifications.AiFollowupWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class ToolsHubUiState(
    val waterLogs: List<WaterLog> = emptyList(),
    val todayWaterTotal: Int = 0,
    val moodLogs: List<HealthLog> = emptyList(),
    val latestMood: String = "—",
    val vomitLogs: List<HealthLog> = emptyList(),
    val vomitCountToday: Int = 0,
    val notes: List<Note> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val todayExpenseTotal: Double = 0.0,
    val totalExpenses: Double = 0.0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ToolsHubViewModel(
    private val repository: PetRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _petId = MutableStateFlow(0L)

    fun setPetId(petId: Long) {
        _petId.value = petId
    }

    val uiState: StateFlow<ToolsHubUiState> = _petId.flatMapLatest { pid ->
        if (pid == 0L) return@flatMapLatest flowOf(ToolsHubUiState())

        val todayStart = todayStartMillis()

        combine(
            repository.observeWaterLogs(pid),
            repository.observeHealthLogs(pid),
            repository.observeNotes(pid),
            repository.observeExpenses(pid),
        ) { waterLogs, healthLogs, notes, expenses ->
            val moodLogs = healthLogs.filter { it.category == HealthCategory.MOOD }
            val vomitLogs = healthLogs.filter { it.category == HealthCategory.VOMIT }

            ToolsHubUiState(
                waterLogs = waterLogs.take(10),
                todayWaterTotal = waterLogs.filter { it.timestamp >= todayStart }.sumOf { it.amountMl },
                moodLogs = moodLogs.take(10),
                latestMood = moodLogs.firstOrNull()?.note?.takeIf { it.isNotBlank() } ?: "—",
                vomitLogs = vomitLogs.take(10),
                vomitCountToday = vomitLogs.count { it.timestamp >= todayStart },
                notes = notes.take(20),
                expenses = expenses.take(15),
                todayExpenseTotal = expenses.filter { it.timestamp >= todayStart }.sumOf { it.amount },
                totalExpenses = expenses.sumOf { it.amount },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ToolsHubUiState())

    fun clearMessage() {
        _message.value = null
    }

    fun logWater(petId: Long, amountMl: Int) {
        viewModelScope.launch {
            repository.saveWaterLog(
                WaterLog(
                    id = 0,
                    petId = petId,
                    amountMl = amountMl,
                    timestamp = System.currentTimeMillis()
                )
            )
            _message.value = appContext.inAppLanguage().getString(R.string.ui_90ed3b2b7f, amountMl)
        }
    }

    fun logMood(petId: Long, moodEmoji: String, note: String) {
        viewModelScope.launch {
            repository.addHealthLog(
                petId = petId,
                category = HealthCategory.MOOD,
                value = 0.0,
                note = "$moodEmoji $note".trim()
            )
            _message.value = appContext.inAppLanguage().getString(R.string.ui_2b3fab2036, moodEmoji)
            // Sick/sad moods get a next-morning AI check-in.
            if (moodEmoji in SICK_MOODS) {
                scheduleFollowup(petId, "low mood ($moodEmoji)")
            }
        }
    }

    fun logVomit(petId: Long, note: String) {
        viewModelScope.launch {
            repository.addHealthLog(
                petId = petId,
                category = HealthCategory.VOMIT,
                value = null,
                note = note
            )
            _message.value = appContext.inAppLanguage().getString(R.string.ui_b0298cf6b2)
            scheduleFollowup(petId, "vomiting")
        }
    }

    fun saveNote(petId: Long, content: String, isTodo: Boolean) {
        viewModelScope.launch {
            repository.saveNote(
                Note(
                    id = 0,
                    petId = petId,
                    content = content,
                    isTodo = isTodo,
                    isCompleted = false,
                    timestamp = System.currentTimeMillis()
                )
            )
            _message.value = appContext.inAppLanguage().getString(
                if (isTodo) R.string.pet_todo_added else R.string.pet_note_saved,
            )
        }
    }

    fun toggleNoteCompleted(note: Note) {
        viewModelScope.launch {
            repository.updateNote(note.copy(isCompleted = !note.isCompleted))
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            repository.deleteNote(id)
            _message.value = appContext.inAppLanguage().getString(R.string.ui_788d82fb2f)
        }
    }

    fun saveExpense(petId: Long, amount: Double, category: String, note: String) {
        viewModelScope.launch {
            repository.saveExpense(
                Expense(
                    id = 0,
                    petId = petId,
                    amount = amount,
                    category = category,
                    note = note,
                    timestamp = System.currentTimeMillis()
                )
            )
            _message.value = appContext.inAppLanguage().getString(R.string.ui_cbcdb86a6a, "%.2f".format(amount))
        }
    }

    fun deleteWaterLog(id: Long) {
        viewModelScope.launch {
            repository.deleteWaterLog(id)
            _message.value = appContext.inAppLanguage().getString(R.string.ui_1f0416964e)
        }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch {
            repository.deleteExpense(id)
            _message.value = appContext.inAppLanguage().getString(R.string.ui_4bcf227234)
        }
    }

    private fun todayStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private suspend fun scheduleFollowup(petId: Long, eventLabel: String) {
        val ctx = appContext ?: return
        val petName = repository.getPet(petId)?.name?.takeIf { it.isNotBlank() } ?: "your pet"
        AiFollowupWorker.schedule(ctx, petId, petName, eventLabel)
    }

    companion object {
        private val SICK_MOODS = setOf("😢", "🤒", "😰")
    }
}
