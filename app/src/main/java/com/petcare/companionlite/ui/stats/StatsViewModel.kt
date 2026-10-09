package com.petcare.companionlite.ui.stats

import android.app.Application
import com.petcare.companionlite.core.inAppLanguage
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.petcare.companionlite.PetCareApplication
import com.petcare.companionlite.data.model.PetActivity
import com.petcare.companionlite.data.model.PetProfile
import com.petcare.companionlite.data.model.SettingsState
import com.petcare.companionlite.data.model.TaskType
import com.petcare.companionlite.data.model.WeightPoint
import com.petcare.companionlite.data.model.countDailyCareStreak
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TaskFrequency(
    val type: TaskType,
    val count: Int,
)

data class StatsUiState(
    val pet: PetProfile? = null,
    val points: List<WeightPoint> = emptyList(),
    val alert: String? = null,
    val streakDays: Int = 0,
    val totalActivities: Int = 0,
    val taskFrequencies: List<TaskFrequency> = emptyList(),
    val settings: SettingsState = SettingsState(),
)

class StatsViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val petId: Long = checkNotNull(savedStateHandle["petId"]).toString().toLong()
    private val app = application as PetCareApplication
    private val repository = app.container.repository

    val uiState: StateFlow<StatsUiState> = combine(
        repository.observePet(petId),
        repository.observeWeightPoints(petId),
        repository.observeActivities(petId),
        repository.settings,
    ) { pet, points, activities, settings ->
        StatsUiState(
            pet = pet,
            points = points,
            alert = rapidChangeAlert(points),
            streakDays = countDailyCareStreak(activities),
            totalActivities = activities.size,
            taskFrequencies = computeFrequencies(activities),
            settings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    private fun rapidChangeAlert(points: List<WeightPoint>): String? {
        if (points.size < 2) return null
        val first = points.first().value
        val last = points.last().value
        if (first == 0.0) return null
        val change = ((last - first) / first) * 100
        return if (kotlin.math.abs(change) >= 10) {
            app.inAppLanguage().getString(com.petcare.companionlite.R.string.weight_change_label, "%.1f".format(change))
        } else {
            null
        }
    }

    private fun computeFrequencies(activities: List<PetActivity>): List<TaskFrequency> {
        return TaskType.entries.map { type ->
            TaskFrequency(type, activities.count { it.type == type })
        }.filter { it.count > 0 }
    }
}
