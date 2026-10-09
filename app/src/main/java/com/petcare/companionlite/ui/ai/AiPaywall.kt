package com.petcare.companionlite.ui.ai

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcare.companionlite.PetCareApplication
import com.petcare.companionlite.R
import com.petcare.companionlite.data.ai.AiQuota
import com.petcare.companionlite.ui.components.bouncyPress
import com.petcare.companionlite.ui.theme.Ink
import com.petcare.companionlite.ui.theme.PremiumGold
import kotlin.math.roundToInt

enum class AiPlan { WEEKLY, MONTHLY }

/**
 * One Play plan: the formatted price alone (e.g. "₹950.00") and, when Play provides it,
 * the raw amount in micros. Micros are only needed for the "save N%" badge.
 */
data class AiPlanPrice(val formatted: String, val priceMicros: Long? = null)

/**
 * Yearly saving from paying monthly instead of weekly, or null when it cannot be computed
 * or is not a real saving. Both plans are annualised (12 months vs 52 weeks).
 */
internal fun monthlySavingsPercent(monthlyMicros: Long, weeklyMicros: Long): Int? {
    if (monthlyMicros <= 0L || weeklyMicros <= 0L) return null
    val yearlyMonthly = monthlyMicros * 12.0
    val yearlyWeekly = weeklyMicros * 52.0
    return ((1.0 - yearlyMonthly / yearlyWeekly) * 100.0).roundToInt().takeIf { it in 1..90 }
}

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
    weeklyPrice: AiPlanPrice? = null,
    monthlyPrice: AiPlanPrice? = null,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        (context.applicationContext as PetCareApplication).container.diagnostics.event("paywall_view")
    }
    var plan by remember { mutableStateOf(AiPlan.MONTHLY) }
    val selectedReady = if (plan == AiPlan.MONTHLY) monthlyReady else weeklyReady

    // "₹950.00 / month" when the formatted price is known; otherwise the price text Play gave us.
    val monthlyLine = when {
        !monthlyReady -> stringResource(R.string.paywall_price_loading)
        monthlyPrice != null -> stringResource(R.string.ai_price_per_month, monthlyPrice.formatted)
        else -> monthlyPriceText
    }
    val weeklyLine = when {
        !weeklyReady -> stringResource(R.string.paywall_price_loading)
        weeklyPrice != null -> stringResource(R.string.ai_price_per_week, weeklyPrice.formatted)
        else -> weeklyPriceText
    }
    val monthlyMicros = monthlyPrice?.priceMicros
    val weeklyMicros = weeklyPrice?.priceMicros
    val savings = if (monthlyMicros != null && weeklyMicros != null) {
        monthlySavingsPercent(monthlyMicros, weeklyMicros)
    } else {
        null
    }
    val bestValueLabel = if (savings != null) {
        stringResource(R.string.ai_best_value_save, savings)
    } else {
        stringResource(R.string.ai_best_value)
    }
    val ctaSource = remember { MutableInteractionSource() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.paywall_headline, petName),
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
            )
            Text(
                stringResource(R.string.paywall_sub),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    stringResource(R.string.benefit_questions),
                    stringResource(R.string.benefit_personalized, petName),
                    stringResource(R.string.benefit_insights),
                    stringResource(R.string.benefit_no_ads),
                ).forEach { line ->
                    Text(
                        stringResource(R.string.ui_98d3f752d4, line),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            PaywallPlanCard(
                selected = plan == AiPlan.MONTHLY,
                title = stringResource(R.string.monthly_pro),
                badge = bestValueLabel,
                price = monthlyLine,
                subtitle = stringResource(R.string.monthly_subtitle),
                onSelect = { plan = AiPlan.MONTHLY },
            )
            PaywallPlanCard(
                selected = plan == AiPlan.WEEKLY,
                title = stringResource(R.string.weekly_pro),
                badge = null,
                price = weeklyLine,
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
                interactionSource = ctaSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .bouncyPress(ctaSource),
            ) {
                Text(
                    when {
                        billingLoading -> stringResource(R.string.paywall_store_loading)
                        !selectedReady -> stringResource(R.string.paywall_retry)
                        plan == AiPlan.MONTHLY -> stringResource(R.string.continue_monthly)
                        else -> stringResource(R.string.continue_weekly)
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (onWatchAd != null && (quota?.bonusRemaining ?: 0) > 0) {
                OutlinedButton(onClick = onWatchAd, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.watch_ad))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onRestore) { Text(stringResource(R.string.restore_purchases), fontSize = 13.sp) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.maybe_later), fontSize = 13.sp) }
            }
            Text(
                stringResource(R.string.cancel_in_play),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
            Row {
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_policy_url)))) }
                }) { Text(stringResource(R.string.privacy_policy), fontSize = 12.sp) }
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.terms_of_service_url)))) }
                }) { Text(stringResource(R.string.terms_of_service), fontSize = 12.sp) }
            }
            Text(
                stringResource(R.string.paywall_legal),
                fontSize = 11.sp,
                lineHeight = 16.sp,
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
    val scheme = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (selected) scheme.primaryContainer else scheme.surfaceVariant,
        animationSpec = tween(durationMillis = 220),
        label = "planContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.outlineVariant,
        animationSpec = tween(durationMillis = 220),
        label = "planBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 1.dp,
        label = "planBorderWidth",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.975f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "planScale",
    )

    Card(
        onClick = onSelect,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(borderWidth, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(PremiumGold)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(badge, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ink)
                    }
                    Spacer(Modifier.height(2.dp))
                }
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(price, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }
            Spacer(Modifier.width(8.dp))
            RadioButton(selected = selected, onClick = null)
        }
    }
}
