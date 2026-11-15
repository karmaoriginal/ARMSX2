package com.armsx2.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Ultra (Moto G24 class) and Shadow of the Colossus one-tap presets.
 * A wrong value here does not fail loudly: a phone just runs slower, or a
 * game a user paid nothing to misconfigure renders wrong.
 */
class UltraLowPresetTest {
    @Test
    fun ultraPresetPushesEveryCheapLever() {
        val ultra = Settings.ultraLowPreset(Settings(), mtvu = true)
        // CPU: skip 2, every recommended speedhack on, IOP thread on.
        assertEquals(0, ultra.cpu.eeCycleRate)
        assertEquals(2, ultra.cpu.eeCycleSkip)
        assertTrue(ultra.cpu.mtvu)
        assertTrue(ultra.cpu.iopThread)
        assertTrue(ultra.cpu.vu1Instant)
        assertTrue(ultra.cpu.vuFlagHack)
        assertTrue(ultra.cpu.fastCDVD)
        assertTrue(ultra.cpu.intcStat)
        assertTrue(ultra.cpu.waitLoop)
        // Render side: minimum blending, no HW mipmaps, partial preloading,
        // coalesced passes, no ROV, no GPU palette conversion.
        assertEquals(0, ultra.graphics.accurateBlendingUnit)
        assertFalse(ultra.graphics.hwMipmap)
        assertEquals(1, ultra.graphics.texturePreloading)
        assertTrue(ultra.display.coalesceRenderPasses)
        assertFalse(ultra.display.hwRov)
        assertFalse(ultra.hwFixes.gpuPaletteConversion)
        // Output: Vulkan pinned, native internal, 448p output surface.
        assertEquals("vulkan", ultra.output.renderer)
        assertEquals(1.0f, ultra.output.upscaleFloat, 0.0f)
        assertEquals(1, ultra.output.hwScaler)
    }

    @Test
    fun ultraPresetHonoursTheMtvuArgument() {
        assertTrue(Settings.ultraLowPreset(Settings(), mtvu = true).cpu.mtvu)
        assertFalse(Settings.ultraLowPreset(Settings(), mtvu = false).cpu.mtvu)
    }

    @Test
    fun ultraPresetKeepsUnrelatedSettings() {
        val base = Settings().copy(audio = Settings().audio.copy(audioVolume = 37))
        val ultra = Settings.ultraLowPreset(base, mtvu = true)
        assertEquals(37, ultra.audio.audioVolume)
        // …including the auto-tune choice (the Performance tab preserves it explicitly).
        assertEquals(base.emuCore.autoTune, ultra.emuCore.autoTune)
    }

    @Test
    fun ultraPresetIsIdempotent() {
        val once = Settings.ultraLowPreset(Settings(), mtvu = true)
        assertEquals(once, Settings.ultraLowPreset(once, mtvu = true))
    }

    @Test
    fun everyRetailSotcSerialIsRecognised() {
        for (serial in listOf(
            "SCUS-97472", "SCES-53326", "SCPS-15097", "SCPS-19320", "SCPS-19335",
            "SCKA-20061", "SCKA-20106",
        )) {
            assertTrue(serial, Settings.isShadowOfTheColossus(serial))
        }
    }

    @Test
    fun sotcDemosAreRecognised() {
        for (serial in listOf("SCED-53962", "SCED-53990", "SCUS-97505")) {
            assertTrue(serial, Settings.isShadowOfTheColossus(serial))
        }
    }

    @Test
    fun sotcMatcherIsLenientAboutCaseAndSurroundings() {
        assertTrue(Settings.isShadowOfTheColossus("  scus-97472 "))
        assertTrue(Settings.isShadowOfTheColossus("SCES-53326 (CRC)"))
        assertFalse(Settings.isShadowOfTheColossus("SCUS-97399"))
        assertFalse(Settings.isShadowOfTheColossus("SCUS-9747"))
        assertFalse(Settings.isShadowOfTheColossus(null))
        assertFalse(Settings.isShadowOfTheColossus(""))
    }

    @Test
    fun sotcPresetIsUltraPlusUnderclockAndSubNative() {
        val base = Settings()
        val sotc = Settings.shadowColossusPreset(base, mtvu = true)
        val ultra = Settings.ultraLowPreset(base, mtvu = true)
        // The two SOTC-specific moves…
        assertEquals(-1, sotc.cpu.eeCycleRate)
        assertEquals(0.75f, sotc.output.upscaleFloat, 0.0f)
        // …on top of Ultra, untouched otherwise.
        assertEquals(ultra.cpu.copy(eeCycleRate = -1), sotc.cpu)
        assertEquals(ultra.output.copy(upscaleFloat = 0.75f), sotc.output)
        assertEquals(ultra.graphics, sotc.graphics)
        assertEquals(ultra.display, sotc.display)
        assertEquals(ultra.hwFixes, sotc.hwFixes)
    }

    @Test
    fun sotcPresetKeepsTheEeSkipAtTwo() {
        assertEquals(2, Settings.shadowColossusPreset(Settings(), mtvu = true).cpu.eeCycleSkip)
    }
}
