package com.convx.extension.jiosaavn

/**
 * The `__call` names this extension uses, and their parameters.
 *
 * Every one of these was observed in a working client of the same private API
 * (see docs/extensions/jiosaavn-extension-spec.md §4.3); none are guessed. The API is
 * undocumented and can change without notice, so the parsers stay defensive.
 */
object SaavnCalls {
    const val SEARCH_TRACKS = "search.getResults"
    const val SEARCH_ALBUMS = "search.getAlbumResults"
    const val SEARCH_ARTISTS = "search.getArtistResults"
    const val SEARCH_PLAYLISTS = "search.getPlaylistResults"

    const val WEBAPI_GET = "webapi.get"
    const val SONG_DETAILS = "song.getDetails"

    // Captured 2026-11-17 from a CI probe (run 44041180486). `webapi.get
    // type=album|artist|playlist` answers with a shell: empty title, empty subtitle,
    // zero or absent track list. These three are the calls that actually carry the
    // detail page.
    const val ALBUM_DETAILS = "content.getAlbumDetails"
    const val ARTIST_PAGE = "artist.getArtistPageDetails"
    const val PLAYLIST_DETAILS = "playlist.getDetails"

    fun search(query: String, page: Int = 1, limit: Int = 20): Map<String, String> = mapOf(
        "q" to query,
        "p" to page.toString(),
        "n" to limit.toString(),
    )

    /**
     * Song detail is `song.getDetails` with `pids`, not `webapi.get type=song` — the
     * latter answers `{"results": []}` for a search-result id (captured in CI), while
     * Convx's own SaavnService uses this call and gets `more_info.encrypted_media_url`.
     */
    fun song(token: String): Map<String, String> = mapOf(
        "pids" to token,
        "ctx" to "android",
    )

    /** `content.getAlbumDetails` wants `albumid`; `type`/`token` belong to the shell call. */
    fun album(token: String): Map<String, String> = mapOf(
        "albumid" to token,
    )

    /**
     * `artist.getArtistPageDetails` with `artistId` alone returns name, bio, topSongs
     * and topAlbums. Adding n_song/n_album/page/category/sort_order makes it return
     * topSongs=-1 and topAlbums=-1 — captured in the same probe — so the extra
     * parameters are deliberately not sent.
     */
    fun artist(token: String): Map<String, String> = mapOf(
        "artistId" to token,
    )

    /** `playlist.getDetails` wants `listid`; it returns title, list_count and the list. */
    fun playlist(token: String): Map<String, String> = mapOf(
        "listid" to token,
    )
}
