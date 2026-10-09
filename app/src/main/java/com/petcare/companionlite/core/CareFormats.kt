package com.petcare.companionlite.core

import android.content.Context
import java.text.NumberFormat
import java.text.ParsePosition
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CareFormats {
    const val POUNDS_PER_KG=2.2046226218

    /** Locale of the in-app language, falling back to the device locale. */
    fun appLocale(): Locale = AppLocale.currentTag()?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()

    fun parseNumber(value:String,locale:Locale=Locale.getDefault()):Double? {
        val trimmed=value.trim()
        if (trimmed.isEmpty()) return null
        val position=ParsePosition(0)
        val number=NumberFormat.getNumberInstance(locale).apply { isGroupingUsed=false }.parse(trimmed,position)?.toDouble()
        return number?.takeIf { position.index == trimmed.length && it.isFinite() }
    }
    fun toKg(value:Double,unit:String):Double=if (unit == "lb") value/POUNDS_PER_KG else value
    fun fromKg(value:Double,unit:String):Double=if (unit == "lb") value*POUNDS_PER_KG else value

    /** Weight with at most one decimal, e.g. "27.6 lb" or "12.5 kg". */
    fun weight(value:Double,unit:String,locale:Locale=appLocale()):String =
        NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits=1 }.format(fromKg(value,unit))+" "+unit

    fun currency(value:Double,code:String,locale:Locale=Locale.getDefault()):String =
        NumberFormat.getCurrencyInstance(locale).apply { currency=Currency.getInstance(code) }.format(value)

    /** Short clock time that follows the device locale and the 12/24-hour setting. */
    fun clockText(context: Context, epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        android.text.format.DateFormat.getTimeFormat(context)
            .apply { timeZone = TimeZone.getTimeZone(zoneId) }
            .format(Date(epochMillis))

    /** Context-free variant: locale-aware short time (the 12/24-hour override is not available here). */
    fun clockText(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault(), locale: Locale = appLocale()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId)
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
}
