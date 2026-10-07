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
) {
    // ── ACTIVE SELECTION WITH STATE PERSISTENCE ──
    var selectedPetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showProPaywall by rememberSaveable { mutableStateOf(false) }

    val activePetId = remember(state.pets, selectedPetId) {
        val ids = state.pets.map { it.pet.id }
        if (selectedPetId == null || selectedPetId !in ids) {
            ids.firstOrNull()
        } else {
            selectedPetId
        }
    }

    LaunchedEffect(activePetId) {
        if (selectedPetId != activePetId) {
            selectedPetId = activePetId
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
                            "🐾 $greeting",
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        val subtitle = when {
                            state.pets.size > 1 -> "Manage care for your companions ❤️"
                            activePetItem != null -> stringResource(R.string.pet_needs_you, activePetItem.pet.name)
                            else -> "Your pet is waiting 🐾"
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
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
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
                        onPetSelect = { selectedPetId = it },
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
                                contentDescription = "Add pet",
                                modifier = Modifier.size(24.dp),
                                tint = Sage
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🐾 Add another companion",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "More love, more care ❤️",
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
@Composable
fun PetAvatarRow(
    pets: List<PetDashboardItem>,
    selectedPetId: Long?,
    onPetSelect: (Long) -> Unit,
    onPetDetailClick: (Long) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(pets, key = { it.pet.id }) { petItem ->
            val isSelected = petItem.pet.id == selectedPetId

            // Interactive scaling and border glow spring animations
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.08f else 1.0f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                label = "avatar-scale"
            )

            val borderBrush = if (isSelected) {
                Brush.horizontalGradient(listOf(Sage, Honey))
            } else {
                null
            }

            Column(
                modifier = Modifier
                    .scale(scale)
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (borderBrush != null) Modifier.border(2.dp, borderBrush, RoundedCornerShape(20.dp))
                        else Modifier
                    )
                    .clickable {
                        if (isSelected) {
                            onPetDetailClick(petItem.pet.id)
                        } else {
                            onPetSelect(petItem.pet.id)
                        }
                    }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                com.petcare.companionlite.ui.components.PetPhotoOrAvatar(
                    pet = petItem.pet,
                    mood = petItem.mood,
                    modifier = Modifier.size(56.dp),
                )
                Text(
                    petItem.pet.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
                // Compact status
                val statusText = when {
                    petItem.pendingCount + petItem.overdueCount > 0 ->
                        "${petItem.pendingCount + petItem.overdueCount} pending"
                    petItem.completedCount > 0 -> "All done ✅"
                    else -> "No tasks"
                }
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (petItem.overdueCount > 0) Coral
                        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// ── UNIFIED SINGLE PET SECTION ──
@Composable
fun PetSection(
    petItem: PetDashboardItem,
    onPetClick: () -> Unit,
    onLogQuickAction: (TaskType) -> Unit,
    onLogWater: () -> Unit,
    onOpenTools: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Today's Progress summary card (replaces TodayActionCard with clean graph metric)
        TodayProgressCard(petItem = petItem, onOpenPet = onPetClick)

        // Live Vitals status section (Feel Alive)
        val ateToday = petItem.timeline.any { it.reminder.type == TaskType.FEED && it.status == TimelineStatus.COMPLETED }
        val walkedToday = petItem.timeline.any { it.reminder.type == TaskType.WALK && it.status == TimelineStatus.COMPLETED }
        val medsToday = petItem.timeline.any { it.reminder.type == TaskType.MEDICINE && it.status == TimelineStatus.COMPLETED }

        LivePetStatusRow(
            mood = petItem.mood,
            weightKg = petItem.pet.weightKg,
            ateToday = ateToday,
            waterToday = petItem.hasWaterToday,
            walkedToday = walkedToday,
            medsToday = medsToday,
        )

        // Compact circular quick actions
        NewQuickActionRow(
            onLogAction = onLogQuickAction,
            onLogWater = onLogWater
        )

        // Next Care Hero Card (Surfaces single most urgent upcoming task)
        val nextCareTask = petItem.timeline.firstOrNull {
            it.status == TimelineStatus.PENDING ||
            it.status == TimelineStatus.OVERDUE ||
            it.status == TimelineStatus.MISSED
        }
        if (nextCareTask != null) {
            NextCareHeroCard(
                nextTask = nextCareTask,
                petName = petItem.pet.name,
                onMarkDone = { onLogQuickAction(nextCareTask.reminder.type) }
            )
        } else {
            // Completion / perfect day card
            if (petItem.timeline.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Sage.copy(alpha = 0.12f)),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("🎉", style = MaterialTheme.typography.titleLarge)
                        Column {
                            Text(
                                "All routines done!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Sage
                            )
                            Text(
                                "Enjoy your time with ${petItem.pet.name}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        // Checklist timeline for the day
        if (petItem.timeline.isNotEmpty()) {
            TodayTimelineSection(
                timeline = petItem.timeline,
                petName = petItem.pet.name,
                onLogTask = onLogQuickAction,
            )
        } else {
            // Empty state helper
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("🎉", style = MaterialTheme.typography.titleLarge)
                    Text("Nothing scheduled today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Enjoy your time with ${petItem.pet.name}.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Care Kit shortcut
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            onClick = onOpenTools,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "💊 ${petItem.pet.name}'s Care Kit",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        "Everything ${petItem.pet.name} needs in one place",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Handyman, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

// ── TODAY PROGRESS CARD (REPLACES TODAYACTIONCARD) ──
@Composable
fun TodayProgressCard(
    petItem: PetDashboardItem,
    onOpenPet: () -> Unit,
) {
    val total = petItem.timeline.size
    val completed = petItem.completedCount
    val progress = if (total == 0) 0f else completed.toFloat() / total

    val nextItem = petItem.timeline.firstOrNull {
        it.status == TimelineStatus.PENDING ||
        it.status == TimelineStatus.OVERDUE ||
        it.status == TimelineStatus.MISSED
    }
    val nextReminderInfo = nextItem?.let {
        val timeLabel = Instant.ofEpochMilli(it.scheduledAt)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("h:mm a"))
        "Next: ${it.reminder.type.label} at $timeLabel"
    } ?: "All done today! 🎉"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        onClick = onOpenPet
    ) {
        Box(
            modifier = Modifier
                .background(Brush.horizontalGradient(listOf(DeepSage, Sage)))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            petItem.emotionalTaskPrompt.ifBlank { "${petItem.pet.name} needs you" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            if (total == 0) "No routine set" else "$completed of $total completed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    if (petItem.streakDays > 0) {
                        StreakBadge(days = petItem.streakDays)
                    }
                }

                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        strokeCap = StrokeCap.Round
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        nextReminderInfo,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        "Details →",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ── LIVE VITALS STATUS ROW ──
@Composable
fun LivePetStatusRow(
    mood: MoodState,
    weightKg: String,
    ateToday: Boolean,
    waterToday: Boolean,
    walkedToday: Boolean,
    medsToday: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val moodText = when (mood) {
            MoodState.HAPPY -> "😊 Happy"
            MoodState.NEUTRAL -> "😐 Neutral"
            MoodState.SAD -> "🥺 Sad"
        }
        val weightText = if (weightKg.isBlank() || weightKg == "0" || weightKg == "0.0") "⚖️ Stable" else "⚖️ ${weightKg}kg"

        StatusChip(moodText, MaterialTheme.colorScheme.secondary)
        StatusChip(weightText, Sage)
        StatusChip("🥣 Food " + if (ateToday) "✔" else "○", if (ateToday) Sage else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        StatusChip("💧 Water " + if (waterToday) "✔" else "○", if (waterToday) Color(0xFF4FC3F7) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        StatusChip("🚶 Walk " + if (walkedToday) "✔" else "○", if (walkedToday) Sage else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        StatusChip("💊 Meds " + if (medsToday) "✔" else "○", if (medsToday) Sage else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
    }
}

@Composable
fun StatusChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// ── PREMIUM CIRCULAR QUICK LOG TRAY ──
@Composable
fun NewQuickActionRow(
    onLogAction: (TaskType) -> Unit,
    onLogWater: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuickActionCircleButton(
            icon = "🥣",
            label = "Feed",
            accentColor = Sage,
            onClick = { onLogAction(TaskType.FEED) }
        )
        QuickActionCircleButton(
            icon = "💧",
            label = "Water",
            accentColor = Color(0xFF4FC3F7),
            onClick = onLogWater
        )
        QuickActionCircleButton(
            icon = "🚶",
            label = "Walk",
            accentColor = Sage,
            onClick = { onLogAction(TaskType.WALK) }
        )
        QuickActionCircleButton(
            icon = "💊",
            label = "Med",
            accentColor = Sage,
            onClick = { onLogAction(TaskType.MEDICINE) }
        )
    }
}

@Composable
fun QuickActionCircleButton(
    icon: String,
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1.0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "button-scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    ) {
        Box(
            modifier = Modifier
                .scale(scale)
                .size(54.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.15f))
                .border(1.dp, accentColor.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, style = MaterialTheme.typography.titleLarge)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )
    }
}

// ── NEXT CARE HERO CARD ──
@Composable
fun NextCareHeroCard(
    nextTask: TimelineItem,
    petName: String,
    onMarkDone: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val emoji = when (nextTask.reminder.type) {
                TaskType.FEED -> "🥣"
                TaskType.WALK -> "🚶"
                TaskType.MEDICINE -> "💊"
                TaskType.GROOMING -> "✂️"
            }
            val timeLabel = Instant.ofEpochMilli(nextTask.scheduledAt)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("h:mm a"))

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, style = MaterialTheme.typography.titleLarge)
                }
                Column {
                    val statusPrefix = when (nextTask.status) {
                        TimelineStatus.OVERDUE -> "Overdue: "
                        TimelineStatus.MISSED -> "Missed: "
                        else -> "Upcoming: "
                    }
                    Text(
                        statusPrefix + nextTask.reminder.type.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (nextTask.status != TimelineStatus.PENDING) Coral else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "$petName · Today at $timeLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            Button(
                onClick = onMarkDone,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("Mark Done", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ── STREAK BADGE ──────────────────────────────────────────────────
@Composable
fun StreakBadge(days: Int) {
    val (emoji, label) = when {
        days >= 30 -> "⭐" to "Champion"
        days >= 7  -> "🔥" to "$days days"
        days >= 3  -> "🟢" to "$days days"
        else       -> "🐾" to "$days day${if (days > 1) "s" else ""}"
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold, color = Color.White)
    }
}

// ── PROGRESS TO NEXT BADGE ────────────────────────────────────────
@Composable
fun StreakProgressCard(currentStreak: Int) {
    val (nextTarget, nextEmoji, nextLabel) = when {
        currentStreak < 3  -> Triple(3,  "🟢", "3-day habit")
        currentStreak < 7  -> Triple(7,  "🔥", "7-day streak")
        currentStreak < 30 -> Triple(30, "⭐", "30-day champion")
        else               -> Triple(currentStreak, "⭐", "Legendary!")
    }
    val progress = if (nextTarget <= currentStreak) 1f
    else currentStreak.toFloat() / nextTarget.toFloat()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Progress to next badge",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "$nextEmoji $nextLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                strokeCap = StrokeCap.Round
            )
            Text(
                if (progress >= 1f) "You reached $nextLabel! 🎉"
                else "$currentStreak / $nextTarget days — ${nextTarget - currentStreak} more to go!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
            )
        }
    }
}

// ── TODAY SUMMARY ROW ─────────────────────────────────────────────
@Composable
fun TodaySummaryRow(totalDone: Int, totalPending: Int, maxStreak: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SummaryChip("✅", "Done",    "$totalDone",       Modifier.weight(1f))
        SummaryChip("⏰", "Pending", "$totalPending",    Modifier.weight(1f))
        SummaryChip("🔥", "Streak",  "$maxStreak days",  Modifier.weight(1f))
    }
}

@Composable
fun SummaryChip(emoji: String, label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

// ── SECOND SESSION TRIGGER ────────────────────────────────────────
@Composable
fun SecondSessionTrigger(totalPending: Int, onComplete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Sage.copy(alpha = 0.18f)
        ),
        shape = RoundedCornerShape(16.dp),
        onClick = onComplete
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, Sage.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🎯", style = MaterialTheme.typography.titleMedium)
                Column {
                    Text(
                        "Almost there! $totalPending more to go",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text("A perfect day is just one tap away ❤️",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                }
            }
            Text("→", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ── AI COMPANION HERO ───────────────────────────────────────────
@Composable
fun AiCompanionHeroCard(petName: String, onOpenAi: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenAi,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.ask_ai_about, petName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    stringResource(R.string.ask_ai_subtitle, petName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
fun AiProUpgradeCard(onExplore: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PremiumGold.copy(alpha = 0.16f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        onClick = onExplore,
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = PremiumGold, modifier = Modifier.size(22.dp))
                Text(
                    stringResource(R.string.upgrade_to_pro),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                stringResource(R.string.pro_card_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
            Button(
                onClick = onExplore,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.explore_pro), fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── CONTEXTUAL PREMIUM CARD ───────────────────────────────────────
@Composable
fun ContextualPremiumCard(
    state: DashboardUiState,
    maxStreak: Int,
    onBuyPremium: () -> Unit,
    onBuyRemoveAds: () -> Unit
) {
    val headline = when {
        maxStreak >= 7  -> "You're on a 🔥 roll! Unlock deeper insights with Premium"
        maxStreak >= 3  -> "Building a habit! 🟢 Premium unlocks advanced stats"
        state.pets.size >= 2 -> "Love your pets? 🐾 Premium gives unlimited pets + PDF export"
        else -> "Upgrade your care toolkit"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null,
                    tint = Honey, modifier = Modifier.size(22.dp))
                Text(headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(
                "Free plan: 2 pets & 3 reminders each. Premium unlocks unlimited pets, advanced stats, PDF export, and no ads.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
            )
            if (state.billing.isLoading) {
                Text("Loading…", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f))
            } else {
                val premium   = state.billing.products.firstOrNull { it.id == BillingManager.premiumProductId }
                val removeAds = state.billing.products.firstOrNull { it.id == BillingManager.removeAdsProductId }
                if (!state.settings.hasFullAccess) {
                    Button(
                        onClick = onBuyPremium,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(premium?.let { "Unlock Premium — ${it.priceText}" } ?: "Unlock Premium",
                        fontWeight = FontWeight.Bold) }
                }
                if (!state.settings.hasNoAds) {
                    OutlinedButton(
                        onClick = onBuyRemoveAds,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(removeAds?.let { "Remove Ads — ${it.priceText}" } ?: "Remove Ads") }
                }
            }
        }
    }
}

// ── FIRST TIME LANDING ────────────────────────────────────────────
@Composable
fun FirstTimeLanding(onAddPet: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(28.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PetAvatar(petType = PetType.DOG, mood = MoodState.HAPPY, modifier = Modifier.size(120.dp))
            Text(
                "Your pet is waiting 🐾",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Add your first pet and build a daily care streak. Feed, walk, and medicine — tracked with love.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
            Button(
                onClick = onAddPet,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Add your first pet 🐾",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
            }
            Text(
                "Takes 30 seconds · 100% offline · No account needed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun WeeklyReportBanner(onOpenReport: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSage),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🐾", style = MaterialTheme.typography.displaySmall)
                    Column {
                        Text(
                            "Your weekly report is ready",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "See how you did this week 💚",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Close, "Dismiss", tint = Color.White.copy(alpha = 0.6f))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onOpenReport,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = DeepSage),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Now", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── STREAK RISK BANNER (fear of loss — #1 retention driver) ──────
@Composable
fun StreakRiskBanner(petItem: PetDashboardItem, onOpenPet: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "risk-pulse").animateFloat(
        initialValue = 0.85f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Coral.copy(alpha = 0.12f)),
        onClick = onOpenPet
    ) {
        Row(
            modifier = Modifier
                .border(1.5.dp, Coral.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚠️", style = MaterialTheme.typography.headlineSmall)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${petItem.pet.name}'s \uD83D\uDD25${petItem.streakDays}-day streak is fading!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Coral
                )
                Text(
                    "One quick task keeps the love going ❤️",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
            Button(
                onClick = onOpenPet,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Coral),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) { Text("Save it!", fontWeight = FontWeight.Bold, color = Color.White) }
        }
    }
}
