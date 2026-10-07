package com.petcare.companionlite.ui.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcare.companionlite.R
import com.petcare.companionlite.data.ai.AiQuota
import com.petcare.companionlite.ui.theme.PremiumGold

enum class AiPlan { WEEKLY, MONTHLY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPaywallSheet(
    quota: AiQuota?,
    petName: String,
    weeklyPriceText: String,
    monthlyPriceText: String,
    onDismiss: () -> Unit,
    onBuyWeekly: () -> Unit,
    onBuyMonthly: () -> Unit,
    onRestore: () -> Unit,
    onWatchAd: (() -> Unit)? = null,
    weeklyReady: Boolean = false,
    monthlyReady: Boolean = false,
    billingLoading: Boolean = false,
    billingMessage: String? = null,
) {
    var plan by remember { mutableStateOf(AiPlan.MONTHLY) }
    val selectedReady = if (plan == AiPlan.MONTHLY) monthlyReady else weeklyReady
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.paywall_headline, petName), fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Text(
                stringResource(R.string.paywall_sub),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
            listOf(
                stringResource(R.string.benefit_questions),
                stringResource(R.string.benefit_personalized, petName),
                stringResource(R.string.benefit_insights),
                stringResource(R.string.benefit_no_ads),
            ).forEach { line ->
                Text("✓ $line", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            PaywallPlanCard(
                selected = plan == AiPlan.MONTHLY,
                title = stringResource(R.string.monthly_pro),
                badge = stringResource(R.string.best_value),
                price = if (monthlyReady) monthlyPriceText else stringResource(R.string.paywall_price_loading),
                subtitle = stringResource(R.string.monthly_subtitle),
                onSelect = { plan = AiPlan.MONTHLY },
            )
            PaywallPlanCard(
                selected = plan == AiPlan.WEEKLY,
                title = stringResource(R.string.weekly_pro),
                badge = null,
                price = if (weeklyReady) weeklyPriceText else stringResource(R.string.paywall_price_loading),
                subtitle = stringResource(R.string.weekly_subtitle),
                onSelect = { plan = AiPlan.WEEKLY },
            )
            if (!billingMessage.isNullOrBlank()) {
                Text(
                    billingMessage,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (!selectedReady && !billingLoading) {
                Text(
                    stringResource(R.string.paywall_store_unavailable),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = {
                    if (!selectedReady) {
                        onRestore()
                        return@Button
                    }
                    // Dismiss first so the Play purchase sheet is not hidden behind this modal.
                    val buy = if (plan == AiPlan.MONTHLY) onBuyMonthly else onBuyWeekly
                    onDismiss()
                    buy()
                },
                enabled = !billingLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        billingLoading -> stringResource(R.string.paywall_store_loading)
                        !selectedReady -> stringResource(R.string.paywall_retry)
                        plan == AiPlan.MONTHLY -> stringResource(R.string.continue_monthly)
                        else -> stringResource(R.string.continue_weekly)
                    },
                )
            }
            if (onWatchAd != null && (quota?.bonusRemaining ?: 0) > 0) {
                OutlinedButton(onClick = onWatchAd, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.watch_ad))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onRestore) { Text(stringResource(R.string.restore_purchases), fontSize = 13.sp) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.maybe_later), fontSize = 13.sp) }
            }
            Text(
                stringResource(R.string.paywall_legal),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PaywallPlanCard(
    selected: Boolean,
    title: String,
    badge: String?,
    price: String,
    subtitle: String,
    onSelect: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().clickable { onSelect() },
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (badge != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(badge, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PremiumGold)
                    }
                }
                Text(price, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
            }
        }
    }
}
