package com.petcare.companionlite.ui.ai

import com.petcare.companionlite.R

import androidx.compose.ui.res.stringResource

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petcare.companionlite.data.ai.AiAnswer
import com.petcare.companionlite.data.ai.AiQuota
import com.petcare.companionlite.data.ai.UiChatItem
import com.petcare.companionlite.data.model.ChatMessage
import com.petcare.companionlite.data.model.ChatSession
import com.petcare.companionlite.ui.theme.PremiumGold
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    onBack: () -> Unit,
    onBuyWeekly: () -> Unit,
    onBuyMonthly: () -> Unit,
    onRestorePurchases: () -> Unit,
    onWatchAdForBonus: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(prefill) { vm.prefill(prefill) }
    LaunchedEffect(ui.error) { ui.error?.let { snackbar.showSnackbar(it); vm.consumeError() } }
    LaunchedEffect(items.size, ui.sending) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.size - 1)
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
                    AiQuotaPill(ui.quota, isPro)
                    IconButton(onClick = vm::openHistory) {
                        Icon(Icons.Filled.History, contentDescription = stringResource(R.string.ui_3baf780081))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
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
            // Soft quota nudge at 4/5 — no modal, just a chip.
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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (items.isEmpty() && !ui.sending) {
                    item {
                        AiWelcomeState(petName = petName, onPick = vm::prefillFollowUp)
                    }
                }
                itemsIndexed(items, key = { _, item ->
                    when (item) {
                        is UiChatItem.User -> "u_${item.id}"
                        is UiChatItem.Assistant -> "a_${item.id}"
                    }
                }) { index, item ->
                    when (item) {
                        is UiChatItem.User -> UserBubble(item)
                        is UiChatItem.Assistant -> {
                            if (item.status == "FAILED") {
                                val question = items.take(index)
                                    .lastOrNull { it is UiChatItem.User }
                                    ?.let { (it as UiChatItem.User).text }.orEmpty()
                                FailedCard(
                                    errorCode = item.errorCode,
                                    onRetry = { vm.retryFailed(
                                        ChatMessage(
                                            id = item.id, sessionId = ui.sessionId,
                                            role = com.petcare.companionlite.data.model.ChatRole.ASSISTANT,
                                            content = "", createdAt = item.time,
                                            requestId = item.requestId, isSaved = false,
                                            reportState = null,
                                        ),
                                        question,
                                    ) },
                                )
                            } else {
                                AiAnswerCard(
                                    item = item,
                                    petName = petName,
                                    onSave = {
                                        vm.toggleSaved(
                                            ChatMessage(
                                                id = item.id, sessionId = ui.sessionId,
                                                role = com.petcare.companionlite.data.model.ChatRole.ASSISTANT,
                                                content = "", createdAt = item.time,
                                                requestId = item.requestId, isSaved = item.isSaved,
                                                reportState = null,
                                            ),
                                        )
                                    },
                                    onHelpful = {
                                        vm.markFeedback(
                                            ChatMessage(
                                                id = item.id, sessionId = ui.sessionId,
                                                role = com.petcare.companionlite.data.model.ChatRole.ASSISTANT,
                                                content = "", createdAt = item.time,
                                                requestId = item.requestId, isSaved = item.isSaved,
                                                reportState = null,
                                            ),
                                            true,
                                        )
                                    },
                                    onNotHelpful = {
                                        vm.markFeedback(
                                            ChatMessage(
                                                id = item.id, sessionId = ui.sessionId,
                                                role = com.petcare.companionlite.data.model.ChatRole.ASSISTANT,
                                                content = "", createdAt = item.time,
                                                requestId = item.requestId, isSaved = item.isSaved,
                                                reportState = null,
                                            ),
                                            false,
                                        )
                                    },
                                    onReport = {
                                        vm.openReport(
                                            ChatMessage(
                                                id = item.id, sessionId = ui.sessionId,
                                                role = com.petcare.companionlite.data.model.ChatRole.ASSISTANT,
                                                content = item.answer.summary, createdAt = item.time,
                                                requestId = item.requestId, isSaved = item.isSaved,
                                                reportState = null,
                                            ),
                                        )
                                    },
                                    onFollowUp = vm::prefillFollowUp,
                                )
                            }
                        }
                    }
                }
                if (ui.sending) {
                    item { ThinkingPawIndicator(petName = petName, hasContext = items.isNotEmpty()) }
                }
            }

            Text(
                stringResource(R.string.ui_40abca6ed5),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = ui.input,
                    onValueChange = vm::onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.ui_c3b17ac9f3, petName), fontSize = 14.sp) },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 3,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { vm.send() },
                    enabled = !ui.sending && ui.input.isNotBlank(),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (ui.input.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.ui_9bc2575c39),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
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

// ---------- Welcome ----------
