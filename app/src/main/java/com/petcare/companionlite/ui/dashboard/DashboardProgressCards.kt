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
                        TimelineStatus.OVERDUE -> stringResource(R.string.ui_efb8e2b275)
                        TimelineStatus.MISSED -> stringResource(R.string.ui_c62b29a9e8)
                        else -> stringResource(R.string.ui_ec82d657e3)
                    }
                    Text(
                        statusPrefix + com.petcare.companionlite.ui.components.taskLabel(nextTask.reminder.type),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (nextTask.status != TimelineStatus.PENDING) Coral else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        stringResource(R.string.ui_e31ddfb300, petName, timeLabel),
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
                Text(stringResource(R.string.ui_2ae8bf7c4f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ── STREAK BADGE ──────────────────────────────────────────────────
@Composable
fun StreakBadge(days: Int) {
    val (emoji, label) = when {
        days >= 30 -> "⭐" to stringResource(R.string.ui_7881bf71d2)
        days >= 7  -> "🔥" to stringResource(R.string.ui_b2de92cf43, days)
        days >= 3  -> "🟢" to stringResource(R.string.ui_b2de92cf43, days)
        else       -> "🐾" to stringResource(R.string.ui_adec391fc6, days, if (days > 1) "s" else "")
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
