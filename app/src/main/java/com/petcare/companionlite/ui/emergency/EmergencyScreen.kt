package com.petcare.companionlite.ui.emergency

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.petcare.companionlite.R

/** Veterinary contact is the primary action. Country choice never uses location permission. */
@Composable
fun EmergencyScreen(onAskAi: () -> Unit = {}) {
    val context = LocalContext.current
    var region by remember { mutableStateOf(android.content.res.Resources.getSystem().configuration.locales[0].country) }
    fun open(uri: String) {
        runCatching { context.startActivity(Intent(if (uri.startsWith("tel:")) Intent.ACTION_DIAL else Intent.ACTION_VIEW, Uri.parse(uri))) }
            .onFailure {
                if (uri.startsWith("geo:")) runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/maps/search/emergency+veterinarian")))
                }
            }
    }
    val guides = listOf(
        R.string.emergency_choking to listOf(R.string.emergency_breathing, R.string.emergency_mouth, R.string.emergency_transport),
        R.string.emergency_poison to listOf(R.string.emergency_poison_call, R.string.emergency_poison_no_remedy, R.string.emergency_poison_container),
        R.string.emergency_injury to listOf(R.string.emergency_bleeding, R.string.emergency_objects, R.string.emergency_limit_movement),
    )
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text(stringResource(R.string.emergency_title),style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.emergency_vet_first))
            Button(onClick={ open("geo:0,0?q=emergency+veterinarian") },modifier=Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.emergency_find_vet))
            }
        }
        item {
            Text(stringResource(R.string.emergency_region),style=MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("US" to R.string.region_us,"GB" to R.string.region_uk,"OTHER" to R.string.region_other).forEach { (id,label) ->
                    FilterChip(selected=region==id,onClick={region=id},label={Text(stringResource(label))})
                }
            }
            if (region=="US") {
                OutlinedButton(onClick={open("tel:+18884264435")},modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.emergency_aspca)) }
                OutlinedButton(onClick={open("tel:+18557647661")},modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.emergency_us_poison)) }
            }
            if (region=="GB") {
                OutlinedButton(onClick={open("tel:+441202509000")},modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.emergency_uk_poison)) }
            }
            Text(stringResource(R.string.emergency_poison_fee))
        }
        guides.forEach { (title,steps) ->
            item {
                Card {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(title),style=MaterialTheme.typography.titleLarge)
                        steps.forEach { Text(stringResource(it)) }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick={open("https://www.avma.org/resources-tools/pet-owners/emergency-care/pet-first-aid-basic-procedures")}) { Text(stringResource(R.string.emergency_first_aid_source)) }
            Text(stringResource(R.string.emergency_ai_optional))
            TextButton(onClick=onAskAi) { Text(stringResource(R.string.emergency_ask_ai)) }
        }
    }
}
