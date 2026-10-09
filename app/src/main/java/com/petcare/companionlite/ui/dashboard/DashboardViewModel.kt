package com.petcare.companionlite.ui.dashboard

import com.petcare.companionlite.core.inAppLanguage

import com.petcare.companionlite.R

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.petcare.companionlite.PetCareApplication
import com.petcare.companionlite.data.model.PetDashboardItem
import com.petcare.companionlite.data.model.SettingsState
import com.petcare.companionlite.data.model.TaskType
import com.petcare.companionlite.monetization.BillingUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val pets: List<PetDashboardItem> = emptyList(),
    val settings: SettingsState = SettingsState(),
    val billing: BillingUiState = BillingUiState(),
    val settingsLoaded: Boolean = false,
    val hasUnreadWeeklyReport: Boolean = false,
    val showReportBanner: Boolean = false,
    val navigateToWeeklyReport: Boolean = false,
    val showCelebration: Boolean = false,
    val celebrationMessage: String = "",
    val celebrationPetName: String = "",
    val celebrationStreakDays: Int = 0,
    val celebrationAllDone: Boolean = false,
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PetCareApplication
    private val repository = app.container.repository
    private val settingsStore = app.container.settingsStore
    private val billingManager = app.container.billingManager

    private val _navigateToWeeklyReport = kotlinx.coroutines.flow.MutableStateFlow(false)

    data class CelebrationData(
        val show: Boolean = false,
        val message: String = "",
        val petName: String = "",
        val streakDays: Int = 0,
        val allDone: Boolean = false,
    )

    private val _celebrationState = kotlinx.coroutines.flow.MutableStateFlow(CelebrationData())
    val celebrationState: StateFlow<CelebrationData> = _celebrationState

    val uiState: StateFlow<DashboardUiState> = combine(
        combine(
            repository.observeDashboard(),
            repository.settings,
            billingManager.uiState,
            ::Triple
        ),
        combine(
            settingsStore.hasUnreadWeeklyReport,
            settingsStore.lastBannerShownTimestamp,
            _navigateToWeeklyReport,
            ::Triple
        ),
        _celebrationState
    ) { (pets, settings, billing), (hasUnread, lastBannerTime, navigateToReport), celebration ->
        // Limit banner frequency (don't show if already shown today)
        val nowMillis = System.currentTimeMillis()
        val shownToday = (nowMillis - lastBannerTime) < 24 * 60 * 60 * 1000L
        val showBanner = hasUnread && !shownToday

        DashboardUiState(
            pets = pets,
            settings = settings,
            billing = billing,
            settingsLoaded = true,
            hasUnreadWeeklyReport = hasUnread,
            showReportBanner = showBanner,
            navigateToWeeklyReport = navigateToReport,
            showCelebration = celebration.show,
            celebrationMessage = celebration.message,
            celebrationPetName = celebration.petName,
            celebrationStreakDays = celebration.streakDays,
            celebrationAllDone = celebration.allDone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun handleDeepLinkWeeklyReport() {
        _navigateToWeeklyReport.value = true
    }

    fun markDeepLinkConsumed() {
        _navigateToWeeklyReport.value = false
        markReportRead()
    }

    fun markReportRead() {
        viewModelScope.launch {
            settingsStore.setUnreadWeeklyReport(false)
        }
    }

    fun markBannerShown() {
        viewModelScope.launch {
            settingsStore.setLastBannerShownTimestamp(System.currentTimeMillis())
        }
    }

    fun startBilling() {
        billingManager.startConnection()
    }

    fun refreshBilling() {
        billingManager.refreshPurchases()
    }

    fun restorePurchases() {
        billingManager.restorePurchases()
    }

    fun consumeBillingMessage() {
        billingManager.consumeMessage()
    }

    fun markNotificationPromptShown() {
        viewModelScope.launch {
            repository.setNotificationPromptShown(true)
        }
    }

    fun logQuickAction(petId: Long, taskType: TaskType) {
        viewModelScope.launch {
            val result = repository.logActivity(petId, taskType)
            if (!result.duplicateDetected) {
                val pet = uiState.value.pets.firstOrNull { it.pet.id == petId }
                val petName = pet?.pet?.name ?: "Your pet"
                val newStreak = (pet?.streakDays ?: 0)
                app.container.diagnostics.event("care_logged",taskType.name)
                if (newStreak >= 7) app.container.reviewPrompter.positiveMoment()
                val pendingAfter = (pet?.pendingCount ?: 1) + (pet?.overdueCount ?: 0) - 1
                _celebrationState.value = CelebrationData(
                    show = true,
                    message = app.inAppLanguage().getString(R.string.ui_63e1851b37, petName),
                    petName = petName,
                    streakDays = newStreak,
                    allDone = pendingAfter <= 0,
                )
            }
        }
    }

    fun logWaterQuickAction(petId: Long) {
        viewModelScope.launch {
            repository.saveWaterLog(
                com.petcare.companionlite.data.model.WaterLog(
                    id = 0,
                    petId = petId,
                    amountMl = 250,
                    timestamp = System.currentTimeMillis()
                )
            )
            val pet = uiState.value.pets.firstOrNull { it.pet.id == petId }
            val petName = pet?.pet?.name ?: "Your pet"
            _celebrationState.value = CelebrationData(
                show = true,
                message = app.inAppLanguage().getString(R.string.ui_70ec4a9dff, petName),
                petName = petName,
                streakDays = pet?.streakDays ?: 0,
                allDone = false,
            )
        }
    }

    fun consumeCelebration() {
        _celebrationState.value = CelebrationData()
    }

}
