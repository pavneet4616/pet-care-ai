@file:OptIn(ExperimentalMaterial3Api::class)

package com.petcare.companionlite.ui.pet

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.petcare.companionlite.R
import com.petcare.companionlite.core.petPhotoModel
import com.petcare.companionlite.data.model.PetSaveResult
import com.petcare.companionlite.data.model.PetType
import com.petcare.companionlite.ui.components.BobbingEmoji
import com.petcare.companionlite.ui.components.LocalWeightUnit
import com.petcare.companionlite.ui.components.petTypeLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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

    val screenTitle = if (viewModel.isEditMode) {
        stringResource(R.string.onb_edit_title, state.name)
    } else {
        stringResource(R.string.ui_10a8b386c1)
    }
    // Same unit check as onboarding: compare the unit code, never a translated label.
    val weightLabel = stringResource(if (LocalWeightUnit.current == "lb") R.string.unit_lb else R.string.unit_kg)

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
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    Text(stringResource(R.string.ui_115c11769d), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Dog", "Cat", "Bird", "Other").forEach { option ->
                            val selected = state.type.equals(option, ignoreCase = true)
                            AssistChip(
                                onClick = { viewModel.updateType(option) },
                                label = { Text(petTypeLabel(PetType.fromString(option))) },
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
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.weightKg,
                            onValueChange = viewModel::updateWeight,
                            label = { Text(weightLabel) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next,
                            ),
                        )
                        OutlinedTextField(
                            value = state.ageYears,
                            onValueChange = viewModel::updateAge,
                            label = { Text(stringResource(R.string.ui_3a996f0dac)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }

                    // Birth date: date picker, stored as ISO yyyy-MM-dd (or blank when unknown).
                    PetBirthDateField(
                        value = state.birthDate,
                        onValueChange = viewModel::updateBirthDate,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Photo selection
                    Button(onClick = {
                        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Text(if (state.photoUri == null) stringResource(R.string.ui_cc4c1c5d8d) else stringResource(R.string.ui_19d722403a))
                    }

                    // Photo preview (species emoji if the photo cannot be loaded)
                    state.photoUri?.let { photo ->
                        PetPhoto(
                            photoUri = photo,
                            petType = PetType.fromString(state.type),
                            contentDescription = stringResource(R.string.ui_1d4b6e854f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(16.dp)),
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

/**
 * Read-only birth date field that opens a Material3 DatePicker (today or earlier only).
 * [value] and [onValueChange] use ISO yyyy-MM-dd (LocalDate.toString()), or "" when unknown.
 * Shared with onboarding.
 */
@Composable
internal fun PetBirthDateField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val savedDate = remember(value) { runCatching { LocalDate.parse(value) }.getOrNull() }
    val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val shownText = savedDate?.format(dateFormatter) ?: value
    val label = stringResource(R.string.birth_date_optional)
    val hint = stringResource(R.string.onb_birth_date_hint)
    val shape = RoundedCornerShape(16.dp)

    Box(modifier = modifier) {
        OutlinedTextField(
            value = shownText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(hint) },
            trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
            singleLine = true,
            shape = shape,
            modifier = Modifier.fillMaxWidth(),
        )
        // A read-only field swallows taps, so a transparent overlay opens the picker instead.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .clickable(role = Role.Button, onClick = { showPicker = true })
                .semantics { contentDescription = "$label, ${shownText.ifBlank { hint }}" },
        )
    }

    if (showPicker) {
        val selectable = remember { NoFutureDates(LocalDate.now()) }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = savedDate?.toUtcMillis(),
            selectableDates = selectable,
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onValueChange(it.toUtcLocalDate().toString()) }
                        showPicker = false
                    },
                    enabled = pickerState.selectedDateMillis != null,
                ) {
                    Text(stringResource(R.string.ui_9ce3bd4224))
                }
            },
            dismissButton = {
                // "Not sure" clears the date (unknown birth date).
                TextButton(
                    onClick = {
                        onValueChange("")
                        showPicker = false
                    },
                ) {
                    Text(stringResource(R.string.onb_birth_date_not_sure))
                }
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Pet photo that falls back to the species emoji when the image cannot be loaded. Shared with onboarding. */
@Composable
internal fun PetPhoto(
    photoUri: String,
    petType: PetType,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = context.petPhotoModel(photoUri),
        contentDescription = contentDescription,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = ContentScale.Crop,
        error = {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BobbingEmoji(emoji = petType.emoji, size = 96.dp)
            }
        },
    )
}

/** Allows today and earlier only. Millis are UTC midnight, as DatePickerState uses. */
private class NoFutureDates(today: LocalDate) : SelectableDates {
    private val todayMillis = today.toUtcMillis()
    private val todayYear = today.year

    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis

    override fun isSelectableYear(year: Int): Boolean = year <= todayYear
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
