package com.petcare.companionlite.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.petcare.companionlite.R
import com.petcare.companionlite.data.model.MoodState
import com.petcare.companionlite.data.model.PetDashboardItem
import com.petcare.companionlite.data.model.PetType
import com.petcare.companionlite.data.model.TaskType
import com.petcare.companionlite.data.model.TimelineItem
import com.petcare.companionlite.data.model.TimelineStatus
import com.petcare.companionlite.monetization.BillingManager
import com.petcare.companionlite.ui.components.AdBanner
import com.petcare.companionlite.ui.components.PetAvatar
import com.petcare.companionlite.ui.components.PetCard
import com.petcare.companionlite.ui.ai.AiPaywallSheet
import com.petcare.companionlite.ui.theme.Coral
import com.petcare.companionlite.ui.theme.DeepSage
import com.petcare.companionlite.ui.theme.Honey
import com.petcare.companionlite.ui.theme.PremiumGold
import com.petcare.companionlite.ui.theme.Sage
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    weeklyPriceText: String = "$2.99",
    monthlyPriceText: String = "$9.99",
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


    val totalDone    = state.pets.sumOf { it.completedCount }
    val totalPending = state.pets.sumOf { it.pendingCount + it.overdueCount }
    val maxStreak    = state.pets.maxOfOrNull { it.streakDays } ?: 0

    LaunchedEffect(state.showCelebration) {
        if (state.showCelebration) {
            delay(2000)
            onConsumeCelebration()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── 1. Header ─────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val activePetItem = state.pets.firstOrNull { it.pet.id == activePetId }
                        val hour = java.time.LocalTime.now().hour
                        val greeting = when (hour) {
                            in 5..11 -> stringResource(R.string.greeting_morning)
                            in 12..16 -> stringResource(R.string.greeting_afternoon)
                            in 17..20 -> stringResource(R.string.greeting_evening)
                            else -> stringResource(R.string.greeting_night)
                        }
                        Text(
                            stringResource(R.string.ui_adfbf75c4f, greeting),
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        val subtitle = when {
                            state.pets.size > 1 -> stringResource(R.string.ui_1cd0c04e4e)
                            activePetItem != null -> stringResource(R.string.pet_needs_you, activePetItem.pet.name)
                            else -> stringResource(R.string.ui_64ccc1309a)
                        }
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.ui_c7f73bb54d), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // ── 1.5 WEEKLY REPORT BAN─
            if (state.showReportBanner) {
                item {
                    WeeklyReportBanner(
                        onOpenReport = onViewWeeklyReport,
                        onDismiss = onDismissWeeklyReportBanner
                    )
                }
            }

            // ── 2. PET AVATAR ROW (horizontally scrollable switcher) ──
            if (state.pets.size > 1) {
                item {
                    PetAvatarRow(
                        pets = state.pets,
                        selectedPetId = activePetId,
                        onPetSelect = onSelectPet,
                        onPetDetailClick = onPetClick,
                    )
                }
            }

            // ── 3. FILTERED SINGLE PET SECTION ────────────────────────
            if (activePetId != null) {
                val activePetItem = state.pets.firstOrNull { it.pet.id == activePetId }
                if (activePetItem != null) {
                    item(key = activePetItem.pet.id) {
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
                // If there are absolutely no pets, show onboarding / landing state
                item {
                    FirstTimeLanding(onAddPet = onAddPet)
                }
            }

            // ── 3.5 AI COMPANION HERO ───────────────────────────────
            if (activePetId != null) {
                val heroPet = state.pets.firstOrNull { it.pet.id == activePetId }
                if (heroPet != null) {
                    item(key = "ai_hero_${heroPet.pet.id}") {
                        AiCompanionHeroCard(
                            petName = heroPet.pet.name,
                            onOpenAi = { onOpenAi(heroPet.pet.id) },
                        )
                    }
                }
            }

            // ── 4. GLOBAL STREAK PROGRESS ─────────────────────────────
            if (maxStreak > 0 && activePetId != null) {
                item { StreakProgressCard(maxStreak) }
            }

            // ── 4.5 STREAK RISK BANNER ────────────────────────────
            state.pets.filter { it.streakAtRisk }.forEach { atRiskPet ->
                item(key = "risk_${atRiskPet.pet.id}") {
                    StreakRiskBanner(petItem = atRiskPet, onOpenPet = { onPetClick(atRiskPet.pet.id) })
                }
            }

            // ── 5. ADD ANOTHER PET ────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    onClick = onAddPet
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
                                tint = Sage
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

            // ── 6. AI PRO UPSELL ───────────────────────────────────────
            item {
                AnimatedVisibility(visible = !state.settings.hasFullAccess) {
                    AiProUpgradeCard(onExplore = { showProPaywall = true })
                }
            }

            // ── 7. BANNER AD ──────────────────────────────────────────
            item {
                Spacer(Modifier.height(4.dp))
                AdBanner(enabled = !state.settings.hasNoAds)
            }

            // ── FOOTER ────────────────────────────────────────────────
            item {
                Text(
                    stringResource(R.string.not_vet_advice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
                )
            }
        }

        // ── CELEBRATION OVERLAY ───────────────────────────────────────
        com.petcare.companionlite.ui.components.CelebrationOverlay(
            visible = state.showCelebration,
            message = state.celebrationMessage,
            petName = state.celebrationPetName,
            streakDays = state.celebrationStreakDays,
            allDone = state.celebrationAllDone,
            onDismiss = onConsumeCelebration,
        )
        if (showProPaywall) {
            val petName = state.pets.firstOrNull { it.pet.id == activePetId }?.pet?.name ?: "your pet"
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

// ── PET AVATAR ROW (horizontally scrollable switcher with premium selection states) ──
