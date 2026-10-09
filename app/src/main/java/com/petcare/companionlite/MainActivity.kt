package com.petcare.companionlite

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.petcare.companionlite.notifications.AiFollowupWorker
import com.petcare.companionlite.notifications.WeeklyReportWorker
import com.petcare.companionlite.ui.PetCareApp
import com.petcare.companionlite.ui.theme.PetCareTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf

class MainActivity : AppCompatActivity() {
    private var deliveredIntent by mutableStateOf<android.content.Intent?>(null)
    private var deliverySequence by mutableIntStateOf(0)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliveredIntent = intent
        deliverySequence++
        selectNotifiedPet(intent)
    }

    private fun selectNotifiedPet(received: android.content.Intent?) {
        received?.getLongExtra("selected_pet_id", 0)?.takeIf { it > 0 }?.let { id ->
            received.removeExtra("selected_pet_id")
            lifecycleScope.launch { (application as PetCareApplication).container.settingsStore.selectPet(id) }
        }
    }
    override fun onResume() {
        super.onResume()
        com.petcare.companionlite.notifications.RescheduleRemindersWorker.enqueue(this)
        selectNotifiedPet(intent)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deliveredIntent = intent
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )

        val app = application as PetCareApplication
        app.container.adsManager.gatherConsent(this)
        lifecycleScope.launch(Dispatchers.IO) {
            app.container.settingsStore.recordAppOpen(
                hour = java.time.LocalTime.now().hour,
                timestamp = System.currentTimeMillis(),
            )
        }

        setContent {
        val received = deliveredIntent
        val openReport = received?.getBooleanExtra(
            WeeklyReportWorker.EXTRA_OPEN_WEEKLY_REPORT, false,
        ) ?: false
        val openAiChat = received?.getBooleanExtra(
            AiFollowupWorker.EXTRA_OPEN_AI_CHAT, false,
        ) ?: false
        val openAiPetId = received?.getLongExtra(AiFollowupWorker.EXTRA_AI_PET_ID, 0L) ?: 0L
        val openAiPrefill = received?.getStringExtra(AiFollowupWorker.EXTRA_AI_PREFILL).orEmpty()

            val settings by app.container.repository.settings.collectAsStateWithLifecycle(
                initialValue = com.petcare.companionlite.data.model.SettingsState(),
            )
            PetCareTheme(themeMode = settings.themeMode) {
                androidx.compose.runtime.CompositionLocalProvider(com.petcare.companionlite.ui.components.LocalWeightUnit provides settings.weightUnit) {
                PetCareApp(
                    deepLinkKey = deliverySequence,
                    openWeeklyReport = openReport,
                    openAiChatPetId = if (openAiChat) openAiPetId else 0L,
                    openAiChatPrefill = if (openAiChat) openAiPrefill else "",
                )
                }
            }
        }
    }
}
