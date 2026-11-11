/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.convx.music.R
import com.convx.music.constants.ExtensionEnabledKey
import com.convx.music.ui.component.IosSettingsGroup
import com.convx.music.ui.component.IosSettingsItem
import com.convx.music.ui.component.ModernSwitch
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Text
import com.convx.music.utils.rememberPreference
import com.convx.music.viewmodels.ExtensionsViewModel

/**
 * Lists the extensions compiled into this build and the single switch that turns them
 * on.
 *
 * One switch for all of them is deliberate: the first pass ships exactly one extension,
 * and the switch maps to one preference ([ExtensionEnabledKey]) that both the browse
 * path and the playback path read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsSettingsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ExtensionsViewModel = hiltViewModel(),
) {
    val (extensionsEnabled, onExtensionsEnabledChange) = rememberPreference(
        ExtensionEnabledKey,
        defaultValue = false,
    )

    SettingsPage(
        navController = navController,
        title = stringResource(R.string.extensions),
        pageColor = AppTheme.colorScheme.background,
        horizontalPadding = 24.dp,
    ) {
        Text(
            text = stringResource(R.string.extensions_description),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        )

        IosSettingsGroup(
            title = stringResource(R.string.extensions_installed),
            items = viewModel.installed.map { extension ->
                IosSettingsItem(
                    title = { Text(extension.name) },
                    description = {
                        Text(
                            stringResource(
                                R.string.extension_summary,
                                extension.metadata.description,
                                extension.metadata.version,
                                extension.metadata.author,
                            )
                        )
                    },
                    trailingContent = {
                        ModernSwitch(
                            checked = extensionsEnabled,
                            onCheckedChange = onExtensionsEnabledChange,
                        )
                    },
                    onClick = { onExtensionsEnabledChange(!extensionsEnabled) },
                )
            },
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.extensions_source_note),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}
