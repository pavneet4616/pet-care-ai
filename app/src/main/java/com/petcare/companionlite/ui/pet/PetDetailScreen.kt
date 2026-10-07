package com.petcare.companionlite.ui.pet

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.petcare.companionlite.data.model.TaskType
import com.petcare.companionlite.data.model.toDateTimeLabel
import com.petcare.companionlite.ui.components.PetPhotoOrAvatar
import com.petcare.companionlite.ui.components.ReminderActionCard
import com.petcare.companionlite.ui.components.TimelineCard
import com.petcare.companionlite.ui.components.TimePickerDialogWrapper
import com.petcare.companionlite.ui.theme.Coral
import com.petcare.companionlite.ui.theme.DeepSage
import com.petcare.companionlite.ui.theme.Honey
import com.petcare.companionlite.ui.theme.PremiumGold
import com.petcare.companionlite.ui.theme.Sage
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PetDetailScreen(
    onBack: () -> Unit,
    onOpenStats: (Long) -> Unit,
    onEditPet: (Long) -> Unit,
    onOpenTools: (Long) -> Unit,
    onMessage: (String) -> Unit,
    viewModel: PetDetailViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddReminderSheet by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Celebration state
    var celebrationVisible by remember { mutableStateOf(false) }
    var celebrationMsg by remember { mutableStateOf("") }

    // Reminder drafting state
    var selectedTask by remember { mutableStateOf(TaskType.FEED) }
    var repeatMode by remember { mutableIntStateOf(0) }
    val currentLocalTime = LocalTime.now().plusHours(1)
    val timePickerState = rememberTimePickerState(
        initialHour = currentLocalTime.hour,
        initialMinute = currentLocalTime.minute,
        is24Hour = false
    )

    // Auto-dismiss celebration
    LaunchedEffect(celebrationVisible) {
        if (celebrationVisible) {
            delay(2200)
            celebrationVisible = false
        }
    }

    // Delete confirmation
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${state.pet?.name ?: "Pet"}?") },
            text = { Text("This will permanently remove this pet and all their care history.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deletePet { onBack() }
                }) { Text("Delete", color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showTimePicker) {
        TimePickerDialogWrapper(
            state = timePickerState,
            onDismissRequest = { showTimePicker = false },
            onConfirm = { showTimePicker = false }
        )
    }

    // Add Reminder Bottom Sheet
    if (showAddReminderSheet) {
        ModalBottomSheet(onDismissRequest = { showAddReminderSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text("Set a Routine", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Pick the care type and time — we'll remind you every day automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )

                // Task selection grid
                Text("Care Type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(TaskType.entries.take(4)) { taskType ->
                        ReminderActionCard(
                            taskType = taskType,
                            selected = selectedTask == taskType,
                            onClick = { selectedTask = taskType }
                        )
                    }
                }

                // Time picker card
                Text("Reminder Time", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                val formattedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    .format(DateTimeFormatter.ofPattern("h:mm a"))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    onClick = { showTimePicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Remind me at", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                Text(formattedTime, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        TextButton(onClick = { showTimePicker = true }) { Text("Change") }
                    }
                }

                // Repeat modes
                Text("Repeat", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Daily", "Weekdays", "Weekends").forEachIndexed { index, label ->
                        val isActive = repeatMode == index
                        if (isActive) {
                            Button(
                                onClick = { repeatMode = index },
                                shape = RoundedCornerShape(12.dp)
                            ) { Text(label) }
                        } else {
                            OutlinedButton(
                                onClick = { repeatMode = index },
                                shape = RoundedCornerShape(12.dp)
                            ) { Text(label) }
                        }
                    }
                }

                Button(
                    onClick = {
                        val repeatDays = when (repeatMode) {
                            1 -> setOf(1, 2, 3, 4, 5)
                            2 -> setOf(6, 7)
                            else -> setOf(1, 2, 3, 4, 5, 6, 7)
                        }
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        viewModel.addReminder(selectedTask, timePickerState.hour, timePickerState.minute, repeatDays) { resultMsg ->
                            onMessage(if (resultMsg.contains("tier allows")) resultMsg else "⏰ ${selectedTask.label} reminder set for $formattedTime")
                        }
                        showAddReminderSheet = false
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Set Reminder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(state.pet?.name ?: "", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        TextButton(onClick = onBack) {
                            Text("← Back", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { state.pet?.id?.let(onEditPet) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit pet")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete pet", tint = Coral)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showAddReminderSheet = true },
                    text = { Text("Add Reminder", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    shape = RoundedCornerShape(16.dp),
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 100.dp, start = 20.dp, end = 20.dp, top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // ── 1. Pet Header with gradient ─────────────────────
                item {
                    state.pet?.let { pet ->
                        Card(
                            shape = RoundedCornerShape(28.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.horizontalGradient(listOf(DeepSage, Sage))
                                    )
                                    .padding(20.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PetPhotoOrAvatar(
                                        pet = pet,
                                        mood = state.mood,
                                        modifier = Modifier.size(100.dp)
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            "${pet.petType.emoji} ${pet.name}",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White
                                        )
                                        // Personality line first — makes it feel alive
                                        if (state.personalityMessage.isNotBlank()) {
                                            Text(
                                                "\"${state.personalityMessage}\"",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Honey,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        } else {
                                            val moodLabel = when (state.mood) {
                                                com.petcare.companionlite.data.model.MoodState.HAPPY -> "Feeling great 😊"
                                                com.petcare.companionlite.data.model.MoodState.NEUTRAL -> "Doing okay 🐾"
                                                com.petcare.companionlite.data.model.MoodState.SAD -> "Needs some love 🥺"
                                            }
                                            Text(
                                                moodLabel,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Honey
                                            )
                                        }
                                        val ageDouble = pet.ageYears.toDoubleOrNull() ?: 0.0
                                        Text(
                                            "${pet.weightKg} kg · ${pet.ageYears} yr${if (ageDouble != 1.0) "s" else ""}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 2. Quick Log ─────────────────────────────────────
                item {
                    Text("What did ${state.pet?.name ?: "your pet"} do?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TaskType.entries.take(4).forEach { taskType ->
                            Box(modifier = Modifier.weight(1f)) {
                                QuickLogAnimatedCard(taskType = taskType) { loggedAt ->
                                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                    viewModel.logTask(taskType, { msg ->
                                        celebrationMsg = msg
                                        celebrationVisible = true
                                        onMessage(msg)
                                    })
                                }
                            }
                        }
                    }
                }

                // ── 3. Action Hub ────────────────────────────────
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { state.pet?.id?.let(onOpenTools) },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.Handyman, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Care Tools")
                        }
                        
                        OutlinedButton(
                            onClick = { state.pet?.id?.let(onOpenStats) },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("📊 Stats")
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.settings.hasFullAccess) {
                            OutlinedButton(
                                onClick = { viewModel.exportPdf(onMessage) },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Filled.PictureAsPdf, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Export Medical Report")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onMessage("Upgrade to Premium to export PDF reports") },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PremiumGold),
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Filled.WorkspacePremium, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Export Medical Report")
                            }
                        }
                    }
                }

                // ── 4. Reminders / Timeline ──────────────────────────
                item {
                    Text("${state.pet?.name ?: "Your pet"}'s Daily Routine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                if (state.timeline.isEmpty() && state.reminders.isEmpty()) {
                    item {
                        // Smart empty state
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(24.dp),
                            onClick = { showAddReminderSheet = true }
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text("🐾", style = MaterialTheme.typography.displayMedium)
                                Text(
                                    "${state.pet?.name ?: "Your pet"} doesn't have a routine yet",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Let's build healthy habits together — daily feeding, walks, and medicine reminders create a loving routine.",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                                Button(
                                    onClick = { showAddReminderSheet = true },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Set First Routine", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                } else {
                    items(state.timeline, key = { it.reminder.id }) { item ->
                        TimelineCard(item = item) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            viewModel.logTask(item.reminder.type, { msg ->
                                celebrationMsg = msg
                                celebrationVisible = true
                                onMessage(msg)
                            })
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Not a substitute for veterinary advice",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // ── Celebration Overlay ──────────────────────────────────
        com.petcare.companionlite.ui.components.CelebrationOverlay(
            visible = celebrationVisible,
            message = celebrationMsg,
            petName = state.pet?.name ?: "",
            streakDays = com.petcare.companionlite.data.model.countDailyCareStreak(state.activities),
            allDone = state.timeline.none { it.status == com.petcare.companionlite.data.model.TimelineStatus.PENDING } && state.timeline.any { it.status == com.petcare.companionlite.data.model.TimelineStatus.COMPLETED },
            onDismiss = { celebrationVisible = false }
        )
    }
}

// ── Animated Quick Log Card ───────────────────────────────────
@Composable
fun QuickLogAnimatedCard(taskType: TaskType, onClick: (String) -> Unit) {
    var tapped by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (tapped) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessHigh),
        label = "tap-scale",
        finishedListener = { tapped = false }
    )

    Card(
        modifier = Modifier.scale(scale),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        onClick = {
            tapped = true
            val timeLabel = java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
            onClick(timeLabel)
        }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 6.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val icon = when (taskType) {
                TaskType.FEED -> androidx.compose.material.icons.Icons.Filled.Restaurant
                TaskType.WALK -> androidx.compose.material.icons.Icons.Filled.Route
                TaskType.MEDICINE -> androidx.compose.material.icons.Icons.Filled.Medication
                TaskType.GROOMING -> androidx.compose.material.icons.Icons.Filled.ContentCut
            }
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                taskType.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}
