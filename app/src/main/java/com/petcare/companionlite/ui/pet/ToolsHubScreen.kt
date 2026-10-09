package com.petcare.companionlite.ui.pet

import com.petcare.companionlite.R

import androidx.compose.ui.res.stringResource

import androidx.compose.animation.*
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.petcare.companionlite.PetCareApplication
import com.petcare.companionlite.core.CareFormats
import com.petcare.companionlite.ui.components.AdBanner
import com.petcare.companionlite.ui.components.PawBurst
import com.petcare.companionlite.ui.components.bouncyPress
import com.petcare.companionlite.ui.components.expenseLabel
import com.petcare.companionlite.ui.components.popIn
import com.petcare.companionlite.ui.theme.*

private val WaterBlue = Color(0xFF4FC3F7)
private val MoodAmber = Color(0xFFFFCA28)
private val VomitOrange = Color(0xFFFF7043)
private val NotesTeal = Color(0xFF26A69A)
private val ExpensePurple = Color(0xFFAB47BC)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsHubScreen(petId: Long, onBack: () -> Unit, onAskAi: (Long, String) -> Unit = { _, _ -> }) {
    val context = LocalContext.current
    val app = context.applicationContext as PetCareApplication
    val vm: ToolsHubViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ToolsHubViewModel(
                app.container.repository,
                context.applicationContext,
            ) as T
        }
    })
    LaunchedEffect(petId) { vm.setPetId(petId) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val adsManager = app.container.adsManager

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    var showWater by remember { mutableStateOf(false) }
    var showMood by remember { mutableStateOf(false) }
    var showVomit by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(false) }
    var showExpense by remember { mutableStateOf(false) }

    // Celebration triggers: null until the first log, then a new value for every log.
    var waterBurst by remember { mutableStateOf<Int?>(null) }
    var moodBurst by remember { mutableStateOf<Int?>(null) }

    val appSettings by app.container.repository.settings.collectAsStateWithLifecycle(
        initialValue = com.petcare.companionlite.data.model.SettingsState(),
    )
    val adsAllowed by adsManager.adsAllowed.collectAsStateWithLifecycle()
    LaunchedEffect(adsAllowed, appSettings.hasNoAds) {
        if (adsAllowed && !appSettings.hasNoAds) adsManager.loadRewarded(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ui_01df9def1d), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.ui_b52b36b726))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                // The NavHost host already applies the status bar inset; a second one leaves an empty band.
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── TODAY SUMMARY ──
            item { TodaySummaryCard(state, appSettings.currencyCode, Modifier.popIn(0)) }

            // ── WATER SECTION ──
            item {
                Box(modifier = Modifier.fillMaxWidth().popIn(1)) {
                    ToolSection(stringResource(R.string.ui_85042a9e4e), WaterBlue, Icons.Filled.LocalDrink, stringResource(R.string.ui_b1788cfa6e, state.todayWaterTotal), { showWater = true }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(100, 250, 500).forEach { ml ->
                                WaterAmountButton(ml = ml, modifier = Modifier.weight(1f)) {
                                    vm.logWater(petId, ml)
                                    waterBurst = (waterBurst ?: 0) + 1
                                }
                            }
                        }
                        state.waterLogs.take(3).forEach { log ->
                            LogEntry("${log.amountMl}ml", PetTimeText.stamp(context, log.timestamp), WaterBlue) { vm.deleteWaterLog(log.id) }
                        }
                    }
                    PawBurst(trigger = waterBurst)
                }
            }

            // ── MOOD SECTION ──
            item {
                val moodChoices = listOf(
                    "😊" to stringResource(R.string.ui_be31a86c98),
                    "😐" to stringResource(R.string.ui_4bd2913945),
                    "😢" to stringResource(R.string.ui_8057f8b2a5),
                    "😴" to stringResource(R.string.ui_d73ccd1316),
                    "🤒" to stringResource(R.string.ui_630aa21093),
                )
                Box(modifier = Modifier.fillMaxWidth().popIn(2)) {
                    ToolSection(stringResource(R.string.ui_2004b16ce5), MoodAmber, Icons.Filled.Mood, stringResource(R.string.ui_27e04ccd43, state.latestMood), { showMood = true }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            moodChoices.forEach { (emoji, label) ->
                                MoodChoice(emoji = emoji, label = label, modifier = Modifier.weight(1f)) {
                                    vm.logMood(petId, emoji, label)
                                    moodBurst = (moodBurst ?: 0) + 1
                                }
                            }
                        }
                        state.moodLogs.take(3).forEach { log ->
                            LogEntry(log.note, PetTimeText.stamp(context, log.timestamp), MoodAmber)
                        }
                    }
                    PawBurst(trigger = moodBurst)
                }
            }

            // ── AD BANNER between sections ──
            item { AdBanner(enabled = !appSettings.hasNoAds) }

            // ── VOMIT SECTION ──
            item {
                Box(modifier = Modifier.fillMaxWidth().popIn(4)) {
                    ToolSection(stringResource(R.string.ui_2b43466a75), VomitOrange, Icons.Filled.Sick, stringResource(R.string.ui_4398d86c0f, state.vomitCountToday), { showVomit = true }) {
                        state.vomitLogs.take(3).forEach { log ->
                            LogEntry(log.note.ifBlank { stringResource(R.string.ui_feb141d571) }, PetTimeText.stamp(context, log.timestamp), VomitOrange)
                        }
                        if (state.vomitLogs.isEmpty()) EmptyHint("🌿", stringResource(R.string.ui_d62455077a))
                        OutlinedButton(
                            onClick = {
                                val latest = state.vomitLogs.firstOrNull()?.note?.take(80).orEmpty()
                                val q = if (latest.isNotBlank()) context.getString(R.string.health_incident_prompt, latest)
                                else context.getString(R.string.ui_4cd769c70c)
                                onAskAi(petId, q)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(stringResource(R.string.ui_9b2f07654e), fontSize = 13.sp)
                        }
                    }
                }
            }

            // ── NOTES SECTION ──
            item {
                Box(modifier = Modifier.fillMaxWidth().popIn(5)) {
                    ToolSection(stringResource(R.string.ui_f401f582e6), NotesTeal, Icons.AutoMirrored.Filled.Notes, stringResource(R.string.ui_e21aba7108, state.notes.size), { showNote = true }) {
                        state.notes.take(5).forEach { note ->
                            NoteEntry(note, onToggle = { vm.toggleNoteCompleted(note) }, onDelete = { vm.deleteNote(note.id) })
                        }
                        if (state.notes.isEmpty()) EmptyHint("📝", stringResource(R.string.ui_25345bddcb))
                    }
                }
            }

            // ── EXPENSES SECTION ──
            item {
                Box(modifier = Modifier.fillMaxWidth().popIn(6)) {
                    ToolSection(stringResource(R.string.ui_8d437dbf95), ExpensePurple, Icons.Filled.AttachMoney, CareFormats.currency(state.totalExpenses, appSettings.currencyCode), { showExpense = true }) {
                        state.expenses.take(4).forEach { exp ->
                            LogEntry(expenseLabel(exp.category) + ": " + CareFormats.currency(exp.amount, appSettings.currencyCode), PetTimeText.stamp(context, exp.timestamp), ExpensePurple) { vm.deleteExpense(exp.id) }
                        }
                        if (state.expenses.isEmpty()) EmptyHint("💸", stringResource(R.string.ui_8792e123b4))
                    }
                }
            }

            // ── BOTTOM AD ──
            item { AdBanner(enabled = !appSettings.hasNoAds) }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    // ── DIALOGS ──
    if (showWater) WaterDialog({ showWater = false }) { ml ->
        vm.logWater(petId, ml)
        waterBurst = (waterBurst ?: 0) + 1
    }
    if (showMood) MoodDialog({ showMood = false }) { emoji, note ->
        vm.logMood(petId, emoji, note)
        moodBurst = (moodBurst ?: 0) + 1
    }
    if (showVomit) InputDialog(stringResource(R.string.ui_2bf1654bd5), stringResource(R.string.health_incident_details), { showVomit = false }) { vm.logVomit(petId, it) }
    if (showNote) NoteDialog({ showNote = false }) { content, isTodo -> vm.saveNote(petId, content, isTodo) }
    if (showExpense) ExpenseDialog({ showExpense = false }) { amt, cat, note -> vm.saveExpense(petId, amt, cat, note) }
}

// ── COMPOSABLES ──────────────────────────────────────────

@Composable
private fun TodaySummaryCard(state: ToolsHubUiState, currencyCode: String, modifier: Modifier = Modifier) {
    val animatedWater by animateIntAsState(
        targetValue = state.todayWaterTotal,
        animationSpec = tween(durationMillis = 600),
        label = "today-water",
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(DeepSage, Color(0xFF2E5A47)))).padding(20.dp)) {
            Column {
                Text(stringResource(R.string.ui_8f6d5be5c7), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SummaryChip("💧", "${animatedWater}ml", Modifier.weight(1f))
                    SummaryChip("😊", state.latestMood, Modifier.weight(1f))
                    SummaryChip("💰", CareFormats.currency(state.todayExpenseTotal, currencyCode), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(emoji: String, value: String, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.15f))) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 20.sp)
            Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ToolSection(title: String, accent: Color, icon: ImageVector, subtitle: String, onAdd: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val addSource = remember { MutableInteractionSource() }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(accent.copy(0.15f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                }
                FilledIconButton(
                    onClick = onAdd,
                    modifier = Modifier.size(44.dp).bouncyPress(addSource),
                    interactionSource = addSource,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent.copy(0.2f)),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = title, tint = accent, modifier = Modifier.size(18.dp))
                }
            }
            content()
        }
    }
}

@Composable
private fun WaterAmountButton(ml: Int, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.bouncyPress(source),
        interactionSource = source,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(stringResource(R.string.ui_b34fdeb8c4, ml), fontSize = 13.sp)
    }
}

@Composable
private fun MoodChoice(emoji: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .bouncyPress(source)
            .clip(RoundedCornerShape(12.dp))
            .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
            .padding(8.dp),
    ) {
        Text(emoji, fontSize = 24.sp)
        Text(label, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
    }
}

@Composable
private fun LogEntry(text: String, time: String, accent: Color, onDelete: (() -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface.copy(0.5f)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(10.dp))
        Text(text, modifier = Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(time, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
        onDelete?.let {
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = it, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ui_f6fdbe48dc), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.3f))
            }
        }
    }
}

@Composable
private fun NoteEntry(note: com.petcare.companionlite.data.model.Note, onToggle: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface.copy(0.5f)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (note.isTodo) {
            Checkbox(checked = note.isCompleted, onCheckedChange = { onToggle() }, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        } else {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(NotesTeal))
            Spacer(Modifier.width(10.dp))
        }
        Text(note.content, modifier = Modifier.weight(1f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textDecoration = if (note.isCompleted) TextDecoration.LineThrough else null, color = if (note.isCompleted) MaterialTheme.colorScheme.onSurface.copy(0.4f) else MaterialTheme.colorScheme.onSurface)
        Text(PetTimeText.stamp(context, note.timestamp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ui_f6fdbe48dc), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.3f))
        }
    }
}

@Composable
private fun EmptyHint(emoji: String, text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
    }
}

// ── DIALOGS ──────────────────────────────────────────────

@Composable
private fun WaterDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var input by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.ui_f8d55b24fa)) },
        text = { OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text(stringResource(R.string.ui_c068fc8e89)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) },
        confirmButton = { TextButton(onClick = { input.toIntOrNull()?.let { if (it > 0) { onConfirm(it); onDismiss() } } }) { Text(stringResource(R.string.ui_8bf95ea372)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_77dfd2135f)) } })
}

@Composable
private fun MoodDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var selected by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val moods = listOf("😊" to stringResource(R.string.ui_be31a86c98), "😐" to stringResource(R.string.ui_4bd2913945), "😢" to stringResource(R.string.ui_8057f8b2a5), "😴" to stringResource(R.string.ui_d73ccd1316), "🤒" to stringResource(R.string.ui_630aa21093), "😡" to stringResource(R.string.ui_b674094954), "🥰" to stringResource(R.string.ui_6f345fa2ac), "😰" to stringResource(R.string.ui_1f15785ad4))
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.ui_6818bebd34)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    moods.forEach { (emoji, label) ->
                        val isSel = selected == emoji
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (isSel) MoodAmber.copy(0.2f) else Color.Transparent).border(if (isSel) BorderStroke(2.dp, MoodAmber) else BorderStroke(1.dp, Color.Transparent), RoundedCornerShape(12.dp)).clickable { selected = emoji }.padding(10.dp)) {
                            Text(emoji, fontSize = 28.sp)
                            Text(label, fontSize = 11.sp)
                        }
                    }
                }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.ui_c9414f7e6b)) }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { if (selected.isNotEmpty()) { onConfirm(selected, note); onDismiss() } }) { Text(stringResource(R.string.ui_8bf95ea372)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_77dfd2135f)) } })
}

@Composable
private fun NoteDialog(onDismiss: () -> Unit, onConfirm: (String, Boolean) -> Unit) {
    var content by remember { mutableStateOf("") }
    var isTodo by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.ui_dce84e3dee)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text(stringResource(R.string.ui_319782c1ba)) }, minLines = 2, maxLines = 4)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isTodo, onCheckedChange = { isTodo = it })
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.ui_477b3a53c6), fontSize = 14.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (content.isNotBlank()) { onConfirm(content, isTodo); onDismiss() } }) { Text(stringResource(R.string.ui_efc007a393)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_77dfd2135f)) } })
}

@Composable
private fun ExpenseDialog(onDismiss: () -> Unit, onConfirm: (Double, String, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val cats = listOf("vet" to stringResource(R.string.expense_vet), "food" to stringResource(R.string.expense_food), "grooming" to stringResource(R.string.expense_grooming), "medicine" to stringResource(R.string.expense_medicine), "toys" to stringResource(R.string.expense_toys), "other" to stringResource(R.string.expense_other))
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.ui_b6665200cd)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text(stringResource(R.string.ui_0dde6c5905)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                Text(stringResource(R.string.ui_a3c686e711), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    cats.forEach { (cat, label) ->
                        val isSel = category == cat
                        FilterChip(selected = isSel, onClick = { category = cat }, label = { Text(label, fontSize = 12.sp) })
                    }
                }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.ui_4e39567064)) }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { val a = CareFormats.parseNumber(amount); if (a != null && a > 0 && category.isNotBlank()) { onConfirm(a, category, note); onDismiss() } }) { Text(stringResource(R.string.ui_efc007a393)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_77dfd2135f)) } })
}

@Composable
private fun InputDialog(title: String, label: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text(label) }, minLines = 2) },
        confirmButton = { TextButton(onClick = { if (input.isNotBlank()) { onConfirm(input); onDismiss() } }) { Text(stringResource(R.string.ui_efc007a393)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_77dfd2135f)) } })
}
