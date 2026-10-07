package com.petcare.companionlite.core

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.petcare.companionlite.data.model.ThemeMode

data class SupportedLanguage(
    val tag: String?,
    val nativeName: String,
    val englishName: String,
)

object AppLocale {
    val supported = listOf(
        SupportedLanguage(null, "System default", "System default"),
        SupportedLanguage("en", "English", "English"),
        SupportedLanguage("hi", "हिन्दी", "Hindi"),
        SupportedLanguage("pa", "ਪੰਜਾਬੀ", "Punjabi"),
        SupportedLanguage("es", "Español", "Spanish"),
        SupportedLanguage("pt", "Português", "Portuguese"),
        SupportedLanguage("fr", "Français", "French"),
        SupportedLanguage("de", "Deutsch", "German"),
        SupportedLanguage("ar", "العربية", "Arabic"),
        SupportedLanguage("bn", "বাংলা", "Bengali"),
        SupportedLanguage("ta", "தமிழ்", "Tamil"),
        SupportedLanguage("te", "తెలుగు", "Telugu"),
        SupportedLanguage("mr", "मराठी", "Marathi"),
    )

    fun setAppLanguage(languageTag: String?) {
        val locales = if (languageTag.isNullOrBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun applyTheme(mode: ThemeMode) {
        val night = when (mode) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(night)
    }

    fun currentTag(): String? {
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        return tags.takeIf { it.isNotBlank() }?.substringBefore(",")
    }

    fun displayName(tag: String?): String {
        return supported.firstOrNull { it.tag == tag }?.nativeName ?: "System default"
    }
}
