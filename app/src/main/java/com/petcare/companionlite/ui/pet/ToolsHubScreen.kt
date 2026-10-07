package com.petcare.companionlite.ui.pet

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import com.petcare.companionlite.ui.components.AdBanner
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

    LaunchedEffect(Unit) { adsManager.loadRewarded(context) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }

    var showWater by remember { mutableStateOf(false) }
    var showMood by remember { mutableStateOf(false) }
    var showVomit by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(false) }
    var showExpense by remember { mutableStateOf(false) }

    // Track actions for smart interstitial (never for Pro / ad-free users)
    val activity = context as? Activity
    val appSettings by app.container.repository.settings.collectAsStateWithLifecycle(
        initialValue = com.petcare.companionlite.data.model.SettingsState(),
    )
    fun onLogged() {
        if (!appSettings.hasNoAds) {
            activity?.let { adsManager.trackActionAndMaybeShowInterstitial(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Care Tools", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── TODAY SUMMARY ──
            item { TodaySummaryCard(state) }

            // ── WATER SECTION ──
            item {
                ToolSection("Water Intake", WaterBlue, Icons.Filled.LocalDrink, "💧 ${state.todayWaterTotal}ml today", { showWater = true }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(100, 250, 500).forEach { ml ->
                            FilledTonalButton(onClick = { vm.logWater(petId, ml); onLogged() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                                Text("${ml}ml", fontSize = 13.sp)
                            }
                        }
                    }
                    state.waterLogs.take(3).forEach { log ->
                        LogEntry("${log.amountMl}ml", ToolsHubViewModel.formatTimestamp(log.timestamp), WaterBlue) { vm.deleteWaterLog(log.id) }
                    }
                }
            }

            // ── MOOD SECTION ──
            item {
                ToolSection("Mood Tracker", MoodAmber, Icons.Filled.Mood, "Latest: ${state.latestMood}", { showMood = true }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("😊" to "Happy", "😐" to "Neutral", "😢" to "Sad", "😴" to "Tired", "🤒" to "Sick").forEach { (emoji, label) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { vm.logMood(petId, emoji, label); onLogged() }.padding(8.dp)) {
                                Text(emoji, fontSize = 24.sp)
                                Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                            }
                        }
                    }
                    state.moodLogs.take(3).forEach { log ->
                        LogEntry(log.note, ToolsHubViewModel.formatTimestamp(log.timestamp), MoodAmber)
                    }
                }
            }

            // ── AD BANNER between sections ──
            item { AdBanner(enabled = !appSettings.hasNoAds) }

            // ── VOMIT SECTION ──
            item {
                ToolSection("Health Incidents", VomitOrange, Icons.Filled.Sick, "${state.vomitCountToday} today", { showVomit = true }) {
                    state.vomitLogs.take(3).forEach { log ->
                        LogEntry(log.note.ifBlank { "Incident recorded" }, ToolsHubViewModel.formatTimestamp(log.timestamp), VomitOrange)
                    }
                    if (state.vomitLogs.isEmpty()) EmptyHint("No incidents logged yet")
                    OutlinedButton(
                        onClick = {
                            val latest = state.vomitLogs.firstOrNull()?.note?.take(80).orEmpty()
                            val q = if (latest.isNotBlank()) "My pet had a health incident: $latest. What should I do?"
                            else "My pet had a health incident. What should I watch for?"
                            onAskAi(petId, q)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Ask AI what to do next", fontSize = 13.sp)
                    }
                }
            }

            // ── NOTES SECTION ──
            item {
                ToolSection("Notes & To-Dos", NotesTeal, Icons.AutoMirrored.Filled.Notes, "${state.notes.size} notes", { showNote = true }) {
                    state.notes.take(5).forEach { note ->
                        NoteEntry(note, onToggle = { vm.toggleNoteCompleted(note) }, onDelete = { vm.deleteNote(note.id) })
                    }
                    if (state.notes.isEmpty()) EmptyHint("Add notes about your pet")
                }
            }

            // ── EXPENSES SECTION ──
            item {
                ToolSection("Expenses", ExpensePurple, Icons.Filled.AttachMoney, "Total: ${"$"}${"%.2f".format(state.totalExpenses)}", { showExpense = true }) {
                    state.expenses.take(4).forEach { exp ->
                        LogEntry("${exp.category}: ${"$"}${"%.2f".format(exp.amount)}", ToolsHubViewModel.formatTimestamp(exp.timestamp), ExpensePurple) { vm.deleteExpense(exp.id) }
                    }
                    if (state.expenses.isEmpty()) EmptyHint("Track vet visits, food costs, etc.")
                }
            }

            // ── REWARDED AD CTA ──
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1F3D))
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("📊 Unlock Weekly Insights", fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Watch a short video to see trends", fontSize = 13.sp, color = Color.White.copy(0.7f))
                        }
                        FilledTonalButton(onClick = {
                            activity?.let { act -> adsManager.showRewarded(act) { vm.clearMessage() } }
                        }, shape = RoundedCornerShape(12.dp)) { Text("Watch") }
                    }
                }
            }

            // ── BOTTOM AD ──
            item { AdBanner(enabled = !appSettings.hasNoAds) }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    // ── DIALOGS ──
    if (showWater) WaterDialog({ showWater = false }) { ml -> vm.logWater(petId, ml); onLogged() }
    if (showMood) MoodDialog({ showMood = false }) { emoji, note -> vm.logMood(petId, emoji, note); onLogged() }
    if (showVomit) InputDialog("Log Health Incident", "Details (color, contents, time)", { showVomit = false }) { vm.logVomit(petId, it); onLogged() }
    if (showNote) NoteDialog({ showNote = false }) { content, isTodo -> vm.saveNote(petId, content, isTodo); onLogged() }
    if (showExpense) ExpenseDialog({ showExpense = false }) { amt, cat, note -> vm.saveExpense(petId, amt, cat, note); onLogged() }
}

// ── COMPOSABLES ──────────────────────────────────────────

@Composable
private fun TodaySummaryCard(state: ToolsHubUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(DeepSage, Color(0xFF2E5A47)))).padding(20.dp)) {
            Column {
                Text("Today's Overview", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SummaryChip("💧", "${state.todayWaterTotal}ml", Modifier.weight(1f))
                    SummaryChip("😊", state.latestMood.take(10), Modifier.weight(1f))
                    SummaryChip("💰", "${"$"}${"%.0f".format(state.todayExpenseTotal)}", Modifier.weight(1f))
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
                FilledIconButton(onClick = onAdd, modifier = Modifier.size(36.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent.copy(0.2f))) {
                    Icon(Icons.Filled.Add, null, tint = accent, modifier = Modifier.size(18.dp))
                }
            }
            content()
        }
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
                Icon(Icons.Filled.Close, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.3f))
            }
        }
    }
}

@Composable
private fun NoteEntry(note: com.petcare.companionlite.data.model.Note, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface.copy(0.5f)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (note.isTodo) {
            Checkbox(checked = note.isCompleted, onCheckedChange = { onToggle() }, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        } else {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(NotesTeal))
            Spacer(Modifier.width(10.dp))
        }
        Text(note.content, modifier = Modifier.weight(1f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textDecoration = if (note.isCompleted) TextDecoration.LineThrough else null, color = if (note.isCompleted) MaterialTheme.colorScheme.onSurface.copy(0.4f) else MaterialTheme.colorScheme.onSurface)
        Text(ToolsHubViewModel.formatTimestamp(note.timestamp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Filled.Close, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.3f))
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f), modifier = Modifier.padding(vertical = 4.dp))
}

// ── DIALOGS ──────────────────────────────────────────────

@Composable
private fun WaterDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var input by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("💧 Log Water Intake") },
        text = { OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("Amount (ml)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) },
        confirmButton = { TextButton(onClick = { input.toIntOrNull()?.let { if (it > 0) { onConfirm(it); onDismiss() } } }) { Text("Log") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun MoodDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var selected by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val moods = listOf("😊" to "Happy", "😐" to "Neutral", "😢" to "Sad", "😴" to "Tired", "🤒" to "Sick", "😡" to "Angry", "🥰" to "Loved", "😰" to "Anxious")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("How is your pet feeling?") },
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
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Add a note (optional)") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { if (selected.isNotEmpty()) { onConfirm(selected, note); onDismiss() } }) { Text("Log") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun NoteDialog(onDismiss: () -> Unit, onConfirm: (String, Boolean) -> Unit) {
    var content by remember { mutableStateOf("") }
    var isTodo by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("📝 Add Note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Note content") }, minLines = 2, maxLines = 4)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isTodo, onCheckedChange = { isTodo = it })
                    Spacer(Modifier.width(4.dp))
                    Text("Mark as to-do", fontSize = 14.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (content.isNotBlank()) { onConfirm(content, isTodo); onDismiss() } }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ExpenseDialog(onDismiss: () -> Unit, onConfirm: (Double, String, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val cats = listOf("🏥 Vet", "🍖 Food", "🧴 Grooming", "💊 Medicine", "🎾 Toys", "📦 Other")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("💰 Log Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount ($)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                Text("Category", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    cats.forEach { cat ->
                        val isSel = category == cat
                        FilterChip(selected = isSel, onClick = { category = cat }, label = { Text(cat, fontSize = 12.sp) })
                    }
                }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (optional)") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { val a = amount.toDoubleOrNull(); if (a != null && a > 0 && category.isNotBlank()) { onConfirm(a, category, note); onDismiss() } }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun InputDialog(title: String, label: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text(label) }, minLines = 2) },
        confirmButton = { TextButton(onClick = { if (input.isNotBlank()) { onConfirm(input); onDismiss() } }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
