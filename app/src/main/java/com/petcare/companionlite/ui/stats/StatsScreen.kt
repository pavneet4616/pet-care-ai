package com.petcare.companionlite.ui.stats

import com.petcare.companionlite.R
import com.petcare.companionlite.data.model.toDateLabel
import com.petcare.companionlite.ui.components.weightLabel

import androidx.compose.ui.res.stringResource

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Pets
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.petcare.companionlite.ui.theme.Cream
import com.petcare.companionlite.ui.theme.DeepSage
import com.petcare.companionlite.ui.theme.Honey
import com.petcare.companionlite.ui.theme.PremiumGold
import com.petcare.companionlite.ui.theme.Sage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = viewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val isPremium = state.settings.hasFullAccess

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ui_dc78298c12, state.pet?.name ?: "Pet")) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.ui_b52b36b726)) } },
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
            // ── Streak + Overview ────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val streakEmoji = when {
                        state.streakDays >= 30 -> "⭐"
                        state.streakDays >= 7  -> "🔥"
                        state.streakDays >= 3  -> "🟢"
                        else -> "🐾"
                    }
                    Text(streakEmoji, style = MaterialTheme.typography.headlineLarge)
                    Text(
                        stringResource(R.string.ui_79db3d71b9, state.streakDays),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val milestoneMsg = when {
                        state.streakDays >= 30 -> stringResource(R.string.ui_b9bf9285c6)
                        state.streakDays >= 7  -> stringResource(R.string.ui_afa5ccee0a)
                        state.streakDays >= 3  -> stringResource(R.string.ui_684feeab23)
                        state.streakDays > 0   -> stringResource(R.string.ui_a42c5a0384)
                        else -> stringResource(R.string.ui_0c6629aa97)
                    }
                    Text(
                        milestoneMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.ui_238b00a90f, state.totalActivities),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // ── Weight Chart ─────────────────────────────────────
            if (state.points.isNotEmpty()) {
                Card(
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
                                    12f + (size.width-24f) * ((it.timestamp-start).toDouble()/duration).toFloat(),
                                    size.height-12f - (size.height-24f) * ((it.value-low)/range).toFloat())
                            }
                            positions.zipWithNext().forEach { (a,b) -> drawLine(lineColor,a,b,4f) }
                            positions.forEach { drawCircle(lineColor,5f,it) }
                        }
                        ordered.takeLast(10).forEach { point ->
                            Text(point.timestamp.toDateLabel() + " : " + weightLabel(point.value.toString()))
                        }

                    }
                }
            } else {
                // Empty chart state
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Filled.BarChart, contentDescription = null, tint = Sage, modifier = Modifier.size(48.dp))
                        Text(stringResource(R.string.ui_8eca17da71), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        Text(
                            stringResource(R.string.ui_13214e2320),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // ── Weight alert ─────────────────────────────────────
            state.alert?.let { alert ->
                Card(
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
                AnimatedVisibility(visible = state.taskFrequencies.isNotEmpty()) {
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
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(com.petcare.companionlite.ui.components.taskLabel(freq.type))
                                        Text(stringResource(R.string.ui_9e5a28324f, freq.count), fontWeight = FontWeight.Bold)
                                    }
                                    LinearProgressIndicator(
                                        progress = { freq.count.toFloat() / maxCount },
                                        modifier = Modifier.fillMaxWidth().height(8.dp),
                                        color = DeepSage,
                                        trackColor = Sage.copy(alpha = 0.2f),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Premium gate for advanced stats
                Card(
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
