package com.petcare.companionlite.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.petcare.companionlite.R
import com.petcare.companionlite.data.model.TaskType
import com.petcare.companionlite.data.model.TimelineItem
import com.petcare.companionlite.data.model.TimelineStatus
import com.petcare.companionlite.core.CareFormats
import com.petcare.companionlite.ui.components.taskActionLabel
import com.petcare.companionlite.ui.theme.Coral
import com.petcare.companionlite.ui.theme.Sage
import androidx.compose.ui.platform.LocalContext

/**
 * Today's task timeline: the checklist for the day.
 * Pending, overdue or missed items are tappable, so they can be logged directly.
 */
@Composable
fun TodayTimelineSection(
    timeline: List<TimelineItem>,
    petName: String,
    onLogTask: (TaskType) -> Unit,
) {
    if (timeline.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.ui_24345a1437),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            timeline.forEach { item ->
                TimelineRow(
                    item = item,
                    onTap = {
                        if (item.status == TimelineStatus.PENDING ||
                            item.status == TimelineStatus.OVERDUE ||
                            item.status == TimelineStatus.MISSED
                        ) {
                            onLogTask(item.reminder.type)
                        }
                    },
                )
            }

            // Completion summary
            val done = timeline.count { it.status == TimelineStatus.COMPLETED }
            val total = timeline.size
            val allDone = done == total
            Spacer(Modifier.height(8.dp))
            Text(
                if (allDone) stringResource(R.string.dash_timeline_perfect, petName)
                else stringResource(R.string.dash_timeline_progress, done, total),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (allDone) Sage else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun TimelineRow(
    item: TimelineItem,
    onTap: () -> Unit,
) {
    val isCompleted = item.status == TimelineStatus.COMPLETED
    val isOverdue = item.status == TimelineStatus.OVERDUE
    val isTappable = item.status == TimelineStatus.PENDING || isOverdue || item.status == TimelineStatus.MISSED

    val checkColor by animateColorAsState(
        targetValue = when {
            isCompleted -> Sage
            isOverdue || item.status == TimelineStatus.MISSED -> Coral
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        },
        animationSpec = spring(),
        label = "check-color",
    )

    val taskEmoji = when (item.reminder.type) {
        TaskType.FEED -> "🍖"
        TaskType.WALK -> "🚶"
        TaskType.MEDICINE -> "💊"
        TaskType.GROOMING -> "✂️"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (isTappable) Modifier.clickable(onClick = onTap) else Modifier)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Checkbox
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(checkColor.copy(alpha = if (isCompleted) 1f else 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            if (isCompleted) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White,
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        // Task label: short, capitalized action label
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.ui_78c505fe4e, taskEmoji, taskActionLabel(item.reminder.type)),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCompleted) FontWeight.Normal else FontWeight.SemiBold,
                color = if (isCompleted)
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
            )
        }

        // Time, in the device's clock format (12h or 24h) and language
        val context = LocalContext.current
        val timeText = when {
            isCompleted && item.matchedActivity != null -> CareFormats.clockText(context, item.matchedActivity.timestamp)
            else -> CareFormats.clockText(context, item.scheduledAt)
        }
        Text(
            timeText,
            style = MaterialTheme.typography.bodySmall,
            color = when {
                isOverdue -> Coral
                isCompleted -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            },
        )

        // Overdue indicator
        if (isOverdue) {
            Spacer(Modifier.width(6.dp))
            Text(
                "⚠️",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
