package com.petcare.companionlite

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.ExistingWorkPolicy
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
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val settings = container.settingsStore.settings.first()
            AppLocale.applyTheme(settings.themeMode)
            AppLocale.setAppLanguage(settings.appLanguageTag)
        }
        // Off the main thread. The saved report hour is used, and KEEP leaves an existing schedule alone,
        // so a cold start no longer resets the user's weekly report time to the default.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                WeeklyReportWorker.enqueue(
                    context = this@PetCareApplication,
                    preferredHour = container.settingsStore.getPreferredReportHour(),
                    policy = ExistingWorkPolicy.KEEP,
                )
            }.onFailure { Log.e("PetCareApplication", "Weekly report scheduling failed", it) }
        }
    }
}
