@file:OptIn(ExperimentalMaterial3Api::class)

package com.petcare.companionlite.ui.onboarding

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.petcare.companionlite.PetCareApplication
import com.petcare.companionlite.R
import com.petcare.companionlite.core.CareFormats
import com.petcare.companionlite.data.model.PetProfile
import com.petcare.companionlite.data.model.PetSaveResult
import com.petcare.companionlite.data.model.PetType
import com.petcare.companionlite.data.model.Reminder
import com.petcare.companionlite.data.model.TaskType
import com.petcare.companionlite.ui.components.LocalWeightUnit
import com.petcare.companionlite.ui.components.PawBurst
import com.petcare.companionlite.ui.components.bouncyPress
import com.petcare.companionlite.ui.components.gentleBob
import com.petcare.companionlite.ui.components.petTypeLabel
import com.petcare.companionlite.ui.components.popIn
import com.petcare.companionlite.ui.components.wiggleOn
import com.petcare.companionlite.ui.pet.PetBirthDateField
import com.petcare.companionlite.ui.pet.PetPhoto
import com.petcare.companionlite.ui.theme.DeepSage
import com.petcare.companionlite.ui.theme.Sage
import kotlinx.coroutines.launch

/** Below this step height the clock-dial TimePicker (plus pinned buttons) does not fit; use the text TimeInput. */
private val CompactReminderHeight = 760.dp

@Composable
fun OnboardingScreen(
    onComplete: (petId: Long) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as PetCareApplication
    val repository = app.container.repository
    val settingsStore = app.container.settingsStore
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(0) }
    val totalSteps = 6
    val formSteps = 4 // steps 1..4 are shown on the progress bar

    // Pet data being collected
    var petType by remember { mutableStateOf(PetType.DOG) }
    var petName by remember { mutableStateOf("") }
    var petBreed by remember { mutableStateOf("") }
    var petWeight by remember { mutableStateOf("") }
    var petBirthDate by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<String?>(null) }
    var savedPetId by remember { mutableStateOf(0L) }

    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val weightUnit = LocalWeightUnit.current

    // Reminder data, using the device 12/24h setting
    val timePickerState = rememberTimePickerState(
        initialHour = 8,
        initialMinute = 0,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        photoUri = uri?.toString()
    }

    val petNameOrDefault = petName.ifBlank { stringResource(R.string.ui_5ec089c9b1) }
    val progressLabel = stringResource(R.string.onb_step_progress, step, formSteps)

    // Save pet at step 3 (photo), create the reminder at step 4.
    fun savePetAndProceed() {
        if (saving) return
        saving = true
        scope.launch {
            val parsedWeight = CareFormats.parseNumber(petWeight)
            val profile = PetProfile(
                id = 0,
                name = petName.trim().ifBlank { context.getString(R.string.ui_f08cf0fe15) },
                type = petType.label,
                breed = petBreed.trim(),
                weightKg = parsedWeight?.let { CareFormats.toKg(it, weightUnit).toString() }.orEmpty(),
                birthDate = petBirthDate.trim(),
                photoUri = photoUri,
            )
            val result = if (petWeight.isNotBlank() && parsedWeight == null) {
                PetSaveResult.InvalidInput
            } else {
                runCatching { repository.savePet(profile) }.getOrDefault(PetSaveResult.InvalidInput)
            }
            saving = false
            if (result is PetSaveResult.Success) {
                savedPetId = result.petId
                saveError = null
                step = 4 // reminder step
            } else {
                saveError = context.getString(
                    if (result == PetSaveResult.PhotoError) R.string.photo_save_failed else R.string.invalid_pet_details,
                )
            }
        }
    }

    fun saveReminderAndFinish() {
        scope.launch {
            if (savedPetId > 0) {
                repository.upsertReminder(
                    Reminder(
                        petId = savedPetId,
                        type = TaskType.FEED,
                        hour = timePickerState.hour,
                        minute = timePickerState.minute,
                        repeatDays = setOf(1, 2, 3, 4, 5, 6, 7), // Every day
                    )
                )
            }
            step = 5 // Congratulations
        }
    }

    fun finishOnboarding() {
        scope.launch {
            settingsStore.setOnboardingCompleted(true)
            onComplete(savedPetId)
        }
    }

    // Steps 1-3 can go back. Once the pet is saved (step 4+) the flow only moves forward.
    fun goBack() {
        if (step in 1..3) step -= 1
    }
    BackHandler(enabled = step in 1..3) { goBack() }

    val animatedProgress by animateFloatAsState(
        targetValue = (step.toFloat() / formSteps).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 450),
        label = "onboarding-progress",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Progress bar (hidden on welcome & congrats)
        AnimatedVisibility(visible = step in 1..formSteps) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .semantics { contentDescription = progressLabel },
                color = Sage,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                // Forward slides in from the right, back slides in from the left.
                val forward = targetState > initialState
                (slideInHorizontally(animationSpec = tween(360)) { if (forward) it / 3 else -it / 3 } +
                    fadeIn(animationSpec = tween(360))) togetherWith
                    (slideOutHorizontally(animationSpec = tween(360)) { if (forward) -it / 3 else it / 3 } +
                        fadeOut(animationSpec = tween(180)))
            },
            label = "onboarding-step",
            modifier = Modifier.weight(1f),
        ) { currentStep ->
            when (currentStep) {
                0 -> WelcomeStep(onNext = { step = 1 })
                1 -> PetTypeStep(
                    selected = petType,
                    onSelect = { petType = it },
                    onNext = { step = 2 },
                    onBack = { goBack() },
                )
                2 -> NameBreedStep(
                    name = petName,
                    breed = petBreed,
                    weight = petWeight,
                    birthDate = petBirthDate,
                    onNameChange = { petName = it },
                    onBreedChange = { petBreed = it },
                    onWeightChange = { petWeight = it },
                    onBirthDateChange = { petBirthDate = it },
                    petType = petType,
                    onNext = { step = 3 },
                    onBack = { goBack() },
                )
                3 -> PhotoStep(
                    petName = petNameOrDefault,
                    petType = petType,
                    photoUri = photoUri,
                    saving = saving,
                    errorText = saveError,
                    onPickPhoto = {
                        photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onNext = { savePetAndProceed() },
                    onBack = { goBack() },
                )
                4 -> ReminderStep(
                    petName = petNameOrDefault,
                    timePickerState = timePickerState,
                    onNext = { saveReminderAndFinish() },
                    onSkip = { step = 5 },
                )
                5 -> CongratsStep(
                    petName = petNameOrDefault,
                    petType = petType,
                    onFinish = { finishOnboarding() },
                )
            }
        }
    }
}

// ── Shared step layout ───────────────────────────────────────────
/**
 * Scrollable content area with the action buttons pinned below it. Buttons stay reachable on any
 * screen size or font scale, and the content scrolls when it does not fit.
 */
@Composable
private fun StepLayout(
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val visibleHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // Minimum height = visible area, so Arrangement.Center works for short content.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 16.dp)
                        .heightIn(min = visibleHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = verticalArrangement,
                    content = content,
                )
            }
        }
        StepActions(content = actions)
    }
}

@Composable
private fun StepActions(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun PrimaryStepButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(containerColor = Sage),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .bouncyPress(interaction),
        shape = RoundedCornerShape(16.dp),
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interaction,
        content = content,
    )
}

@Composable
private fun StepBackButton(onBack: () -> Unit) {
    TextButton(onClick = onBack) {
        Text(
            stringResource(R.string.ui_b52b36b726),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
}

// ── Step 0: Welcome ──────────────────────────────────────────────
@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    StepLayout(
        verticalArrangement = Arrangement.Center,
        actions = {
            PrimaryStepButton(onClick = onNext) {
                Text(stringResource(R.string.ui_bd2cb05177), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        },
    ) {
        Text(
            "🐾",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier
                .popIn(0)
                .gentleBob(amplitude = 10.dp, periodMillis = 1400),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.ui_5a6c406b47),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.popIn(1),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.ui_cbe0d45dd3),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            modifier = Modifier.popIn(2),
        )
    }
}

// ── Step 1: Pet Type ─────────────────────────────────────────────
@Composable
private fun PetTypeStep(
    selected: PetType,
    onSelect: (PetType) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    StepLayout(
        actions = {
            PrimaryStepButton(onClick = onNext) {
                Text(stringResource(R.string.ui_2e02623966), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            StepBackButton(onBack)
        },
    ) {
        Text(
            stringResource(R.string.ui_aa185a5a19),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.popIn(0),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onb_pet_type_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        PetType.entries.chunked(2).forEachIndexed { rowIndex, rowTypes ->
            if (rowIndex > 0) Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowTypes.forEachIndexed { colIndex, type ->
                    PetTypeCard(
                        type = type,
                        selected = type == selected,
                        index = rowIndex * 2 + colIndex,
                        onClick = { onSelect(type) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PetTypeCard(
    type: PetType,
    selected: Boolean,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "petTypeScale",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) Sage else Color.Transparent,
        animationSpec = tween(durationMillis = 250),
        label = "petTypeBorder",
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) Sage.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(durationMillis = 250),
        label = "petTypeContainer",
    )
    Card(
        onClick = onClick,
        modifier = modifier
            .popIn(index)
            .bouncyPress(interaction)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        interactionSource = interaction,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(2.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                type.emoji,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.wiggleOn(if (selected) type else null),
            )
            Text(
                petTypeLabel(type),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) DeepSage else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Step 2: Name + Breed + Weight + Birth date ───────────────────
@Composable
private fun NameBreedStep(
    name: String,
    breed: String,
    weight: String,
    birthDate: String,
    onNameChange: (String) -> Unit,
    onBreedChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onBirthDateChange: (String) -> Unit,
    petType: PetType,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val fieldShape = RoundedCornerShape(16.dp)
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        // The list scrolls with the keyboard open; the actions below are lifted above the IME.
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(top = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.ui_3d6d58eab3, petType.emoji, petTypeLabel(petType)),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.popIn(0),
                )
            }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.ui_709a23220f)) },
                    placeholder = { Text(stringResource(R.string.ui_4c0ac94037)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = fieldShape,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                )
            }
            item {
                OutlinedTextField(
                    value = breed,
                    onValueChange = onBreedChange,
                    label = { Text(stringResource(R.string.ui_7622f8804d)) },
                    placeholder = { Text(stringResource(R.string.ui_114c8a28d7)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = fieldShape,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                )
            }
            item {
                OutlinedTextField(
                    value = weight,
                    onValueChange = onWeightChange,
                    label = { Text(stringResource(if (LocalWeightUnit.current == "lb") R.string.unit_lb else R.string.unit_kg)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = fieldShape,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                )
            }
            item {
                PetBirthDateField(
                    value = birthDate,
                    onValueChange = onBirthDateChange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        StepActions {
            PrimaryStepButton(onClick = onNext, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.ui_2e02623966), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            StepBackButton(onBack)
        }
    }
}

// ── Step 3: Photo ────────────────────────────────────────────────
@Composable
private fun PhotoStep(
    petName: String,
    petType: PetType,
    photoUri: String?,
    saving: Boolean,
    errorText: String?,
    onPickPhoto: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    StepLayout(
        verticalArrangement = Arrangement.Center,
        actions = {
            errorText?.let {
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            PrimaryStepButton(onClick = onNext, enabled = !saving) {
                Text(
                    if (photoUri != null) stringResource(R.string.ui_2e02623966) else stringResource(R.string.ui_cfecd1316b),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            StepBackButton(onBack)
        },
    ) {
        Text(
            stringResource(R.string.ui_ae601382c0, petName),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.popIn(0),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.ui_f01392ce5d),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.popIn(1),
        )
        Spacer(Modifier.height(32.dp))

        if (photoUri != null) {
            // Subtle pop each time a photo is chosen.
            val pop = remember(photoUri) { Animatable(0.8f) }
            LaunchedEffect(photoUri) {
                pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
            }
            PetPhoto(
                photoUri = photoUri,
                petType = petType,
                contentDescription = stringResource(R.string.ui_4e1af604ac, petName),
                modifier = Modifier
                    .size(200.dp)
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                    .clip(CircleShape),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.onb_photo_looks_good),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = DeepSage,
                modifier = Modifier.popIn(0),
            )
            TextButton(onClick = onPickPhoto) {
                Text(stringResource(R.string.ui_ed5690c3ae), color = Sage)
            }
        } else {
            val interaction = remember { MutableInteractionSource() }
            Card(
                onClick = onPickPhoto,
                modifier = Modifier
                    .size(200.dp)
                    .bouncyPress(interaction),
                interactionSource = interaction,
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Sage,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.ui_22c76fc2c8), color = Sage, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ── Step 4: First Reminder ───────────────────────────────────────
@Composable
private fun ReminderStep(
    petName: String,
    timePickerState: TimePickerState,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxHeight < CompactReminderHeight
        StepLayout(
            actions = {
                PrimaryStepButton(onClick = onNext) {
                    Text(stringResource(R.string.ui_20fac06f98), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = onSkip) {
                    Text(
                        stringResource(R.string.ui_6fc09607ae),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
            },
        ) {
            Text(
                stringResource(R.string.ui_d050b437ca, petName),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.popIn(0),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.ui_5aa334466c),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.popIn(1),
            )
            Spacer(Modifier.height(24.dp))
            if (compact) {
                TimeInput(state = timePickerState)
            } else {
                TimePicker(state = timePickerState)
            }
        }
    }
}

// ── Step 5: Congratulations ──────────────────────────────────────
@Composable
private fun CongratsStep(
    petName: String,
    petType: PetType,
    onFinish: () -> Unit,
) {
    StepLayout(
        verticalArrangement = Arrangement.Center,
        actions = {
            PrimaryStepButton(
                onClick = onFinish,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(listOf(DeepSage, Sage)),
                            RoundedCornerShape(16.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.ui_1a284c7ba7, petName),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }
        },
    ) {
        // Celebration: bobbing party emoji with a one-shot paw burst.
        Box(contentAlignment = Alignment.Center, modifier = Modifier.popIn(0)) {
            Text(
                "🎉",
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.gentleBob(amplitude = 10.dp, periodMillis = 900),
            )
            PawBurst(trigger = Unit)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.ui_058a813016),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.popIn(1),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.ui_5f3f0e5677, petType.emoji, petName),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.popIn(2),
        )
    }
}
