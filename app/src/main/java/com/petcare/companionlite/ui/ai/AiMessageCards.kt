package com.petcare.companionlite.ui.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcare.companionlite.R
import com.petcare.companionlite.data.ai.AiAnswer
import com.petcare.companionlite.data.ai.AiAnswerSection
import com.petcare.companionlite.core.CareFormats
import com.petcare.companionlite.data.ai.UiChatItem
import com.petcare.companionlite.data.model.PetType
import com.petcare.companionlite.ui.components.BobbingEmoji
import com.petcare.companionlite.ui.components.PawBurst
import com.petcare.companionlite.ui.components.bouncyPress
import com.petcare.companionlite.ui.components.popIn
import com.petcare.companionlite.ui.theme.EmergencyRed
import kotlinx.coroutines.delay

/** Chat bubble corners: the user's bubble has its tail bottom-right, the assistant's top-left. */
internal val AiUserBubbleShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
internal val AiCardShape = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp)

private const val PAW = "🐾"

// ---------- Welcome ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiWelcomeState(petName: String, petType: PetType?, onPick: (String) -> Unit) {
    val example = when (petType) {
        PetType.DOG -> stringResource(R.string.ai_example_dog)
        PetType.CAT -> stringResource(R.string.ai_example_cat)
        PetType.BIRD -> stringResource(R.string.ai_example_bird)
        else -> stringResource(R.string.ai_example_other)
    }
    val chips = listOf(
        stringResource(R.string.ai_chip_vomiting) to stringResource(R.string.ui_8cc8394424),
        stringResource(R.string.ai_chip_diet) to stringResource(R.string.ui_77cc221865),
        stringResource(R.string.ai_chip_behaviour) to stringResource(R.string.ui_d080f7cc4f),
        stringResource(R.string.ai_chip_medicine) to stringResource(R.string.ui_225a8609bb),
        stringResource(R.string.ai_chip_training) to stringResource(R.string.ui_7060f7b75c),
        stringResource(R.string.ai_chip_grooming) to stringResource(R.string.ui_edb418b42a),
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(128.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            BobbingEmoji(emoji = (petType ?: PetType.OTHER).emoji, size = 92.dp)
        }
        Text(
            stringResource(R.string.ai_welcome_title, petName),
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.ui_d6c045295f, petName),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("💡", fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    example,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.ai_chips_heading),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                chips.forEachIndexed { index, (label, prompt) ->
                    AiSuggestionPill(
                        label = label,
                        onClick = { onPick(prompt) },
                        modifier = Modifier.popIn(index),
                    )
                }
            }
        }
    }
}

/**
 * Rounded, springy suggestion pill. The 48dp touch target wraps a smaller visible pill, so the
 * pill stays compact while the tap area meets the minimum.
 */
@Composable
internal fun AiSuggestionPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .bouncyPress(source)
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}

// ---------- Messages ----------

@Composable
internal fun UserBubble(item: UiChatItem.User, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Surface(
            shape = AiUserBubbleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.CenterEnd).widthIn(max = maxWidth * 0.86f),
        ) {
            Text(
                item.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
        }
    }
}

/** Round paw avatar that marks the assistant's messages. */
@Composable
internal fun AiAvatar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(PAW, fontSize = 16.sp)
    }
}

/** Three paws bouncing in turn while the assistant is replying. */
@Composable
internal fun ThinkingPawIndicator(petName: String, hasContext: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aiThinking")
    var step by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        delay(1600)
        step = 1
    }
    Row(
        modifier = modifier.fillMaxWidth().popIn(),
        verticalAlignment = Alignment.Top,
    ) {
        AiAvatar()
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = AiCardShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) { index ->
                    val lift by transition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 380, delayMillis = index * 120, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "pawBounce$index",
                    )
                    Text(
                        text = PAW,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .graphicsLayer {
                                translationY = -lift * 6.dp.toPx()
                                alpha = 0.45f + 0.55f * lift
                            },
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = when {
                        step == 0 && hasContext -> stringResource(R.string.ui_e42a53bdc7, petName)
                        step == 0 -> stringResource(R.string.ui_0fd52840c9)
                        else -> stringResource(R.string.ui_d0c8676961)
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
internal fun DelayedVisibility(delayMs: Int, animate: Boolean = true, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) {
        if (animate) {
            delay(delayMs.toLong())
            visible = true
        }
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
    animateIn: Boolean,
    onSave: () -> Unit,
    onHelpful: () -> Unit,
    onNotHelpful: () -> Unit,
    onReport: () -> Unit,
    onFollowUp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val answer = item.answer
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }
    val disclaimer = answer.disclaimerOr(stringResource(R.string.ai_disclaimer_default))
    val shareTitle = stringResource(R.string.ai_share_chooser_title)
    // Device locale and 12/24-hour setting, via the shared helper (no hard-coded clock pattern).
    val timeLabel = remember(context, item.time) { CareFormats.clockText(context, item.time) }
    val urgent = answer.urgency == "URGENT"

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            AiAvatar()
            PawBurst(trigger = if (animateIn) item.id else null, count = 6, radius = 30.dp)
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = AiCardShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = if (urgent) BorderStroke(2.dp, EmergencyRed) else null,
            modifier = Modifier.weight(1f),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DelayedVisibility(0, animate = animateIn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.ui_1a27b9cd8d),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.weight(1f))
                        UrgencyBadge(answer.urgency)
                    }
                }
                if (answer.title.isNotBlank()) {
                    DelayedVisibility(80, animate = animateIn) {
                        Text(
                            answer.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            lineHeight = 25.sp,
                        )
                    }
                }
                DelayedVisibility(160, animate = animateIn) {
                    Text(answer.summary, fontSize = 15.sp, lineHeight = 22.sp)
                }
                if (urgent) {
                    DelayedVisibility(200, animate = animateIn) {
                        UrgentActionBlock()
                    }
                }
                answer.sections.forEachIndexed { index, section ->
                    DelayedVisibility(240 + index * 120, animate = animateIn) {
                        AnswerSectionBlock(section)
                    }
                }
                answer.contextNote?.let { note ->
                    Text(
                        note,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
                Text(
                    disclaimer,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        timeLabel,
                        fontSize = 11.sp,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                    if (item.feedback == null && !item.reported) {
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onHelpful) {
                            Icon(
                                Icons.Filled.ThumbUp,
                                contentDescription = stringResource(R.string.ui_45e36dd93e),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(onClick = onNotHelpful) {
                            Icon(
                                Icons.Filled.ThumbDown,
                                contentDescription = stringResource(R.string.ui_85efce01b3),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        Text(
                            when {
                                item.reported -> stringResource(R.string.ui_d14e23dc3a)
                                item.feedback == "helpful" -> stringResource(R.string.ui_629814075e)
                                else -> stringResource(R.string.ui_7a8c959d3c)
                            },
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                    }
                    IconButton(onClick = onSave) {
                        Icon(
                            if (item.isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = stringResource(R.string.ui_efc007a393),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.ui_4bab2d8fe1),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_af74f7c536)) },
                                leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    clipboard.setText(AnnotatedString(answerToText(answer, disclaimer)))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_09ca55ca52)) },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, answerToText(answer, disclaimer))
                                    }
                                    runCatching { context.startActivity(Intent.createChooser(intent, shareTitle)) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_f3d0cb1498)) },
                                leadingIcon = { Icon(Icons.Filled.Flag, contentDescription = null) },
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
                            AiSuggestionPill(label = followUp, onClick = { onFollowUp(followUp) })
                        }
                    }
                }
            }
        }
    }
}

/** Urgent answers get a red border plus a one-tap way to find an emergency vet. */
@Composable
private fun UrgentActionBlock() {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = EmergencyRed.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚠️", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.ai_urgent_banner),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f),
                )
            }
            Button(
                onClick = { openEmergencyVetSearch(context) },
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.emergency_find_vet), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Same search the Emergency screen opens: Google Maps first, then any map app, then the web. */
private fun openEmergencyVetSearch(context: Context) {
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=emergency+veterinarian"))
    val maps = Intent(geo).setPackage("com.google.android.apps.maps")
    runCatching { context.startActivity(maps) }
        .recoverCatching { context.startActivity(geo) }
        .onFailure {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/emergency+veterinarian")),
                )
            }
        }
}

@Composable
private fun AnswerSectionBlock(section: AiAnswerSection) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sectionEmoji(section.type), fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                section.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        }
        section.items.forEach { bullet ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier.width(16.dp).height(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (section.type == "ACTION") {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(bullet, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun sectionEmoji(type: String): String = when (type) {
    "ACTION" -> "✅"
    "WATCH" -> "👀"
    "VET" -> "🩺"
    else -> "💡"
}

internal fun answerToText(answer: AiAnswer, disclaimer: String): String {
    val sb = StringBuilder()
    if (answer.title.isNotBlank()) sb.append(answer.title).append("\n\n")
    sb.append(answer.summary)
    answer.sections.forEach { s ->
        sb.append("\n\n").append(s.title).append(":\n")
        s.items.forEach { sb.append("• ").append(it).append("\n") }
    }
    sb.append("\n").append(disclaimer)
    return sb.toString().trim()
}

@Composable
internal fun UrgencyBadge(urgency: String) {
    val (label, color) = when (urgency) {
        "MONITOR" -> stringResource(R.string.ui_ec0f106980) to Color(0xFFB7791F)
        "VET_SOON" -> stringResource(R.string.ui_cc1e89759d) to Color(0xFFE57B6C)
        "URGENT" -> stringResource(R.string.ui_ecb26f46e3) to EmergencyRed
        else -> stringResource(R.string.ui_d1eb45176a) to Color(0xFF2E7D5B)
    }
    // URGENT is a solid chip so it stands out; the others are soft tints.
    val solid = urgency == "URGENT"
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (solid) color else color.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (solid) Color.White else color,
        )
    }
}

/** Retry card for a failed send. Error codes come from AiApi / AiRepository and map to localized text. */
@Composable
internal fun FailedCard(errorCode: String?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        AiAvatar()
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = AiCardShape,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(failureMessage(errorCode), fontSize = 14.sp, lineHeight = 20.sp)
                OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ui_9f5cd8a2e8), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun failureMessage(errorCode: String?): String = when {
    errorCode == "OFFLINE" -> stringResource(R.string.ui_56a07c9344)
    errorCode == "NETWORK" -> stringResource(R.string.ui_fee216d567)
    errorCode == "RATE_LIMITED" -> stringResource(R.string.ui_9ffa9a1a62)
    errorCode == "QUOTA" -> stringResource(R.string.ui_640985fa1f)
    errorCode == "EMPTY_REPLY" -> stringResource(R.string.ui_ab02c70c11)
    errorCode == "BAD_RESPONSE" -> stringResource(R.string.ui_77568584db)
    errorCode == "BAD_ANSWER" -> stringResource(R.string.ui_9eb7c346dc)
    errorCode == "DUPLICATE" -> stringResource(R.string.ui_c59b6c9c51)
    errorCode == "DATA_DELETED" -> stringResource(R.string.ui_423380173a)
    errorCode?.startsWith("HTTP_5") == true -> stringResource(R.string.ui_16b670bbd2)
    else -> stringResource(R.string.ui_154076ea03)
}
