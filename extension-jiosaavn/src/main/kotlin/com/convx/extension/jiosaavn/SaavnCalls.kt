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

    fun album(token: String): Map<String, String> = mapOf(
        "type" to "album",
        "token" to token,
    )

    fun artist(token: String, songCount: Int = 20, albumCount: Int = 20): Map<String, String> = mapOf(
        "type" to "artist",
        "token" to token,
        "n_song" to songCount.toString(),
        "n_album" to albumCount.toString(),
        // The reference client sends a 0-based page here while search uses 1-based.
        // This pass does not paginate, so the difference cannot bite yet.
        "p" to "0",
        "sub_type" to "",
        "category" to "popularity",
        "sort_order" to "asc",
    )

    fun playlist(token: String, page: Int = 1, count: Int = 100): Map<String, String> = mapOf(
        "type" to "playlist",
        "token" to token,
        "p" to page.toString(),
        "n" to count.toString(),
    )
}
