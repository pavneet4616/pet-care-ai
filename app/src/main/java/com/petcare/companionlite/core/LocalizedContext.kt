package com.petcare.companionlite.core

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Apply the app language to messages generated outside an Activity. */
fun Context.inAppLanguage(tag: String? = AppLocale.currentTag()): Context {
    if (tag.isNullOrBlank()) return this
    val configuration = Configuration(resources.configuration).apply {
        setLocale(Locale.forLanguageTag(tag))
    }
    return createConfigurationContext(configuration)
}
