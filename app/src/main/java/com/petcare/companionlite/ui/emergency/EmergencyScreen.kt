package com.petcare.companionlite.ui.emergency

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.petcare.companionlite.R
import com.petcare.companionlite.ui.components.bouncyPress
import com.petcare.companionlite.ui.components.popIn
import com.petcare.companionlite.ui.theme.EmergencyRed

private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
private const val VET_SEARCH_GEO = "geo:0,0?q=emergency+veterinarian"
private const val VET_SEARCH_WEB = "https://www.google.com/maps/search/?api=1&query=emergency+veterinarian"
private const val AVMA_FIRST_AID_URL = "https://www.avma.org/resources-tools/pet-owners/emergency-care/pet-first-aid-basic-procedures"

/** Region chips: code to label. */
private val regionChips = listOf(
    "US" to R.string.region_us,
    "GB" to R.string.region_uk,
    "OTHER" to R.string.region_other,
)

private data class Hotline(val label: Int, val tel: String)

private fun hotlinesFor(region: String): List<Hotline> = when (region) {
    "US" -> listOf(
        Hotline(R.string.emergency_aspca, "tel:+18884264435"),
        Hotline(R.string.emergency_us_poison, "tel:+18557647661"),
    )
    "GB" -> listOf(Hotline(R.string.emergency_uk_poison, "tel:+441202509000"))
    else -> emptyList()
}

private data class FirstAidGuide(val icon: ImageVector, val title: Int, val steps: List<Int>)

private val firstAidGuides = listOf(
    FirstAidGuide(
        icon = Icons.Filled.Air,
        title = R.string.emergency_choking,
        steps = listOf(R.string.emergency_breathing, R.string.emergency_mouth, R.string.emergency_transport),
    ),
    FirstAidGuide(
        icon = Icons.Filled.Science,
        title = R.string.emergency_poison,
        steps = listOf(R.string.emergency_poison_call, R.string.emergency_poison_no_remedy, R.string.emergency_poison_container),
    ),
    FirstAidGuide(
        icon = Icons.Filled.Healing,
        title = R.string.emergency_injury,
        steps = listOf(R.string.emergency_bleeding, R.string.emergency_objects, R.string.emergency_limit_movement),
    ),
)

/** Veterinary contact is the primary action. Country choice never uses location permission. */
@Composable
fun EmergencyScreen(onAskAi: () -> Unit = {}) {
    val context = LocalContext.current
    var region by rememberSaveable { mutableStateOf(initialRegion()) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            HeaderCard(onFindVet = { openVetSearch(context) }, modifier = Modifier.popIn(0))
        }
        item(key = "region") {
            RegionSection(
                region = region,
                onRegionChange = { region = it },
                onDial = { tel -> dial(context, tel) },
                modifier = Modifier.popIn(1),
            )
        }
        firstAidGuides.forEachIndexed { index, guide ->
            item(key = guide.title) {
                FirstAidCard(guide = guide, modifier = Modifier.popIn(index + 2))
            }
        }
        item(key = "footer") {
            FooterSection(
                onOpenFirstAid = { openLink(context, AVMA_FIRST_AID_URL) },
                onAskAi = onAskAi,
                modifier = Modifier.popIn(firstAidGuides.size + 2),
            )
        }
    }
}

@Composable
private fun HeaderCard(onFindVet: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(
                    icon = Icons.Filled.LocalHospital,
                    containerColor = EmergencyRed,
                    iconColor = Color.White,
                    bubbleSize = 52.dp,
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    stringResource(R.string.emergency_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                stringResource(R.string.emergency_vet_first),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            VetSearchButton(onClick = onFindVet)
        }
    }
}

@Composable
private fun VetSearchButton(onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .bouncyPress(source),
    ) {
        Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.emergency_find_vet), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RegionSection(
    region: String,
    onRegionChange: (String) -> Unit,
    onDial: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.emergency_region),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            regionChips.forEach { (id, labelRes) ->
                RegionChip(
                    label = stringResource(labelRes),
                    selected = region == id,
                    onClick = { onRegionChange(id) },
                )
            }
        }
        // Hotlines slide/fade when the region changes.
        AnimatedContent(
            targetState = region,
            label = "emergencyHotlines",
            transitionSpec = { fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(160)) },
        ) { current ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                hotlinesFor(current).forEach { hotline ->
                    HotlineCard(
                        label = stringResource(hotline.label),
                        onClick = { onDial(hotline.tel) },
                    )
                }
            }
        }
        Text(
            stringResource(R.string.emergency_poison_fee),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RegionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        interactionSource = source,
        modifier = Modifier.bouncyPress(source),
    )
}

@Composable
private fun HotlineCard(label: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .bouncyPress(source),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBubble(
                icon = Icons.Filled.Phone,
                containerColor = EmergencyRed,
                iconColor = Color.White,
            )
            Spacer(Modifier.width(14.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FirstAidCard(guide: FirstAidGuide, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(
                    icon = guide.icon,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    iconColor = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(guide.title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            guide.steps.forEach { step ->
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.padding(top = 9.dp)) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(EmergencyRed),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(step),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FooterSection(
    onOpenFirstAid: () -> Unit,
    onAskAi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val firstAidSource = remember { MutableInteractionSource() }
        OutlinedButton(
            onClick = onOpenFirstAid,
            interactionSource = firstAidSource,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .bouncyPress(firstAidSource),
        ) {
            Icon(Icons.Filled.MedicalServices, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.emergency_first_aid_source))
        }
        Text(
            stringResource(R.string.emergency_ai_optional),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val aiSource = remember { MutableInteractionSource() }
        FilledTonalButton(
            onClick = onAskAi,
            interactionSource = aiSource,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .bouncyPress(aiSource),
        ) {
            Icon(Icons.Filled.SmartToy, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.emergency_ask_ai))
        }
    }
}

@Composable
private fun IconBubble(
    icon: ImageVector,
    containerColor: Color,
    iconColor: Color,
    bubbleSize: Dp = 44.dp,
) {
    Box(
        modifier = Modifier
            .size(bubbleSize)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(bubbleSize * 0.5f))
    }
}

/** Google Maps directly, then the generic map chooser, then the Maps web page. */
private fun openVetSearch(context: Context) {
    val attempts = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse(VET_SEARCH_GEO)).setPackage(GOOGLE_MAPS_PACKAGE),
        Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(VET_SEARCH_GEO)), null),
        Intent(Intent.ACTION_VIEW, Uri.parse(VET_SEARCH_WEB)),
    )
    val opened = attempts.any { intent -> runCatching { context.startActivity(intent) }.isSuccess }
    if (!opened) showUnavailable(context)
}

/** Calls only through the dialer (ACTION_DIAL); the user confirms the call. */
private fun dial(context: Context, tel: String) {
    if (runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse(tel))) }.isFailure) {
        showUnavailable(context)
    }
}

private fun openLink(context: Context, url: String) {
    if (runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isFailure) {
        showUnavailable(context)
    }
}

private fun showUnavailable(context: Context) {
    Toast.makeText(context, R.string.shell_no_app_for_link, Toast.LENGTH_SHORT).show()
}

/** US or UK from the device locale, anything else falls back to the generic region. No location permission. */
private fun initialRegion(): String {
    val country = Resources.getSystem().configuration.locales.get(0)?.country.orEmpty()
    return if (country == "US" || country == "GB") country else "OTHER"
}
