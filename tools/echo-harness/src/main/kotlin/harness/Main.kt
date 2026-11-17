package harness

import dev.brahmkshatriya.echo.common.clients.QuickSearchClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.models.QuickSearchItem
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Track
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.URLClassLoader

fun main(args: Array<String>) = runBlocking {
    val jar = File(args.getOrElse(0) { "saavn.jar" })
    val query = args.getOrElse(1) { "kesariya" }
    println("HARNESS|jar exists=${jar.exists()} size=${jar.length()}")
    check(jar.exists()) { "extension jar missing" }

    val loader = URLClassLoader(
        arrayOf(jar.toURI().toURL()),
        QuickSearchClient::class.java.classLoader,
    )
    val clazz = loader.loadClass("dev.brahmkshatriya.echo.extension.SaavnExtension")
    println("HARNESS|class loaded ${clazz.name}")
    val instance = try {
        clazz.getConstructor().newInstance()
    } catch (e: Throwable) {
        var c: Throwable? = e
        while (c != null) {
            println("HARNESS|ctor failure ${c::class.java.name}: ${c.message}")
            c = c.cause
        }
        return@runBlocking
    }
    println("HARNESS|instantiated ${instance::class.java.name}")

    val quick = instance as? QuickSearchClient
    println("HARNESS|implementsQuickSearch=$quick")
    if (quick == null) return@runBlocking

    val items = quick.quickSearch(query)
    println("HARNESS|quickSearch('$query') -> ${items.size} items")

    val tracks = items.filterIsInstance<QuickSearchItem.Media>()
        .map { it.media }.filterIsInstance<Track>()
    println("HARNESS|tracks=${tracks.size}")
    tracks.take(3).forEach {
        println("HARNESS|track id=${it.id} title='${it.title}' artists=${it.artists.map { a -> a.name }} ms=${it.duration} streamables=${it.streamables.size}")
    }

    val tc = instance as? TrackClient
    println("HARNESS|implementsTrackClient=$tc")
    val first = tracks.firstOrNull() ?: return@runBlocking
    val streamable = first.streamables.firstOrNull()
    if (tc == null || streamable == null) {
        println("HARNESS|stream SKIPPED tc=$tc streamable=$streamable")
        return@runBlocking
    }
    val media = tc.loadStreamableMedia(streamable, false)
    val url = (media as? Streamable.Media.Server)
        ?.sources?.firstOrNull()
        ?.let { it as? Streamable.Source.Http }
        ?.request?.url
    println("HARNESS|stream quality=${streamable.quality} url=${url?.take(70)}")
}
