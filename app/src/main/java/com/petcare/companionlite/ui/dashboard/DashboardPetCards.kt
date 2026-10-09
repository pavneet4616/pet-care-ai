package com.petcare.companionlite.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.petcare.companionlite.R
import com.petcare.companionlite.data.model.MoodState
import com.petcare.companionlite.data.model.PetDashboardItem
import com.petcare.companionlite.data.model.TaskType
import com.petcare.companionlite.data.model.TimelineStatus
import com.petcare.companionlite.ui.components.PawBurst
import com.petcare.companionlite.ui.components.PetPhotoOrAvatar
import com.petcare.companionlite.ui.components.bouncyPress
import com.petcare.companionlite.ui.components.taskActionLabel
import com.petcare.companionlite.ui.components.weightLabel
import com.petcare.companionlite.ui.components.wiggleOn
import com.petcare.companionlite.ui.theme.Coral
import com.petcare.companionlite.ui.theme.DeepSage
import com.petcare.companionlite.ui.theme.Honey
import com.petcare.companionlite.ui.theme.Sage
import kotlinx.coroutines.delay

@Composable
fun PetAvatarRow(
    pets: List<PetDashboardItem>,
    selectedPetId: Long?,
    onPetSelect: (Long) -> Unit,
    onPetDetailClick: (Long) -> Unit,
) {
    LazyRow(
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
            val pendingTotal = petItem.pendingCount + petItem.overdueCount

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
                PetPhotoOrAvatar(
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
                    pendingTotal > 0 -> pluralStringResource(R.plurals.dash_pending_count, pendingTotal, pendingTotal)
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
    val emptyInteraction = remember { MutableInteractionSource() }
    val kitInteraction = remember { MutableInteractionSource() }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Hero: today's progress with an animated bar and a celebration at 100%
        TodayProgressCard(petItem = petItem, onOpenPet = onPetClick)

        // Live status chips
        val ateToday = petItem.timeline.any { it.reminder.type == TaskType.FEED && it.status == TimelineStatus.COMPLETED }
        val walkedToday = petItem.timeline.any { it.reminder.type == TaskType.WALK && it.status == TimelineStatus.COMPLETED }
        val medsToday = petItem.timeline.any { it.reminder.type == TaskType.MEDICINE && it.status == TimelineStatus.COMPLETED }

        LivePetStatusRow(
            mood = petItem.mood,
            statusText = dayStatusText(petItem),
            weightKg = petItem.pet.weightKg,
            ateToday = ateToday,
            waterToday = petItem.hasWaterToday,
            walkedToday = walkedToday,
            medsToday = medsToday,
        )

        // Quick actions with immediate feedback
        NewQuickActionRow(
            onLogAction = onLogQuickAction,
            onLogWater = onLogWater,
        )

        // Next care hero card: the single most urgent open task
        val nextCareTask = petItem.timeline.firstOrNull {
            it.status == TimelineStatus.PENDING ||
                it.status == TimelineStatus.OVERDUE ||
                it.status == TimelineStatus.MISSED
        }
        if (nextCareTask != null) {
            NextCareHeroCard(
                nextTask = nextCareTask,
                petName = petItem.pet.name,
                onMarkDone = { onLogQuickAction(nextCareTask.reminder.type) },
            )
        } else if (petItem.timeline.isNotEmpty()) {
            // Every task for today is done
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

        // Checklist timeline for the day
        if (petItem.timeline.isNotEmpty()) {
            TodayTimelineSection(
                timeline = petItem.timeline,
                petName = petItem.pet.name,
                onLogTask = onLogQuickAction,
            )
        } else {
            // Nothing due today. Without any routine, the card explains how to set one up.
            val noRoutine = petItem.reminderCount == 0
            Card(
                modifier = Modifier.fillMaxWidth().bouncyPress(emptyInteraction),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                onClick = onPetClick,
                interactionSource = emptyInteraction,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (noRoutine) {
                        Text("🐾", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        if (noRoutine) stringResource(R.string.dash_status_no_routine)
                        else stringResource(R.string.dash_status_nothing_due),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        if (noRoutine) stringResource(R.string.dash_empty_routine_body, petItem.pet.name)
                        else stringResource(R.string.ui_ba54c5d391, petItem.pet.name),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Care Kit shortcut
        Card(
            modifier = Modifier.fillMaxWidth().bouncyPress(kitInteraction),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            onClick = onOpenTools,
            interactionSource = kitInteraction,
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

/**
 * The single source of truth for the day's status. The hero subtitle and the status chip both use it,
 * so they can never contradict each other.
 */
@Composable
fun dayStatusText(petItem: PetDashboardItem): String {
    val remaining = petItem.pendingCount + petItem.overdueCount
    return when {
        petItem.reminderCount == 0 -> stringResource(R.string.dash_status_no_routine)
        petItem.timeline.isEmpty() -> stringResource(R.string.dash_status_nothing_due)
        remaining == 0 -> stringResource(R.string.dash_status_all_done)
        petItem.overdueCount > 0 -> pluralStringResource(R.plurals.dash_status_overdue, petItem.overdueCount, petItem.overdueCount)
        else -> pluralStringResource(R.plurals.dash_status_to_go, petItem.pendingCount, petItem.pendingCount)
    }
}

// ── TODAY PROGRESS CARD (hero) ──
@Composable
fun TodayProgressCard(
    petItem: PetDashboardItem,
    onOpenPet: () -> Unit,
) {
    val total = petItem.timeline.size
    val completed = petItem.completedCount
    val targetProgress = if (total == 0) 0f else completed.toFloat() / total
    val progress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 450),
        label = "hero-progress",
    )

    // Celebrate once, when the last task of the day is logged (not on every visit).
    val allDone = total > 0 && completed == total
    var burst by remember { mutableIntStateOf(0) }
    var wasAllDone by remember { mutableStateOf(allDone) }
    LaunchedEffect(allDone) {
        if (allDone && !wasAllDone) burst++
        wasAllDone = allDone
    }

    val interactionSource = remember { MutableInteractionSource() }
    val title = petItem.emotionalTaskPrompt.ifBlank { stringResource(R.string.ui_6b7694feec, petItem.pet.name) }

    Card(
        modifier = Modifier.fillMaxWidth().bouncyPress(interactionSource),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        onClick = onOpenPet,
        interactionSource = interactionSource,
    ) {
        Box(
            modifier = Modifier
                .background(Brush.horizontalGradient(listOf(DeepSage, Sage)))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            dayStatusText(petItem),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                    if (petItem.streakDays > 0) {
                        StreakBadge(days = petItem.streakDays)
                    }
                }

                if (total > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.25f),
                            strokeCap = StrokeCap.Round,
                        )
                        AnimatedContent(
                            targetState = completed,
                            transitionSpec = {
                                (slideInVertically { it / 2 } + fadeIn()) togetherWith
                                    (slideOutVertically { -it / 2 } + fadeOut())
                            },
                            label = "hero-counter",
                        ) { count ->
                            Text(
                                stringResource(R.string.dash_done_of_total, count, total),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        petItem.nextReminderText,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    Text(
                        stringResource(R.string.ui_22fafc39e6),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
            PawBurst(trigger = if (burst == 0) null else burst)
        }
    }
}

// ── LIVE VITALS STATUS ROW ──
@Composable
fun LivePetStatusRow(
    mood: MoodState,
    statusText: String,
    weightKg: String,
    ateToday: Boolean,
    waterToday: Boolean,
    walkedToday: Boolean,
    medsToday: Boolean,
) {
    val statusColor = when (mood) {
        MoodState.HAPPY -> Sage
        MoodState.NEUTRAL -> MaterialTheme.colorScheme.secondary
        MoodState.SAD -> Coral
    }
    val idleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    val waterBlue = Color(0xFF4FC3F7)
    val feedLabel = taskActionLabel(TaskType.FEED)
    val walkLabel = taskActionLabel(TaskType.WALK)
    val medicineLabel = taskActionLabel(TaskType.MEDICINE)
    val waterLabel = stringResource(R.string.dash_action_water)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusChip(statusText, statusColor)
        StatusChip(weightLabel(weightKg), Sage)
        StatusChip("🥣 $feedLabel" + (if (ateToday) " ✔" else " ○"), if (ateToday) Sage else idleColor)
        StatusChip("💧 $waterLabel" + (if (waterToday) " ✔" else " ○"), if (waterToday) waterBlue else idleColor)
        StatusChip("🚶 $walkLabel" + (if (walkedToday) " ✔" else " ○"), if (walkedToday) Sage else idleColor)
        StatusChip("💊 $medicineLabel" + (if (medsToday) " ✔" else " ○"), if (medsToday) Sage else idleColor)
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

// ── QUICK LOG TRAY ──
@Composable
fun NewQuickActionRow(
    onLogAction: (TaskType) -> Unit,
    onLogWater: () -> Unit,
) {
    // Inline confirmation under the row. It is always visible for a moment, even if the log was a duplicate.
    var showLogged by remember { mutableStateOf(false) }
    var loggedTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(loggedTick) {
        if (loggedTick == 0) return@LaunchedEffect
        showLogged = true
        delay(1400)
        showLogged = false
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val feedLabel = taskActionLabel(TaskType.FEED)
            QuickActionCircleButton(
                icon = "🥣",
                label = feedLabel,
                accentColor = Sage,
                onClick = {
                    loggedTick++
                    onLogAction(TaskType.FEED)
                },
            )
            val waterLabel = stringResource(R.string.dash_action_water)
            QuickActionCircleButton(
                icon = "💧",
                label = waterLabel,
                accentColor = Color(0xFF4FC3F7),
                onClick = {
                    loggedTick++
                    onLogWater()
                },
            )
            val walkLabel = taskActionLabel(TaskType.WALK)
            QuickActionCircleButton(
                icon = "🚶",
                label = walkLabel,
                accentColor = Sage,
                onClick = {
                    loggedTick++
                    onLogAction(TaskType.WALK)
                },
            )
            val medicineLabel = taskActionLabel(TaskType.MEDICINE)
            QuickActionCircleButton(
                icon = "💊",
                label = medicineLabel,
                accentColor = Sage,
                onClick = {
                    loggedTick++
                    onLogAction(TaskType.MEDICINE)
                },
            )
        }

        // Fixed-height slot, so showing the label never shifts the layout below it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = showLogged,
                enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.85f),
                exit = fadeOut(tween(220)),
            ) {
                Text(
                    stringResource(R.string.dash_logged),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Sage,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Sage.copy(alpha = 0.15f))
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }
        }
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
    // Every tap bumps this counter, which replays the icon wiggle and the paw burst.
    var taps by remember { mutableIntStateOf(0) }
    val feedbackKey: Int? = if (taps == 0) null else taps

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .bouncyPress(interactionSource, pressedScale = 0.9f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    taps++
                    onClick()
                },
            ),
    ) {
        Box(modifier = Modifier.size(54.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .wiggleOn(feedbackKey)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f))
                    .border(1.dp, accentColor.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(icon, style = MaterialTheme.typography.titleLarge)
            }
            // Outside the clipped circle, so the paws can fly past its edge.
            PawBurst(trigger = feedbackKey, count = 6, radius = 44.dp)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            maxLines = 1,
        )
    }
}
