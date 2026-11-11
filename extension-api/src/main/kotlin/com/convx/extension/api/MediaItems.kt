package com.convx.extension.api

/** Anything an extension can return as a browsable item. */
sealed interface ExtensionMediaItem {
    val id: String
    val title: String
    val cover: String?
}

/** A lightweight artist reference, as it appears inside tracks, albums and playlists. */
data class ExtensionArtistRef(
    val id: String,
    val name: String,
)

data class ExtensionTrack(
    override val id: String,
    override val title: String,
    override val cover: String?,
    val artists: List<ExtensionArtistRef> = emptyList(),
    val albumId: String? = null,
    val albumTitle: String? = null,
    val durationMs: Long? = null,
    val isExplicit: Boolean = false,
    /** False when the source could not offer a playable rendition for this track. */
    val streamable: Boolean = true,
) : ExtensionMediaItem

data class ExtensionAlbum(
    override val id: String,
    override val title: String,
    override val cover: String?,
    val artists: List<ExtensionArtistRef> = emptyList(),
    val year: Int? = null,
    val trackCount: Int? = null,
) : ExtensionMediaItem

data class ExtensionArtist(
    override val id: String,
    override val title: String,
    override val cover: String?,
    val subtitle: String? = null,
) : ExtensionMediaItem

data class ExtensionPlaylist(
    override val id: String,
    override val title: String,
    override val cover: String?,
    val owner: String? = null,
    val trackCount: Int? = null,
) : ExtensionMediaItem
