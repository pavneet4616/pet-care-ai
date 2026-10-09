package com.petcare.companionlite.data

import com.petcare.companionlite.core.CareFormats
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class CareFormatsTest {
    @Test fun acceptsLocalDecimalSeparatorAndRejectsTrailingJunk() {
        assertEquals(12.5, CareFormats.parseNumber("12,5", Locale.GERMANY)!!, 0.0)
        assertEquals(12.5, CareFormats.parseNumber("12.5", Locale.US)!!, 0.0)
        assertNull(CareFormats.parseNumber("12kg", Locale.US))
        assertNull(CareFormats.parseNumber("12,5", Locale.US))
        assertNull(CareFormats.parseNumber("NaN", Locale.US))
        assertNull(CareFormats.parseNumber("", Locale.US))
    }
    @Test fun roundTripsPoundsToCanonicalKilograms() {
        assertEquals(10.0, CareFormats.toKg(22.046226218, "lb"), 0.000001)
        assertEquals(22.046226218, CareFormats.fromKg(10.0, "lb"), 0.000001)
        assertEquals(10.0, CareFormats.toKg(10.0, "kg"), 0.0)
    }
    @Test fun displaysChosenCurrencyAndWeightUsingLocalNumberFormat() {
        // One decimal at most: 12.5 kg = 27.557 lb.
        assertEquals("27,6 lb", CareFormats.weight(12.5, "lb", Locale.GERMANY))
        assertEquals("12,5 kg", CareFormats.weight(12.5, "kg", Locale.GERMANY))
        assertTrue(CareFormats.currency(12.5, "EUR", Locale.GERMANY).contains("12,50"))
        assertTrue(CareFormats.currency(12.5, "USD", Locale.US).contains("$12.50"))
    }
}
