package com.petcare.companionlite.core

import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Currency
import java.util.Locale

object CareFormats {
    const val POUNDS_PER_KG=2.2046226218
    fun parseNumber(value:String,locale:Locale=Locale.getDefault()):Double? {
        val trimmed=value.trim()
        if (trimmed.isEmpty()) return null
        val position=ParsePosition(0)
        val number=NumberFormat.getNumberInstance(locale).apply { isGroupingUsed=false }.parse(trimmed,position)?.toDouble()
        return number?.takeIf { position.index == trimmed.length && it.isFinite() }
    }
    fun toKg(value:Double,unit:String):Double=if (unit == "lb") value/POUNDS_PER_KG else value
    fun fromKg(value:Double,unit:String):Double=if (unit == "lb") value*POUNDS_PER_KG else value
    fun weight(value:Double,unit:String,locale:Locale=Locale.getDefault()):String =
        NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits=2 }.format(fromKg(value,unit))+" "+unit
    fun currency(value:Double,code:String,locale:Locale=Locale.getDefault()):String =
        NumberFormat.getCurrencyInstance(locale).apply { currency=Currency.getInstance(code) }.format(value)
}
