/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.component

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * How much visual work this device can afford.
 *
 * [LOW] does not mean "old": a 4 GB phone with a weak GPU janks on the glass pipeline
 * while reporting plenty of free RAM. Tiering on RAM alone misses exactly that class,
 * which is the class most of Convx's installs run on.
 */
enum class DeviceTier { LOW, MID, HIGH }

object DevicePerf {
    /** Total physical RAM in MiB. */
    fun totalRamMb(context: Context): Int {
        val am = context.getSystemService(ActivityManager::class.java) ?: return 2048
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return (info.totalMem / (1024L * 1024L)).toInt()
    }

    /**
     * Android's own low-RAM flag (Android Go, roughly 2 GB and under). Kept separate from
     * [tier] because it also drives decisions outside the glass pipeline.
     */
    fun isAndroidLowRam(context: Context): Boolean =
        context.getSystemService(ActivityManager::class.java)?.isLowRamDevice ?: false

    /**
     * Tiers are deliberately conservative. Glass is a per-frame RenderEffect on a
     * RenderNode; below 4 GB or with four cores or fewer it does not hold frame rate.
     */
    fun tier(context: Context): DeviceTier {
        if (isAndroidLowRam(context)) return DeviceTier.LOW
        val ramMb = totalRamMb(context)
        val cores = Runtime.getRuntime().availableProcessors()
        return when {
            ramMb < 4096 -> DeviceTier.LOW
            ramMb < 6144 || cores <= 4 -> DeviceTier.MID
            else -> DeviceTier.HIGH
        }
    }

    /** Inference threads for the ONNX sessions: never more than the UI can spare. */
    fun inferenceThreads(context: Context): Int = when (tier(context)) {
        DeviceTier.LOW -> 1
        DeviceTier.MID -> 2
        DeviceTier.HIGH -> (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)
    }
}

@Composable
fun rememberDeviceTier(): DeviceTier {
    val context = LocalContext.current
    return remember(context) { DevicePerf.tier(context) }
}
