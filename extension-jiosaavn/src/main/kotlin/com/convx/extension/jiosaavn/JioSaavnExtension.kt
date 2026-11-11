package com.convx.extension.jiosaavn

import com.convx.extension.api.AlbumClient
import com.convx.extension.api.ArtistClient
import com.convx.extension.api.ExtensionAlbumPage
import com.convx.extension.api.ExtensionArtistPage
import com.convx.extension.api.ExtensionClient
import com.convx.extension.api.ExtensionException
import com.convx.extension.api.ExtensionMetadata
import com.convx.extension.api.ExtensionPlaylistPage
import com.convx.extension.api.ExtensionSearchResult
import com.convx.extension.api.ExtensionStream
import com.convx.extension.api.ExtensionType
import com.convx.extension.api.PlaylistClient
import com.convx.extension.api.SearchClient
import com.convx.extension.api.StreamClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject

/**
 * JioSaavn as a Convx extension.
 *
 * Search fans out to the four catalogue searches in parallel and tolerates any single
 * one failing — a dead album search should not empty the song results. Detail calls
 * and streaming fail loudly instead, because a caller asking for a specific album has
 * no sensible fallback.
 */
class JioSaavnExtension(
    private val api: SaavnApi = SaavnApi(),
    private val parser: SaavnParser = SaavnParser(),
) : ExtensionClient, SearchClient, AlbumClient, ArtistClient, PlaylistClient, StreamClient {

    val metadata: ExtensionMetadata = METADATA

    override suspend fun search(query: String, limit: Int): ExtensionSearchResult {
        if (query.isBlank()) return ExtensionSearchResult()
        val params = SaavnCalls.search(query, limit = limit)
        return coroutineScope {
            val tracks = async { safeCall(SaavnCalls.SEARCH_TRACKS, params) }
            val albums = async { safeCall(SaavnCalls.SEARCH_ALBUMS, params) }
            val artists = async { safeCall(SaavnCalls.SEARCH_ARTISTS, params) }
            val playlists = async { safeCall(SaavnCalls.SEARCH_PLAYLISTS, params) }

            ExtensionSearchResult(
                tracks = parser.tracks(tracks.await() ?: JsonObject(emptyMap()), "results"),
                albums = parser.albums(albums.await() ?: JsonObject(emptyMap()), "results"),
                artists = parser.artists(artists.await() ?: JsonObject(emptyMap()), "results"),
                playlists = parser.playlists(playlists.await() ?: JsonObject(emptyMap()), "results"),
            )
        }
    }

    override suspend fun album(albumId: String): ExtensionAlbumPage {
        val response = api.call(SaavnCalls.WEBAPI_GET, SaavnCalls.album(albumId))
        val album = parser.album(response, fallbackId = albumId)
            ?: throw ExtensionException("JioSaavn album $albumId not found")
        // Observed clients read the track list from "songs"; "list" is accepted too
        // because the playlist shape uses it.
        return ExtensionAlbumPage(album = album, tracks = parser.tracks(response, "songs", "list"))
    }

    override suspend fun artist(artistId: String): ExtensionArtistPage {
        val response = api.call(SaavnCalls.WEBAPI_GET, SaavnCalls.artist(artistId))
        val artist = parser.artist(response, fallbackId = artistId)
            ?: throw ExtensionException("JioSaavn artist $artistId not found")
        return ExtensionArtistPage(
            artist = artist,
            topTracks = parser.tracks(response, "topSongs", "songs"),
            albums = parser.albums(response, "topAlbums", "albums"),
        )
    }

    override suspend fun playlist(playlistId: String): ExtensionPlaylistPage {
        val response = api.call(SaavnCalls.WEBAPI_GET, SaavnCalls.playlist(playlistId))
        val playlist = parser.playlist(response, fallbackId = playlistId)
            ?: throw ExtensionException("JioSaavn playlist $playlistId not found")
        return ExtensionPlaylistPage(
            playlist = playlist,
            tracks = parser.tracks(response, "songs", "list"),
        )
    }

    override suspend fun stream(trackId: String): ExtensionStream? {
        val response = api.call(SaavnCalls.SONG_DETAILS, SaavnCalls.song(trackId))
        // The detail call returns either a `songs` array or an object keyed by song id.
        val song = parser.rawObjects(response, "songs").firstOrNull()
            ?: (response[trackId] as? JsonObject)
            ?: response.takeIf { parser.stringOf(it, "id") == trackId }
            ?: return null
        val moreInfo = parser.moreInfo(song) ?: return null
        val encrypted = parser.stringOf(moreInfo, "encrypted_media_url")?.takeIf { it.isNotBlank() }
            ?: return null
        val supports320 = parser.stringOf(moreInfo, "320kbps")
            ?.equals("true", ignoreCase = true)
            ?: false
        return SaavnStreamResolver.stream(encrypted, supports320)
    }

    private suspend fun safeCall(call: String, params: Map<String, String>): JsonObject? =
        runCatching { api.call(call, params) }.getOrNull()

    companion object {
        const val ID = "jiosaavn"

        val METADATA = ExtensionMetadata(
            id = ID,
            name = "JioSaavn",
            version = "1.0.0",
            description = "Search, browse and stream the JioSaavn catalogue.",
            author = "Convx",
            type = ExtensionType.MUSIC,
            repoUrl = "https://github.com/cosmictaserdev-creator/Convx",
        )
    }
}
