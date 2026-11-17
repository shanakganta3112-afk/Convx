package probe.echo

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import dalvik.system.DexClassLoader
import dev.brahmkshatriya.echo.common.clients.QuickSearchClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.models.QuickSearchItem
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Track
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.URL

/** Loads the released Echo extension and reports what it returns, via logcat tag PROBE. */
class ProbeActivity : Activity() {

    private val url =
        "https://github.com/Abhishek890/Echo-Saavn-Extension/releases/download/vf933435/yt-f933435.eapk"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread {
            try {
                run()
            } catch (t: Throwable) {
                Log.e("PROBE", "FAILED ${t::class.java.name}: ${t.message}", t)
            } finally {
                Log.i("PROBE", "DONE")
            }
        }.start()
    }

    private fun run() = runBlocking {
        val apk = File(filesDir, "ext.eapk")
        if (!apk.exists() || apk.length() < 1024) {
            URL(url).openStream().use { input ->
                apk.outputStream().use { output -> input.copyTo(output) }
            }
        }
        Log.i("PROBE", "apk bytes=${apk.length()}")

        val info = packageManager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_META_DATA)
        val className = info?.applicationInfo?.metaData?.getString("class")
        Log.i("PROBE", "manifest class=$className")
        checkNotNull(className) { "no meta-data class" }

        val loader = DexClassLoader(apk.absolutePath, codeCacheDir.absolutePath, null, classLoader)
        val instance = loader.loadClass(className).getConstructor().newInstance()
        Log.i("PROBE", "instantiated=${instance.javaClass.name}")

        val quick = instance as? QuickSearchClient
        Log.i("PROBE", "implementsQuickSearch=${quick != null}")
        val items = quick?.quickSearch("kesariya") ?: emptyList()
        Log.i("PROBE", "quickSearch returned ${items.size}")

        val tracks = items.filterIsInstance<QuickSearchItem.Media>()
            .map { it.media }.filterIsInstance<Track>()
        Log.i("PROBE", "tracks=${tracks.size}")
        tracks.take(3).forEach {
            Log.i("PROBE", "track id=${it.id} title='${it.title}' artists=${it.artists.map { a -> a.name }} streamables=${it.streamables.size}")
        }

        val tc = instance as? TrackClient
        val first = tracks.firstOrNull()
        val streamable = first?.streamables?.firstOrNull()
        if (tc != null && streamable != null) {
            val media = tc.loadStreamableMedia(streamable, false)
            val link = (media as? Streamable.Media.Server)?.sources?.firstOrNull()
                ?.let { it as? Streamable.Source.Http }?.request?.url
            Log.i("PROBE", "stream quality=${streamable.quality} url=${link?.take(70)}")
        } else {
            Log.i("PROBE", "stream skipped tc=${tc != null} streamable=${streamable != null}")
        }
    }
}
