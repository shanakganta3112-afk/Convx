/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.convx.music.ui.ios.component.IosMenuItem
import com.convx.music.ui.ios.component.IosMenuSection
import com.convx.music.ui.screens.Screens
import com.convx.music.R

/**
 * The shortcuts behind a press-and-hold on a nav bar tab.
 *
 * iOS's own tab bar opens a context menu on hold with the destinations that tab leads to
 * but the bar has no room for. Only real routes here -- a menu entry that navigates
 * nowhere is worse than no menu, so a destination with nothing to offer returns an empty
 * list and simply has no menu (see FloatingNavBar's tabContextMenu).
 */
@Composable
internal fun navTabContextMenu(
    screen: Screens,
    navController: NavController,
): List<IosMenuSection> {
    @Composable
    fun item(@StringRes titleId: Int, route: String) = IosMenuItem(
        title = stringResource(titleId),
        onClick = { navController.navigate(route) },
    )

    val items = when (screen) {
        // Every entry that needed a server is gone: Listen Together, the Google account,
        // and Search's four discovery shortcuts (explore, charts, new releases, moods).
        // History and stats are read from the on-device database.
        Screens.Home -> listOf(
            item(R.string.history, "history"),
            item(R.string.stats, "stats"),
        )
        Screens.Library -> listOf(
            item(R.string.history, "history"),
            item(R.string.stats, "stats"),
        )
        Screens.Search -> emptyList()
        else -> emptyList()
    }
    return if (items.isEmpty()) emptyList() else listOf(IosMenuSection(items))
}
