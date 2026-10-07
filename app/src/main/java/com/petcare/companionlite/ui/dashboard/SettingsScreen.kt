package com.petcare.companionlite.ui.dashboard

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petcare.companionlite.BuildConfig
import com.petcare.companionlite.PetCareApplication
import com.petcare.companionlite.R
import com.petcare.companionlite.core.AppLocale
import com.petcare.companionlite.data.model.AiLanguageMode
import com.petcare.companionlite.data.model.AiResponseStyle
import com.petcare.companionlite.data.model.SettingsState
import com.petcare.companionlite.data.model.ThemeMode
import com.petcare.companionlite.ui.ai.AiPaywallSheet
import com.petcare.companionlite.ui.theme.PremiumGold
import kotlinx.coroutines.launch

private enum class SettingsPage {
    HOME, LANGUAGE, APPEARANCE, NOTIFICATIONS, AI_PREFS, CHAT_HISTORY, PRIVACY, SAFETY, DATA, ABOUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onManagePets: () -> Unit,
    onOpenChat: (Long) -> Unit,
    weeklyPriceText: String,
    monthlyPriceText: String,
    petName: String,
    onBuyWeekly: () -> Unit,
    onBuyMonthly: () -> Unit,
    onRestorePurchases: () -> Unit,
    weeklyReady: Boolean = false,
    monthlyReady: Boolean = false,
    billingLoading: Boolean = false,
    billingMessage: String? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext as PetCareApplication
    val settings by app.container.repository.settings.collectAsStateWithLifecycle(initialValue = SettingsState())
    val pets by app.container.repository.observePets().collectAsStateWithLifecycle(initialValue = emptyList())
    val sessions by app.container.aiRepository.observeAllSessions()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val store = app.container.settingsStore

    var page by remember { mutableStateOf(SettingsPage.HOME) }
    var showPaywall by remember { mutableStateOf(false) }
    var confirmClearChats by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    val title = when (page) {
        SettingsPage.HOME -> stringResource(R.string.settings)
        SettingsPage.LANGUAGE -> stringResource(R.string.app_language)
        SettingsPage.APPEARANCE -> stringResource(R.string.appearance)
        SettingsPage.NOTIFICATIONS -> stringResource(R.string.notifications)
        SettingsPage.AI_PREFS -> stringResource(R.string.ai_preferences)
        SettingsPage.CHAT_HISTORY -> stringResource(R.string.chat_history)
        SettingsPage.PRIVACY -> stringResource(R.string.privacy)
        SettingsPage.SAFETY -> stringResource(R.string.ai_safety)
        SettingsPage.DATA -> stringResource(R.string.data_storage)
        SettingsPage.ABOUT -> stringResource(R.string.about_petcare)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (page == SettingsPage.HOME) onBack() else page = SettingsPage.HOME
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (page) {
                SettingsPage.HOME -> SettingsHome(
                    settings = settings,
                    petCount = pets.size,
                    isPro = settings.hasFullAccess,
                    onOpen = { page = it },
                    onManagePets = onManagePets,
                    onOpenPro = { showPaywall = true },
                    onRestore = onRestorePurchases,
                    onClearChats = { confirmClearChats = true },
                    onRate = { rateApp(context) },
                    onShare = { shareApp(context) },
                    onContact = { contactSupport(context) },
                    onPrivacyPolicy = { openUrl(context, context.getString(R.string.privacy_policy_url)) },
                    onTerms = { openUrl(context, context.getString(R.string.terms_of_service_url)) },
                )
                SettingsPage.LANGUAGE -> LanguagePage(selected = settings.appLanguageTag) { tag ->
                    scope.launch {
                        store.setAppLanguageTag(tag)
                        AppLocale.setAppLanguage(tag)
                    }
                }
                SettingsPage.APPEARANCE -> AppearancePage(selected = settings.themeMode) { mode ->
                    scope.launch {
                        store.setThemeMode(mode)
                        AppLocale.applyTheme(mode)
                    }
                }
                SettingsPage.NOTIFICATIONS -> NotificationsPage(settings, store, scope)
                SettingsPage.AI_PREFS -> AiPrefsPage(settings, store, scope)
                SettingsPage.CHAT_HISTORY -> ChatHistoryPage(
                    pets = pets.map { it.id to it.name },
                    sessions = sessions,
                    onOpen = onOpenChat,
                )
                SettingsPage.PRIVACY -> PrivacyPage()
                SettingsPage.SAFETY -> Text(
                    stringResource(R.string.ai_safety_body),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyLarge,
                )
                SettingsPage.DATA -> DataPage(onDeleteAll = { confirmDeleteAll = true })
                SettingsPage.ABOUT -> AboutPage()
            }
            if (page == SettingsPage.HOME) {
                Text(
                    stringResource(R.string.version_label, BuildConfig.VERSION_NAME),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                )
            }
        }
    }

    if (showPaywall) {
        AiPaywallSheet(
            quota = null,
            petName = petName,
            weeklyPriceText = weeklyPriceText,
            monthlyPriceText = monthlyPriceText,
            onDismiss = { showPaywall = false },
            onBuyWeekly = onBuyWeekly,
            onBuyMonthly = onBuyMonthly,
            onRestore = onRestorePurchases,
            weeklyReady = weeklyReady,
            monthlyReady = monthlyReady,
            billingLoading = billingLoading,
            billingMessage = billingMessage,
        )
    }

    if (confirmClearChats) {
        AlertDialog(
            onDismissRequest = { confirmClearChats = false },
            title = { Text(stringResource(R.string.clear_chats_title)) },
            text = { Text(stringResource(R.string.clear_chats_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearChats = false
                    scope.launch { app.container.aiRepository.clearAllChats() }
                }) { Text(stringResource(R.string.clear_chats_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearChats = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.delete_all_title)) },
            text = { Text(stringResource(R.string.delete_all_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    scope.launch {
                        app.container.aiRepository.clearAllChats()
                        app.container.clearAllLocalData()
                    }
                }) { Text(stringResource(R.string.delete_everything)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SettingsHome(
    settings: SettingsState,
    petCount: Int,
    isPro: Boolean,
    onOpen: (SettingsPage) -> Unit,
    onManagePets: () -> Unit,
    onOpenPro: () -> Unit,
    onRestore: () -> Unit,
    onClearChats: () -> Unit,
    onRate: () -> Unit,
    onShare: () -> Unit,
    onContact: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onTerms: () -> Unit,
) {
    SectionLabel(stringResource(R.string.section_general))
    SettingsCard {
        SettingsRow(Icons.Filled.Language, stringResource(R.string.app_language), AppLocale.displayName(settings.appLanguageTag)) { onOpen(SettingsPage.LANGUAGE) }
        SettingsRow(Icons.Filled.DarkMode, stringResource(R.string.appearance), themeLabel(settings.themeMode)) { onOpen(SettingsPage.APPEARANCE) }
        SettingsRow(Icons.Filled.Notifications, stringResource(R.string.notifications), stringResource(R.string.notifications_subtitle), showBottomDivider = false) { onOpen(SettingsPage.NOTIFICATIONS) }
    }

    SectionLabel(stringResource(R.string.section_ai))
    SettingsCard {
        SettingsRow(Icons.Filled.SmartToy, stringResource(R.string.ai_preferences), stringResource(R.string.ai_preferences_subtitle)) { onOpen(SettingsPage.AI_PREFS) }
        SettingsRow(Icons.Filled.History, stringResource(R.string.chat_history), stringResource(R.string.chat_history_subtitle)) { onOpen(SettingsPage.CHAT_HISTORY) }
        SettingsRow(Icons.Filled.Delete, stringResource(R.string.clear_ai_chats), showBottomDivider = false, onClick = onClearChats)
    }

    SectionLabel(stringResource(R.string.section_pet_data))
    SettingsCard {
        SettingsRow(Icons.Filled.Pets, stringResource(R.string.manage_pets), "$petCount", onClick = onManagePets)
        SettingsRow(Icons.Filled.Storage, stringResource(R.string.data_storage), showBottomDivider = false) { onOpen(SettingsPage.DATA) }
    }

    SectionLabel(stringResource(R.string.section_premium))
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isPro) MaterialTheme.colorScheme.primaryContainer
            else PremiumGold.copy(alpha = 0.16f),
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPro),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WorkspacePremium, null, tint = PremiumGold, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isPro) stringResource(R.string.petcare_pro) else stringResource(R.string.upgrade_to_pro),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (isPro) stringResource(R.string.pro_monthly_active) else stringResource(R.string.pro_card_subtitle),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
    SettingsCard {
        SettingsRow(Icons.Filled.Restore, stringResource(R.string.restore_purchases), showBottomDivider = false, onClick = onRestore)
    }

    SectionLabel(stringResource(R.string.section_privacy))
    SettingsCard {
        SettingsRow(Icons.Filled.Lock, stringResource(R.string.privacy)) { onOpen(SettingsPage.PRIVACY) }
        SettingsRow(Icons.Filled.Lock, stringResource(R.string.privacy_policy), onClick = onPrivacyPolicy)
        SettingsRow(Icons.Filled.Description, stringResource(R.string.terms_of_service), onClick = onTerms)
        SettingsRow(Icons.Filled.MedicalServices, stringResource(R.string.ai_safety), showBottomDivider = false) { onOpen(SettingsPage.SAFETY) }
    }

    SectionLabel(stringResource(R.string.section_help))
    SettingsCard {
        SettingsRow(Icons.Filled.Star, stringResource(R.string.rate_us), onClick = onRate)
        SettingsRow(Icons.Filled.Share, stringResource(R.string.share_app), onClick = onShare)
        SettingsRow(
            Icons.Filled.Email,
            stringResource(R.string.contact_support),
            stringResource(R.string.support_email),
            showBottomDivider = false,
            onClick = onContact,
        )
    }

    SectionLabel(stringResource(R.string.section_about))
    SettingsCard {
        SettingsRow(Icons.Filled.Info, stringResource(R.string.about_petcare), showBottomDivider = false) { onOpen(SettingsPage.ABOUT) }
    }
}

@Composable
private fun LanguagePage(selected: String?, onSelect: (String?) -> Unit) {
    SettingsCard {
        AppLocale.supported.forEachIndexed { index, lang ->
            SettingsRadioRow(
                title = lang.nativeName,
                subtitle = if (lang.tag != null && lang.englishName != lang.nativeName) lang.englishName else null,
                selected = selected == lang.tag,
                onClick = { onSelect(lang.tag) },
                showBottomDivider = index != AppLocale.supported.lastIndex,
            )
        }
    }
}

@Composable
private fun AppearancePage(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    SettingsCard {
        SettingsRadioRow(stringResource(R.string.system_default), selected = selected == ThemeMode.SYSTEM, onClick = { onSelect(ThemeMode.SYSTEM) })
        SettingsRadioRow(stringResource(R.string.theme_light), selected = selected == ThemeMode.LIGHT, onClick = { onSelect(ThemeMode.LIGHT) })
        SettingsRadioRow(stringResource(R.string.theme_dark), selected = selected == ThemeMode.DARK, onClick = { onSelect(ThemeMode.DARK) }, showBottomDivider = false)
    }
}

@Composable
private fun NotificationsPage(
    settings: SettingsState,
    store: com.petcare.companionlite.data.settings.SettingsStore,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    SectionLabel(stringResource(R.string.notif_care))
    SettingsCard {
        SettingsSwitchRow(stringResource(R.string.notif_feeding), settings.feedingNotifications) { scope.launch { store.setFeedingNotifications(it) } }
        SettingsSwitchRow(stringResource(R.string.notif_water), settings.waterNotifications) { scope.launch { store.setWaterNotifications(it) } }
        SettingsSwitchRow(stringResource(R.string.notif_medicine), settings.medicineNotifications) { scope.launch { store.setMedicineNotifications(it) } }
        SettingsSwitchRow(stringResource(R.string.notif_walk), settings.walkNotifications, showBottomDivider = false) { scope.launch { store.setWalkNotifications(it) } }
    }
    SectionLabel(stringResource(R.string.notif_companion))
    SettingsCard {
        SettingsSwitchRow(stringResource(R.string.notif_health), settings.healthFollowUps) { scope.launch { store.setHealthFollowUps(it) } }
        SettingsSwitchRow(stringResource(R.string.notif_weekly), settings.weeklySummary) { scope.launch { store.setWeeklySummary(it) } }
        SettingsSwitchRow(stringResource(R.string.notif_streak), settings.streakReminders, showBottomDivider = false) { scope.launch { store.setStreakReminders(it) } }
    }
    SectionLabel(stringResource(R.string.notif_ai))
    SettingsCard {
        SettingsSwitchRow(stringResource(R.string.notif_daily_tips), settings.dailyAiTips, showBottomDivider = false) { scope.launch { store.setDailyAiTips(it) } }
    }
}

@Composable
private fun AiPrefsPage(
    settings: SettingsState,
    store: com.petcare.companionlite.data.settings.SettingsStore,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    SectionLabel(stringResource(R.string.reply_language))
    SettingsCard {
        SettingsRadioRow(stringResource(R.string.automatic_recommended), selected = settings.aiLanguageMode == AiLanguageMode.AUTO, onClick = { scope.launch { store.setAiLanguageMode(AiLanguageMode.AUTO) } })
        SettingsRadioRow(stringResource(R.string.same_as_app), selected = settings.aiLanguageMode == AiLanguageMode.APP_LANGUAGE, onClick = { scope.launch { store.setAiLanguageMode(AiLanguageMode.APP_LANGUAGE) } }, showBottomDivider = false)
    }
    SectionLabel(stringResource(R.string.use_care_history))
    SettingsCard {
        SettingsSwitchRow(stringResource(R.string.use_care_history), settings.aiUseCareHistory, stringResource(R.string.use_care_history_body), showBottomDivider = false) {
            scope.launch { store.setAiUseCareHistory(it) }
        }
    }
    SettingsCard {
        SettingsSwitchRow(stringResource(R.string.personalize_profile), settings.aiUseProfile) { scope.launch { store.setAiUseProfile(it) } }
        SettingsSwitchRow(stringResource(R.string.personalize_activity), settings.aiUseActivity) { scope.launch { store.setAiUseActivity(it) } }
        SettingsSwitchRow(stringResource(R.string.personalize_health), settings.aiUseHealth) { scope.launch { store.setAiUseHealth(it) } }
        SettingsSwitchRow(stringResource(R.string.personalize_reminders), settings.aiUseReminders, showBottomDivider = false) { scope.launch { store.setAiUseReminders(it) } }
    }
    SectionLabel(stringResource(R.string.response_style))
    SettingsCard {
        SettingsRadioRow(stringResource(R.string.style_short), selected = settings.aiResponseStyle == AiResponseStyle.SHORT, onClick = { scope.launch { store.setAiResponseStyle(AiResponseStyle.SHORT) } })
        SettingsRadioRow(stringResource(R.string.style_balanced), selected = settings.aiResponseStyle == AiResponseStyle.BALANCED, onClick = { scope.launch { store.setAiResponseStyle(AiResponseStyle.BALANCED) } })
        SettingsRadioRow(stringResource(R.string.style_detailed), selected = settings.aiResponseStyle == AiResponseStyle.DETAILED, onClick = { scope.launch { store.setAiResponseStyle(AiResponseStyle.DETAILED) } }, showBottomDivider = false)
    }
}

@Composable
private fun ChatHistoryPage(
    pets: List<Pair<Long, String>>,
    sessions: List<com.petcare.companionlite.data.model.ChatSession>,
    onOpen: (Long) -> Unit,
) {
    if (pets.isEmpty()) {
        Text(stringResource(R.string.no_chats_yet), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        return
    }
    SettingsCard {
        pets.forEachIndexed { index, (id, name) ->
            val count = sessions.count { it.petId == id }
            SettingsRow(
                icon = Icons.Filled.Pets,
                title = name,
                subtitle = pluralStringResource(R.plurals.conversation_count, count, count),
                showBottomDivider = index != pets.lastIndex,
                onClick = { onOpen(id) },
            )
        }
    }
}

@Composable
private fun DataPage(onDeleteAll: () -> Unit) {
    Text(stringResource(R.string.pet_data_local), color = MaterialTheme.colorScheme.onBackground)
    Text(stringResource(R.string.ai_chats_local), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
    SettingsCard {
        SettingsRow(Icons.Filled.Delete, stringResource(R.string.delete_local_data), showBottomDivider = false, onClick = onDeleteAll)
    }
}

@Composable
private fun PrivacyPage() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.privacy_body),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyLarge,
        )
        SettingsCard {
            SettingsRow(
                Icons.Filled.Lock,
                stringResource(R.string.read_full_privacy_policy),
                showBottomDivider = false,
            ) {
                openUrl(context, context.getString(R.string.privacy_policy_url))
            }
        }
    }
}

@Composable
private fun AboutPage() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("🐾 PetCare", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(stringResource(R.string.about_tagline), color = MaterialTheme.colorScheme.onBackground)
        Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f))
        Text(stringResource(R.string.about_made), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f))
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            SettingsRow(
                Icons.Filled.Language,
                stringResource(R.string.visit_website),
                stringResource(R.string.website_url),
                showBottomDivider = false,
            ) {
                openUrl(context, context.getString(R.string.website_url))
            }
        }
        Text(stringResource(R.string.legal_placeholder), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) { Column { content() } }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    showBottomDivider: Boolean = true,
    onClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f), maxLines = 2)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
        }
        if (showBottomDivider) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    }
}

@Composable
private fun SettingsRadioRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
    showBottomDivider: Boolean = true,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
        if (showBottomDivider) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    showBottomDivider: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        if (showBottomDivider) HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.system_default)
    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
    ThemeMode.DARK -> stringResource(R.string.theme_dark)
}

private fun rateApp(context: Context) {
    val packageName = context.packageName
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
    }
}

private fun shareApp(context: Context) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_subject))
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text, context.packageName))
    }
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_app)))
}

private fun contactSupport(context: Context) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:${context.getString(R.string.support_email)}")
        putExtra(Intent.EXTRA_SUBJECT, "PetCare Support Request")
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.no_email_app), Toast.LENGTH_SHORT).show()
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, url, Toast.LENGTH_SHORT).show()
    }
}
