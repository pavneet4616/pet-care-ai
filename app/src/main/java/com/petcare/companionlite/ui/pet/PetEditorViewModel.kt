package com.petcare.companionlite.ui.pet

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.petcare.companionlite.PetCareApplication
import com.petcare.companionlite.data.model.PetProfile
import com.petcare.companionlite.data.model.PetSaveResult
import kotlinx.coroutines.launch

class PetEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as PetCareApplication).container.repository

    var profile by mutableStateOf(PetProfile())
        private set

    var lastSaveResult by mutableStateOf<PetSaveResult?>(null)
        private set

    var isEditMode by mutableStateOf(false)
        private set

    fun loadExistingPet(petId: Long) {
        if (isEditMode) return // already loaded
        viewModelScope.launch {
            val existing = repository.getPet(petId)
            if (existing != null) {
                profile = existing
                isEditMode = true
            }
        }
    }

    fun updateName(value: String) {
        profile = profile.copy(name = value)
    }

    fun updateType(value: String) {
        profile = profile.copy(type = value)
    }

    fun updateBreed(value: String) {
        profile = profile.copy(breed = value)
    }

    fun updateWeight(value: String) {
        profile = profile.copy(weightKg = value)
    }

    fun updateAge(value: String) {
        profile = profile.copy(ageYears = value)
    }

    fun updatePhoto(value: String?) {
        // Take persistable URI permission so the photo survives app restart
        if (value != null) {
            try {
                val uri = android.net.Uri.parse(value)
                getApplication<PetCareApplication>().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
                // Permission may not be available for all URIs; continue anyway
            }
        }
        profile = profile.copy(photoUri = value)
    }

    fun savePet() {
        viewModelScope.launch {
            lastSaveResult = repository.savePet(profile)
        }
    }

    fun consumeSaveResult() {
        lastSaveResult = null
    }
}
