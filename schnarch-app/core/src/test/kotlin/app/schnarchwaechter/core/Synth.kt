package app.schnarchwaechter.core

import app.schnarchwaechter.core.dsp.Biquad
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Synthetic test signals. Work on DoubleArray (full scale = 1.0), convert with [toPcm]. */
object Synth {
    fun amp(db: Double) = 10.0.pow(db / 20.0)

    fun silence(sr: Int, sec: Double) = DoubleArray((sr * sec).toInt())

    /** White noise with the given RMS level in dBFS. */
    fun whiteNoise(sr: Int, sec: Double, db: Double, seed: Long = 1): DoubleArray {
        val r = Random(seed)
        val a = amp(db)
        return DoubleArray((sr * sec).toInt()) { r.nextGaussian() * a }
    }

    /** Fan-like rumble: low-passed noise (lots of energy inside the snore band!), normalised to [db]. */
    fun fanNoise(sr: Int, sec: Double, db: Double, seed: Long = 2): DoubleArray {
        val r = Random(seed)
        val lp = Biquad.lowPass(sr, 600.0, 0.7071)
        val raw = DoubleArray((sr * sec).toInt()) { lp.process(r.nextGaussian()) }
        return normalise(raw, db)
    }

    /**
     * Snore-like sound: harmonic buzz (f0 ~ 80-120 Hz, harmonics falling with 1/k) under a smooth
     * envelope, once per breath. [db] is the RMS level at the loudest point of each snore.
     */
    fun snoring(
        sr: Int,
        sec: Double,
        db: Double,
        intervalSec: Double = 4.0,
        burstSec: Double = 1.2,
        jitterSec: Double = 0.3,
        seed: Long = 3,
        skipBreaths: Set<Int> = emptySet(),
    ): DoubleArray {
        val out = DoubleArray((sr * sec).toInt())
        val r = Random(seed)
        var t = 0.5
        var breath = 0
        while (t + burstSec < sec) {
            if (breath !in skipBreaths) {
                val f0 = 80.0 + r.nextDouble() * 40.0
                addSnore(out, sr, (t * sr).toInt(), burstSec, f0, amp(db), r)
            }
            breath++
            t += intervalSec + (r.nextDouble() * 2 - 1) * jitterSec
        }
        return out
    }

    private fun addSnore(out: DoubleArray, sr: Int, start: Int, dur: Double, f0: Double, a: Double, r: Random) {
        val n = (dur * sr).toInt()
        val harmonics = (1..15).filter { it * f0 < sr * 0.45 }
        val norm = sqrt(harmonics.sumOf { 1.0 / (it * it) } / 2)
        val phases = harmonics.map { r.nextDouble() * 2 * PI }
        for (i in 0 until n) {
            val idx = start + i
            if (idx >= out.size) break
            val t = i.toDouble() / sr
            val env = sin(PI * i / n).pow(2)
            var s = 0.0
            for ((j, k) in harmonics.withIndex()) s += sin(2 * PI * k * f0 * t + phases[j]) / k
            out[idx] += a * env * s / norm
        }
    }

    /** Broadband bursts (door, cough, clapping) at given start times. */
    fun broadbandBursts(sr: Int, sec: Double, startsSec: List<Double>, durSec: Double, db: Double, seed: Long = 4): DoubleArray {
        val out = DoubleArray((sr * sec).toInt())
        val r = Random(seed)
        val a = amp(db)
        val n = (durSec * sr).toInt()
        for (s in startsSec) {
            val start = (s * sr).toInt()
            for (i in 0 until n) {
                if (start + i >= out.size) break
                val env = exp(-3.0 * i / n)
                out[start + i] += r.nextGaussian() * a * env
            }
        }
        return out
    }

    fun mix(vararg parts: DoubleArray): DoubleArray {
        val len = parts.maxOf { it.size }
        return DoubleArray(len) { i -> parts.sumOf { if (i < it.size) it[i] else 0.0 } }
    }

    fun concat(vararg parts: DoubleArray): DoubleArray {
        val out = DoubleArray(parts.sumOf { it.size })
        var p = 0
        for (part in parts) {
            part.copyInto(out, p)
            p += part.size
        }
        return out
    }

    fun toPcm(x: DoubleArray) = ShortArray(x.size) { (x[it] * 32767).roundToInt().coerceIn(-32768, 32767).toShort() }

    private fun normalise(x: DoubleArray, db: Double): DoubleArray {
        val rms = sqrt(x.sumOf { it * it } / x.size)
        val g = amp(db) / rms
        for (i in x.indices) x[i] *= g
        return x
    }
}

/** Runs a detector over a signal in 1 s blocks (like the app) and collects everything it reports. */
class DetectorRun(signal: DoubleArray, sr: Int, config: DetectionConfig = DetectionConfig()) : DetectorListener {
    val episodes = mutableListOf<SnoreEpisode>()
    val pauses = mutableListOf<BreathingPause>()
    val bursts = mutableListOf<SnoreBurst>()
    val frameLevels = mutableListOf<Double>()
    var floorDb = Double.NaN
    val detector = SnoreDetector(sr, config, 0, this)

    init {
        val pcm = Synth.toPcm(signal)
        var i = 0
        while (i < pcm.size) {
            val n = minOf(sr, pcm.size - i)
            detector.process(pcm, i, n)
            i += n
        }
        detector.finish()
    }

    override fun onFrame(timeMs: Long, levelDb: Double, floorDb: Double) {
        frameLevels += levelDb
    }

    override fun onCalibrated(floorDb: Double) {
        this.floorDb = floorDb
    }

    override fun onSnoreBurst(burst: SnoreBurst, episodeStartMs: Long) {
        bursts += burst
    }

    override fun onEpisodeEnded(episode: SnoreEpisode) {
        episodes += episode
    }

    override fun onPause(pause: BreathingPause) {
        pauses += pause
    }
}
