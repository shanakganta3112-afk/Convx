/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.extensions.echo

import android.content.Context
import android.content.pm.PackageManager
import com.convx.extension.api.ExtensionArtistRef
import com.convx.extension.api.ExtensionSearchResult
import com.convx.extension.api.ExtensionStream
import com.convx.extension.api.ExtensionTrack
import com.convx.music.utils.reportException
import dalvik.system.DexClassLoader
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.QuickSearchClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.QuickSearchItem
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs a real Echo extension inside Convx.
 *
 * The Echo contract is object-based: [TrackClient] is handed a `Track` and returns the
 * media for it, so an id alone is not enough to play something. This host keeps the
 * `Track` objects it saw during search in [tracks], which is what makes playback work for
 * items found in this session. An item that survives a restart has no cached object and
 * falls back to the compiled-in client, which reaches the same JioSaavn ids.
 *
 * The extension is an APK downloaded once and loaded with [DexClassLoader]; nothing about
 * it is compiled into the app. See docs/extensions/echo-saavn.md for the contract this
 * depends on.
 */
object EchoSaavnHost {

    private const val APK_NAME = "echo-saavn.eapk"
    private const val APK_URL =
        "https://github.com/Abhishek890/Echo-Saavn-Extension/releases/download/vf933435/yt-f933435.eapk"

    /** `meta-data` key Echo's own loader reads to find the entry class. */
    private const val META_CLASS = "class"

    private val mutex = Mutex()
    private var client: ExtensionClient? = null
    private val tracks = ConcurrentHashMap<String, Track>()

    /** Downloads the extension once, then loads it. Null on any failure. */
    private suspend fun load(context: Context): ExtensionClient? {
        client?.let { return it }
        return mutex.withLock {
            client ?: withContext(Dispatchers.IO) {
                runCatching { open(context) }
                    .onFailure { reportException(it) }
                    .getOrNull()
            }.also { client = it }
        }
    }

    private fun open(context: Context): ExtensionClient {
        val apk = File(context.filesDir, APK_NAME)
        if (!apk.exists() || apk.length() < 1024L) {
            val bytes = OkHttpClient().newCall(Request.Builder().url(APK_URL).build())
                .execute().use { response ->
                    if (!response.isSuccessful) error("extension download HTTP ${response.code}")
                    response.body?.bytes() ?: error("extension download returned no body")
                }
            apk.writeBytes(bytes)
        }
        @Suppress("DEPRECATION")
        val info = context.packageManager
            .getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_META_DATA)
            ?: error("downloaded file is not an APK")
        val className = info.applicationInfo?.metaData?.getString(META_CLASS)
            ?: error("extension manifest has no meta-data '$META_CLASS'")
        val loader = DexClassLoader(
            apk.absolutePath,
            context.codeCacheDir.absolutePath,
            null,
            context.classLoader,
        )
        // Echo does exactly this: no-arg constructor, cast to the client interface.
        return loader.loadClass(className).getConstructor().newInstance() as ExtensionClient
    }

    /** True once the extension has been downloaded and instantiated. */
    suspend fun isReady(context: Context): Boolean = load(context) != null

    /**
     * Search through the extension's [QuickSearchClient].
     *
     * Only tracks are mapped. Albums, artists and playlists come back from the same call
     * and are dropped on purpose: Convx's album/artist pages are built from ids that the
     * Echo contract does not hand back in a form they can use without the object cache.
     */
    suspend fun search(context: Context, query: String): ExtensionSearchResult {
        if (query.isBlank()) return ExtensionSearchResult()
        val quick = load(context) as? QuickSearchClient ?: return ExtensionSearchResult()
        val items = withContext(Dispatchers.IO) {
            runCatching { quick.quickSearch(query) }
                .onFailure { reportException(it) }
                .getOrNull()
        } ?: return ExtensionSearchResult()

        val found = items
            .filterIsInstance<QuickSearchItem.Media>()
            .map { it.media }
            .filterIsInstance<Track>()

        found.forEach { tracks[it.id] = it }
        return ExtensionSearchResult(tracks = found.map { it.toConvxTrack() })
    }

    /**
     * Stream resolution for a track this host handed out. Returns null when the track was
     * not seen in this session, which is the caller's cue to try the compiled-in client.
     */
    suspend fun stream(context: Context, rawId: String): ExtensionStream? {
        val track = tracks[rawId] ?: return null
        val trackClient = load(context) as? TrackClient ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                // quickSearch hands back unloaded tracks: no artists, no streamables.
                // loadTrack hydrates one, and only the hydrated track carries the
                // streamable that loadStreamableMedia needs. Skipping this step is why
                // playback silently fell through to the compiled-in client.
                val hydrated = trackClient.loadTrack(track, false)
                val streamable = hydrated.streamables.firstOrNull() ?: return@runCatching null
                val media = trackClient.loadStreamableMedia(streamable, false)
                val server = media as? Streamable.Media.Server ?: return@runCatching null
                val source = server.sources
                    .filterIsInstance<Streamable.Source.Http>()
                    .firstOrNull() ?: return@runCatching null
                ExtensionStream(
                    url = source.request.url,
                    bitrateKbps = streamable.quality,
                    mimeType = null,
                )
            }.onFailure { reportException(it) }.getOrNull()
        }
    }

    private fun Track.toConvxTrack(): ExtensionTrack = ExtensionTrack(
        id = id,
        title = title,
        cover = cover.url(),
        artists = artists.map { ExtensionArtistRef(it.id, it.name) },
        albumId = album?.id,
        albumTitle = album?.title,
        durationMs = duration,
        streamable = streamables.isNotEmpty(),
    )

    private fun ImageHolder?.url(): String? = when (this) {
        is ImageHolder.NetworkRequestImageHolder -> request.url
        is ImageHolder.ResourceUriImageHolder -> uri
        else -> null
    }
}
