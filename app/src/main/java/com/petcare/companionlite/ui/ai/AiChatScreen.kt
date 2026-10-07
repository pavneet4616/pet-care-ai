package com.petcare.companionlite.ui.ai

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
                        Text("Ask about $petName", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(
                            "AI Pet Companion",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    AiQuotaPill(ui.quota, isPro)
                    IconButton(onClick = vm::openHistory) {
                        Icon(Icons.Filled.History, contentDescription = "Chat history")
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
                    "AI is temporarily unavailable. Your chats are saved and readable offline.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
            }
            // Soft quota nudge at 4/5 — no modal, just a chip.
            if (!isPro && (ui.quota?.remaining ?: 99) == 1 && ui.quota?.tier != "pro") {
                Text(
                    "1 free AI question left today",
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
                        is UiChatItem.User -> "u-${item.id}"
                        is UiChatItem.Assistant -> "a-${item.id}"
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
                "AI guidance only — see a vet for diagnosis. Replies in your language.",
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
                    placeholder = { Text("Ask anything about $petName...", fontSize = 14.sp) },
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
                        contentDescription = "Send",
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

@Composable
private fun AiWelcomeState(petName: String, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Ask anything about $petName", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            "Personalized help using ${petName}'s profile and recent care.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        )
        val chips = listOf(
            "\uD83E\uDD22 Vomiting" to "My pet vomited today. What should I do?",
            "\uD83C\uDF7D Food & diet" to "Is my pet's current diet healthy?",
            "\uD83D\uDC3E Behaviour" to "Why is my pet behaving differently?",
            "\uD83D\uDC8A Medicine" to "How do I give medicine safely to my pet?",
            "\uD83C\uDF93 Training" to "Give me one simple training tip for my pet.",
            "\uD83E\uDDFC Grooming" to "How should I groom my pet at home?",
        )
        chips.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, prompt) ->
                    FilterChip(selected = false, onClick = { onPick(prompt) }, label = {
                        Text(label, fontSize = 13.sp)
                    })
                }
            }
        }
        Text(
            "Try asking: “My cat vomited today. Should I worry?”",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}

// ---------- Messages ----------

@Composable
private fun UserBubble(item: UiChatItem.User) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(0.88f),
        ) {
            Text(
                item.text,
                modifier = Modifier.padding(12.dp),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun ThinkingPawIndicator(petName: String, hasContext: Boolean) {
    val transition = rememberInfiniteTransition(label = "pawThinking")
    var step by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1600)
        step = 1
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(0.88f),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { index ->
                val alpha by transition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 450, delayMillis = index * 130),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "paw$index",
                )
                Text(text = "\uD83D\uDC3E", modifier = Modifier.alpha(alpha), fontSize = 15.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (step == 0) {
                    if (hasContext) "Checking $petName's recent care..." else "Thinking about your question..."
                } else {
                    "Putting together a clear answer..."
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun DelayedVisibility(delayMs: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMs.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(220)) + slideInVertically(animationSpec = tween(220)) { it / 4 },
    ) {
        content()
    }
}

@Composable
private fun AiAnswerCard(
    item: UiChatItem.Assistant,
    petName: String,
    onSave: () -> Unit,
    onHelpful: () -> Unit,
    onNotHelpful: () -> Unit,
    onReport: () -> Unit,
    onFollowUp: (String) -> Unit,
) {
    val answer = item.answer
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }
    val timeLabel = remember(item.time) {
        Instant.ofEpochMilli(item.time).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("h:mm a"))
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DelayedVisibility(0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\uD83D\uDC3E", fontSize = 15.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("Pet AI Companion", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    UrgencyBadge(answer.urgency)
                }
            }
            if (answer.title.isNotBlank()) {
                DelayedVisibility(80) {
                    Text(answer.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            }
            DelayedVisibility(160) {
                Text(answer.summary, fontSize = 14.sp)
            }
            answer.sections.forEachIndexed { index, section ->
                DelayedVisibility(240 + index * 120) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        Text(
                            section.title.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        section.items.forEach { bullet ->
                            Row {
                                Text("• ", fontSize = 14.sp)
                                Text(bullet, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            if (answer.contextNote != null) {
                Text(
                    answer.contextNote,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Text(
                answer.disclaimer,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(timeLabel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSave, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (item.isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = "Save",
                        modifier = Modifier.size(17.dp),
                    )
                }
                if (item.feedback == null && !item.reported) {
                    IconButton(onClick = onHelpful, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.ThumbUp, contentDescription = "Helpful", modifier = Modifier.size(17.dp))
                    }
                    IconButton(onClick = onNotHelpful, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.ThumbDown, contentDescription = "Not helpful", modifier = Modifier.size(17.dp))
                    }
                } else {
                    Text(
                        when {
                            item.reported -> "Reported. Thank you."
                            item.feedback == "helpful" -> "Thanks for the feedback"
                            else -> "Thanks — we'll improve"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", modifier = Modifier.size(17.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Copy") },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                            onClick = {
                                menuOpen = false
                                clipboard.setText(AnnotatedString(answerToText(answer)))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                            onClick = {
                                menuOpen = false
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, answerToText(answer))
                                }
                                context.startActivity(Intent.createChooser(intent, "Share AI answer"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Report response") },
                            leadingIcon = { Icon(Icons.Filled.Flag, null) },
                            onClick = { menuOpen = false; onReport() },
                        )
                    }
                }
            }
            if (answer.followUps.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    answer.followUps.forEach { followUp ->
                        FilterChip(
                            selected = false,
                            onClick = { onFollowUp(followUp) },
                            label = { Text(followUp, fontSize = 12.sp) },
                        )
                    }
                }
            }
        }
    }
}

private fun answerToText(answer: AiAnswer): String {
    val sb = StringBuilder()
    if (answer.title.isNotBlank()) sb.append(answer.title).append("\n\n")
    sb.append(answer.summary)
    answer.sections.forEach { s ->
        sb.append("\n\n").append(s.title).append(":\n")
        s.items.forEach { sb.append("• ").append(it).append("\n") }
    }
    sb.append("\n").append(answer.disclaimer)
    return sb.toString().trim()
}

@Composable
private fun UrgencyBadge(urgency: String) {
    val (label, color) = when (urgency) {
        "MONITOR" -> "Monitor" to Color(0xFFB7791F)
        "VET_SOON" -> "Vet recommended" to Color(0xFFE57B6C)
        "URGENT" -> "Urgent" to Color(0xFFD32F2F)
        else -> "General guidance" to Color(0xFF2E7D5B)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun FailedCard(errorCode: String?, onRetry: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(0.88f),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when (errorCode) {
                    "OFFLINE" -> "You're offline. Your question was kept."
                    "HTTP_502" -> "AI is busy right now. Your question was kept — tap Retry."
                    "QUOTA" -> "Today's free AI questions are used up."
                    else -> "Couldn't send. Your question was kept."
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Retry", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun AiQuotaPill(quota: AiQuota?, localPro: Boolean) {
    val serverPro = quota?.tier == "pro" || quota?.tier == "lifetime"
    val limit = when {
        serverPro -> quota?.limit
        localPro -> 20
        else -> quota?.limit
    }
    val remaining = when {
        serverPro -> quota?.remaining
        localPro -> quota?.let { (20 - it.used).coerceAtLeast(0) } ?: 20
        else -> quota?.remaining
    }
    if (remaining == null || limit == null) return
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (remaining > 0) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            "$remaining of $limit left",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
    Spacer(Modifier.width(8.dp))
}

// ---------- History ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiHistorySheet(
    sessions: List<ChatSession>,
    currentId: Long,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onRename: (ChatSession) -> Unit,
    onDelete: (ChatSession) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AI chats", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
            Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("New chat")
            }
            sessions.forEach { session ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (session.id == currentId) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(session.id) },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                session.title.ifBlank { "Conversation" },
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1,
                            )
                            session.lastMessagePreview?.takeIf { it.isNotBlank() }?.let {
                                Text(it, fontSize = 12.sp, maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                        if (session.id == currentId) {
                            Icon(Icons.Filled.Check, contentDescription = "Current", modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { onRename(session) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Edit, contentDescription = "Rename", modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { onDelete(session) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            if (sessions.isEmpty()) {
                Text("No chats yet.", fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AiRenameDialog(current: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var title by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename chat") },
        text = {
            OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true,
                label = { Text("Title") })
        },
        confirmButton = { TextButton(onClick = { onSubmit(title) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------- Report ----------

@Composable
private fun AiReportDialog(onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var category by remember { mutableStateOf("incorrect") }
    var detail by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report this answer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "unsafe" to "Unsafe advice",
                    "incorrect" to "Incorrect information",
                    "offensive" to "Offensive response",
                    "other" to "Other",
                ).forEach { (id, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { category = id },
                    ) {
                        val selected = category == id
                        Box(
                            modifier = Modifier.size(18.dp).clip(CircleShape).background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, fontSize = 14.sp)
                    }
                }
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text("Details (optional)") },
                    maxLines = 3,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(category, detail) }) { Text("Send report") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
