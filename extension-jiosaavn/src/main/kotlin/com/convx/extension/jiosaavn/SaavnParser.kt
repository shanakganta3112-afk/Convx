package com.convx.extension.jiosaavn

import com.convx.extension.api.ExtensionAlbum
import com.convx.extension.api.ExtensionArtist
import com.convx.extension.api.ExtensionArtistRef
import com.convx.extension.api.ExtensionPlaylist
import com.convx.extension.api.ExtensionTrack
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Maps JioSaavn's JSON onto the extension models.
 *
 * The API is undocumented, so every read here is optional: a missing field falls back
 * to a default and an item without an id is dropped rather than thrown. Field names
 * are those documented in docs/extensions/jiosaavn-extension-spec.md §4.3.
 */
class SaavnParser {

    fun tracks(response: JsonObject, vararg keys: String): List<ExtensionTrack> =
        rawObjects(response, *keys).mapNotNull { track(it) }

    fun albums(response: JsonObject, vararg keys: String): List<ExtensionAlbum> =
        rawObjects(response, *keys).mapNotNull { album(it) }

    fun artists(response: JsonObject, vararg keys: String): List<ExtensionArtist> =
        rawObjects(response, *keys).mapNotNull { artist(it) }

    fun playlists(response: JsonObject, vararg keys: String): List<ExtensionPlaylist> =
        rawObjects(response, *keys).mapNotNull { playlist(it) }

    /** First key that holds a JSON array wins; keys are tried in the order given. */
    fun rawObjects(response: JsonObject, vararg keys: String): List<JsonObject> {
        for (key in keys) {
            val array = response[key] as? JsonArray ?: continue
            return array.mapNotNull { it as? JsonObject }
        }
        return emptyList()
    }

    fun track(obj: JsonObject): ExtensionTrack? {
        val id = stringOf(obj, "id")?.takeIf { it.isNotBlank() } ?: return null
        val moreInfo = moreInfo(obj)
        return ExtensionTrack(
            id = id,
            title = decodeHtml(stringOf(obj, "title").orEmpty()),
            cover = imageUrl(stringOf(obj, "image")),
            artists = artistsFrom(moreInfo?.get("artistMap") as? JsonObject),
            albumId = stringOf(moreInfo ?: JsonObject(emptyMap()), "album_id")?.takeIf { it.isNotBlank() },
            albumTitle = stringOf(moreInfo ?: JsonObject(emptyMap()), "album")?.let(::decodeHtml),
            durationMs = stringOf(moreInfo ?: JsonObject(emptyMap()), "duration")
                ?.toLongOrNull()
                ?.times(1000L),
            isExplicit = stringOf(obj, "explicit_content") == "1",
            streamable = stringOf(moreInfo ?: JsonObject(emptyMap()), "encrypted_media_url").isNullOrBlank().not(),
        )
    }

    fun album(obj: JsonObject): ExtensionAlbum? {
        val id = stringOf(obj, "id")?.takeIf { it.isNotBlank() } ?: return null
        val moreInfo = moreInfo(obj)
        return ExtensionAlbum(
            id = id,
            title = decodeHtml(stringOf(obj, "title").orEmpty()),
            cover = imageUrl(stringOf(obj, "image")),
            artists = artistsFrom(moreInfo?.get("artistMap") as? JsonObject),
            year = stringOf(obj, "year")?.toIntOrNull(),
            trackCount = stringOf(obj, "song_count")?.toIntOrNull(),
        )
    }

    fun artist(obj: JsonObject): ExtensionArtist? {
        val id = (stringOf(obj, "id") ?: stringOf(obj, "artistId"))?.takeIf { it.isNotBlank() }
            ?: return null
        return ExtensionArtist(
            id = id,
            title = decodeHtml(stringOf(obj, "name") ?: stringOf(obj, "title").orEmpty()),
            cover = imageUrl(stringOf(obj, "image") ?: stringOf(obj, "image_url")),
            subtitle = stringOf(obj, "subtitle") ?: stringOf(obj, "role"),
        )
    }

    fun playlist(obj: JsonObject): ExtensionPlaylist? {
        val id = stringOf(obj, "id")?.takeIf { it.isNotBlank() } ?: return null
        val moreInfo = moreInfo(obj)
        val owner = listOfNotNull(
            stringOf(moreInfo ?: JsonObject(emptyMap()), "firstname"),
            stringOf(moreInfo ?: JsonObject(emptyMap()), "lastname"),
        ).joinToString(" ").trim().ifBlank { null }
        return ExtensionPlaylist(
            id = id,
            title = decodeHtml(stringOf(obj, "title").orEmpty()),
            cover = imageUrl(stringOf(obj, "image")),
            owner = owner,
            trackCount = stringOf(obj, "song_count")?.toIntOrNull(),
        )
    }

    fun moreInfo(obj: JsonObject): JsonObject? = obj["more_info"] as? JsonObject

    fun stringOf(obj: JsonObject, key: String): String? =
        (obj[key] as? JsonPrimitive)?.contentOrNull

    /** Primary, featured and other artists, de-duplicated by id. */
    fun artistsFrom(artistMap: JsonObject?): List<ExtensionArtistRef> {
        if (artistMap == null) return emptyList()
        val arrays = listOf("primary_artists", "featured_artists", "artists")
        val out = mutableListOf<ExtensionArtistRef>()
        for (key in arrays) {
            val array = artistMap[key] as? JsonArray ?: continue
            for (element in array) {
                val item = element as? JsonObject ?: continue
                val id = stringOf(item, "id").orEmpty()
                val name = decodeHtml(stringOf(item, "name").orEmpty())
                if (name.isBlank()) continue
                out.add(ExtensionArtistRef(id = id, name = name))
            }
        }
        return out.distinctBy { if (it.id.isBlank()) it.name else it.id }
    }

    /** Saavn serves 50x50 by default; ask the CDN for a usable size. */
    fun imageUrl(url: String?): String? {
        val value = url?.takeIf { it.isNotBlank() } ?: return null
        return value
            .replace("50x50", "500x500")
            .replace("150x150", "500x500")
    }

    fun decodeHtml(text: String): String = text
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}
