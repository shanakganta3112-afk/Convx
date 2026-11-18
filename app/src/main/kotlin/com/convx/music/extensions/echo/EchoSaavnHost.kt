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
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.QuickSearchItem
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
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

    /**
     * android.util.Log, not Timber: App.kt only plants a Timber tree when
     * BuildConfig.DEBUG, so Timber output is invisible in the release APK — which is the
     * only place this feature has ever failed.
     */
    private const val TAG = "EchoSaavn"

    /**
     * Explicit timeouts. The default client waits long enough that a blocked host looks
     * like a hung screen; these make a dead network a fast, logged failure instead.
     */
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Diagnostics that survive a release build.
     *
     * Neither of the obvious channels works here: App.kt only plants a Timber tree under
     * BuildConfig.DEBUG, and proguard-rules.pro strips android.util.Log with
     * -assumenosideeffects, so a release APK logs nothing at all. A file in filesDir is
     * not stripped, and println reaches logcat under System.out.
     *
     * Pull it with:
     *   adb shell run-as com.convx.music cat files/echo-saavn.log
     */
    private fun diag(context: Context, message: String) {
        println("EchoSaavn: $message")
        runCatching {
            val file = File(context.filesDir, "echo-saavn.log")
            if (file.length() > 64 * 1024) file.delete()
            file.appendText("${System.currentTimeMillis()}  $message\n")
        }
    }

    private val mutex = Mutex()
    private var client: ExtensionClient? = null
    private val tracks = ConcurrentHashMap<String, Track>()

    /** Downloads the extension once, then loads it. Null on any failure. */
    private suspend fun load(context: Context): ExtensionClient? {
        client?.let { return it }
        return mutex.withLock {
            client ?: withContext(Dispatchers.IO) {
                runCatching { open(context) }
                    .onFailure {
                        reportException(it)
                        diag(context, "load failed: ${it::class.java.name}: ${it.message}")
                    }
                    .getOrNull()
            }.also { client = it }
        }
    }

    private fun open(context: Context): ExtensionClient {
        val apk = File(context.filesDir, APK_NAME)
        if (!apk.exists() || apk.length() < 1024L) {
            diag(context, "downloading extension from $APK_URL")
            val bytes = http.newCall(Request.Builder().url(APK_URL).build())
                .execute().use { response ->
                    if (!response.isSuccessful) error("extension download HTTP ${response.code}")
                    response.body?.bytes() ?: error("extension download returned no body")
                }
            apk.writeBytes(bytes)
            diag(context, "extension downloaded, ${bytes.size} bytes")
        } else {
            diag(context, "extension already on disk, ${apk.length()} bytes")
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
        val client = loader.loadClass(className).getConstructor().newInstance() as ExtensionClient
        diag(context, "instantiated $className")
        return client
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
        val client = load(context) ?: return ExtensionSearchResult()

        // loadSearchFeed is the real search. quickSearch is only the suggestion list under
        // the search bar ("a lightweight search that returns a list of items" per Echo's
        // own KDoc), which is why using it produced "No results found" for real queries
        // while the probe's suggestion query appeared to work.
        val found = (client as? SearchFeedClient)?.let { feedSearch(context, it, query) }
            ?: (client as? QuickSearchClient)?.let { quickSearch(context, it, query) }
            ?: emptyList()

        found.forEach { tracks[it.id] = it }
        return ExtensionSearchResult(tracks = found.map { it.toConvxTrack() })
    }

    /** Walks the search feed: Feed -> default Tab -> PagedData -> Shelf.Lists.Tracks. */
    private suspend fun feedSearch(
        context: Context,
        client: SearchFeedClient,
        query: String,
    ): List<Track> = withContext(Dispatchers.IO) {
        runCatching {
            withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                val feed = client.loadSearchFeed(query)
                val page = feed.getPagedData(null)
                val shelves = page.pagedData.loadAll()
                val shelfKinds = shelves.joinToString(",") { it::class.java.simpleName }
                diag(context, "loadSearchFeed('$query') -> ${shelves.size} shelves [$shelfKinds]")
                // The feed mixes shelf kinds. Tracks shelves carry Track directly; Items
                // shelves carry EchoMediaItem, which is usually Track. Reading only one
                // of the two is how a working search renders as "No results found".
                val fromTracks = shelves.filterIsInstance<Shelf.Lists.Tracks>().flatMap { it.list }
                val fromItems = shelves.filterIsInstance<Shelf.Lists.Items>()
                    .flatMap { it.list }
                    .filterIsInstance<Track>()
                diag(context, "tracks: ${fromTracks.size} from Tracks shelves, ${fromItems.size} from Items shelves")
                (fromTracks + fromItems).distinctBy { it.id }
            }
        }.onFailure {
            reportException(it)
            diag(context, "loadSearchFeed failed: ${it::class.java.name}: ${it.message}")
        }.getOrNull().orEmpty()
    }

    /** Suggestion list. Kept as a fallback for an extension with no feed client. */
    private suspend fun quickSearch(
        context: Context,
        client: QuickSearchClient,
        query: String,
    ): List<Track> = withContext(Dispatchers.IO) {
        runCatching {
            withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                client.quickSearch(query)
                    .filterIsInstance<QuickSearchItem.Media>()
                    .map { it.media }
                    .filterIsInstance<Track>()
            }
        }.onFailure {
            reportException(it)
            diag(context, "quickSearch failed: ${it::class.java.name}: ${it.message}")
        }.getOrNull().orEmpty()
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

    private const val SEARCH_TIMEOUT_MS = 15_000L

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
