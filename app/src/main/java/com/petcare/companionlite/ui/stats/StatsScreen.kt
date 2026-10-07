package com.petcare.companionlite.ui.stats

import android.graphics.Color as AndroidColor
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
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
                title = { Text("${state.pet?.name ?: "Pet"} Stats") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
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
                        "${state.streakDays} Day Streak",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val milestoneMsg = when {
                        state.streakDays >= 30 -> "🏆 Champion! 30+ days of care!"
                        state.streakDays >= 7  -> "🔥 One week strong! Keep it going!"
                        state.streakDays >= 3  -> "🟢 3 days in — building a habit!"
                        state.streakDays > 0   -> "🐾 Great start! Log every day to grow your streak."
                        else -> "Start your streak by logging care today!"
                    }
                    Text(
                        milestoneMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${state.totalActivities} total care actions logged",
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
                        Text("Weight Trend", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            factory = { context ->
                                LineChart(context).apply {
                                    description = Description().apply { text = "" }
                                    axisRight.isEnabled = false
                                    legend.isEnabled = false
                                    setTouchEnabled(true)
                                    setScaleEnabled(false)
                                    xAxis.setDrawGridLines(false)
                                    axisLeft.setDrawGridLines(true)
                                    axisLeft.gridColor = AndroidColor.rgb(221, 239, 224)
                                }
                            },
                            update = { chart ->
                                val entries = state.points.mapIndexed { index, point ->
                                    Entry(index.toFloat(), point.value.toFloat())
                                }
                                val dataSet = LineDataSet(entries, "Weight").apply {
                                    color = AndroidColor.rgb(132, 181, 159)
                                    setCircleColor(AndroidColor.rgb(78, 122, 103))
                                    lineWidth = 3f
                                    circleRadius = 4f
                                    setDrawFilled(true)
                                    fillColor = AndroidColor.rgb(221, 239, 224)
                                    mode = LineDataSet.Mode.CUBIC_BEZIER
                                    setDrawValues(false)
                                }
                                chart.data = LineData(dataSet)
                                // Chart animation (spec requirement)
                                chart.animateY(1000)
                            },
                        )
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
                        Text("No weight data yet", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        Text(
                            "Add weight logs from the pet profile to see weekly and monthly trends.",
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
                            Text("Activity Breakdown", style = MaterialTheme.typography.titleLarge)
                            val maxCount = state.taskFrequencies.maxOfOrNull { it.count } ?: 1
                            state.taskFrequencies.forEach { freq ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(freq.type.label)
                                        Text("${freq.count}×", fontWeight = FontWeight.Bold)
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
                        Text("Advanced Stats", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Upgrade to premium to see activity breakdown, care completion rates, and more.",
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // ── Disclaimer ───────────────────────────────────────
            Text(
                "Not a substitute for veterinary advice",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
