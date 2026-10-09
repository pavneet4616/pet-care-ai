package com.petcare.companionlite.ui.onboarding

import com.petcare.companionlite.R

import androidx.compose.ui.res.stringResource

import com.petcare.companionlite.core.petPhotoModel
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.petcare.companionlite.PetCareApplication
import com.petcare.companionlite.data.model.PetProfile
import com.petcare.companionlite.data.model.PetType
import com.petcare.companionlite.data.model.Reminder
import com.petcare.companionlite.data.model.TaskType
import com.petcare.companionlite.ui.theme.DeepSage
import com.petcare.companionlite.ui.theme.Honey
import com.petcare.companionlite.ui.theme.Sage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    val weightUnit = com.petcare.companionlite.ui.components.LocalWeightUnit.current

    // Reminder data
    val timePickerState = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = false)

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        photoUri = uri?.toString()
    }

    // Save pet at step 4 (after photo), create reminder at step 5
    fun savePetAndProceed() {
        if (saving) return
        saving = true
        scope.launch {
            val profile = PetProfile(
                id = 0,
                name = petName.trim().ifBlank { context.getString(R.string.ui_f08cf0fe15) },
                type = petType.label,
                breed = petBreed.trim(),
                weightKg = com.petcare.companionlite.core.CareFormats.parseNumber(petWeight)?.let { com.petcare.companionlite.core.CareFormats.toKg(it, weightUnit).toString() }.orEmpty(),
                birthDate = petBirthDate.trim(),
                photoUri = photoUri,
            )
            val result = if (petWeight.isNotBlank() && com.petcare.companionlite.core.CareFormats.parseNumber(petWeight) == null) com.petcare.companionlite.data.model.PetSaveResult.InvalidInput else runCatching { repository.savePet(profile) }.getOrDefault(com.petcare.companionlite.data.model.PetSaveResult.InvalidInput)
            saving = false
            if (result is com.petcare.companionlite.data.model.PetSaveResult.Success) {
                savedPetId = result.petId
                step = 4 // Move to reminder step
                saveError = null
            } else {
                saveError = context.getString(if (result == com.petcare.companionlite.data.model.PetSaveResult.PhotoError) R.string.photo_save_failed else R.string.invalid_pet_details)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        // Progress bar (hidden on welcome & congrats)
        if (step in 1..4) {
            LinearProgressIndicator(
                progress = { step.toFloat() / (totalSteps - 1) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Sage,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 3 } + fadeOut())
            },
            label = "onboarding-step",
            modifier = Modifier.weight(1f)
        ) { currentStep ->
            when (currentStep) {
                0 -> WelcomeStep(onNext = { step = 1 })
                1 -> PetTypeStep(
                    selected = petType,
                    onSelect = { petType = it },
                    onNext = { step = 2 }
                )
                2 -> NameBreedStep(
                    name = petName,
                    breed = petBreed,
                    weight = petWeight,
                    age = petBirthDate,
                    onNameChange = { petName = it },
                    onBreedChange = { petBreed = it },
                    onWeightChange = { petWeight = it },
                    onAgeChange = { petBirthDate = it },
                    petType = petType,
                    onNext = { step = 3 }
                )
                3 -> PhotoStep(
                    petName = petName.ifBlank { stringResource(R.string.ui_5ec089c9b1) },
                    photoUri = photoUri,
                    onPickPhoto = {
                        photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onNext = { savePetAndProceed() },
                    onSkip = { savePetAndProceed() }
                )
                4 -> ReminderStep(
                    petName = petName.ifBlank { stringResource(R.string.ui_5ec089c9b1) },
                    timePickerState = timePickerState,
                    onNext = { saveReminderAndFinish() },
                    onSkip = {
                        scope.launch { step = 5 }
                    }
                )
                5 -> CongratsStep(
                    petName = petName.ifBlank { stringResource(R.string.ui_5ec089c9b1) },
                    petType = petType,
                    onFinish = { finishOnboarding() }
                )
            }
        }
    }
}

// ── Step 0: Welcome ──────────────────────────────────────────────
@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "welcome-pulse").animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulse"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "🐾",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse }
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.ui_5a6c406b47),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.ui_cbe0d45dd3),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Sage)
        ) {
            Text(stringResource(R.string.ui_bd2cb05177), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ── Step 1: Pet Type ─────────────────────────────────────────────
@Composable
private fun PetTypeStep(
    selected: PetType,
    onSelect: (PetType) -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(R.string.ui_aa185a5a19),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PetType.entries.forEach { type ->
                val isSelected = type == selected
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(type) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Sage.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Sage) else null,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(type.emoji, style = MaterialTheme.typography.headlineLarge)
                        Text(
                            com.petcare.companionlite.ui.components.petTypeLabel(type),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Sage else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Sage)
        ) {
            Text(stringResource(R.string.ui_2e02623966), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ── Step 2: Name + Breed + Weight + Age ──────────────────────────
@Composable
private fun NameBreedStep(
    name: String,
    breed: String,
    weight: String,
    age: String,
    onNameChange: (String) -> Unit,
    onBreedChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onAgeChange: (String) -> Unit,
    petType: PetType,
    onNext: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.ui_3d6d58eab3, petType.emoji, com.petcare.companionlite.ui.components.petTypeLabel(petType)),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
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
                shape = RoundedCornerShape(16.dp),
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
                shape = RoundedCornerShape(16.dp),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = onWeightChange,
                    label = { Text(stringResource(if (com.petcare.companionlite.ui.components.LocalWeightUnit.current == "lb") R.string.unit_lb else R.string.unit_kg)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                OutlinedTextField(
                    value = age,
                    onValueChange = onAgeChange,
                    label = { Text(stringResource(R.string.birth_date_optional)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onNext,
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Sage)
            ) {
                Text(stringResource(R.string.ui_2e02623966), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

// ── Step 3: Photo ────────────────────────────────────────────────
@Composable
private fun PhotoStep(
    petName: String,
    photoUri: String?,
    onPickPhoto: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(R.string.ui_ae601382c0, petName),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.ui_f01392ce5d),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        if (photoUri != null) {
            AsyncImage(
                model = androidx.compose.ui.platform.LocalContext.current.petPhotoModel(photoUri),
                contentDescription = stringResource(R.string.ui_4e1af604ac, petName),
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(28.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onPickPhoto) {
                Text(stringResource(R.string.ui_ed5690c3ae), color = Sage)
            }
        } else {
            Card(
                modifier = Modifier
                    .size(200.dp)
                    .clickable(onClick = onPickPhoto),
                shape = RoundedCornerShape(28.dp),
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

        Spacer(Modifier.weight(1f))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Sage)
        ) {
            Text(
                if (photoUri != null) stringResource(R.string.ui_2e02623966) else stringResource(R.string.ui_cfecd1316b),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

// ── Step 4: First Reminder ───────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderStep(
    petName: String,
    timePickerState: androidx.compose.material3.TimePickerState,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.ui_d050b437ca, petName),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.ui_5aa334466c),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        TimePicker(state = timePickerState)

        Spacer(Modifier.weight(1f))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Sage)
        ) {
            Text(stringResource(R.string.ui_20fac06f98), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSkip) {
            Text(stringResource(R.string.ui_6fc09607ae), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
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
    val bounce by rememberInfiniteTransition(label = "congrats-bounce").animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "bounce",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "🎉",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.graphicsLayer { scaleX = bounce; scaleY = bounce },
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.ui_058a813016),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.ui_5f3f0e5677, petType.emoji, petName),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
            ),
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
    }
}
