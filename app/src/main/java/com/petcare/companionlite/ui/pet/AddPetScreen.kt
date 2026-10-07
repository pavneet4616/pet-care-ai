package com.petcare.companionlite.ui.pet

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.petcare.companionlite.data.model.PetSaveResult
import com.petcare.companionlite.ui.theme.Cream
import com.petcare.companionlite.ui.theme.DeepSage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPetScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    editPetId: Long? = null,
    viewModel: PetEditorViewModel = viewModel(),
) {
    val state = viewModel.profile
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        viewModel.updatePhoto(uri?.toString())
    }

    LaunchedEffect(editPetId) {
        if (editPetId != null && editPetId > 0) {
            viewModel.loadExistingPet(editPetId)
        }
    }

    LaunchedEffect(viewModel.lastSaveResult) {
        when (val result = viewModel.lastSaveResult) {
            is PetSaveResult.Success -> {
                viewModel.consumeSaveResult()
                onSaved(result.petId)
            }
            else -> Unit
        }
    }

    val screenTitle = if (viewModel.isEditMode) "Edit ${state.name}" else "Add your first pet \uD83D\uDC3E"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::updateName,
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Text("Pet type", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Dog", "Cat", "Bird", "Other").forEach { option ->
                            val selected = state.type.equals(option, ignoreCase = true)
                            AssistChip(
                                onClick = { viewModel.updateType(option) },
                                label = { Text(option) },
                                colors = if (selected) {
                                    AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primary, labelColor = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    AssistChipDefaults.assistChipColors()
                                },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = state.breed,
                        onValueChange = viewModel::updateBreed,
                        label = { Text("Breed (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.weightKg,
                            onValueChange = viewModel::updateWeight,
                            label = { Text("Weight (kg)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.ageYears,
                            onValueChange = viewModel::updateAge,
                            label = { Text("Age (years)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }

                    // Photo selection with preview
                    Button(onClick = {
                        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Text(if (state.photoUri == null) "📷 Choose Photo" else "📷 Change Photo")
                    }

                    // Photo preview
                    if (state.photoUri != null) {
                        AsyncImage(
                            model = state.photoUri,
                            contentDescription = "Selected pet photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }

                    // Save button
                    Button(
                        onClick = viewModel::savePet,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            if (viewModel.isEditMode) "Save Changes" else "Save Pet 🐾",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }

                    // Error feedback
                    when (viewModel.lastSaveResult) {
                        PetSaveResult.FreeLimitReached -> Text(
                            "Free tier allows up to 2 pets. Upgrade to add more.",
                            color = MaterialTheme.colorScheme.error,
                        )
                        PetSaveResult.InvalidInput -> Text(
                            "Please complete name, weight, and age with valid values.",
                            color = MaterialTheme.colorScheme.error,
                        )
                        else -> Unit
                    }

                    // Disclaimer
                    Text(
                        "Not a substitute for veterinary advice",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}
