package com.convx.extension.api

/** A page of results. [continuation] is null when the source has nothing more. */
data class ExtensionPage<T>(
    val items: List<T>,
    val continuation: String? = null,
)

/** One search response, split by item kind because the host renders them in sections. */
data class ExtensionSearchResult(
    val tracks: List<ExtensionTrack> = emptyList(),
    val albums: List<ExtensionAlbum> = emptyList(),
    val artists: List<ExtensionArtist> = emptyList(),
    val playlists: List<ExtensionPlaylist> = emptyList(),
) {
    val isEmpty: Boolean
        get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
}

data class ExtensionAlbumPage(
    val album: ExtensionAlbum,
    val tracks: List<ExtensionTrack> = emptyList(),
)

data class ExtensionArtistPage(
    val artist: ExtensionArtist,
    val topTracks: List<ExtensionTrack> = emptyList(),
    val albums: List<ExtensionAlbum> = emptyList(),
)

data class ExtensionPlaylistPage(
    val playlist: ExtensionPlaylist,
    val tracks: List<ExtensionTrack> = emptyList(),
)

/**
 * A playable URL.
 *
 * @property bitrateKbps the bitrate the URL will really deliver, or null when the
 * source does not state it
 * @property mimeType container/stream type hint for the player
 */
data class ExtensionStream(
    val url: String,
    val bitrateKbps: Int? = null,
    val mimeType: String? = null,
)
