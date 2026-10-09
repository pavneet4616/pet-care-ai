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
internal fun AiQuotaPill(quota: AiQuota?, localPro: Boolean) {
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
            stringResource(R.string.ui_992e32ba92, remaining, limit),
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
internal fun AiHistorySheet(
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
                Text(stringResource(R.string.ui_3fd9fe09d1), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ui_bbfa773e5a))
                }
            }
            Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.ui_009bf6b90c))
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
                                session.title.ifBlank { stringResource(R.string.ui_2a20c75033) },
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1,
                            )
                            session.lastMessagePreview?.takeIf { it.isNotBlank() }?.let {
                                Text(it, fontSize = 12.sp, maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                        if (session.id == currentId) {
                            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.ui_4fc0e2bc80), modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { onRename(session) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.ui_d3f4cb898f), modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { onDelete(session) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.ui_f6fdbe48dc), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            if (sessions.isEmpty()) {
                Text(stringResource(R.string.ui_198b4b30a6), fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun AiRenameDialog(current: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var title by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_3c1a5c629b)) },
        text = {
            OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true,
                label = { Text(stringResource(R.string.ui_768e0c1c69)) })
        },
        confirmButton = { TextButton(onClick = { onSubmit(title) }) { Text(stringResource(R.string.ui_efc007a393)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_77dfd2135f)) } },
    )
}

// ---------- Report ----------

@Composable
internal fun AiReportDialog(onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var category by remember { mutableStateOf("incorrect") }
    var detail by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_6efc1bad13)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "unsafe" to stringResource(R.string.ui_cc55c6931b),
                    "incorrect" to stringResource(R.string.ui_b533c5013d),
                    "offensive" to stringResource(R.string.ui_feed8c4249),
                    "other" to stringResource(R.string.ui_6e6a6f2086),
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
                    label = { Text(stringResource(R.string.ui_138a572192)) },
                    maxLines = 3,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(category, detail) }) { Text(stringResource(R.string.ui_a5b32af957)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_77dfd2135f)) } },
    )
}
