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
        SupportedLanguage("de", "Deutsch", "German"),
        SupportedLanguage("fr", "Fran\u00e7ais", "French"),
        SupportedLanguage("es", "Espa\u00f1ol", "Spanish"),
        SupportedLanguage("it", "Italiano", "Italian"),
        SupportedLanguage("pt-BR", "Portugu\u00eas (Brasil)", "Brazilian Portuguese"),
        SupportedLanguage("nl", "Nederlands", "Dutch"),
        SupportedLanguage("ja", "\u65e5\u672c\u8a9e", "Japanese"),
    )

    fun setAppLanguage(languageTag: String?) {
        val locales = if (languageTag.isNullOrBlank() || supported.none { it.tag == languageTag }) {
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
