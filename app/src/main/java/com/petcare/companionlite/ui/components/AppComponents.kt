package com.petcare.companionlite.ui.components

import com.petcare.companionlite.R

import androidx.compose.ui.res.stringResource

import com.petcare.companionlite.core.petPhotoModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.view.ViewGroup
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.petcare.companionlite.data.model.MoodState
import com.petcare.companionlite.data.model.PetDashboardItem
import com.petcare.companionlite.data.model.PetProfile
import com.petcare.companionlite.data.model.PetType
import com.petcare.companionlite.data.model.TaskType
import com.petcare.companionlite.data.model.TimelineItem
import com.petcare.companionlite.data.model.TimelineStatus
import com.petcare.companionlite.data.model.toTimeLabel
import com.petcare.companionlite.monetization.AdsManager
import com.petcare.companionlite.ui.theme.Blush
import com.petcare.companionlite.ui.theme.Coral
import com.petcare.companionlite.ui.theme.Cream
import com.petcare.companionlite.ui.theme.DeepSage
import com.petcare.companionlite.ui.theme.Honey
import com.petcare.companionlite.ui.theme.Sage

@Composable
fun PetCard(item: PetDashboardItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PetPhotoOrAvatar(pet = item.pet, mood = item.mood, modifier = Modifier.size(88.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    stringResource(R.string.ui_78c505fe4e, item.pet.petType.emoji, item.pet.name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    item.greetingLine.ifBlank { item.pet.breed.ifBlank { stringResource(R.string.ui_46238ae1be) } },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
                Text(
                    item.emotionalTaskPrompt.ifBlank { item.dailyMessage },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                // Pet personality voice — makes app feel alive
                if (item.personalityMessage.isNotBlank()) {
                    Text(
                        stringResource(R.string.ui_d1394e67e3, item.personalityMessage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        fontWeight = FontWeight.Normal
                    )
                }
                // Simplified progress summary instead of 4 tiny chips
                val progressParts = mutableListOf<String>()
                if (item.completedCount > 0 || item.pendingCount > 0 || item.overdueCount > 0) {
                    val total = item.completedCount + item.pendingCount + item.overdueCount
                    progressParts.add("${item.completedCount} of $total done")
                }
                if (item.streakDays > 0) progressParts.add("🔥 ${item.streakDays} day streak")
                if (progressParts.isNotEmpty()) {
                    Text(
                        progressParts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.overdueCount > 0) Coral else Sage
                    )
                }
                if (item.nextReminderText.isNotBlank()) {
                    Text(
                        item.nextReminderText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}

@Composable
fun PetPhotoOrAvatar(pet: PetProfile, mood: MoodState, modifier: Modifier = Modifier) {
    if (pet.photoUri != null) {
        AsyncImage(
            model = androidx.compose.ui.platform.LocalContext.current.petPhotoModel(pet.photoUri),
            contentDescription = stringResource(R.string.ui_4e1af604ac, pet.name),
            modifier = modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Blush),
            contentScale = ContentScale.Crop,
        )
    } else {
        PetAvatar(petType = pet.petType, mood = mood, modifier = modifier)
    }
}

/**
 * Canvas-drawn pet avatar with mood expressions.
 * Different animals (Dog, Cat, Bird, Other) with distinct faces.
 * Mood affects eyes and mouth expression.
 */
@Composable
fun PetAvatar(petType: PetType, mood: MoodState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "avatar-idle")
    val yOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(animation = tween(1600), repeatMode = RepeatMode.Reverse),
        label = "avatar-float",
    )

    val bgColors = when (mood) {
        MoodState.HAPPY -> listOf(Sage.copy(alpha = 0.95f), Honey.copy(alpha = 0.5f))
        MoodState.NEUTRAL -> listOf(Sage.copy(alpha = 0.85f), Blush.copy(alpha = 0.5f))
        MoodState.SAD -> listOf(Blush.copy(alpha = 0.9f), Coral.copy(alpha = 0.4f))
    }

    Box(
        modifier = modifier
            .graphicsLayer { translationY = yOffset }
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(bgColors)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            when (petType) {
                PetType.DOG -> drawDog(mood)
                PetType.CAT -> drawCat(mood)
                PetType.BIRD -> drawBird(mood)
                PetType.OTHER -> drawGenericPet(mood)
            }
        }
    }
}

private fun DrawScope.drawDog(mood: MoodState) {
    val cx = size.width / 2
    val cy = size.height / 2
    val headR = size.minDimension * 0.35f
    val bodyColor = Color(0xFF8B6914)
    val darkColor = Color(0xFF5C4710)
    val white = Color.White

    // Ears (floppy)
    drawOval(darkColor, Offset(cx - headR * 0.85f, cy - headR * 0.6f), Size(headR * 0.6f, headR * 1.1f))
    drawOval(darkColor, Offset(cx + headR * 0.25f, cy - headR * 0.6f), Size(headR * 0.6f, headR * 1.1f))

    // Head
    drawCircle(bodyColor, headR, Offset(cx, cy))

    // Muzzle
    drawOval(white, Offset(cx - headR * 0.4f, cy + headR * 0.1f), Size(headR * 0.8f, headR * 0.65f))

    // Nose
    drawCircle(darkColor, headR * 0.12f, Offset(cx, cy + headR * 0.25f))

    // Eyes
    drawMoodEyes(cx, cy - headR * 0.15f, headR * 0.1f, mood, darkColor)

    // Mouth
    drawMoodMouth(cx, cy + headR * 0.45f, headR * 0.3f, mood, darkColor)
}

private fun DrawScope.drawCat(mood: MoodState) {
    val cx = size.width / 2
    val cy = size.height / 2
    val headR = size.minDimension * 0.33f
    val bodyColor = Color(0xFFB0B0B0)
    val darkColor = Color(0xFF444444)
    val pink = Color(0xFFFFB6C1)

    // Ears (pointed triangles)
    val earPath = Path().apply {
        moveTo(cx - headR * 0.7f, cy - headR * 0.3f)
        lineTo(cx - headR * 0.4f, cy - headR * 1.1f)
        lineTo(cx - headR * 0.05f, cy - headR * 0.3f)
        close()
    }
    drawPath(earPath, bodyColor)
    val earPathR = Path().apply {
        moveTo(cx + headR * 0.7f, cy - headR * 0.3f)
        lineTo(cx + headR * 0.4f, cy - headR * 1.1f)
        lineTo(cx + headR * 0.05f, cy - headR * 0.3f)
        close()
    }
    drawPath(earPathR, bodyColor)

    // Inner ears
    val innerEarL = Path().apply {
        moveTo(cx - headR * 0.6f, cy - headR * 0.3f)
        lineTo(cx - headR * 0.4f, cy - headR * 0.85f)
        lineTo(cx - headR * 0.15f, cy - headR * 0.3f)
        close()
    }
    drawPath(innerEarL, pink)
    val innerEarR = Path().apply {
        moveTo(cx + headR * 0.6f, cy - headR * 0.3f)
        lineTo(cx + headR * 0.4f, cy - headR * 0.85f)
        lineTo(cx + headR * 0.15f, cy - headR * 0.3f)
        close()
    }
    drawPath(innerEarR, pink)

    // Head
    drawCircle(bodyColor, headR, Offset(cx, cy))

    // Nose
    val noseSize = headR * 0.08f
    drawCircle(pink, noseSize, Offset(cx, cy + headR * 0.1f))

    // Whiskers
    val whiskerColor = darkColor.copy(alpha = 0.5f)
    drawLine(whiskerColor, Offset(cx - headR * 0.15f, cy + headR * 0.15f), Offset(cx - headR * 0.8f, cy), strokeWidth = 1.5f)
    drawLine(whiskerColor, Offset(cx - headR * 0.15f, cy + headR * 0.2f), Offset(cx - headR * 0.8f, cy + headR * 0.2f), strokeWidth = 1.5f)
    drawLine(whiskerColor, Offset(cx + headR * 0.15f, cy + headR * 0.15f), Offset(cx + headR * 0.8f, cy), strokeWidth = 1.5f)
    drawLine(whiskerColor, Offset(cx + headR * 0.15f, cy + headR * 0.2f), Offset(cx + headR * 0.8f, cy + headR * 0.2f), strokeWidth = 1.5f)

    // Eyes (cat-style slits when neutral/sad)
    drawMoodEyes(cx, cy - headR * 0.15f, headR * 0.1f, mood, darkColor)

    // Mouth
    drawMoodMouth(cx, cy + headR * 0.3f, headR * 0.25f, mood, darkColor)
}

private fun DrawScope.drawBird(mood: MoodState) {
    val cx = size.width / 2
    val cy = size.height / 2
    val headR = size.minDimension * 0.3f
    val bodyColor = Color(0xFF4DB8A4)
    val darkColor = Color(0xFF1A5C50)
    val orange = Color(0xFFFF8C42)

    // Body (oval below head)
    drawOval(bodyColor.copy(alpha = 0.7f), Offset(cx - headR * 0.7f, cy + headR * 0.2f), Size(headR * 1.4f, headR * 1.0f))

    // Head
    drawCircle(bodyColor, headR * 0.85f, Offset(cx, cy - headR * 0.1f))

    // Beak
    val beakPath = Path().apply {
        moveTo(cx + headR * 0.5f, cy)
        lineTo(cx + headR * 1.1f, cy + headR * 0.1f)
        lineTo(cx + headR * 0.5f, cy + headR * 0.2f)
        close()
    }
    drawPath(beakPath, orange)

    // Wing
    val wingPath = Path().apply {
        moveTo(cx - headR * 0.6f, cy + headR * 0.3f)
        lineTo(cx - headR * 1.2f, cy + headR * 0.6f)
        lineTo(cx - headR * 0.3f, cy + headR * 0.7f)
        close()
    }
    drawPath(wingPath, darkColor.copy(alpha = 0.6f))

    // Eyes
    drawMoodEyes(cx - headR * 0.05f, cy - headR * 0.2f, headR * 0.08f, mood, darkColor)

    // Small expression line under beak
    drawMoodMouth(cx + headR * 0.1f, cy + headR * 0.35f, headR * 0.15f, mood, darkColor)
}

private fun DrawScope.drawGenericPet(mood: MoodState) {
    val cx = size.width / 2
    val cy = size.height / 2
    val headR = size.minDimension * 0.32f
    val bodyColor = Color(0xFF9B8EC4)
    val darkColor = Color(0xFF4A3D6E)

    // Head
    drawCircle(bodyColor, headR, Offset(cx, cy))

    // Paw print on forehead
    drawCircle(darkColor.copy(alpha = 0.3f), headR * 0.12f, Offset(cx, cy - headR * 0.4f))
    drawCircle(darkColor.copy(alpha = 0.3f), headR * 0.07f, Offset(cx - headR * 0.15f, cy - headR * 0.55f))
    drawCircle(darkColor.copy(alpha = 0.3f), headR * 0.07f, Offset(cx + headR * 0.15f, cy - headR * 0.55f))
    drawCircle(darkColor.copy(alpha = 0.3f), headR * 0.07f, Offset(cx, cy - headR * 0.65f))

    // Eyes
    drawMoodEyes(cx, cy - headR * 0.1f, headR * 0.1f, mood, darkColor)

    // Mouth
    drawMoodMouth(cx, cy + headR * 0.35f, headR * 0.25f, mood, darkColor)
}

private fun DrawScope.drawMoodEyes(cx: Float, cy: Float, radius: Float, mood: MoodState, color: Color) {
    val eyeSpacing = radius * 3f
    val leftX = cx - eyeSpacing / 2
    val rightX = cx + eyeSpacing / 2

    when (mood) {
        MoodState.HAPPY -> {
            // Happy curved eyes (^  ^)
            drawArc(color, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(leftX - radius, cy - radius), size = Size(radius * 2, radius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = radius * 0.4f))
            drawArc(color, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(rightX - radius, cy - radius), size = Size(radius * 2, radius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = radius * 0.4f))
        }
        MoodState.NEUTRAL -> {
            // Normal round eyes
            drawCircle(color, radius, Offset(leftX, cy))
            drawCircle(color, radius, Offset(rightX, cy))
            // Highlights
            drawCircle(Color.White, radius * 0.35f, Offset(leftX + radius * 0.25f, cy - radius * 0.25f))
            drawCircle(Color.White, radius * 0.35f, Offset(rightX + radius * 0.25f, cy - radius * 0.25f))
        }
        MoodState.SAD -> {
            // Sad droopy eyes (slightly angled down)
            drawCircle(color, radius, Offset(leftX, cy))
            drawCircle(color, radius, Offset(rightX, cy))
            // Eyebrow lines angled inward-down
            drawLine(color, Offset(leftX - radius * 1.2f, cy - radius * 1.8f),
                Offset(leftX + radius * 0.5f, cy - radius * 1.2f), strokeWidth = radius * 0.3f)
            drawLine(color, Offset(rightX + radius * 1.2f, cy - radius * 1.8f),
                Offset(rightX - radius * 0.5f, cy - radius * 1.2f), strokeWidth = radius * 0.3f)
        }
    }
}

private fun DrawScope.drawMoodMouth(cx: Float, cy: Float, width: Float, mood: MoodState, color: Color) {
    when (mood) {
        MoodState.HAPPY -> {
            // Smile arc
            drawArc(color, startAngle = 10f, sweepAngle = 160f, useCenter = false,
                topLeft = Offset(cx - width, cy - width * 0.5f), size = Size(width * 2, width),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = width * 0.12f))
        }
        MoodState.NEUTRAL -> {
            // Flat line
            drawLine(color, Offset(cx - width * 0.5f, cy), Offset(cx + width * 0.5f, cy), strokeWidth = width * 0.1f)
        }
        MoodState.SAD -> {
            // Frown arc (inverted smile)
            drawArc(color, startAngle = 190f, sweepAngle = 160f, useCenter = false,
                topLeft = Offset(cx - width, cy - width * 0.3f), size = Size(width * 2, width),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = width * 0.12f))
        }
    }
}

@Composable
fun MoodAvatar(mood: MoodState, modifier: Modifier = Modifier) {
    PetAvatar(petType = PetType.DOG, mood = mood, modifier = modifier)
}

@Composable
fun MetricChip(text: String, tint: Color) {
    Surface(
        color = tint.copy(alpha = 0.16f),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = tint,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun TaskActionChip(taskType: TaskType, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "chip-scale",
    )
    AssistChip(
        onClick = onClick,
        label = { Text(com.petcare.companionlite.ui.components.taskLabel(taskType)) },
        leadingIcon = { Icon(taskIcon(taskType), contentDescription = null, modifier = Modifier.size(18.dp)) },
        modifier = Modifier.scale(scale),
        interactionSource = interactionSource,
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun QuickLogCard(taskType: TaskType, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "quicklog-scale",
    )
    Card(
        modifier = Modifier
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(taskIcon(taskType), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Text(com.petcare.companionlite.ui.components.taskLabel(taskType), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ReminderActionCard(taskType: TaskType, selected: Boolean, onClick: () -> Unit) {
    val bgColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    
    Card(
        modifier = Modifier
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(taskIcon(taskType), contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
            Text(com.petcare.companionlite.ui.components.taskLabel(taskType), fontWeight = FontWeight.Bold, color = contentColor, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialogWrapper(
    state: TimePickerState,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.ui_77dfd2135f)) }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.ui_9ce3bd4224)) }
        },
        text = {
            TimePicker(state = state)
        }
    )
}

@Composable
fun TimelineCard(item: TimelineItem, onLogNow: () -> Unit) {
    val statusColor = when (item.status) {
        TimelineStatus.COMPLETED -> Sage
        TimelineStatus.PENDING   -> Honey
        TimelineStatus.OVERDUE   -> Coral
        TimelineStatus.MISSED    -> Coral.copy(alpha = 0.75f)
    }
    val statusIcon = when (item.status) {
        TimelineStatus.COMPLETED -> Icons.Filled.CheckCircle
        TimelineStatus.PENDING   -> Icons.Filled.AccessTime
        TimelineStatus.OVERDUE   -> Icons.Filled.Warning
        TimelineStatus.MISSED    -> Icons.Filled.Warning
    }
    val statusLabel = when (item.status) {
        TimelineStatus.COMPLETED -> stringResource(R.string.ui_17cd91a7ad, item.matchedActivity?.timestamp?.toTimeLabel().orEmpty())
        TimelineStatus.PENDING   -> stringResource(R.string.ui_c90341fee7)
        TimelineStatus.OVERDUE   -> stringResource(R.string.ui_0fa251c1f3)
        TimelineStatus.MISSED    -> stringResource(R.string.ui_62e50884f4)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(R.string.ui_53861d6315, com.petcare.companionlite.ui.components.taskLabel(item.reminder.type), item.scheduledAt.toTimeLabel()),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    statusLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            if (item.status != TimelineStatus.COMPLETED) {
                TaskActionChip(taskType = item.reminder.type, onClick = onLogNow)
            }
        }
    }
}

@Composable
fun HealthLogCard(title: String, subtitle: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle)
        }
    }
}

@Composable
fun AdBanner(enabled: Boolean) {
    val context = LocalContext.current
    val app = context.applicationContext as com.petcare.companionlite.PetCareApplication
    val adsAllowed by app.container.adsManager.adsAllowed.collectAsStateWithLifecycle()
    if (!enabled || !adsAllowed) return

    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val bannerWidthDp = maxWidth.value.toInt().coerceAtLeast(1)
    val adaptiveAdSize = remember(context, bannerWidthDp) {
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, bannerWidthDp)
    }
    val adView = remember(context, adaptiveAdSize) {
        AdView(context).apply {
            adUnitId = AdsManager.bannerAdUnitId
            setAdSize(adaptiveAdSize)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(adView) {
        onDispose {
            adView.destroy()
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        factory = { adView },
    )
    }
}

@Composable
fun TimelineEmptyState() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Pets, contentDescription = null, tint = Sage, modifier = Modifier.size(40.dp))
            Text(stringResource(R.string.ui_cf047f8761), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.ui_b1bd80c635),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private fun taskIcon(taskType: TaskType) = when (taskType) {
    TaskType.FEED -> Icons.Filled.Restaurant
    TaskType.WALK -> Icons.Filled.Route
    TaskType.MEDICINE -> Icons.Filled.Medication
    TaskType.GROOMING -> Icons.Filled.ContentCut
}


// ── CELEBRATION OVERLAY ──────────────────────────────────────────
@Composable
fun CelebrationOverlay(
    visible: Boolean,
    message: String,
    petName: String,
    streakDays: Int,
    allDone: Boolean,
    onDismiss: () -> Unit,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(tween(300)),
        exit = androidx.compose.animation.fadeOut(tween(500)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            val bounce by rememberInfiniteTransition(label = "celebration-bounce").animateFloat(
                initialValue = 0.9f, targetValue = 1.1f,
                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                label = "bounce",
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (allDone) "🎉" else "❤️",
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.graphicsLayer { scaleX = bounce; scaleY = bounce },
                )
                Text(
                    if (allDone) stringResource(R.string.ui_946feb858f, petName) else message,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                    color = androidx.compose.ui.graphics.Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                if (allDone) {
                    Text(
                        stringResource(R.string.ui_46a0c3cdf1),
                        style = MaterialTheme.typography.titleLarge,
                        color = Honey,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                }
                if (streakDays > 0) {
                    Text(
                        stringResource(R.string.ui_5615decc68, streakDays),
                        style = MaterialTheme.typography.titleMedium,
                        color = Sage,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
