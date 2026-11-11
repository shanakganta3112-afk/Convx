/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.di

import com.convx.extension.jiosaavn.JioSaavnExtension
import com.convx.music.extensions.ExtensionHost
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hands the extension graph the process-wide extension instances from [ExtensionHost],
 * so Hilt-injected code and the static playback path share one HTTP client per
 * extension.
 */
@Module
@InstallIn(SingletonComponent::class)
object ExtensionModule {

    @Provides
    @Singleton
    fun provideJioSaavnExtension(): JioSaavnExtension = ExtensionHost.jioSaavn
}
