package com.armsx2

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ultra-low-tier (Moto G24 class) device predicates. Every predicate takes
 * its inputs explicitly so these run as plain JVM tests; the defaults read the
 * live Build values on a phone.
 */
class DeviceTierUltraLowTest {
    @Test
    fun motoG24IsRecognised() {
        assertTrue(DeviceTier.isMotoG24("moto g24", "motorola"))
        assertTrue(DeviceTier.isMotoG24("moto g24 Power", "motorola"))
        assertTrue(DeviceTier.isMotoG24("XT2423-1", "motorola"))
        assertTrue(DeviceTier.isMotoG24("XT2423-2", "motorola"))
    }

    @Test
    fun otherPhonesAreNotMotoG24() {
        assertFalse(DeviceTier.isMotoG24("moto g84", "motorola"))
        assertFalse(DeviceTier.isMotoG24("moto g24", "samsung"))
        assertFalse(DeviceTier.isMotoG24(null, "motorola"))
        assertFalse(DeviceTier.isMotoG24("moto g24", null))
        assertFalse(DeviceTier.isMotoG24(null, null))
    }

    @Test
    fun helioG85ClassIsRecognised() {
        // MT6769 covers the G70/G80/G85 family, which shares the 2xA75 + Mali-G52 class.
        assertTrue(DeviceTier.isHelioG85Class("MT6769V", "mt6769"))
        assertTrue(DeviceTier.isHelioG85Class("MT6769Z", "mt6769"))
        assertTrue(DeviceTier.isHelioG85Class(null, "mt6769"))
        assertTrue(DeviceTier.isHelioG85Class("MT6769V", null))
        assertTrue(DeviceTier.isHelioG85Class("Helio G85", "mt6769"))
    }

    @Test
    fun otherSocsAreNotHelioG85Class() {
        assertFalse(DeviceTier.isHelioG85Class("MT6789", "mt6789"))
        assertFalse(DeviceTier.isHelioG85Class("SM8650", "pineapple"))
        assertFalse(DeviceTier.isHelioG85Class(null, null))
        assertFalse(DeviceTier.isHelioG85Class("unknown", "unknown"))
    }

    @Test
    fun smallBifrostMaliIsRecognised() {
        assertTrue(DeviceTier.isSmallBifrostMali("Mali-G52 MC2"))
        assertTrue(DeviceTier.isSmallBifrostMali("Mali-G51 MP4"))
        assertTrue(DeviceTier.isSmallBifrostMali("Mali-G31 MP2"))
    }

    @Test
    fun largerGpusAreNotSmallBifrost() {
        assertFalse(DeviceTier.isSmallBifrostMali("Mali-G76 MC4"))
        assertFalse(DeviceTier.isSmallBifrostMali("Mali-G57 MC2"))
        assertFalse(DeviceTier.isSmallBifrostMali("Mali-G615 MC6"))
        assertFalse(DeviceTier.isSmallBifrostMali("Mali-G310 MC1"))
        assertFalse(DeviceTier.isSmallBifrostMali("Adreno (TM) 740"))
        assertFalse(DeviceTier.isSmallBifrostMali(null))
        assertFalse(DeviceTier.isSmallBifrostMali(""))
    }

    @Test
    fun g51DoesNotMatchG510() {
        // The (?!\\d) guard: a longer model number must not match on its prefix.
        assertFalse(DeviceTier.isSmallBifrostMali("Mali-G510 MC6"))
        assertFalse(DeviceTier.isSmallBifrostMali("Mali-G310 MC1"))
    }

    @Test
    fun anySingleMatchMakesTheUltraLowTier() {
        // The Moto G24 itself.
        assertTrue(DeviceTier.isUltraLowEndDevice("moto g24", "motorola", "MT6769V", "mt6769", "Mali-G52 MC2"))
        // Same silicon under another brand still counts, by SoC or by GPU.
        assertTrue(DeviceTier.isUltraLowEndDevice("Redmi 12C", "Xiaomi", "MT6769H", "mt6769", "Mali-G52 MC2"))
        assertTrue(DeviceTier.isUltraLowEndDevice("unknown", "unknown", "MT6769V", "mt6769", null))
        assertTrue(DeviceTier.isUltraLowEndDevice("unknown", "unknown", null, null, "Mali-G52 MC2"))
    }

    @Test
    fun midRangeAndFlagshipPhonesAreNotUltraLow() {
        assertFalse(DeviceTier.isUltraLowEndDevice("Pixel 8", "Google", "Tensor G3", "shiba", "Mali-G715 MC7"))
        assertFalse(DeviceTier.isUltraLowEndDevice("moto g84", "motorola", "SM6375", "tofu", "Adreno (TM) 619"))
        assertFalse(DeviceTier.isUltraLowEndDevice(null, null, null, null, null))
    }
}
