package com.petcare.companionlite

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.petcare.companionlite.core.AppContainer
import com.petcare.companionlite.core.AppLocale
import com.petcare.companionlite.notifications.NotificationChannels
import com.petcare.companionlite.notifications.RescheduleRemindersWorker
import com.petcare.companionlite.notifications.WeeklyReportWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PetCareApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Default dark until saved preference loads.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        NotificationChannels.create(this)
        container = AppContainer(this)
        RescheduleRemindersWorker.enqueue(this)
        WeeklyReportWorker.enqueue(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val settings = container.settingsStore.settings.first()
            AppLocale.applyTheme(settings.themeMode)
            AppLocale.setAppLanguage(settings.appLanguageTag)
        }
    }
}
