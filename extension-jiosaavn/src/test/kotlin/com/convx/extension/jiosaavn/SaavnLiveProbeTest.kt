package com.convx.extension.jiosaavn

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Prints the real shape of api.php responses and then drives the extension end to end,
 * so the parsers can be checked against captured output instead of against another
 * client's source.
 *
 * Skipped unless SAAVN_LIVE=1, so the normal test task never touches the network.
 * Run it from the "CI & Debug Build" workflow with `probe_saavn` set to true.
 */
class SaavnLiveProbeTest {

    private val api = SaavnApi()
    private val parser = SaavnParser()

    private fun keys(obj: JsonObject?): String =
        obj?.keys?.sorted()?.joinToString(",") ?: "<none>"

    private fun id(obj: JsonObject, key: String): String? =
        (obj[key] as? JsonPrimitive)?.content

    private fun firstObject(response: JsonObject, key: String): JsonObject? =
        (response[key] as? JsonArray)?.firstOrNull() as? JsonObject

    private fun dump(label: String, response: JsonObject) {
        println("PROBE|$label|root=${keys(response)}")
        for (key in listOf("results", "songs", "list")) {
            val array = response[key] as? JsonArray ?: continue
            println("PROBE|$label|array '$key' size=${array.size}")
            val item = array.firstOrNull() as? JsonObject ?: continue
            println("PROBE|$label|item keys=${keys(item)}")
            (item["more_info"] as? JsonObject)?.let { println("PROBE|$label|more_info keys=${keys(it)}") }
        }
    }

    @Test fun `probe live api shapes and extension surface`() = runBlocking {
        assumeTrue("set SAAVN_LIVE=1 to run", System.getenv("SAAVN_LIVE") == "1")
        val query = System.getenv("SAAVN_QUERY") ?: "kesariya"

        val songs = api.call(SaavnCalls.SEARCH_TRACKS, SaavnCalls.search(query))
        val albums = api.call(SaavnCalls.SEARCH_ALBUMS, SaavnCalls.search(query))
        val artists = api.call(SaavnCalls.SEARCH_ARTISTS, SaavnCalls.search(query))
        val playlists = api.call(SaavnCalls.SEARCH_PLAYLISTS, SaavnCalls.search(query))
        dump("search.getResults", songs)
        dump("search.getAlbumResults", albums)
        dump("search.getArtistResults", artists)
        dump("search.getPlaylistResults", playlists)

        val songId = firstObject(songs, "results")?.let { id(it, "id") }
        val albumId = firstObject(albums, "results")?.let { id(it, "id") }
        val artistId = firstObject(artists, "results")?.let { id(it, "id") }
        val playlistId = firstObject(playlists, "results")?.let { id(it, "id") }
        println("PROBE|ids|song=$songId album=$albumId artist=$artistId playlist=$playlistId")

        if (songId != null) dump("song.getDetails", api.call(SaavnCalls.SONG_DETAILS, SaavnCalls.song(songId)))
        if (albumId != null) {
            val albumResponse = api.call(SaavnCalls.WEBAPI_GET, SaavnCalls.album(albumId))
            println("PROBE|album detail|raw id=${albumResponse["id"]} requested=$albumId")
            dump("webapi.get album", albumResponse)
        }
        if (artistId != null) dump("webapi.get artist", api.call(SaavnCalls.WEBAPI_GET, SaavnCalls.artist(artistId)))
        if (playlistId != null) dump("webapi.get playlist", api.call(SaavnCalls.WEBAPI_GET, SaavnCalls.playlist(playlistId)))

        // Now drive the extension the way the app does, so the mapping is verified too.
        val extension = JioSaavnExtension()

        val search = extension.search(query)
        println(
            "EXT|search|tracks=${search.tracks.size} albums=${search.albums.size} " +
                "artists=${search.artists.size} playlists=${search.playlists.size}"
        )
        search.tracks.firstOrNull()?.let {
            println(
                "EXT|track|title='${it.title}' artists=${it.artists.map { a -> a.name }} " +
                    "album='${it.albumTitle}' durationMs=${it.durationMs} streamable=${it.streamable}"
            )
        }
        search.albums.firstOrNull()?.let { println("EXT|album-item|'${it.title}' year=${it.year} count=${it.trackCount}") }
        search.artists.firstOrNull()?.let { println("EXT|artist-item|'${it.title}'") }
        search.playlists.firstOrNull()?.let { println("EXT|playlist-item|'${it.title}' count=${it.trackCount} owner=${it.owner}") }

        if (songId != null) {
            val stream = extension.stream(songId)
            println(
                "EXT|stream|" + (
                    stream?.let { "url=${it.url.take(48)}… kbps=${it.bitrateKbps} mime=${it.mimeType}" }
                        ?: "null (no stream resolved)"
                    )
            )
        }
        if (albumId != null) {
            val result = runCatching { extension.album(albumId) }
            result.onFailure { println("EXT|album|ERROR ${it::class.simpleName}: ${it.message}") }
            result.getOrNull()?.let {
                println("EXT|album|title='${it.album.title}' count=${it.album.trackCount} tracks=${it.tracks.size}")
            }
        }
        if (artistId != null) {
            val result = runCatching { extension.artist(artistId) }
            result.onFailure { println("EXT|artist|ERROR ${it::class.simpleName}: ${it.message}") }
            result.getOrNull()?.let {
                println("EXT|artist|title='${it.artist.title}' topTracks=${it.topTracks.size} albums=${it.albums.size}")
            }
        }
        if (playlistId != null) {
            val result = runCatching { extension.playlist(playlistId) }
            result.onFailure { println("EXT|playlist|ERROR ${it::class.simpleName}: ${it.message}") }
            result.getOrNull()?.let {
                println("EXT|playlist|title='${it.playlist.title}' count=${it.playlist.trackCount} tracks=${it.tracks.size}")
            }
        }
    }
}
