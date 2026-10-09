package com.petcare.companionlite.ui.ai

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petcare.companionlite.R
import com.petcare.companionlite.data.ai.AI_MAX_INPUT_CHARS
import com.petcare.companionlite.data.ai.UiChatItem
import com.petcare.companionlite.data.model.ChatMessage
import com.petcare.companionlite.data.model.ChatRole
import com.petcare.companionlite.ui.components.bouncyPress
import com.petcare.companionlite.ui.components.popIn

/** The composer shows its character counter once the draft gets this close to the limit. */
private const val COUNTER_THRESHOLD = 1_500

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    petId: Long,
    petName: String,
    prefill: String,
    vm: AiChatViewModel,
    weeklyPriceText: String,
    monthlyPriceText: String,
    weeklyReady: Boolean = false,
    monthlyReady: Boolean = false,
    billingLoading: Boolean = false,
    billingMessage: String? = null,
    isPro: Boolean = false,
    weeklyPlanPrice: AiPlanPrice? = null,
    monthlyPlanPrice: AiPlanPrice? = null,
    onBack: () -> Unit,
    onBuyWeekly: () -> Unit,
    onBuyMonthly: () -> Unit,
    onRestorePurchases: () -> Unit,
    onWatchAdForBonus: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val petType by vm.petType.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    // Messages created after this moment animate in; history loaded on open does not replay.
    val screenOpenedAt = remember { System.currentTimeMillis() }
    val showWelcome = items.isEmpty() && !ui.sending
    val listCount = items.size + (if (showWelcome) 1 else 0) + (if (ui.sending) 1 else 0)

    LaunchedEffect(prefill) { vm.prefill(prefill) }
    LaunchedEffect(ui.error) { ui.error?.let { snackbar.showSnackbar(it); vm.consumeError() } }
    // Keep the newest row (answer or the thinking indicator) in view.
    LaunchedEffect(listCount) {
        if (listCount > 0) listState.animateScrollToItem(listCount - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.ui_25f32ac033, petName), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(
                            stringResource(R.string.ui_744916cf5e),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ui_b52b36b726))
                    }
                },
                actions = {
                    AiQuotaPill(quota = ui.quota, localPro = isPro, onClick = vm::openPaywall)
                    IconButton(onClick = vm::openHistory) {
                        Icon(Icons.Filled.History, contentDescription = stringResource(R.string.ui_3baf780081))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        // The composer pads itself by the taller of the navigation bar and the keyboard,
        // so the Scaffold must not add those insets again (that would double the gap).
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(
            WindowInsets.navigationBars.union(WindowInsets.ime),
        ),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!ui.aiEnabled) {
                Text(
                    stringResource(R.string.ui_717f2b2b22),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
            }
            // Soft quota nudge when one question is left: a chip, no modal.
            if (!isPro && (ui.quota?.remaining ?: 99) == 1 && ui.quota?.tier != "pro") {
                Text(
                    stringResource(R.string.ui_f581296f9a),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showWelcome) {
                    item(key = "welcome") {
                        AiWelcomeState(petName = petName, petType = petType, onPick = vm::prefillFollowUp)
                    }
                }
                itemsIndexed(
                    items,
                    key = { _, item ->
                        when (item) {
                            is UiChatItem.User -> "u_${item.id}"
                            is UiChatItem.Assistant -> "a_${item.id}"
                        }
                    },
                ) { index, item ->
                    val animateIn = item.createdAtMillis() >= screenOpenedAt
                    val entrance = if (animateIn) Modifier.popIn() else Modifier
                    when (item) {
                        is UiChatItem.User -> UserBubble(item = item, modifier = entrance)
                        is UiChatItem.Assistant -> {
                            if (item.status == "FAILED") {
                                val question = items.take(index)
                                    .lastOrNull { it is UiChatItem.User }
                                    ?.let { (it as UiChatItem.User).text }
                                    .orEmpty()
                                FailedCard(
                                    errorCode = item.errorCode,
                                    onRetry = { vm.retryFailed(item.toChatMessage(ui.sessionId), question) },
                                    modifier = entrance,
                                )
                            } else {
                                AiAnswerCard(
                                    item = item,
                                    animateIn = animateIn,
                                    onSave = { vm.toggleSaved(item.toChatMessage(ui.sessionId)) },
                                    onHelpful = { vm.markFeedback(item.toChatMessage(ui.sessionId), true) },
                                    onNotHelpful = { vm.markFeedback(item.toChatMessage(ui.sessionId), false) },
                                    onReport = {
                                        vm.openReport(item.toChatMessage(ui.sessionId, content = item.answer.summary))
                                    },
                                    onFollowUp = vm::prefillFollowUp,
                                    modifier = entrance,
                                )
                            }
                        }
                    }
                }
                if (ui.sending) {
                    item(key = "thinking") {
                        ThinkingPawIndicator(petName = petName, hasContext = items.isNotEmpty())
                    }
                }
            }

            // Composer: stays above the keyboard and the navigation bar. Snackbars appear just above it,
            // so they move with the keyboard instead of landing under the navigation bar.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom),
                    ),
            ) {
                SnackbarHost(hostState = snackbar, modifier = Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.ui_40abca6ed5),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val nearLimit = ui.input.length >= COUNTER_THRESHOLD
                    val counter: (@Composable () -> Unit)? = if (nearLimit) {
                        ({ InputCounter(length = ui.input.length) })
                    } else {
                        null
                    }
                    OutlinedTextField(
                        value = ui.input,
                        onValueChange = vm::onInputChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.ui_c3b17ac9f3, petName), fontSize = 14.sp) },
                        supportingText = counter,
                        shape = RoundedCornerShape(28.dp),
                        maxLines = 4,
                        textStyle = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
                    )
                    Spacer(Modifier.width(8.dp))
                    SendButton(
                        enabled = !ui.sending && ui.input.isNotBlank(),
                        onClick = { vm.send() },
                    )
                }
            }
        }
    }

    if (ui.showPaywall) {
        AiPaywallSheet(
            quota = ui.quota,
            petName = petName,
            weeklyPriceText = weeklyPriceText,
            monthlyPriceText = monthlyPriceText,
            onDismiss = vm::dismissPaywall,
            onBuyWeekly = onBuyWeekly,
            onBuyMonthly = onBuyMonthly,
            onRestore = onRestorePurchases,
            onWatchAd = onWatchAdForBonus,
            weeklyReady = weeklyReady,
            monthlyReady = monthlyReady,
            billingLoading = billingLoading,
            billingMessage = billingMessage,
            weeklyPrice = weeklyPlanPrice,
            monthlyPrice = monthlyPlanPrice,
        )
    }

    if (ui.showHistory) {
        AiHistorySheet(
            sessions = sessions,
            currentId = ui.sessionId,
            onOpen = vm::openSession,
            onNew = vm::newChat,
            onRename = vm::openRename,
            onDelete = vm::deleteSession,
            onDismiss = vm::closeHistory,
        )
    }

    ui.renameTarget?.let { target ->
        AiRenameDialog(
            current = target.title,
            onDismiss = vm::closeRename,
            onSubmit = vm::submitRename,
        )
    }

    ui.reportTarget?.let {
        AiReportDialog(
            onDismiss = vm::closeReport,
            onSubmit = vm::submitReport,
        )
    }
}

/** Send button: springs to full size and turns primary once there is something to send. */
@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.86f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "sendScale",
    )
    val containerColor by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        label = "sendContainer",
    )
    val iconTint by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "sendTint",
    )
    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .bouncyPress(source)
            .clip(CircleShape)
            .background(containerColor),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Send,
            contentDescription = stringResource(R.string.ui_9bc2575c39),
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun InputCounter(length: Int) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            stringResource(R.string.ai_input_counter, length, AI_MAX_INPUT_CHARS),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

private fun UiChatItem.createdAtMillis(): Long = when (this) {
    is UiChatItem.User -> time
    is UiChatItem.Assistant -> time
}

private fun UiChatItem.Assistant.toChatMessage(sessionId: Long, content: String = ""): ChatMessage = ChatMessage(
    id = id,
    sessionId = sessionId,
    role = ChatRole.ASSISTANT,
    content = content,
    createdAt = time,
    requestId = requestId,
    isSaved = isSaved,
    reportState = null,
)
