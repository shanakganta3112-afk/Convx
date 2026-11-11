package com.convx.extension.jiosaavn

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Prints the real shape of api.php responses so the parsers can be checked against
 * captured output instead of against another client's source.
 *
 * Skipped unless SAAVN_LIVE=1, so the normal test task never touches the network.
 * Run it from the "JioSaavn API probe" workflow.
 */
class SaavnLiveProbeTest {

    private val api = SaavnApi()

    private fun keys(obj: JsonObject?): String =
        obj?.keys?.sorted()?.joinToString(",") ?: "<none>"

    private fun firstObject(obj: JsonObject, key: String): JsonObject? =
        (obj[key] as? JsonArray)?.firstOrNull() as? JsonObject

    private fun dump(label: String, response: JsonObject, listKeys: List<String> = listOf("results", "songs", "list")) {
        println("PROBE|$label|root=${keys(response)}")
        for (key in listKeys) {
            val array = response[key] as? JsonArray ?: continue
            println("PROBE|$label|array '$key' size=${array.size}")
            val item = array.firstOrNull() as? JsonObject ?: continue
            println("PROBE|$label|item keys=${keys(item)}")
            val moreInfo = item["more_info"] as? JsonObject
            if (moreInfo != null) println("PROBE|$label|more_info keys=${keys(moreInfo)}")
            for (nested in listOf("artistMap", "topSongs", "topAlbums", "songs", "list")) {
                val value = item[nested]
                if (value is JsonArray) println("PROBE|$label|nested '$nested' is array size=${value.size}")
                if (value is JsonObject) println("PROBE|$label|nested '$nested' is object keys=${keys(value)}")
            }
        }
    }

    @Test fun `probe live api shapes`() = runBlocking {
        assumeTrue("set SAAVN_LIVE=1 to run", System.getenv("SAAVN_LIVE") == "1")

        val query = System.getenv("SAAVN_QUERY") ?: "kesariya"

        val songs = api.call(SaavnCalls.SEARCH_TRACKS, SaavnCalls.search(query))
        dump("search.getResults", songs, listOf("results"))

        val albums = api.call(SaavnCalls.SEARCH_ALBUMS, SaavnCalls.search(query))
        dump("search.getAlbumResults", albums, listOf("results"))

        val artists = api.call(SaavnCalls.SEARCH_ARTISTS, SaavnCalls.search(query))
        dump("search.getArtistResults", artists, listOf("results"))

        val playlists = api.call(SaavnCalls.SEARCH_PLAYLISTS, SaavnCalls.search(query))
        dump("search.getPlaylistResults", playlists, listOf("results"))

        val songId = firstObject(songs, "results")?.let { (it["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content }
        val albumId = firstObject(albums, "results")?.let { (it["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content }
        val artistId = firstObject(artists, "results")?.let { (it["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content }
        val playlistId = firstObject(playlists, "results")?.let { (it["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content }

        if (songId != null) dump("webapi.get song", api.call(SaavnCalls.WEBAPI_GET, SaavnCalls.song(songId)))
        if (albumId != null) dump("webapi.get album", api.call(SaavnCalls.WEBAPI_GET, SaavnCalls.album(albumId)))
        if (artistId != null) dump("webapi.get artist", api.call(SaavnCalls.WEBAPI_GET, SaavnCalls.artist(artistId)))
        if (playlistId != null) dump("webapi.get playlist", api.call(SaavnCalls.WEBAPI_GET, SaavnCalls.playlist(playlistId)))
    }
}
