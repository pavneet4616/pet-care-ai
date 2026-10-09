package com.petcare.companionlite.ui.dashboard

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petcare.companionlite.R
import com.petcare.companionlite.data.model.TaskType
import com.petcare.companionlite.data.model.TimelineItem
import com.petcare.companionlite.data.model.TimelineStatus
import com.petcare.companionlite.core.CareFormats
import com.petcare.companionlite.ui.components.bouncyPress
import com.petcare.companionlite.ui.components.taskActionLabel
import com.petcare.companionlite.ui.theme.Coral
import com.petcare.companionlite.ui.theme.Sage

// ── NEXT CARE HERO CARD ──
@Composable
fun NextCareHeroCard(
    nextTask: TimelineItem,
    petName: String,
    onMarkDone: () -> Unit,
) {
    val emoji = when (nextTask.reminder.type) {
        TaskType.FEED -> "🥣"
        TaskType.WALK -> "🚶"
        TaskType.MEDICINE -> "💊"
        TaskType.GROOMING -> "✂️"
    }
    val timeLabel = CareFormats.clockText(LocalContext.current, nextTask.scheduledAt)
    val statusWord = when (nextTask.status) {
        TimelineStatus.OVERDUE -> stringResource(R.string.dash_word_overdue)
        TimelineStatus.MISSED -> stringResource(R.string.dash_word_missed)
        else -> stringResource(R.string.dash_word_upcoming)
    }
    val title = stringResource(R.string.dash_task_title, statusWord, taskActionLabel(nextTask.reminder.type))
    val isLate = nextTask.status != TimelineStatus.PENDING
    val buttonInteraction = remember { MutableInteractionSource() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            // Narrow widths (phones, long translations) put the button below the text instead of squeezing it.
            val stacked = maxWidth < 420.dp

            val taskInfo: @Composable (Modifier) -> Unit = { infoModifier ->
                Row(
                    modifier = infoModifier,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(emoji, style = MaterialTheme.typography.titleLarge)
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isLate) Coral else MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            stringResource(R.string.ui_e31ddfb300, petName, timeLabel),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            val actionButton: @Composable (Modifier) -> Unit = { buttonModifier ->
                Button(
                    onClick = onMarkDone,
                    modifier = buttonModifier.bouncyPress(buttonInteraction),
                    interactionSource = buttonInteraction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        stringResource(R.string.ui_2ae8bf7c4f),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    taskInfo(Modifier.fillMaxWidth())
                    actionButton(Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    taskInfo(Modifier.weight(1f))
                    actionButton(Modifier)
                }
            }
        }
    }
}

// ── STREAK BADGE ──────────────────────────────────────────────────
@Composable
fun StreakBadge(days: Int) {
    val (emoji, label) = when {
        days >= 30 -> "⭐" to stringResource(R.string.ui_7881bf71d2)
        days >= 7 -> "🔥" to pluralStringResource(R.plurals.dash_streak_days, days, days)
        days >= 3 -> "🟢" to pluralStringResource(R.plurals.dash_streak_days, days, days)
        else -> "🐾" to pluralStringResource(R.plurals.dash_streak_days, days, days)
    }
    // Gentle flame pulse for active streaks.
    val pulse by rememberInfiniteTransition(label = "streak-pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "streak-flame",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            emoji,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.scale(if (days >= 3) pulse else 1f),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
        )
    }
}

// ── PROGRESS TO NEXT BADGE ────────────────────────────────────────
@Composable
fun StreakProgressCard(currentStreak: Int) {
    val (nextTarget, nextEmoji, nextLabel) = when {
        currentStreak < 3 -> Triple(3, "🟢", stringResource(R.string.dash_goal_habit))
        currentStreak < 7 -> Triple(7, "🔥", stringResource(R.string.dash_goal_streak))
        currentStreak < 30 -> Triple(30, "⭐", stringResource(R.string.dash_goal_champion))
        else -> Triple(currentStreak, "⭐", stringResource(R.string.dash_goal_legendary))
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
                    stringResource(R.string.ui_d6370263c8),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.ui_78c505fe4e, nextEmoji, nextLabel),
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
                if (progress >= 1f) stringResource(R.string.ui_91a9ed4ede, nextLabel)
                else stringResource(R.string.ui_593f82b8e9, currentStreak, nextTarget, nextTarget - currentStreak),
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
        SummaryChip("✅", stringResource(R.string.dash_summary_done), "$totalDone", Modifier.weight(1f))
        SummaryChip("⏰", stringResource(R.string.dash_summary_pending), "$totalPending", Modifier.weight(1f))
        SummaryChip(
            "🔥",
            stringResource(R.string.dash_summary_streak),
            pluralStringResource(R.plurals.dash_streak_days, maxStreak, maxStreak),
            Modifier.weight(1f),
        )
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
                        stringResource(R.string.ui_d01027395a, totalPending),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(stringResource(R.string.ui_ed26cd1c0b),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                }
            }
            Text("→", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ── AI COMPANION HERO ───────────────────────────────────────────
