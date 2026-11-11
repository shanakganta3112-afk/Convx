/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.extensions

import com.convx.extension.api.ExtensionAlbum
import com.convx.extension.api.ExtensionAlbumPage
import com.convx.extension.api.ExtensionArtist
import com.convx.extension.api.ExtensionArtistPage
import com.convx.extension.api.ExtensionArtistRef
import com.convx.extension.api.ExtensionPlaylist
import com.convx.extension.api.ExtensionPlaylistPage
import com.convx.extension.api.ExtensionTrack
import com.music.innertube.models.Album
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.pages.AlbumPage
import com.music.innertube.pages.ArtistPage
import com.music.innertube.pages.ArtistSection
import com.music.innertube.pages.PlaylistPage

/**
 * Translates extension models into the Innertube-shaped items Convx screens already
 * render.
 *
 * Mapping at this boundary is what keeps the UI untouched: a Saavn song becomes a
 * [SongItem], so the existing list rows, menus, queues and album/artist/playlist pages
 * work without knowing where the item came from. Ids are always namespaced by
 * [ExtensionIds].
 */
object ExtensionMediaMapper {

    fun song(track: ExtensionTrack): SongItem = SongItem(
        id = ExtensionIds.trackId(track.id),
        title = track.title,
        artists = track.artists.map(::artistRef),
        album = track.albumId?.let { rawId ->
            Album(
                name = track.albumTitle.orEmpty(),
                id = ExtensionIds.albumId(rawId),
            )
        },
        duration = track.durationMs?.let { (it / 1000L).toInt() },
        thumbnail = track.cover.orEmpty(),
        explicit = track.isExplicit,
    )

    fun albumItem(album: ExtensionAlbum): AlbumItem = AlbumItem(
        browseId = ExtensionIds.albumId(album.id),
        playlistId = ExtensionIds.albumId(album.id),
        title = album.title,
        artists = album.artists.map(::artistRef),
        year = album.year,
        thumbnail = album.cover.orEmpty(),
    )

    fun artistItem(artist: ExtensionArtist): ArtistItem = ArtistItem(
        id = ExtensionIds.artistId(artist.id),
        title = artist.title,
        thumbnail = artist.cover,
        shuffleEndpoint = null,
        radioEndpoint = null,
    )

    fun playlistItem(playlist: ExtensionPlaylist): PlaylistItem = PlaylistItem(
        id = ExtensionIds.playlistId(playlist.id),
        title = playlist.title,
        author = playlist.owner?.let { Artist(name = it, id = null) },
        songCountText = playlist.trackCount?.let { "$it songs" },
        thumbnail = playlist.cover,
        playEndpoint = null,
        shuffleEndpoint = null,
        radioEndpoint = null,
    )

    fun albumPage(page: ExtensionAlbumPage): AlbumPage = AlbumPage(
        album = albumItem(page.album),
        songs = page.tracks.map(::song),
        otherVersions = emptyList(),
    )

    fun artistPage(page: ExtensionArtistPage): ArtistPage = ArtistPage(
        artist = artistItem(page.artist),
        sections = listOfNotNull(
            page.topTracks
                .takeIf { it.isNotEmpty() }
                ?.let { tracks ->
                    ArtistSection(
                        title = "Songs",
                        items = tracks.map(::song),
                        moreEndpoint = null,
                    )
                },
            page.albums
                .takeIf { it.isNotEmpty() }
                ?.let { albums ->
                    ArtistSection(
                        title = "Albums",
                        items = albums.map(::albumItem),
                        moreEndpoint = null,
                    )
                },
        ),
        description = null,
        subscriberCountText = null,
    )

    fun playlistPage(page: ExtensionPlaylistPage): PlaylistPage = PlaylistPage(
        playlist = playlistItem(page.playlist),
        songs = page.tracks.map(::song),
        songsContinuation = null,
        continuation = null,
    )

    private fun artistRef(ref: ExtensionArtistRef): Artist = Artist(
        name = ref.name,
        id = ref.id.takeIf { it.isNotBlank() },
    )
}
