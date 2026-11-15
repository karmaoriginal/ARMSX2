package com.armsx2

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Best-effort device-capability probe used to seed low-end-friendly defaults
 * and presets. Every reader is defensive: any probe that throws or returns a
 * junk value falls back to a conservative assumption (NOT low-end) so we never
 * hobble a capable device on a bad read.
 *
 * The tier is only ever a *hint* — it seeds first-run wizard defaults and the
 * one-tap "Low-End" preset. Users can always override in Settings, so a false
 * negative just means "no auto-help offered", never a hard cap.
 */
object DeviceTier {
    /** CPU core count reported by the runtime. Clamped to a sane 1..64 range;
     *  a bad read falls back to 8 (assume a capable device). */
    fun coreCount(): Int = try {
        Runtime.getRuntime().availableProcessors().coerceIn(1, 64)
    } catch (_: Throwable) {
        8
    }

    /** MTVU (multi-threaded VU1) is a win only when there are spare cores to run
     *  VU1 on its own thread. On 4-core / big.LITTLE budget SoCs it can cost more
     *  than it saves (EE<->VU1 sync + thread hop), so we gate the *default* on a
     *  6-core minimum. This does NOT change the persisted Settings default (which
     *  would bleed into existing users' saved configs) — it's applied only in
     *  first-run wizard defaults and the Low-End preset. */
    fun mtvuDefault(): Boolean = coreCount() >= 6

    /**
     * Whether the IOP thread setting should start on for this device: its performance tier is two or three cores and
     * the emulator core would run the thread (IopThread::DefaultOnForHost, asked through JNI so the rule is written once).
     * [mtvu] is the MTVU setting the caller has, which counts one more thread in the core's rule. False when the native
     * library did not load or the call fails: the setting stays off, as it was.
     *
     * This does NOT change the persisted Settings default (a save written by an older build holds the old value): it
     * seeds a new install ([com.armsx2.config.ConfigStore.seedFreshInstallDefaults]), moves an existing one once
     * ([com.armsx2.config.ConfigStore.migrateIopThreadNarrowTier]) and answers a Reset.
     */
    fun iopThreadDefault(mtvu: Boolean = true): Boolean = try {
        !kr.co.iefriends.pcsx2.NativeApp.hasNoNativeBinary && kr.co.iefriends.pcsx2.NativeApp.iopThreadDefaultOn(mtvu)
    } catch (_: Throwable) {
        false
    }

    /** Total physical RAM in bytes, or Long.MAX_VALUE if it can't be read (so a
     *  bad read never trips the low-RAM branch). */
    private fun totalMemBytes(context: Context): Long = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am != null) {
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            mi.totalMem
        } else Long.MAX_VALUE
    } catch (_: Throwable) {
        Long.MAX_VALUE
    }

    private fun isLowRam(context: Context): Boolean = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.isLowRamDevice ?: false
    } catch (_: Throwable) {
        false
    }

    /**
     * Heuristic: a device is "low-end" for PS2 emulation when ANY of:
     *   - the OS flags it as a low-RAM device (isLowRamDevice), OR
     *   - it has fewer than 6 CPU cores (no headroom for MTVU + GS thread), OR
     *   - it has under ~4 GB total RAM.
     *
     * Deliberately OR-ed and lenient: on any probe failure the individual
     * checks default to "not low-end", so we only flag a device we're fairly
     * sure is weak. Callers use this to *recommend* (never force) the Low-End
     * preset / Fast profile in the setup wizard.
     */
    fun isLowEnd(context: Context): Boolean = try {
        val lowRam = isLowRam(context)
        val fewCores = coreCount() < 6
        // ~4 GB with a little slack for reserved/kernel memory (report ~3.7 GB
        // on a nominal 4 GB device), so genuine 4 GB devices aren't flagged.
        val lowMem = totalMemBytes(context) < 3_600_000_000L
        lowRam || fewCores || lowMem
    } catch (_: Throwable) {
        false
    }

    /** Human-readable SoC/hardware id for logging/diagnostics. Uses
     *  Build.SOC_MODEL on API 31+, else Build.HARDWARE. */
    fun socModel(): String = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Build.SOC_MODEL ?: Build.HARDWARE ?: "unknown"
        else
            Build.HARDWARE ?: "unknown"
    } catch (_: Throwable) {
        "unknown"
    }

    // ---- Ultra-low tier (Moto G24 class) --------------------------------------
    // The phones at the very bottom of the playable range for PS2 emulation: a
    // ~2 GHz Cortex-A75 pair for the EE/VU recompilers and a small Bifrost Mali
    // (G31/G51/G52, usually 2 cores) for the GS. The reference device is the
    // Motorola Moto G24 (XT2423, Helio G85, Mali-G52 MC2, 4 GB RAM, Android 14),
    // but anything in the same silicon class gets the same treatment.
    //
    // Like [isLowEnd] this is only ever a *hint*: it seeds the Ultra preset on
    // a fresh install and recommends it in the Performance tab. Every function
    // takes its inputs as parameters (defaulting to the live Build values) so
    // JVM unit tests can drive them without the Android framework.

    /** Motorola Moto G24 / G24 Power (retail model "moto g24…", codename XT2423). */
    fun isMotoG24(
        model: String? = runCatching { Build.MODEL }.getOrNull(),
        manufacturer: String? = runCatching { Build.MANUFACTURER }.getOrNull(),
    ): Boolean = try {
        if (manufacturer?.contains("motorola", ignoreCase = true) != true) return false
        val m = model.orEmpty().lowercase()
        m.contains("g24") || m.contains("xt2423")
    } catch (_: Throwable) {
        false
    }

    /** MediaTek Helio G70/G80/G85 class (all MT6769 variants): 2x Cortex-A75 +
     *  6x Cortex-A55 with a Mali-G52. [soc] is [socModel], [hardware] is
     *  Build.HARDWARE (on MediaTek bootloaders usually the "mt6769" platform). */
    fun isHelioG85Class(
        soc: String? = runCatching { socModel() }.getOrNull(),
        hardware: String? = runCatching { Build.HARDWARE }.getOrNull(),
    ): Boolean = try {
        val s = "${soc.orEmpty()} ${hardware.orEmpty()}".lowercase()
        s.contains("mt6769") || (s.contains("helio") && (s.contains("g85") || s.contains("g80")))
    } catch (_: Throwable) {
        false
    }

    /** Small Bifrost Mali by GL_RENDERER ("Mali-G52 MC2", "Mali-G51 MP4", …):
     *  the GPUs whose fill-rate and tile budget put them in the ultra-low
     *  tier. Larger Bifrost (G71/G72/G76) and all Valhall and later are out. */
    fun isSmallBifrostMali(renderer: String?): Boolean = try {
        if (renderer?.contains("mali", ignoreCase = true) != true) return false
        Regex("""\bG(?:31|51|52)(?!\d)""", RegexOption.IGNORE_CASE).containsMatchIn(renderer)
    } catch (_: Throwable) {
        false
    }

    /** Pure predicate behind [isUltraLowEnd], for unit tests. */
    fun isUltraLowEndDevice(
        model: String?,
        manufacturer: String?,
        soc: String?,
        hardware: String?,
        renderer: String?,
    ): Boolean = isMotoG24(model, manufacturer) ||
        isHelioG85Class(soc, hardware) ||
        isSmallBifrostMali(renderer)

    /**
     * Whether this phone is in the ultra-low (Moto G24 class) tier for PS2
     * emulation and should start on the Ultra preset. Any single match —
     * Moto G24 model, Helio G85 SoC, or a small Bifrost Mali GPU — is enough;
     * on any probe failure the device is treated as NOT ultra-low.
     *
     * [context] is currently unused (the tier is model/SoC/GPU based, not
     * RAM based); it keeps the signature parallel to [isLowEnd] and reserves
     * the option to weigh RAM without touching every caller.
     */
    @Suppress("UNUSED_PARAMETER")
    fun isUltraLowEnd(context: Context, renderer: String? = GpuInfo.rendererName()): Boolean = try {
        isUltraLowEndDevice(
            runCatching { Build.MODEL }.getOrNull(),
            runCatching { Build.MANUFACTURER }.getOrNull(),
            runCatching { socModel() }.getOrNull(),
            runCatching { Build.HARDWARE }.getOrNull(),
            renderer,
        )
    } catch (_: Throwable) {
        false
    }
}
