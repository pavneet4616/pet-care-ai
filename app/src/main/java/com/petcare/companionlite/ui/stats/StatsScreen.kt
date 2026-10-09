package com.petcare.companionlite.ui.stats

import com.petcare.companionlite.R
import com.petcare.companionlite.ui.components.BobbingEmoji
import com.petcare.companionlite.ui.components.popIn
import com.petcare.companionlite.ui.components.weightLabel
import com.petcare.companionlite.ui.pet.PetTimeText
import com.petcare.companionlite.ui.pet.petActionLabel
import com.petcare.companionlite.ui.theme.DeepSage
import com.petcare.companionlite.ui.theme.Honey
import com.petcare.companionlite.ui.theme.PremiumGold
import com.petcare.companionlite.ui.theme.Sage

import androidx.compose.ui.res.stringResource

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val isPremium = state.settings.hasFullAccess
    val petName = state.pet?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.pet_fallback_title)

    // Stat numbers count up/down when they change instead of jumping.
    val animatedStreak by animateIntAsState(
        targetValue = state.streakDays,
        animationSpec = tween(durationMillis = 700),
        label = "stats-streak",
    )
    val animatedTotal by animateIntAsState(
        targetValue = state.totalActivities,
        animationSpec = tween(durationMillis = 700),
        label = "stats-total",
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ui_dc78298c12, petName)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.ui_b52b36b726)) } },
                // The NavHost host already applies the status bar inset; a second one leaves an empty band.
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Streak hero ──────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth().popIn(0),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(DeepSage, Sage)))
                        .padding(22.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val streakEmoji = when {
                            state.streakDays >= 30 -> "⭐"
                            state.streakDays >= 7 -> "🔥"
                            state.streakDays >= 3 -> "🟢"
                            else -> "🐾"
                        }
                        BobbingEmoji(emoji = streakEmoji, size = 64.dp)
                        Text(
                            stringResource(R.string.ui_79db3d71b9, animatedStreak),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                        val milestoneMsg = when {
                            state.streakDays >= 30 -> stringResource(R.string.ui_b9bf9285c6)
                            state.streakDays >= 7 -> stringResource(R.string.ui_afa5ccee0a)
                            state.streakDays >= 3 -> stringResource(R.string.ui_684feeab23)
                            state.streakDays > 0 -> stringResource(R.string.ui_a42c5a0384)
                            else -> stringResource(R.string.ui_0c6629aa97)
                        }
                        Text(
                            milestoneMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.92f),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            stringResource(R.string.ui_238b00a90f, animatedTotal),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // ── Weight Chart ─────────────────────────────────────
            if (state.points.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().popIn(1),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.ui_f9e6fe6b07), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        val ordered = state.points.sortedBy { it.timestamp }
                        val lineColor = MaterialTheme.colorScheme.primary
                        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(180.dp)) {
                            val low = ordered.minOf { it.value }
                            val range = (ordered.maxOf { it.value } - low).coerceAtLeast(0.5)
                            val start = ordered.first().timestamp
                            val duration = (ordered.last().timestamp - start).coerceAtLeast(1)
                            val positions = ordered.map {
                                androidx.compose.ui.geometry.Offset(
                                    12f + (size.width - 24f) * ((it.timestamp - start).toDouble() / duration).toFloat(),
                                    size.height - 12f - (size.height - 24f) * ((it.value - low) / range).toFloat())
                            }
                            positions.zipWithNext().forEach { (a, b) -> drawLine(lineColor, a, b, 4f) }
                            positions.forEach { drawCircle(lineColor, 5f, it) }
                        }
                        Spacer(Modifier.height(8.dp))
                        ordered.takeLast(10).forEach { point ->
                            Text(
                                PetTimeText.date(context, point.timestamp) + " : " + weightLabel(point.value.toString()),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            } else {
                // Friendly empty state for the weight chart
                Card(
                    modifier = Modifier.fillMaxWidth().popIn(1),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("⚖️", style = MaterialTheme.typography.displayMedium)
                        Text(
                            stringResource(R.string.pet_chart_empty_title),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            stringResource(R.string.pet_chart_empty_body),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Weight alert ─────────────────────────────────────
            state.alert?.let { alert ->
                Card(
                    modifier = Modifier.fillMaxWidth().popIn(2),
                    colors = CardDefaults.cardColors(containerColor = Honey.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        alert,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // ── Task Frequency (Premium) ─────────────────────────
            if (isPremium) {
                AnimatedVisibility(
                    visible = state.taskFrequencies.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().popIn(3),
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(22.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(stringResource(R.string.ui_214431d6db), style = MaterialTheme.typography.titleLarge)
                            val maxCount = state.taskFrequencies.maxOfOrNull { it.count } ?: 1
                            state.taskFrequencies.forEach { freq ->
                                FrequencyBar(
                                    label = petActionLabel(freq.type),
                                    count = freq.count,
                                    fraction = freq.count.toFloat() / maxCount,
                                )
                            }
                        }
                    }
                }
            } else {
                // Premium gate for advanced stats
                Card(
                    modifier = Modifier.fillMaxWidth().popIn(3),
                    colors = CardDefaults.cardColors(containerColor = PremiumGold.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = PremiumGold, modifier = Modifier.size(32.dp))
                        Text(stringResource(R.string.ui_95d64a3ef0), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.ui_082e3c1e1c),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // ── Disclaimer ───────────────────────────────────────
            Text(
                stringResource(R.string.ui_fe2bdf9779),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FrequencyBar(label: String, count: Int, fraction: Float) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 700),
        label = "frequency-bar",
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label)
            Text(stringResource(R.string.ui_9e5a28324f, count), fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = DeepSage,
            trackColor = Sage.copy(alpha = 0.2f),
        )
    }
}
