package com.convx.extension.api

/** Raised when an extension cannot complete a request. */
class ExtensionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Marker for every extension entry point.
 *
 * Capability is expressed by implementing the interfaces below, not by flags, so the
 * host asks `client as? AlbumClient` and a source that has no albums simply does not
 * implement it.
 */
interface ExtensionClient

interface SearchClient {
    suspend fun search(query: String, limit: Int = 20): ExtensionSearchResult
}

interface AlbumClient {
    suspend fun album(albumId: String): ExtensionAlbumPage
}

interface ArtistClient {
    suspend fun artist(artistId: String): ExtensionArtistPage
}

interface PlaylistClient {
    suspend fun playlist(playlistId: String): ExtensionPlaylistPage
}

/** Resolves a playable URL for a track id owned by this extension. */
interface StreamClient {
    suspend fun stream(trackId: String): ExtensionStream?
}
