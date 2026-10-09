package com.petcare.companionlite.data.ai

import com.petcare.companionlite.data.model.HealthCategory
import com.petcare.companionlite.data.repository.PetRepository
import kotlinx.coroutines.flow.first

// Builds the small pet snapshot sent with each AI request.
// Sends a 7-day summary + overdue items, never the whole database.
class AiContextBuilder(
    private val repository: PetRepository,
    private val settingsStore: com.petcare.companionlite.data.settings.SettingsStore,
) {
    suspend fun build(petId: Long): AiPetContext = runCatching {
        val pet = repository.getPet(petId)
        val settings = settingsStore.settings.first()
        val profileOnly = !settings.aiUseCareHistory

        val base = AiPetContext(
            name = if (settings.aiUseProfile) pet?.name else null,
            species = if (settings.aiUseProfile) pet?.type else null,
            breed = if (settings.aiUseProfile) pet?.breed?.takeIf { it.isNotBlank() } else null,
            ageYears = if (settings.aiUseProfile) pet?.ageYears?.takeIf { (it.toIntOrNull() ?: 0) > 0 } else null,
            ageMonths = if (settings.aiUseProfile) pet?.ageMonths() else null,
            weightKg = if (settings.aiUseProfile) pet?.weightKg?.takeIf { (it.toDoubleOrNull() ?: 0.0) > 0.0 } else null,
        )
        if (profileOnly) return@runCatching base

        val now = System.currentTimeMillis()
        val weekAgo = now - 7L * 24 * 60 * 60 * 1000

        val activities = if (settings.aiUseActivity) {
            repository.observeActivities(petId).first().filter { it.timestamp >= weekAgo }
        } else emptyList()
        val reminders = if (settings.aiUseReminders) repository.observeReminders(petId).first() else emptyList()
        val health = if (settings.aiUseHealth) {
            repository.observeHealthLogs(petId).first().filter { it.timestamp >= weekAgo }
        } else emptyList()
        val water = if (settings.aiUseActivity) {
            repository.observeWaterLogs(petId).first().filter { it.timestamp >= weekAgo }
        } else emptyList()

        val feedCount = activities.count { it.type.name == "FEED" }
        val walkCount = activities.count { it.type.name == "WALK" }
        val medicineCount = activities.count { it.type.name == "MEDICINE" }
        val waterTotal = water.sumOf { it.amountMl }

        val summary = buildString {
            if (settings.aiUseActivity) {
                append("Food: ${if (feedCount > 0) "logged $feedCount times" else "not logged"}; ")
                append("Walks: ${if (walkCount > 0) "logged $walkCount times" else "not logged"}; ")
                append("Medicine given: ${if (medicineCount > 0) "logged $medicineCount times" else "not logged"}; ")
                append("Water: ${if (water.isNotEmpty()) "${waterTotal}ml logged" else "not logged"}; ")
            }
            if (settings.aiUseHealth) {
                val moods = health.filter { it.category == HealthCategory.MOOD }.take(2)
                append("Mood: ${moods.joinToString { it.note }.ifBlank { "not logged" }}; ")
                val visits = health.filter { it.category == HealthCategory.VET_VISIT }.take(2)
                append("Vet visits: ${visits.joinToString { it.note }.ifBlank { "not logged" }}; ")
                val vomit = health.count { it.category == HealthCategory.VOMIT }
                val symptoms = health.filter { it.category == HealthCategory.SYMPTOM }
                append("Vomit events logged: $vomit; ")
                append("Symptoms: ${symptoms.take(3).joinToString { it.note.take(60) }.ifBlank { "not logged" }}; ")
                val weights = health.filter { it.category == HealthCategory.WEIGHT }
                    .sortedBy { it.timestamp }.mapNotNull { it.value }
                if (weights.size >= 2) append("Weight change over logged week: ${weights.last() - weights.first()}kg; ")
            }
            if (settings.aiUseReminders) append("; reminders: ${reminders.size}")
        }.take(800)

        val recentEvents = health.take(3).map {
            "${it.category.label}: ${it.note.take(60)}"
        }

        base.copy(
            summary7d = summary.takeIf { it.isNotBlank() },
            recentEvents = recentEvents,
        )
    }.getOrElse {
        AiPetContext()
    }
}
