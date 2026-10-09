package com.petcare.companionlite.ui.pet

import com.petcare.companionlite.R

import androidx.compose.ui.res.stringResource

import com.petcare.companionlite.core.petPhotoModel
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
    val context = androidx.compose.ui.platform.LocalContext.current
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

    val screenTitle = if (viewModel.isEditMode) "Edit ${state.name}" else stringResource(R.string.ui_10a8b386c1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.ui_b52b36b726)) }
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
                        label = { Text(stringResource(R.string.ui_709a23220f)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Text(stringResource(R.string.ui_115c11769d), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Dog", "Cat", "Bird", "Other").forEach { option ->
                            val selected = state.type.equals(option, ignoreCase = true)
                            AssistChip(
                                onClick = { viewModel.updateType(option) },
                                label = { Text(com.petcare.companionlite.ui.components.petTypeLabel(com.petcare.companionlite.data.model.PetType.fromString(option))) },
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
                        label = { Text(stringResource(R.string.ui_7622f8804d)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.weightKg,
                            onValueChange = viewModel::updateWeight,
                            label = { Text(stringResource(if (com.petcare.companionlite.ui.components.LocalWeightUnit.current == stringResource(R.string.ui_cba4181417)) R.string.unit_lb else R.string.unit_kg)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.ageYears,
                            onValueChange = viewModel::updateAge,
                            label = { Text(stringResource(R.string.ui_3a996f0dac)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }

                    // Photo selection with preview
                    OutlinedTextField(
                        value = state.birthDate,
                        onValueChange = viewModel::updateBirthDate,
                        label = { Text(androidx.compose.ui.res.stringResource(com.petcare.companionlite.R.string.birth_date_optional)) },
                        placeholder = { Text(stringResource(R.string.ui_d3f8f7b810)) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                    )

                    // Photo selection with preview
                    Button(onClick = {
                        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Text(if (state.photoUri == null) stringResource(R.string.ui_cc4c1c5d8d) else stringResource(R.string.ui_19d722403a))
                    }

                    // Photo preview
                    if (state.photoUri != null) {
                        AsyncImage(
                            model = context.petPhotoModel(state.photoUri),
                            contentDescription = stringResource(R.string.ui_1d4b6e854f),
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
                            if (viewModel.isEditMode) stringResource(R.string.ui_fa2984b367) else stringResource(R.string.ui_dca14980d9),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }

                    // Error feedback
                    when (viewModel.lastSaveResult) {
                        PetSaveResult.FreeLimitReached -> Text(
                            stringResource(R.string.ui_8bdbe90db6),
                            color = MaterialTheme.colorScheme.error,
                        )
                        PetSaveResult.PhotoError -> Text(stringResource(R.string.photo_save_failed))
                        PetSaveResult.InvalidInput -> Text(
                            stringResource(R.string.ui_4500c81a97),
                            color = MaterialTheme.colorScheme.error,
                        )
                        else -> Unit
                    }

                    // Disclaimer
                    Text(
                        stringResource(R.string.ui_fe2bdf9779),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}
