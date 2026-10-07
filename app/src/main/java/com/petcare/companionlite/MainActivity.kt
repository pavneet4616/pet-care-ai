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

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        lifecycleScope.launch(Dispatchers.IO) {
            app.container.settingsStore.recordAppOpen(
                hour = java.time.LocalTime.now().hour,
                timestamp = System.currentTimeMillis(),
            )
        }

        val openReport = intent?.getBooleanExtra(
            WeeklyReportWorker.EXTRA_OPEN_WEEKLY_REPORT, false,
        ) ?: false
        val openAiChat = intent?.getBooleanExtra(
            AiFollowupWorker.EXTRA_OPEN_AI_CHAT, false,
        ) ?: false
        val openAiPetId = intent?.getLongExtra(AiFollowupWorker.EXTRA_AI_PET_ID, 0L) ?: 0L
        val openAiPrefill = intent?.getStringExtra(AiFollowupWorker.EXTRA_AI_PREFILL).orEmpty()

        setContent {
            val settings by app.container.repository.settings.collectAsStateWithLifecycle(
                initialValue = com.petcare.companionlite.data.model.SettingsState(),
            )
            PetCareTheme(themeMode = settings.themeMode) {
                PetCareApp(
                    openWeeklyReport = openReport,
                    openAiChatPetId = if (openAiChat) openAiPetId else 0L,
                    openAiChatPrefill = if (openAiChat) openAiPrefill else "",
                )
            }
        }
    }
}
