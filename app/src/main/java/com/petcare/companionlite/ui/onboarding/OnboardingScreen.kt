package com.petcare.companionlite.ui.onboarding

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
    var petAge by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<String?>(null) }
    var savedPetId by remember { mutableStateOf(0L) }

    // Reminder data
    val timePickerState = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = false)

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        photoUri = uri?.toString()
    }

    // Save pet at step 4 (after photo), create reminder at step 5
    fun savePetAndProceed() {
        scope.launch {
            val profile = PetProfile(
                id = 0,
                name = petName.trim().ifBlank { "My Pet" },
                type = petType.label,
                breed = petBreed.trim(),
                weightKg = petWeight.trim().ifBlank { "0" },
                ageYears = petAge.trim().ifBlank { "0" },
                photoUri = photoUri,
            )
            val result = repository.savePet(profile)
            if (result is com.petcare.companionlite.data.model.PetSaveResult.Success) {
                savedPetId = result.petId
                step = 4 // Move to reminder step
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
                    age = petAge,
                    onNameChange = { petName = it },
                    onBreedChange = { petBreed = it },
                    onWeightChange = { petWeight = it },
                    onAgeChange = { petAge = it },
                    petType = petType,
                    onNext = { step = 3 }
                )
                3 -> PhotoStep(
                    petName = petName.ifBlank { "your pet" },
                    photoUri = photoUri,
                    onPickPhoto = {
                        photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onNext = { savePetAndProceed() },
                    onSkip = { savePetAndProceed() }
                )
                4 -> ReminderStep(
                    petName = petName.ifBlank { "your pet" },
                    timePickerState = timePickerState,
                    onNext = { saveReminderAndFinish() },
                    onSkip = {
                        scope.launch { step = 5 }
                    }
                )
                5 -> CongratsStep(
                    petName = petName.ifBlank { "your pet" },
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
            "Welcome to PetCare",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Let's set up your first companion ❤️",
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
            Text("Get Started", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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
            "What kind of pet do you have?",
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
                            type.label,
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
            Text("Continue", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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
                "${petType.emoji} What's your ${petType.label.lowercase()}'s name?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Name") },
                placeholder = { Text("e.g., Shadow, Luna, Mango...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
        }
        item {
            OutlinedTextField(
                value = breed,
                onValueChange = onBreedChange,
                label = { Text("Breed (optional)") },
                placeholder = { Text("e.g., Persian, Labrador...") },
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
                    label = { Text("Weight (kg)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                OutlinedTextField(
                    value = age,
                    onValueChange = onAgeChange,
                    label = { Text("Age (years)") },
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
                Text("Continue", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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
            "📷 Add a photo of $petName!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "This makes everything more personal",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        if (photoUri != null) {
            AsyncImage(
                model = photoUri,
                contentDescription = "$petName photo",
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(28.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onPickPhoto) {
                Text("Change photo", color = Sage)
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
                        Text("Tap to add", color = Sage, fontWeight = FontWeight.SemiBold)
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
                if (photoUri != null) "Continue" else "Continue without photo",
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
            "🍖 When should we remind you to feed $petName?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "You can add more reminders later",
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
            Text("Set Reminder", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSkip) {
            Text("Skip for now", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
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
            "You're all set!",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "${petType.emoji} $petName is ready for a wonderful day!",
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
                    "Meet $petName ❤️",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }
        }
    }
}
