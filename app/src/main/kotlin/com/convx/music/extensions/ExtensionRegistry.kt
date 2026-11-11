/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.extensions

import android.content.Context
import com.convx.extension.api.AlbumClient
import com.convx.extension.api.ArtistClient
import com.convx.extension.api.ExtensionAlbumPage
import com.convx.extension.api.ExtensionArtistPage
import com.convx.extension.api.ExtensionPlaylistPage
import com.convx.extension.api.ExtensionSearchResult
import com.convx.extension.api.ExtensionStream
import com.convx.extension.api.MusicExtension
import com.convx.extension.api.PlaylistClient
import com.convx.extension.api.SearchClient
import com.convx.extension.api.StreamClient
import com.convx.extension.jiosaavn.JioSaavnExtension
import com.convx.music.constants.ExtensionEnabledKey
import com.convx.music.utils.dataStore
import com.convx.music.utils.get
import com.convx.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which extensions exist, whether the user has turned them on, and the typed accessors
 * the app uses.
 *
 * Every entry point returns an empty/null result when the extension is disabled, so a
 * disabled extension can never leak into search, browse or playback. Failures are
 * logged through [reportException] and degrade to the same empty result rather than
 * propagating into a screen.
 */
@Singleton
class ExtensionRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    jioSaavn: JioSaavnExtension,
) {

    val installed: List<MusicExtension> = listOf(
        MusicExtension(metadata = jioSaavn.metadata, client = jioSaavn),
    )

    /** Off until the user turns it on, matching how Saavn streaming is gated. */
    suspend fun isEnabled(): Boolean = context.dataStore.get(ExtensionEnabledKey, false)

    suspend fun search(query: String): ExtensionSearchResult {
        if (query.isBlank() || !isEnabled()) return ExtensionSearchResult()
        val client = clientOf<SearchClient>() ?: return ExtensionSearchResult()
        return withContext(Dispatchers.IO) {
            runCatching { client.search(query) }
                .onFailure { reportException(it) }
                .getOrElse { ExtensionSearchResult() }
        }
    }

    suspend fun album(id: String): ExtensionAlbumPage? = resolve(id) { raw ->
        clientOf<AlbumClient>()?.album(raw)
    }

    suspend fun artist(id: String): ExtensionArtistPage? = resolve(id) { raw ->
        clientOf<ArtistClient>()?.artist(raw)
    }

    suspend fun playlist(id: String): ExtensionPlaylistPage? = resolve(id) { raw ->
        clientOf<PlaylistClient>()?.playlist(raw)
    }

    suspend fun stream(mediaId: String): ExtensionStream? = resolve(mediaId) { raw ->
        clientOf<StreamClient>()?.stream(raw)
    }

    private suspend fun <T> resolve(mediaId: String, block: suspend (String) -> T?): T? {
        if (!isEnabled()) return null
        val raw = ExtensionIds.rawId(mediaId) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching { block(raw) }
                .onFailure { reportException(it) }
                .getOrNull()
        }
    }

    private inline fun <reified T> clientOf(): T? =
        installed.firstNotNullOfOrNull { it.client as? T }
}
