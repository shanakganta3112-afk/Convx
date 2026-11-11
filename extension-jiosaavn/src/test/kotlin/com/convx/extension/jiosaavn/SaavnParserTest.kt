package com.convx.extension.jiosaavn

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures below use the field names documented in the spec (§4.3), which were read
 * off a working client of the same private API. They are shape tests, not captured
 * live responses: this environment has no route to jiosaavn.com.
 */
class SaavnParserTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val parser = SaavnParser()

    private fun obj(text: String): JsonObject = json.parseToJsonElement(text) as JsonObject

    private val songJson = """
        {
          "id": "Gx7F2abc",
          "title": "Kesariya &amp; Reprise",
          "image": "https://c.saavncdn.com/1/cover-50x50.jpg",
          "subtitle": "Arijit Singh",
          "perma_url": "https://www.jiosaavn.com/song/kesariya/Gx7F2abc",
          "language": "hindi",
          "explicit_content": "0",
          "more_info": {
            "album_id": "1234567",
            "album": "Brahmastra",
            "album_url": "https://www.jiosaavn.com/album/brahmastra/abc",
            "encrypted_media_url": "ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsDyXtO9QwbkAG9KX+Pt+JAcK6pju4wPKmVjH0zsaPtTcGE=",
            "duration": "268",
            "release_date": "2022-09-09",
            "has_lyrics": "true",
            "320kbps": "true",
            "label": "Sony Music",
            "artistMap": {
              "primary_artists": [{"id": "459320", "name": "Arijit Singh"}],
              "featured_artists": [],
              "artists": [{"id": "459321", "name": "Pritam"}]
            }
          }
        }
    """.trimIndent()

    @Test fun `track parses title artists album duration and cover`() {
        val track = parser.track(obj(songJson))
        requireNotNull(track)
        assertEquals("Gx7F2abc", track.id)
        assertEquals("Kesariya & Reprise", track.title)
        assertEquals("https://c.saavncdn.com/1/cover-500x500.jpg", track.cover)
        assertEquals(listOf("Arijit Singh", "Pritam"), track.artists.map { it.name })
        assertEquals("1234567", track.albumId)
        assertEquals("Brahmastra", track.albumTitle)
        assertEquals(268_000L, track.durationMs)
        assertFalse(track.isExplicit)
        assertTrue(track.streamable)
    }

    @Test fun `track without id is dropped rather than thrown`() {
        assertNull(parser.track(obj("""{"title":"no id"}""")))
    }

    @Test fun `track without encrypted url is not streamable`() {
        val track = parser.track(
            obj("""{"id":"a","title":"t","more_info":{"album":"x"}}"""),
        )
        requireNotNull(track)
        assertFalse(track.streamable)
    }

    @Test fun `explicit content flag is read from explicit_content`() {
        val track = parser.track(obj("""{"id":"a","title":"t","explicit_content":"1"}"""))
        requireNotNull(track)
        assertTrue(track.isExplicit)
    }

    @Test fun `search response reads results array`() {
        val response = obj("""{"results":[$songJson]}""")
        val tracks = parser.tracks(response, "results")
        assertEquals(1, tracks.size)
        assertEquals("Gx7F2abc", tracks.first().id)
    }

    @Test fun `album detail reads songs container`() {
        val response = obj(
            """
            {
              "id": "1234567",
              "title": "Brahmastra",
              "image": "https://c.saavncdn.com/1/cover-150x150.jpg",
              "year": "2022",
              "song_count": "9",
              "more_info": {"artistMap": {"primary_artists": [{"id": "459321", "name": "Pritam"}]}},
              "songs": [$songJson]
            }
            """.trimIndent(),
        )
        val album = parser.album(response)
        requireNotNull(album)
        assertEquals("Brahmastra", album.title)
        assertEquals(2022, album.year)
        assertEquals(9, album.trackCount)
        assertEquals("Pritam", album.artists.single().name)
        assertEquals(1, parser.tracks(response, "songs", "list").size)
    }

    @Test fun `artist detail reads name and accepts artistId`() {
        val artist = parser.artist(
            obj("""{"artistId":"459320","name":"Arijit Singh","image":"https://c.saavncdn.com/a-50x50.jpg"}"""),
        )
        requireNotNull(artist)
        assertEquals("459320", artist.id)
        assertEquals("Arijit Singh", artist.title)
        assertEquals("https://c.saavncdn.com/a-500x500.jpg", artist.cover)
    }

    @Test fun `playlist detail builds owner from more_info`() {
        val response = obj(
            """
            {
              "id": "99887766",
              "title": "Hindi Hits",
              "image": "https://c.saavncdn.com/p-50x50.jpg",
              "song_count": "40",
              "more_info": {"firstname": "Jio", "lastname": "Saavn"},
              "list": [$songJson]
            }
            """.trimIndent(),
        )
        val playlist = parser.playlist(response)
        requireNotNull(playlist)
        assertEquals("Hindi Hits", playlist.title)
        assertEquals("Jio Saavn", playlist.owner)
        assertEquals(1, parser.tracks(response, "songs", "list").size)
    }

    @Test fun `missing arrays yield empty lists`() {
        assertTrue(parser.tracks(obj("""{"results":[]}"""), "results").isEmpty())
        assertTrue(parser.tracks(obj("""{}"""), "results").isEmpty())
    }
}
