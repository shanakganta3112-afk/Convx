/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.extensions

/**
 * Namespaced ids for items that come from an extension.
 *
 * Convx keys the Room library, the queue and the download cache by id, so an
 * extension id must never collide with a YouTube video id. Every id an extension
 * contributes is wrapped as `saavn:<kind>:<raw id>`; this object is the only place
 * that knows the format.
 */
object ExtensionIds {

    const val PREFIX = "saavn:"

    private const val KIND_TRACK = "track"
    private const val KIND_ALBUM = "album"
    private const val KIND_ARTIST = "artist"
    private const val KIND_PLAYLIST = "playlist"

    fun trackId(raw: String): String = "$PREFIX$KIND_TRACK:$raw"

    fun albumId(raw: String): String = "$PREFIX$KIND_ALBUM:$raw"

    fun artistId(raw: String): String = "$PREFIX$KIND_ARTIST:$raw"

    fun playlistId(raw: String): String = "$PREFIX$KIND_PLAYLIST:$raw"

    fun isExtensionId(id: String?): Boolean = id != null && id.startsWith(PREFIX)

    /** The source's own id, or null when [id] does not belong to an extension. */
    fun rawId(id: String): String? =
        if (isExtensionId(id)) id.substringAfterLast(':').takeIf { it.isNotBlank() } else null

    fun kind(id: String): String? =
        if (isExtensionId(id)) id.removePrefix(PREFIX).substringBefore(':').takeIf { it.isNotBlank() } else null
}
