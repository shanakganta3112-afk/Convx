/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.liquidglass

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Everything the glass engine needs from its host app.
 *
 * The engine itself knows nothing about themes, navigation or persistence: the host
 * provides the three facts below and the engine stays reusable.
 */

/**
 * Whether content drawn on glass should be light or dark.
 *
 * The app this came from derives it from the sampled backdrop chrome, the ambient
 * background, and the surface luminance. Any host can do the same, or simply follow
 * `isSystemInDarkTheme()`.
 */
val LocalGlassDarkAppearance = staticCompositionLocalOf { false }

/** Convenience read for the engine's internal components. */
@androidx.compose.runtime.Composable
fun glassUsesDarkAppearance(): Boolean = LocalGlassDarkAppearance.current

/**
 * Hooks the host wires up once, so navigation-aware backdrops can behave correctly.
 *
 * [lastNavWasPop] is consulted while a backdrop freeze is being prepared: when the last
 * navigation was a pop (a screen closing), the engine skips freezing so the outgoing
 * screen does not leave a stale snapshot behind. Defaults to `false`, which is the safe
 * behaviour for hosts that do not use navigation transitions at all.
 */
object GlassHost {
    var lastNavWasPop: () -> Boolean = { false }
}
