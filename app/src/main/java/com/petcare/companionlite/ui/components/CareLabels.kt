package com.petcare.companionlite.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.petcare.companionlite.R
import com.petcare.companionlite.core.CareFormats
import com.petcare.companionlite.data.model.PetProfile
import com.petcare.companionlite.data.model.PetType
import com.petcare.companionlite.data.model.HealthCategory
import com.petcare.companionlite.core.labelResource
import com.petcare.companionlite.data.model.TaskType

val LocalWeightUnit=compositionLocalOf { "kg" }
@Composable fun taskLabel(type: TaskType): String = stringResource(type.labelResource())
@Composable fun petTypeLabel(type: PetType): String = stringResource(when(type) {
    PetType.DOG -> R.string.pet_dog
    PetType.CAT -> R.string.pet_cat
    PetType.BIRD -> R.string.pet_bird
    PetType.OTHER -> R.string.pet_other
})
@Composable fun healthLabel(type: HealthCategory): String = stringResource(when(type) {
    HealthCategory.WEIGHT -> R.string.health_weight
    HealthCategory.VET_VISIT -> R.string.health_vet_visit
    HealthCategory.SYMPTOM -> R.string.health_symptom
    HealthCategory.GROOMING -> R.string.care_grooming
    HealthCategory.MOOD -> R.string.health_mood
    HealthCategory.VOMIT -> R.string.health_vomit
})
@Composable fun weightLabel(value:String):String = value.toDoubleOrNull()?.let { CareFormats.weight(it,LocalWeightUnit.current) } ?: stringResource(R.string.weight_unknown)
@Composable fun ageLabel(pet:PetProfile):String {
    val months=pet.ageMonths()
    if (months != null && months < 24) return pluralStringResource(R.plurals.age_months,months.toInt(),months)
    val years=months?.div(12)?.toInt() ?: pet.ageYears.toIntOrNull()
    return years?.let { pluralStringResource(R.plurals.age_years,it,it) } ?: stringResource(R.string.age_unknown)
}

@Composable fun expenseLabel(category: String): String {
    val resource = when(category.substringAfter(' ').lowercase(java.util.Locale.ROOT)) {
        "vet" -> R.string.expense_vet
        "food" -> R.string.expense_food
        "grooming" -> R.string.expense_grooming
        "medicine" -> R.string.expense_medicine
        "toys" -> R.string.expense_toys
        "other" -> R.string.expense_other
        else -> null
    }
    return resource?.let { stringResource(it) } ?: category
}
