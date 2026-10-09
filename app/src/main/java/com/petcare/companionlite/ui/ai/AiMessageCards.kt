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

@Composable
internal fun AiWelcomeState(petName: String, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.ui_ec6de1fe06, petName), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            stringResource(R.string.ui_d6c045295f, petName),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        )
        val chips = listOf(
            stringResource(R.string.ai_chip_vomiting) to stringResource(R.string.ui_8cc8394424),
            stringResource(R.string.ai_chip_diet) to stringResource(R.string.ui_77cc221865),
            stringResource(R.string.ai_chip_behaviour) to stringResource(R.string.ui_d080f7cc4f),
            stringResource(R.string.ai_chip_medicine) to stringResource(R.string.ui_225a8609bb),
            stringResource(R.string.ai_chip_training) to stringResource(R.string.ui_7060f7b75c),
            stringResource(R.string.ai_chip_grooming) to stringResource(R.string.ui_edb418b42a),
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
            stringResource(R.string.ui_2663b41a8d),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}

// ---------- Messages ----------

@Composable
internal fun UserBubble(item: UiChatItem.User) {
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
internal fun ThinkingPawIndicator(petName: String, hasContext: Boolean) {
    val transition = rememberInfiniteTransition(label = stringResource(R.string.ui_bf3c264a52))
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
                    label = stringResource(R.string.ui_046ff98928, index),
                )
                Text(text = "\uD83D\uDC3E", modifier = Modifier.alpha(alpha), fontSize = 15.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (step == 0) {
                    if (hasContext) stringResource(R.string.ui_e42a53bdc7, petName) else stringResource(R.string.ui_0fd52840c9)
                } else {
                    stringResource(R.string.ui_d0c8676961)
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
internal fun DelayedVisibility(delayMs: Int, content: @Composable () -> Unit) {
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
internal fun AiAnswerCard(
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
                    Text(stringResource(R.string.ui_1a27b9cd8d), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                        contentDescription = stringResource(R.string.ui_efc007a393),
                        modifier = Modifier.size(17.dp),
                    )
                }
                if (item.feedback == null && !item.reported) {
                    IconButton(onClick = onHelpful, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.ThumbUp, contentDescription = stringResource(R.string.ui_45e36dd93e), modifier = Modifier.size(17.dp))
                    }
                    IconButton(onClick = onNotHelpful, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.ThumbDown, contentDescription = stringResource(R.string.ui_85efce01b3), modifier = Modifier.size(17.dp))
                    }
                } else {
                    Text(
                        when {
                            item.reported -> stringResource(R.string.ui_d14e23dc3a)
                            item.feedback == stringResource(R.string.ui_4b7392910a) -> stringResource(R.string.ui_629814075e)
                            else -> stringResource(R.string.ui_7a8c959d3c)
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.ui_4bab2d8fe1), modifier = Modifier.size(17.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_af74f7c536)) },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                            onClick = {
                                menuOpen = false
                                clipboard.setText(AnnotatedString(answerToText(answer)))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_09ca55ca52)) },
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
                            text = { Text(stringResource(R.string.ui_f3d0cb1498)) },
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

internal fun answerToText(answer: AiAnswer): String {
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
internal fun UrgencyBadge(urgency: String) {
    val (label, color) = when (urgency) {
        "MONITOR" -> stringResource(R.string.ui_ec0f106980) to Color(0xFFB7791F)
        "VET_SOON" -> stringResource(R.string.ui_cc1e89759d) to Color(0xFFE57B6C)
        "URGENT" -> stringResource(R.string.ui_ecb26f46e3) to Color(0xFFD32F2F)
        else -> stringResource(R.string.ui_d1eb45176a) to Color(0xFF2E7D5B)
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
internal fun FailedCard(errorCode: String?, onRetry: () -> Unit) {
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
                    stringResource(R.string.ui_c689932b7d) -> stringResource(R.string.ui_56a07c9344)
                    stringResource(R.string.ui_8a7b905044) -> stringResource(R.string.ui_16b670bbd2)
                    stringResource(R.string.ui_6d1ea03c3b) -> stringResource(R.string.ui_640985fa1f)
                    else -> stringResource(R.string.ui_154076ea03)
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.ui_9f5cd8a2e8), fontSize = 13.sp)
            }
        }
    }
}

