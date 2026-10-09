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
                    petItem.completedCount > 0 -> stringResource(R.string.ui_f935d78685)
                    else -> stringResource(R.string.ui_e542695cd3)
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
                                stringResource(R.string.ui_3d910950ed),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Sage
                            )
                            Text(
                                stringResource(R.string.ui_ba54c5d391, petItem.pet.name),
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
                    Text(stringResource(R.string.ui_50b5d5881d), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.ui_ba54c5d391, petItem.pet.name),
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
                        stringResource(R.string.ui_e0825da78d, petItem.pet.name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        stringResource(R.string.ui_100d7009d1, petItem.pet.name),
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
    val nextReminderInfo = petItem.nextReminderText


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
                            petItem.emotionalTaskPrompt.ifBlank { stringResource(R.string.ui_6b7694feec, petItem.pet.name) },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            if (total == 0) stringResource(R.string.ui_a448f093e1) else stringResource(R.string.ui_b244012464, completed, total),
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
                        stringResource(R.string.ui_22fafc39e6),
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
        val moodText = stringResource(when (mood) {
            MoodState.HAPPY -> R.string.tasks_complete
            MoodState.NEUTRAL -> R.string.tasks_pending
            MoodState.SAD -> R.string.tasks_overdue
        })
        val weightText = com.petcare.companionlite.ui.components.weightLabel(weightKg)

        StatusChip(moodText, MaterialTheme.colorScheme.secondary)
        StatusChip(weightText, Sage)
        StatusChip(stringResource(R.string.ui_e35ff979bb) + if (ateToday) "✔" else "○", if (ateToday) Sage else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        StatusChip(stringResource(R.string.ui_d0c2eac900) + if (waterToday) "✔" else "○", if (waterToday) Color(0xFF4FC3F7) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        StatusChip(stringResource(R.string.ui_167d73fe66) + if (walkedToday) "✔" else "○", if (walkedToday) Sage else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        StatusChip(stringResource(R.string.ui_f1001de751) + if (medsToday) "✔" else "○", if (medsToday) Sage else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
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
            label = stringResource(R.string.ui_ff1928e5d2),
            accentColor = Sage,
            onClick = { onLogAction(TaskType.FEED) }
        )
        QuickActionCircleButton(
            icon = "💧",
            label = stringResource(R.string.ui_de9b1be49d),
            accentColor = Color(0xFF4FC3F7),
            onClick = onLogWater
        )
        QuickActionCircleButton(
            icon = "🚶",
            label = stringResource(R.string.ui_e0c705d18e),
            accentColor = Sage,
            onClick = { onLogAction(TaskType.WALK) }
        )
        QuickActionCircleButton(
            icon = "💊",
            label = stringResource(R.string.ui_97a3b8d4d3),
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
