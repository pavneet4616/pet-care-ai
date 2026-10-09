package com.petcare.companionlite.core

import android.content.Context
import com.petcare.companionlite.data.ai.AiApi
import com.petcare.companionlite.data.ai.AiContextBuilder
import com.petcare.companionlite.data.ai.AiRepository
import com.petcare.companionlite.data.local.PetCareDatabase
import com.petcare.companionlite.data.repository.PetRepository
import com.petcare.companionlite.data.settings.SettingsStore
import com.petcare.companionlite.export.PdfExportManager
import com.petcare.companionlite.monetization.AdsManager
import com.petcare.companionlite.monetization.BillingManager
import com.petcare.companionlite.notifications.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = PetCareDatabase.getDatabase(appContext)

    val settingsStore = SettingsStore(appContext)
    val diagnostics = Diagnostics(appContext,settingsStore)
    val reviewPrompter = ReviewPrompter(settingsStore)
    val notificationScheduler = NotificationScheduler(appContext)
    val photoStore = PetPhotoStore(appContext)
    val repository = PetRepository(
        context = appContext,
        petDao = database.petDao(),
        activityDao = database.activityDao(),
        reminderDao = database.reminderDao(),
        healthLogDao = database.healthLogDao(),
        waterLogDao = database.waterLogDao(),
        noteDao = database.noteDao(),
        expenseDao = database.expenseDao(),
        settingsStore = settingsStore,
        notificationScheduler = notificationScheduler,
        photoStore = photoStore,
    )
    val billingManager = BillingManager(appContext, settingsStore)
    val adsManager = AdsManager(appContext)
    val pdfExportManager = PdfExportManager(appContext)

    // AI Pet Companion stack (Worker-backed, local-first history).
    val aiApi = AiApi(context = appContext, integrity = com.petcare.companionlite.data.ai.IntegrityClient(appContext, settingsStore))
    val aiContextBuilder = AiContextBuilder(repository, settingsStore)
    val aiRepository = AiRepository(
        context = appContext,
        api = aiApi,
        sessions = database.chatSessionDao(),
        messages = database.chatMessageDao(),
        drafts = database.chatDraftDao(),
        settings = settingsStore,
        contextBuilder = aiContextBuilder,
    )

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { repository.migratePhotos() }
        billingManager.onOwnedPurchasesReady = {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                if (settingsStore.getAiPurchaseToken() == null) aiRepository.syncStoredPurchase()
            }
        }
        // Server-side Play verification for AI Pro subscriptions (weekly + monthly).
        billingManager.onAiWeeklyPurchased = { purchaseToken, subscriptionId ->
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                aiRepository.verifyAndApplyPurchase(purchaseToken, subscriptionId)
            }
        }
    }

    suspend fun clearAllLocalData() {
        aiRepository.invalidatePendingResults()
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val installation=settingsStore.getOrCreateInstallationId()
            repository.getAllReminders().forEach { notificationScheduler.cancelReminder(it.id) }
            androidx.work.WorkManager.getInstance(appContext).cancelAllWork().result.get()
            androidx.core.app.NotificationManagerCompat.from(appContext).cancelAll()
            database.clearAllTables()
            photoStore.clear()
            appContext.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            settingsStore.clearPersonalData()
            com.petcare.companionlite.notifications.DataDeletionWorker.enqueue(appContext,installation)
        }
    }
}
