package com.petcare.companionlite.ui.report

import com.petcare.companionlite.R
import com.petcare.companionlite.ui.components.BobbingEmoji
import com.petcare.companionlite.ui.components.popIn

import androidx.compose.ui.res.stringResource

import android.app.Application
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.petcare.companionlite.PetCareApplication
import com.petcare.companionlite.core.PetStateEngine
import com.petcare.companionlite.data.model.EngagementTier
import com.petcare.companionlite.data.model.WeeklyReport
import com.petcare.companionlite.data.model.toDomain
import com.petcare.companionlite.data.model.toProfile
import com.petcare.companionlite.ui.theme.Coral
import com.petcare.companionlite.ui.theme.DeepSage
import com.petcare.companionlite.ui.theme.Honey
import com.petcare.companionlite.ui.theme.PremiumGold
import com.petcare.companionlite.ui.theme.Sage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

// ── ViewModel ─────────────────────────────────────────────────────
data class WeeklyReportUiState(
    val reports: List<WeeklyReport> = emptyList(),
    val isPremium: Boolean = false,
    val isLoading: Boolean = true,
)

class WeeklyReportViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PetCareApplication
    private val repository = app.container.repository

    private val _uiState = MutableStateFlow(WeeklyReportUiState())
    val uiState: StateFlow<WeeklyReportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val zoneId = ZoneId.systemDefault()
            val weekStartMillis = LocalDate.now(zoneId).minusDays(6)
                .atStartOfDay(zoneId).toInstant().toEpochMilli()

            val pets = repository.getAllPets()
            val allActivities = repository.getActivitiesSince(LocalDate.now(zoneId).minusDays(13).atStartOfDay(zoneId).toInstant().toEpochMilli()) // Support 14 day trend
            val allReminders = repository.getAllReminders()
            val isPremium = repository.settings.first().hasFullAccess

            val reports = pets.map { pet ->
                val petActivities = allActivities
                    .filter { it.petId == pet.id }
                    .mapNotNull { it.toDomain() }
                val petReminders = allReminders
                    .filter { it.petId == pet.id }
                    .mapNotNull { it.toDomain() }
                PetStateEngine.generateWeeklyReport(
                    pet = pet.toProfile(),
                    activities = petActivities,
                    reminders = petReminders,
                    zoneId = zoneId,
                    context = app,
                )
            }

            _uiState.value = WeeklyReportUiState(
                reports = reports,
                isPremium = isPremium,
                isLoading = false,
            )
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyReportScreen(
    onBack: () -> Unit,
    onBuyPremium: () -> Unit,
    onShare: (WeeklyReport) -> Unit,
    viewModel: WeeklyReportViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ui_1e41a8f690), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.ui_c32ae9f4a7), color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                // The NavHost host already applies the status bar inset; a second one leaves an empty band.
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.ui_53284c7e03), style = MaterialTheme.typography.bodyLarge)
            }
        } else if (state.reports.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BobbingEmoji(emoji = "🐾", size = 72.dp)
                    Text(
                        stringResource(R.string.ui_8c48f89bdb),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.ui_d74af11b57),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Button(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
                        Text(stringResource(R.string.ui_f7b5bf8cef))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                state.reports.forEach { report ->
                    // ── Headline Card ──────────────────────────────
                    item(key = "headline_${report.petName}") {
                        ReportHeadlineCard(report, Modifier.popIn(0))
                    }

                    // ── Stats Grid ─────────────────────────────────
                    item(key = "stats_${report.petName}") {
                        ReportStatsRow(report, Modifier.popIn(1))
                    }

                    // ── Consistency Score ────────────────────────
                    item(key = "progress_${report.petName}") {
                        ConsistencyScoreCard(report, Modifier.popIn(2))
                    }

                    // ── Next Week Goal ─────────────────────────────
                    item(key = "goal_${report.petName}") {
                        NextWeekGoalCard(report, Modifier.popIn(3))
                    }

                    // ── Share Button ───────────────────────────────
                    item(key = "share_${report.petName}") {
                        OutlinedButton(
                            onClick = { onShare(report) },
                            modifier = Modifier.fillMaxWidth().height(50.dp).popIn(4),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.ui_ead1994747), fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // ── Premium Intent Locks ──────────────────────
                    if (!state.isPremium) {
                        item(key = "trends_${report.petName}") {
                            LockedFeatureCard(
                                title = stringResource(R.string.ui_631642b1c2),
                                description = stringResource(R.string.ui_641a352a37),
                                onClick = onBuyPremium,
                                modifier = Modifier.popIn(5),
                            )
                        }

                        item(key = "upsell_${report.petName}") {
                            PremiumUpsellCard(report, onBuyPremium, Modifier.popIn(6))
                        }
                    } else {
                        item(key = "trends_${report.petName}") {
                            OutlinedButton(
                                onClick = { /* TODO: Open Trends Screen */ },
                                modifier = Modifier.fillMaxWidth().height(50.dp).popIn(5),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Filled.TrendingUp, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.ui_b9c8abca06), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // ── CTA ───────────────────────────────────────────
                item {
                    Button(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(stringResource(R.string.ui_3dad8dfd54), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.ui_0f9723ea17),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ── Components ────────────────────────────────────────────────────

@Composable
private fun ReportHeadlineCard(report: WeeklyReport, modifier: Modifier = Modifier) {
    val gradientColors = when (report.tier) {
        EngagementTier.HIGH -> listOf(DeepSage, Sage)
        EngagementTier.MEDIUM -> listOf(Color(0xFF5E8C6A), Sage)
        EngagementTier.LOW -> listOf(Coral.copy(alpha = 0.8f), Color(0xFFE8A090))
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(gradientColors))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        BobbingEmoji(emoji = report.petEmoji, size = 44.dp)
                    }
                    Column {
                        Text(
                            stringResource(R.string.ui_2ba34f0243, report.petName),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            report.headline,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
                Text(
                    report.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun ReportStatsRow(report: WeeklyReport, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatMiniCard(
            emoji = "✔",
            value = report.tasksCompleted,
            label = stringResource(R.string.ui_1798b3ba42),
            modifier = Modifier.weight(1f)
        )
        StatMiniCard(
            emoji = "🔥",
            value = report.bestStreak,
            label = stringResource(R.string.ui_3b03fa8122),
            modifier = Modifier.weight(1f)
        )
        StatMiniCard(
            emoji = "📅",
            value = report.daysActive,
            label = stringResource(R.string.ui_0ebfff45ec),
            modifier = Modifier.weight(1f)
        )
        StatMiniCard(
            emoji = "📉",
            value = report.missedTasks,
            label = stringResource(R.string.ui_b564001a58),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatMiniCard(
    emoji: String,
    value: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    // The number counts up to its value so the stats feel alive.
    val animatedValue by animateIntAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 700),
        label = "mini-stat",
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Text(
                animatedValue.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ConsistencyScoreCard(report: WeeklyReport, modifier: Modifier = Modifier) {
    val progressColor = when (report.tier) {
        EngagementTier.HIGH -> DeepSage
        EngagementTier.MEDIUM -> Honey
        EngagementTier.LOW -> Coral
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.ui_4c4624ce1a),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                report.consistencyDots,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = progressColor,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.ui_1bc87f95fc, report.daysActive),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                report.trendMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NextWeekGoalCard(report: WeeklyReport, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Sage.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(DeepSage),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.TrendingUp,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.ui_fdc2b827d9),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    report.nextWeekGoal,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun PremiumUpsellCard(report: WeeklyReport, onBuyPremium: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    tint = PremiumGold,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    report.premiumUpsell.split("\n").firstOrNull() ?: stringResource(R.string.ui_30149b30ab),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Text(
                report.premiumUpsell.split("\n").drop(1).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
            )
            Text(
                stringResource(R.string.ui_84b7c3d1d4),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
            )
            Button(
                onClick = onBuyPremium,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(stringResource(R.string.ui_30149b30ab), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockedFeatureCard(title: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.ui_a798882f1c),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}
