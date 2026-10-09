package com.petcare.companionlite.ui.ai

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcare.companionlite.R
import com.petcare.companionlite.data.ai.AiQuota
import com.petcare.companionlite.data.model.ChatSession
import com.petcare.companionlite.ui.components.bouncyPress
import com.petcare.companionlite.ui.components.popIn

/** Free-tier daily allowance shown locally when the user has a Pro subscription in Play but no server quota yet. */
private const val LOCAL_PRO_DAILY_LIMIT = 20

/**
 * Quota pill for the top bar: "9 of 10 left" with a small progress track.
 * Tapping it opens the upgrade sheet for free users; Pro users see a read-only pill.
 */
@Composable
internal fun AiQuotaPill(quota: AiQuota?, localPro: Boolean, onClick: (() -> Unit)? = null) {
    val serverPro = quota?.tier == "pro" || quota?.tier == "lifetime"
    val limit = when {
        serverPro -> quota?.limit
        localPro -> LOCAL_PRO_DAILY_LIMIT
        else -> quota?.limit
    }
    val remaining = when {
        serverPro -> quota?.remaining
        localPro -> quota?.let { (LOCAL_PRO_DAILY_LIMIT - it.used).coerceAtLeast(0) } ?: LOCAL_PRO_DAILY_LIMIT
        else -> quota?.remaining
    }
    if (remaining == null || limit == null) return

    val fraction = if (limit > 0) (remaining.toFloat() / limit).coerceIn(0f, 1f) else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 500),
        label = "quotaFraction",
    )
    val container = if (remaining > 0) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.errorContainer
    val pillShape = RoundedCornerShape(percent = 50)
    val source = remember { MutableInteractionSource() }

    val pillContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                stringResource(R.string.ui_992e32ba92, remaining, limit),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            LinearProgressIndicator(
                progress = { animatedFraction },
                modifier = Modifier.width(56.dp).height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            )
        }
    }

    val pill: @Composable () -> Unit = {
        Surface(shape = pillShape, color = container) {
            pillContent()
        }
    }
    // Free users get a 48dp tap target around the pill; Pro users get a read-only pill.
    val tapAction = onClick.takeIf { !serverPro && !localPro }
    if (tapAction != null) {
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .bouncyPress(source)
                .clickable(
                    interactionSource = source,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = tapAction,
                ),
            contentAlignment = Alignment.Center,
        ) {
            pill()
        }
    } else {
        pill()
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
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.ui_3fd9fe09d1),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ui_bbfa773e5a))
                }
            }
            Button(onClick = onNew, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.ui_009bf6b90c))
            }
            sessions.forEachIndexed { index, session ->
                val isCurrent = session.id == currentId
                Surface(
                    onClick = { onOpen(session.id) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().popIn(index),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                session.title.ifBlank { stringResource(R.string.ui_2a20c75033) },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            session.lastMessagePreview?.takeIf { it.isNotBlank() }?.let { preview ->
                                Text(
                                    preview,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                )
                            }
                        }
                        if (isCurrent) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = stringResource(R.string.ui_4fc0e2bc80),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(onClick = { onRename(session) }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.ui_d3f4cb898f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(onClick = { onDelete(session) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.ui_f6fdbe48dc),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            if (sessions.isEmpty()) {
                Text(stringResource(R.string.ui_198b4b30a6), fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
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
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text(stringResource(R.string.ui_768e0c1c69)) },
            )
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
    val options = listOf(
        "unsafe" to stringResource(R.string.ui_cc55c6931b),
        "incorrect" to stringResource(R.string.ui_b533c5013d),
        "offensive" to stringResource(R.string.ui_feed8c4249),
        "other" to stringResource(R.string.ui_6e6a6f2086),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_6efc1bad13)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { (id, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { category = id },
                    ) {
                        RadioButton(selected = category == id, onClick = null)
                        Spacer(Modifier.width(6.dp))
                        Text(label, fontSize = 14.sp)
                    }
                }
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text(stringResource(R.string.ui_138a572192)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(category, detail) }) { Text(stringResource(R.string.ui_a5b32af957)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_77dfd2135f)) } },
    )
}
