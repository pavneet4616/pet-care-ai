package com.petcare.companionlite.ui.emergency

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.petcare.companionlite.ui.theme.Cream
import com.petcare.companionlite.ui.theme.EmergencyRed
import com.petcare.companionlite.ui.theme.EmergencyRedBg

private val emergencyGuides = listOf(
    "🚨 Choking" to listOf(
        "Stay calm and keep your pet still.",
        "Open the mouth only if it is safe to do so.",
        "If you can clearly see the object, gently remove it.",
        "For small dogs/cats: hold upside down and apply firm back blows.",
        "For large dogs: use the Heimlich maneuver — press upward just below the ribcage.",
        "If breathing is compromised, go to the nearest vet immediately.",
    ),
    "☠️ Poisoning" to listOf(
        "Move your pet away from the substance immediately.",
        "Do NOT induce vomiting unless a vet instructs you to.",
        "Keep packaging or a photo of the substance with you.",
        "Note the time of ingestion and estimated amount.",
        "Call your vet or poison helpline right away.",
        "Common toxic items: chocolate, grapes, xylitol, lilies, antifreeze.",
    ),
    "🩹 Injury" to listOf(
        "Apply pressure to visible bleeding with a clean cloth.",
        "If a fracture is possible, limit movement — do not try to set it.",
        "For burns: cool with clean water for 10 minutes, do not apply ice.",
        "For eye injuries: flush gently with clean water.",
        "Keep your pet warm, calm, and monitored.",
        "Seek urgent veterinary care as soon as possible.",
    ),
)

@Composable
fun EmergencyScreen(onAskAi: () -> Unit = {}) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Emergency header with pulsing red icon ─────────────
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val transition = rememberInfiniteTransition(label = "emergency-pulse")
                val pulseScale by transition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
                    label = "pulse",
                )

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(EmergencyRed),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.LocalHospital,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp),
                    )
                }

                Text(
                    "Emergency Mode",
                    style = MaterialTheme.typography.headlineLarge,
                    color = EmergencyRed,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Quick offline first-aid steps for urgent moments.",
                    textAlign = TextAlign.Center,
                )
            }
        }

        // ── Disclaimer (prominent) ─────────────────────────────
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = EmergencyRedBg),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    "⚠️ Not a substitute for veterinary advice. Always contact your vet in an emergency.",
                    modifier = Modifier.padding(16.dp),
                    color = EmergencyRed,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // ── First aid guides ───────────────────────────────────
        items(emergencyGuides) { (title, steps) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.headlineMedium)
                    steps.forEachIndexed { index, step ->
                        Text(
                            "${index + 1}. $step",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }

        // ── Ask AI while preparing (vet-first, never a gatekeeper) ──
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Need guidance while you prepare?", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Contact your vet first. AI can suggest safe immediate steps while help is on the way.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    androidx.compose.material3.OutlinedButton(onClick = onAskAi) {
                        Text("Ask AI while preparing")
                    }
                }
            }
        }
    }
}
