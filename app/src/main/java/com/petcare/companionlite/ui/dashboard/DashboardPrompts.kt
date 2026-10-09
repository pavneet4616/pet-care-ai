package com.petcare.companionlite.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.petcare.companionlite.R
import com.petcare.companionlite.data.model.MoodState
import com.petcare.companionlite.data.model.PetDashboardItem
import com.petcare.companionlite.data.model.PetType
import com.petcare.companionlite.data.model.TaskType
import com.petcare.companionlite.data.model.TimelineItem
import com.petcare.companionlite.data.model.TimelineStatus
import com.petcare.companionlite.monetization.BillingManager
import com.petcare.companionlite.ui.components.AdBanner
import com.petcare.companionlite.ui.components.PetAvatar
import com.petcare.companionlite.ui.components.PetCard
import com.petcare.companionlite.ui.ai.AiPaywallSheet
import com.petcare.companionlite.ui.theme.Coral
import com.petcare.companionlite.ui.theme.DeepSage
import com.petcare.companionlite.ui.theme.Honey
import com.petcare.companionlite.ui.theme.PremiumGold
import com.petcare.companionlite.ui.theme.Sage
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AiCompanionHeroCard(petName: String, onOpenAi: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenAi,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.ask_ai_about, petName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    stringResource(R.string.ask_ai_subtitle, petName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
fun AiProUpgradeCard(onExplore: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PremiumGold.copy(alpha = 0.16f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        onClick = onExplore,
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = PremiumGold, modifier = Modifier.size(22.dp))
                Text(
                    stringResource(R.string.upgrade_to_pro),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                stringResource(R.string.pro_card_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
            Button(
                onClick = onExplore,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.explore_pro), fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── CONTEXTUAL PREMIUM CARD ───────────────────────────────────────
@Composable
fun ContextualPremiumCard(
    state: DashboardUiState,
    maxStreak: Int,
    onBuyPremium: () -> Unit,
    onBuyRemoveAds: () -> Unit
) {
    val headline = when {
        maxStreak >= 7  -> stringResource(R.string.ui_d0aec35d5d)
        maxStreak >= 3  -> stringResource(R.string.ui_e7265e5c07)
        state.pets.size >= 2 -> stringResource(R.string.ui_6def274b9c)
        else -> stringResource(R.string.ui_642d153d17)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null,
                    tint = Honey, modifier = Modifier.size(22.dp))
                Text(headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(
                stringResource(R.string.ui_eab15d6071),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
            )
            if (state.billing.isLoading) {
                Text(stringResource(R.string.ui_33ce417454), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f))
            } else {
                val premium   = state.billing.products.firstOrNull { it.id == BillingManager.aiMonthlyProductId }
                if (!state.settings.hasFullAccess) {
                    Button(
                        onClick = onBuyPremium,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(premium?.let { stringResource(R.string.ui_56d60cdf15, it.priceText) } ?: stringResource(R.string.ui_51161257eb),
                        fontWeight = FontWeight.Bold) }
                }

            }
        }
    }
}

// ── FIRST TIME LANDING ────────────────────────────────────────────
@Composable
fun FirstTimeLanding(onAddPet: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(28.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PetAvatar(petType = PetType.DOG, mood = MoodState.HAPPY, modifier = Modifier.size(120.dp))
            Text(
                stringResource(R.string.ui_64ccc1309a),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.ui_bb133db4bf),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
            Button(
                onClick = onAddPet,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(stringResource(R.string.ui_10a8b386c1),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
            }
            Text(
                stringResource(R.string.ui_d402a614ea),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun WeeklyReportBanner(onOpenReport: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DeepSage),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🐾", style = MaterialTheme.typography.displaySmall)
                    Column {
                        Text(
                            stringResource(R.string.ui_03086643cd),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            stringResource(R.string.ui_72e7bd7aea),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Close, "Dismiss", tint = Color.White.copy(alpha = 0.6f))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onOpenReport,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = DeepSage),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.ui_fa5576e0db), fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── STREAK RISK BANNER (fear of loss — #1 retention driver) ──────
@Composable
fun StreakRiskBanner(petItem: PetDashboardItem, onOpenPet: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "risk-pulse").animateFloat(
        initialValue = 0.85f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Coral.copy(alpha = 0.12f)),
        onClick = onOpenPet
    ) {
        Row(
            modifier = Modifier
                .border(1.5.dp, Coral.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚠️", style = MaterialTheme.typography.headlineSmall)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.ui_af240977b3, petItem.pet.name, petItem.streakDays),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Coral
                )
                Text(
                    stringResource(R.string.ui_212cfde9f5),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
            Button(
                onClick = onOpenPet,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Coral),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) { Text(stringResource(R.string.ui_7c6faacb41), fontWeight = FontWeight.Bold, color = Color.White) }
        }
    }
}
