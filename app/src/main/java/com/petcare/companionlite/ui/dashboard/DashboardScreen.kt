package com.petcare.companionlite.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.petcare.companionlite.R
import com.petcare.companionlite.data.model.TaskType
import com.petcare.companionlite.ui.ai.AiPaywallSheet
import com.petcare.companionlite.ui.components.AdBanner
import com.petcare.companionlite.ui.components.BobbingEmoji
import com.petcare.companionlite.ui.components.CelebrationOverlay
import com.petcare.companionlite.ui.components.bouncyPress
import com.petcare.companionlite.ui.components.popIn
import com.petcare.companionlite.ui.theme.Sage
import kotlinx.coroutines.delay
import java.time.LocalTime

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    notificationsEnabled: Boolean,
    onPetClick: (Long) -> Unit,
    onAddPet: () -> Unit,
    onEnableNotifications: () -> Unit,
    onBuyPremium: () -> Unit,
    onBuyRemoveAds: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTools: (Long) -> Unit,
    onOpenAi: (Long) -> Unit = {},
    onBuyWeekly: () -> Unit = {},
    onBuyMonthly: () -> Unit = {},
    onRestorePurchases: () -> Unit = {},
    weeklyPriceText: String = "",
    monthlyPriceText: String = "",
    weeklyReady: Boolean = false,
    monthlyReady: Boolean = false,
    billingLoading: Boolean = false,
    billingMessage: String? = null,
    onViewWeeklyReport: () -> Unit,
    onDismissWeeklyReportBanner: () -> Unit,
    onLogQuickAction: (petId: Long, taskType: TaskType) -> Unit = { _, _ -> },
    onConsumeCelebration: () -> Unit = {},
    onLogWaterQuickAction: (petId: Long) -> Unit = {},
    onSelectPet: (Long) -> Unit = {},
) {
    // ── ACTIVE SELECTION WITH STATE PERSISTENCE ──
    val selectedPetId = state.settings.selectedPetId
    var showProPaywall by rememberSaveable { mutableStateOf(false) }

    val activePetId = remember(state.pets, selectedPetId) {
        val ids = state.pets.map { it.pet.id }
        if (selectedPetId == null || selectedPetId !in ids) {
            ids.firstOrNull()
        } else {
            selectedPetId
        }
    }
    val activePetItem = state.pets.firstOrNull { it.pet.id == activePetId }
    val maxStreak = state.pets.maxOfOrNull { it.streakDays } ?: 0

    LaunchedEffect(state.showCelebration) {
        if (state.showCelebration) {
            delay(2000)
            onConsumeCelebration()
        }
    }

    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> stringResource(R.string.greeting_morning)
        in 12..16 -> stringResource(R.string.greeting_afternoon)
        in 17..20 -> stringResource(R.string.greeting_evening)
        else -> stringResource(R.string.greeting_night)
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // The bottom padding keeps the last card clear of the global AI button.
            contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── 1. Header: greeting with a bobbing paw; the settings button keeps its own space ──
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth().popIn(0),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BobbingEmoji(emoji = "🐾", size = 40.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            greeting,
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        val subtitle = when {
                            state.pets.size > 1 -> stringResource(R.string.ui_1cd0c04e4e)
                            activePetItem != null -> stringResource(R.string.pet_needs_you, activePetItem.pet.name)
                            else -> stringResource(R.string.ui_64ccc1309a)
                        }
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.ui_c7f73bb54d),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // ── 1.5 Weekly report banner ──
            if (state.showReportBanner) {
                item(key = "report_banner") {
                    Box(modifier = Modifier.fillMaxWidth().popIn(1)) {
                        WeeklyReportBanner(
                            onOpenReport = onViewWeeklyReport,
                            onDismiss = onDismissWeeklyReportBanner,
                        )
                    }
                }
            }

            // ── 2. Pet switcher (only with several pets) ──
            if (state.pets.size > 1) {
                item(key = "pet_switcher") {
                    Box(modifier = Modifier.fillMaxWidth().popIn(1)) {
                        PetAvatarRow(
                            pets = state.pets,
                            selectedPetId = activePetId,
                            onPetSelect = onSelectPet,
                            onPetDetailClick = onPetClick,
                        )
                    }
                }
            }

            // ── 3. Active pet: hero, vitals, quick actions, next task, timeline, care kit ──
            if (activePetItem != null) {
                item(key = "pet_${activePetItem.pet.id}") {
                    Box(modifier = Modifier.fillMaxWidth().popIn(2)) {
                        PetSection(
                            petItem = activePetItem,
                            onPetClick = { onPetClick(activePetItem.pet.id) },
                            onLogQuickAction = { taskType -> onLogQuickAction(activePetItem.pet.id, taskType) },
                            onLogWater = { onLogWaterQuickAction(activePetItem.pet.id) },
                            onOpenTools = { onOpenTools(activePetItem.pet.id) },
                        )
                    }
                }
            } else {
                // No pets yet: onboarding / landing state
                item(key = "first_time") {
                    Box(modifier = Modifier.fillMaxWidth().popIn(2)) {
                        FirstTimeLanding(onAddPet = onAddPet)
                    }
                }
            }

            // ── 3.1 Banner ad: right after the main timeline, framed, 16dp away from any button ──
            if (!state.settings.hasNoAds) {
                item(key = "dash_ad") {
                    AdBanner(enabled = !state.settings.hasNoAds, framed = true)
                }
            }

            // ── 3.5 AI companion ──
            if (activePetItem != null) {
                item(key = "ai_hero_${activePetItem.pet.id}") {
                    Box(modifier = Modifier.fillMaxWidth().popIn(3)) {
                        AiCompanionHeroCard(
                            petName = activePetItem.pet.name,
                            onOpenAi = { onOpenAi(activePetItem.pet.id) },
                        )
                    }
                }
            }

            // ── 4. Streak progress ──
            if (maxStreak > 0 && activePetItem != null) {
                item(key = "streak_progress") {
                    Box(modifier = Modifier.fillMaxWidth().popIn(4)) {
                        StreakProgressCard(maxStreak)
                    }
                }
            }

            // ── 4.5 Streak risk banners ──
            state.pets.filter { it.streakAtRisk }.forEach { atRiskPet ->
                item(key = "risk_${atRiskPet.pet.id}") {
                    Box(modifier = Modifier.fillMaxWidth().popIn(5)) {
                        StreakRiskBanner(petItem = atRiskPet, onOpenPet = { onPetClick(atRiskPet.pet.id) })
                    }
                }
            }

            // ── 5. Add another pet ──
            item(key = "add_pet") {
                val addInteraction = remember { MutableInteractionSource() }
                Box(modifier = Modifier.fillMaxWidth().popIn(6)) {
                    Card(
                        modifier = Modifier.fillMaxWidth().bouncyPress(addInteraction),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        onClick = onAddPet,
                        interactionSource = addInteraction,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Sage.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = stringResource(R.string.ui_e9a3389f10),
                                    modifier = Modifier.size(24.dp),
                                    tint = Sage,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.ui_4a60573885),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    stringResource(R.string.ui_eb783a1851),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            // ── 6. AI Pro upsell ──
            item(key = "pro_upsell") {
                Box(modifier = Modifier.fillMaxWidth().popIn(7)) {
                    AnimatedVisibility(visible = !state.settings.hasFullAccess) {
                        AiProUpgradeCard(onExplore = { showProPaywall = true })
                    }
                }
            }

            // ── FOOTER ──
            item(key = "footer") {
                Text(
                    stringResource(R.string.not_vet_advice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
                )
            }
        }

        // ── CELEBRATION OVERLAY ──
        CelebrationOverlay(
            visible = state.showCelebration,
            message = state.celebrationMessage,
            petName = state.celebrationPetName,
            streakDays = state.celebrationStreakDays,
            allDone = state.celebrationAllDone,
            onDismiss = onConsumeCelebration,
        )
        if (showProPaywall) {
            val petName = activePetItem?.pet?.name ?: stringResource(R.string.dash_pet_fallback)
            AiPaywallSheet(
                quota = null,
                petName = petName,
                weeklyPriceText = weeklyPriceText,
                monthlyPriceText = monthlyPriceText,
                onDismiss = { showProPaywall = false },
                onBuyWeekly = onBuyWeekly,
                onBuyMonthly = onBuyMonthly,
                onRestore = onRestorePurchases,
                weeklyReady = weeklyReady,
                monthlyReady = monthlyReady,
                billingLoading = billingLoading,
                billingMessage = billingMessage,
            )
        }
    }
}
