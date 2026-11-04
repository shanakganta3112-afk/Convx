/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.convx.music.R
import com.convx.music.constants.EnableHighRefreshRateKey
import com.convx.music.core.UpdateNotificationHelper
import com.convx.music.core.updater.checkForUpdate
import com.convx.music.core.updater.getAutoUpdateCheckSetting
import com.convx.music.core.updater.getLastNotifiedUpdateTag
import com.convx.music.core.updater.saveLastNotifiedUpdateTag
import com.convx.music.core.updater.getUpdateNotificationsSetting
import com.convx.music.core.updater.saveUpdateAvailableState
import com.convx.music.playback.PlayerConnection
import com.convx.music.utils.rememberPreference
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import timber.log.Timber

/** One-shot app effects: update check, refresh-rate request, and the owner-room toast. */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun AppStartupEffects(activity: android.app.Activity, playerConnection: PlayerConnection?) {
    // Default off: "off" now means "express no preference and let the system decide", which
    // on One UI hands control to Motion Smoothness / adaptive refresh — high while touching,
    // 60 when idle. Pinning the fastest mode made the display sit at 120Hz even on a static
    // list, which is pure waste on a mid-range panel that only offers 60 and 120.
    val enableHighRefreshRate by rememberPreference(EnableHighRefreshRateKey, defaultValue = false)
    val context = LocalContext.current

    // The startup update check is gone. Convx plays on-device audio only, so a network
    // request two seconds after every cold start was the one remaining thing the app did
    // online without being asked. Checking for updates is now a deliberate action from
    // Settings, not something that happens on launch.

    LaunchedEffect(enableHighRefreshRate) {
        val window = activity.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val layoutParams = window.attributes
            val modes = window.windowManager.defaultDisplay.supportedModes
            val currentMode = window.windowManager.defaultDisplay.mode
            if (enableHighRefreshRate) {
                // Zero means no preference. Choose the fastest mode at the current
                // resolution so refresh rate changes do not reflow the UI.
                val fastest = modes
                    .filter {
                        it.physicalWidth == currentMode.physicalWidth &&
                            it.physicalHeight == currentMode.physicalHeight
                    }
                    .maxByOrNull { it.refreshRate }
                layoutParams.preferredDisplayModeId = fastest?.modeId ?: 0
            } else {
                // Zero means "no preference". The platform's own frame-rate arbitration and
                // the vendor's adaptive refresh then decide, which is both smoother during
                // interaction and cheaper when the screen is still.
                layoutParams.preferredDisplayModeId = 0
            }
            window.attributes = layoutParams
        } else {
            val params = window.attributes
            params.preferredRefreshRate = if (enableHighRefreshRate) {
                // Zero means no preference, so request the fastest supported rate.
                window.windowManager.defaultDisplay.supportedRefreshRates
                    .maxOrNull()
                    ?: 0f
            } else {
                // No preference: let the system and the vendor's adaptive refresh decide.
                0f
            }
            window.attributes = params
        }
    }

    LaunchedEffect(playerConnection) {
        val connection = playerConnection ?: return@LaunchedEffect
        connection.playbackBlockedByRoom.collect { blocked ->
            if (!blocked) return@collect
            Toast.makeText(
                activity,
                activity.getString(R.string.listen_together_host_controls),
                Toast.LENGTH_SHORT,
            ).show()
            connection.playbackBlockedByRoom.value = false
        }
    }
}
