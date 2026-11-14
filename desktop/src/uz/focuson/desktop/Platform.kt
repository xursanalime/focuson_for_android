package uz.focuson.desktop

import uz.focuson.data.Sound
import uz.focuson.data.Store
import uz.focuson.focus.SoundGenerator
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/** Desktop notifications through notify-send (part of Ubuntu). */
object Notify {
    val iconFile: File get() = File(Store.dir, "icon.png")

    fun show(title: String, body: String, urgent: Boolean = false) {
        runCatching {
            ProcessBuilder(
                "notify-send", "-a", "Focus On", "-i", iconFile.absolutePath,
                "-u", if (urgent) "critical" else "normal", title, body,
            ).redirectErrorStream(true).start()
        }
    }
}

/** GNOME "Do Not Disturb": hides notification pop-ups while focusing, then puts it back. */
object Dnd {
    private const val SCHEMA = "org.gnome.desktop.notifications"
    private const val KEY = "show-banners"

    private fun run(vararg cmd: String): String? = runCatching {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        if (p.waitFor() == 0) out else null
    }.getOrNull()

    fun available(): Boolean = run("gsettings", "get", SCHEMA, KEY) != null

    fun begin() {
        if (!Store.settings.value.dnd) return
        val now = run("gsettings", "get", SCHEMA, KEY) ?: return
        if (Store.extraString("savedBanners").isEmpty()) Store.putExtra("savedBanners", now)
        run("gsettings", "set", SCHEMA, KEY, "false")
    }

    fun end() {
        val saved = Store.extraString("savedBanners")
        if (saved.isEmpty()) return
        run("gsettings", "set", SCHEMA, KEY, saved)
        Store.putExtra("savedBanners", "")
    }
}

/** Plays the calm background sound made by the shared SoundGenerator. */
class DesktopSound(private val sound: Sound) {
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (sound == Sound.NONE || running) return
        running = true
        thread = Thread({ play() }, "ambient").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    private fun play() {
        val rate = 22050
        val format = AudioFormat(rate.toFloat(), 16, 1, true, false)
        val line: SourceDataLine = runCatching { AudioSystem.getSourceDataLine(format) }.getOrNull() ?: return
        val gen = SoundGenerator(sound, rate)
        val samples = ShortArray(2048)
        val bytes = ByteArray(samples.size * 2)
        try {
            line.open(format, rate)
            line.start()
            while (running) {
                gen.fill(samples)
                for (i in samples.indices) {
                    val v = samples[i].toInt()
                    bytes[2 * i] = (v and 0xFF).toByte()
                    bytes[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
                }
                line.write(bytes, 0, bytes.size)
            }
        } catch (_: Throwable) {
        } finally {
            runCatching { line.stop(); line.close() }
        }
    }
}
