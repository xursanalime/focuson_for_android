package uz.focuson.focus

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import uz.focuson.data.Sound

/**
 * Calm background sounds made in code (no audio files): rain, wind, sea and white noise.
 * Runs on its own thread until [stop].
 */
class AmbientPlayer(private val sound: Sound) {
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({ run() }, "ambient").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    private fun run() {
        val rate = 22050
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, rate))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        val gen = SoundGenerator(sound, rate)
        val buf = ShortArray(2048)
        try {
            track.play()
            while (running) {
                gen.fill(buf)
                track.write(buf, 0, buf.size)
            }
        } catch (_: Throwable) {
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }
}
