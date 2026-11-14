package uz.focuson.focus

import uz.focuson.data.Sound
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** The actual sound recipes. Plain math, so it is testable. */
class SoundGenerator(private val sound: Sound, private val rate: Int) {
    private val rnd = Random(7)
    private var t = 0L
    // pink noise state (Paul Kellet)
    private var b0 = 0.0; private var b1 = 0.0; private var b2 = 0.0
    private var b3 = 0.0; private var b4 = 0.0; private var b5 = 0.0; private var b6 = 0.0
    private var brown = 0.0
    private var lp = 0.0
    private var drop = 0.0
    private var dropDecay = 0.0
    private var dropTone = 0.0
    private var dropPhase = 0.0

    private fun white() = rnd.nextDouble() * 2 - 1

    private fun pink(): Double {
        val w = white()
        b0 = 0.99886 * b0 + w * 0.0555179; b1 = 0.99332 * b1 + w * 0.0750759
        b2 = 0.96900 * b2 + w * 0.1538520; b3 = 0.86650 * b3 + w * 0.3104856
        b4 = 0.55000 * b4 + w * 0.5329522; b5 = -0.7616 * b5 - w * 0.0168980
        val p = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362
        b6 = w * 0.115926
        return p * 0.11
    }

    private fun brownNoise(): Double {
        brown = (brown + 0.02 * white()) / 1.02
        return brown * 3.5
    }

    fun next(): Double {
        val sec = t.toDouble() / rate
        t++
        val fadeIn = (sec / 2.0).coerceAtMost(1.0)
        val v = when (sound) {
            Sound.RAIN -> {
                // steady hiss + random little drops
                if (rnd.nextDouble() < 40.0 / rate) {
                    drop = 0.25 + rnd.nextDouble() * 0.35
                    dropDecay = exp(-1.0 / (rate * (0.004 + rnd.nextDouble() * 0.01)))
                    dropTone = 900 + rnd.nextDouble() * 2200
                }
                dropPhase += 2 * PI * dropTone / rate
                val d = drop * sin(dropPhase)
                drop *= dropDecay
                lp += 0.35 * (pink() - lp)
                lp * 0.9 + d * 0.25
            }
            Sound.WIND -> {
                val gust = 0.55 + 0.45 * sin(2 * PI * sec / 9.0) * sin(2 * PI * sec / 23.0 + 1.0)
                val cut = 0.02 + 0.05 * gust
                lp += cut * (white() - lp)
                lp * 5.0 * gust
            }
            Sound.OCEAN -> {
                val phase = (sec % 9.0) / 9.0
                val wave = sin(PI * phase).let { it * it }
                brownNoise() * (0.25 + 0.75 * wave) + pink() * 0.25 * wave
            }
            Sound.WHITE -> {
                lp += 0.5 * (white() - lp)
                lp * 0.6
            }
            Sound.NONE -> 0.0
        }
        return (v * 0.32 * fadeIn).coerceIn(-1.0, 1.0)
    }

    fun fill(buf: ShortArray) {
        for (i in buf.indices) buf[i] = (next() * Short.MAX_VALUE).toInt().toShort()
    }
}
