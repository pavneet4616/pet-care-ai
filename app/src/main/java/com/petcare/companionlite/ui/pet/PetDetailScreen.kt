package com.petcare.companionlite.ui.pet

import com.petcare.companionlite.R
import com.petcare.companionlite.core.petPhotoModel
import com.petcare.companionlite.data.model.MoodState
import com.petcare.companionlite.data.model.PetProfile
import com.petcare.companionlite.data.model.Reminder
import com.petcare.companionlite.data.model.TaskType
import com.petcare.companionlite.data.model.TimelineItem
import com.petcare.companionlite.data.model.TimelineStatus
import com.petcare.companionlite.ui.components.BobbingEmoji
import com.petcare.companionlite.ui.components.ReminderActionCard
import com.petcare.companionlite.ui.components.TimePickerDialogWrapper
import com.petcare.companionlite.ui.components.ageLabel
import com.petcare.companionlite.ui.components.bouncyPress
import com.petcare.companionlite.ui.components.popIn
import com.petcare.companionlite.ui.components.weightLabel
import com.petcare.companionlite.ui.theme.Blush
import com.petcare.companionlite.ui.theme.Coral
import com.petcare.companionlite.ui.theme.Cream
import com.petcare.companionlite.ui.theme.DeepSage
import com.petcare.companionlite.ui.theme.Honey
import com.petcare.companionlite.ui.theme.PremiumGold
import com.petcare.companionlite.ui.theme.Sage

import androidx.compose.ui.res.stringResource

import android.text.format.DateFormat as AndroidDateFormat
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

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
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current
    val listState = rememberLazyListState()

    val fallbackTitle = stringResource(R.string.pet_fallback_title)
    val fallbackSubject = stringResource(R.string.pet_fallback_subject_lower)
    val petName = state.pet?.name?.takeIf { it.isNotBlank() }
    val routineRows = remember(state) { buildRoutineRows(state) }

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
        is24Hour = AndroidDateFormat.is24HourFormat(context),
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
            title = { Text(stringResource(R.string.ui_137cdc2d7f, petName ?: fallbackTitle)) },
            text = { Text(stringResource(R.string.ui_49d87c506b)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deletePet { onBack() }
                }) { Text(stringResource(R.string.ui_f6fdbe48dc), color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.ui_77dfd2135f)) }
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
            val repeatLabels = listOf(
                stringResource(R.string.pet_repeat_daily),
                stringResource(R.string.pet_repeat_weekdays),
                stringResource(R.string.pet_repeat_weekends),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(stringResource(R.string.ui_defb4d7e80), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.ui_08d8a00f3d),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )

                // Task selection grid
                Text(stringResource(R.string.ui_261fd8c633), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
                Text(stringResource(R.string.ui_18e5b40b9f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                val formattedTime = PetTimeText.clock(context, timePickerState.hour, timePickerState.minute)
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
                                Text(stringResource(R.string.ui_23ff37cc4b), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                Text(formattedTime, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        TextButton(onClick = { showTimePicker = true }) { Text(stringResource(R.string.ui_64fbd995d3)) }
                    }
                }

                // Repeat modes
                Text(stringResource(R.string.ui_659eba1219), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeatLabels.forEachIndexed { index, label ->
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
                            onMessage(resultMsg)
                        }
                        showAddReminderSheet = false
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.ui_20fac06f98), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                            Text(stringResource(R.string.ui_c32ae9f4a7), color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { state.pet?.id?.let(onEditPet) }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.ui_3efb5fd9ec))
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.ui_e5cd3ab69b), tint = Coral)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    // The NavHost host already applies the status bar inset; a second one leaves an empty band.
                    windowInsets = WindowInsets(0, 0, 0, 0),
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showAddReminderSheet = true },
                    // Icon-only so it never hides the export button underneath.
                    expanded = false,
                    icon = { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.pet_routine_add)) },
                    text = { Text(stringResource(R.string.ui_9d1c0460dd), fontWeight = FontWeight.SemiBold) },
                    shape = RoundedCornerShape(16.dp),
                )
            },
            // Start side: the global AI button already sits at the bottom end, so the two do not stack.
            floatingActionButtonPosition = FabPosition.Start,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { innerPadding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                // Bottom padding lets the last rows scroll clear of both floating buttons.
                contentPadding = PaddingValues(bottom = 140.dp, start = 20.dp, end = 20.dp, top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // ── 1. Pet hero with photo fallback ──────────────────
                item {
                    state.pet?.let { pet ->
                        PetHeroCard(
                            pet = pet,
                            mood = state.mood,
                            personalityMessage = state.personalityMessage,
                            onAddPhoto = { onEditPet(pet.id) },
                            modifier = Modifier.popIn(0),
                        )
                    }
                }

                // ── 2. Quick Log ─────────────────────────────────────
                item {
                    Text(
                        stringResource(R.string.ui_fa35cc0639, petName ?: fallbackSubject),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.popIn(1),
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().popIn(2),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TaskType.entries.take(4).forEach { taskType ->
                            QuickLogAnimatedCard(taskType = taskType, modifier = Modifier.weight(1f)) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                viewModel.logTask(taskType) { msg ->
                                    celebrationMsg = msg
                                    celebrationVisible = true
                                    onMessage(msg)
                                }
                            }
                        }
                    }
                }

                // ── 3. Action Hub ────────────────────────────────────
                item {
                    val toolsSource = remember { MutableInteractionSource() }
                    val statsSource = remember { MutableInteractionSource() }
                    Row(
                        modifier = Modifier.fillMaxWidth().popIn(3),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { state.pet?.id?.let(onOpenTools) },
                            interactionSource = toolsSource,
                            modifier = Modifier.weight(1f).height(50.dp).bouncyPress(toolsSource),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.Handyman, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.ui_01df9def1d))
                        }

                        OutlinedButton(
                            onClick = { state.pet?.id?.let(onOpenStats) },
                            interactionSource = statsSource,
                            modifier = Modifier.weight(1f).height(50.dp).bouncyPress(statsSource),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(stringResource(R.string.ui_ba9f99b6c1))
                        }
                    }
                }
                item {
                    val exportSource = remember { MutableInteractionSource() }
                    if (state.settings.hasFullAccess) {
                        OutlinedButton(
                            onClick = { viewModel.exportPdf(onMessage) },
                            interactionSource = exportSource,
                            modifier = Modifier.fillMaxWidth().height(50.dp).bouncyPress(exportSource).popIn(4),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.pet_export_care_report))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onMessage(context.getString(R.string.report_upgrade)) },
                            interactionSource = exportSource,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PremiumGold),
                            modifier = Modifier.fillMaxWidth().height(50.dp).bouncyPress(exportSource).popIn(4),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.WorkspacePremium, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.pet_export_care_report))
                        }
                    }
                }

                // ── 4. Daily routine: every reminder, with its next time ─────
                item {
                    Text(
                        petName?.let { stringResource(R.string.ui_8d6f61f6bc, it) }
                            ?: stringResource(R.string.pet_routine_title_generic),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.popIn(5),
                    )
                }

                // Nothing is shown until the pet has loaded, so the empty state never flashes.
                if (state.pet != null && routineRows.isEmpty()) {
                    item(key = "routine_empty") {
                        RoutineEmptyState(
                            petName = petName,
                            onAdd = { showAddReminderSheet = true },
                            modifier = Modifier.popIn(6),
                        )
                    }
                } else if (state.pet != null) {
                    itemsIndexed(routineRows, key = { _, row -> row.reminder.id }) { index, row ->
                        RoutineRowCard(
                            row = row,
                            onLog = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                viewModel.logTask(row.reminder.type) { msg ->
                                    celebrationMsg = msg
                                    celebrationVisible = true
                                    onMessage(msg)
                                }
                            },
                            modifier = Modifier.popIn(6 + index),
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.ui_fe2bdf9779),
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

// ── Hero ──────────────────────────────────────────────────────

@Composable
private fun PetHeroCard(
    pet: PetProfile,
    mood: MoodState,
    personalityMessage: String,
    onAddPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val moodLabel = when (mood) {
        MoodState.HAPPY -> stringResource(R.string.tasks_complete)
        MoodState.NEUTRAL -> stringResource(R.string.tasks_pending)
        MoodState.SAD -> stringResource(R.string.tasks_overdue)
    }
    // The personality line is plain secondary text: no quote marks, no accent colour.
    val subtitle = stripQuotes(personalityMessage).ifBlank { moodLabel }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(DeepSage, Sage)))
                .padding(20.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PetPhotoFrame(pet = pet, onAddPhoto = onAddPhoto)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        stringResource(R.string.ui_78c505fe4e, pet.petType.emoji, pet.name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Text(
                        weightLabel(pet.weightKg) + " / " + ageLabel(pet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}

/** Rounded photo on a soft gradient; falls back to the species emoji when there is no photo or it fails. */
@Composable
private fun PetPhotoFrame(pet: PetProfile, onAddPhoto: () -> Unit) {
    val context = LocalContext.current
    val photo = pet.photoUri
    // Reset when the stored photo changes, so a replaced photo gets a fresh load attempt.
    var photoFailed by remember(photo) { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(Cream, Blush))),
            contentAlignment = Alignment.Center
        ) {
            if (photo == null) {
                SpeciesAvatar(emoji = pet.petType.emoji)
            } else {
                SubcomposeAsyncImage(
                    model = context.petPhotoModel(photo),
                    contentDescription = stringResource(R.string.ui_4e1af604ac, pet.name),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = { SpeciesAvatar(emoji = pet.petType.emoji) },
                    error = { SpeciesAvatar(emoji = pet.petType.emoji) },
                    onError = { photoFailed = true },
                )
            }
        }
        if (photo == null || photoFailed) {
            TextButton(onClick = onAddPhoto) {
                Text(
                    stringResource(R.string.pet_add_photo),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun SpeciesAvatar(emoji: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BobbingEmoji(emoji = emoji, size = 60.dp)
    }
}

private fun stripQuotes(text: String): String =
    text.trim().trim('"', '“', '”', '„', '«', '»').trim()

// ── Quick log ─────────────────────────────────────────────────

@Composable
fun QuickLogAnimatedCard(taskType: TaskType, modifier: Modifier = Modifier, onClick: (String) -> Unit) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        onClick = { onClick(PetTimeText.time(context, System.currentTimeMillis())) },
        modifier = modifier.bouncyPress(interactionSource),
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 6.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                taskIcon(taskType),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            // Shrinks long labels ("Grooming", "Fellpflege") to one line instead of breaking mid-word.
            androidx.compose.foundation.text.BasicText(
                petActionLabel(taskType),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                maxLines = 1,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 14.sp),
            )
        }
    }
}

/** Short, capitalised action label ("Feed", "Walk", ...). Shared with the stats screen. */
@Composable
internal fun petActionLabel(taskType: TaskType): String = stringResource(
    when (taskType) {
        TaskType.FEED -> R.string.pet_action_feed
        TaskType.WALK -> R.string.pet_action_walk
        TaskType.MEDICINE -> R.string.pet_action_medicine
        TaskType.GROOMING -> R.string.pet_action_grooming
    }
)

private fun taskIcon(taskType: TaskType): ImageVector = when (taskType) {
    TaskType.FEED -> Icons.Filled.Restaurant
    TaskType.WALK -> Icons.Filled.Route
    TaskType.MEDICINE -> Icons.Filled.Medication
    TaskType.GROOMING -> Icons.Filled.ContentCut
}

// ── Daily routine ─────────────────────────────────────────────

/** One reminder, with today's occurrence when it is due today, otherwise its next time. */
private data class RoutineRow(
    val reminder: Reminder,
    val today: TimelineItem?,
    val nextRunAt: Long?,
)

private fun buildRoutineRows(state: PetDetailUiState): List<RoutineRow> =
    state.reminders
        .map { reminder ->
            RoutineRow(
                reminder = reminder,
                today = state.timeline.firstOrNull { it.reminder.id == reminder.id },
                nextRunAt = state.nextRunAt[reminder.id],
            )
        }
        .sortedWith(
            compareBy<RoutineRow>(
                { it.today?.scheduledAt ?: Long.MAX_VALUE },
                { it.nextRunAt ?: Long.MAX_VALUE },
            ),
        )

@Composable
private fun RoutineRowCard(row: RoutineRow, onLog: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val today = row.today
    val label = petActionLabel(row.reminder.type)
    val clock = (today?.scheduledAt ?: row.nextRunAt)?.let { PetTimeText.time(context, it) }.orEmpty()

    val accent = when (today?.status) {
        TimelineStatus.COMPLETED -> Sage
        TimelineStatus.PENDING -> Honey
        TimelineStatus.OVERDUE -> Coral
        TimelineStatus.MISSED -> Coral.copy(alpha = 0.75f)
        null -> MaterialTheme.colorScheme.primary
    }
    val icon: ImageVector = when (today?.status) {
        TimelineStatus.COMPLETED -> Icons.Filled.CheckCircle
        TimelineStatus.PENDING -> Icons.Filled.AccessTime
        TimelineStatus.OVERDUE, TimelineStatus.MISSED -> Icons.Filled.Warning
        null -> Icons.Filled.EventNote
    }
    val statusText = if (today != null) {
        when (today.status) {
            TimelineStatus.COMPLETED -> stringResource(
                R.string.pet_status_completed,
                PetTimeText.time(context, today.matchedActivity?.timestamp ?: today.scheduledAt),
            )
            TimelineStatus.PENDING -> stringResource(R.string.pet_status_pending, clock)
            TimelineStatus.OVERDUE -> stringResource(R.string.pet_status_overdue, clock)
            TimelineStatus.MISSED -> stringResource(R.string.pet_status_missed, clock)
        }
    } else {
        upcomingText(nextRunAt = row.nextRunAt, clock = clock)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    label,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (statusText.isNotBlank()) {
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }
            if (today != null && today.status != TimelineStatus.COMPLETED) {
                FilledTonalIconButton(onClick = onLog, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.pet_routine_done))
                }
            }
        }
    }
}

@Composable
private fun upcomingText(nextRunAt: Long?, clock: String): String {
    if (nextRunAt == null) return ""
    val context = LocalContext.current
    val day = PetTimeText.dayOf(nextRunAt)
    val today = LocalDate.now()
    return when (day) {
        today -> stringResource(R.string.pet_routine_next_today, clock)
        today.plusDays(1) -> stringResource(R.string.pet_routine_next_tomorrow, clock)
        else -> stringResource(R.string.pet_routine_next_later, PetTimeText.weekday(context, nextRunAt), clock)
    }
}

@Composable
private fun RoutineEmptyState(petName: String?, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BobbingEmoji(emoji = "🐾", size = 56.dp)
            Text(
                stringResource(R.string.pet_routine_empty_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(
                    R.string.pet_routine_empty_body,
                    petName ?: stringResource(R.string.pet_fallback_subject_lower),
                ),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.pet_routine_add), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
