package app.schnarchwaechter.core.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Small power spectrum helper (Hann window + in-place radix-2 FFT). Used only by stage 2, i.e. only
 * for sounds that stage 1 already found interesting, plus a few times per minute for the floor spectrum.
 */
class Spectrum(val size: Int) {
    init {
        require(size >= 16 && size and (size - 1) == 0) { "size must be a power of two" }
    }

    private val window = DoubleArray(size) { 0.5 - 0.5 * cos(2 * PI * it / (size - 1)) }
    private val re = DoubleArray(size)
    private val im = DoubleArray(size)
    private val cosTable = DoubleArray(size / 2) { cos(2 * PI * it / size) }
    private val sinTable = DoubleArray(size / 2) { sin(2 * PI * it / size) }

    /** Number of bins of the one-sided spectrum. */
    val bins: Int get() = size / 2

    /**
     * Adds the power spectrum of [size] samples starting at [start] (read through [sample]) to [acc].
     */
    fun accumulatePower(start: Int, sample: (Int) -> Double, acc: DoubleArray) {
        for (i in 0 until size) {
            re[i] = sample(start + i) * window[i]
            im[i] = 0.0
        }
        fft()
        for (k in 0 until bins) acc[k] += re[k] * re[k] + im[k] * im[k]
    }

    private fun fft() {
        val n = size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val half = len / 2
            val step = n / len
            var i = 0
            while (i < n) {
                for (k in 0 until half) {
                    val c = cosTable[k * step]
                    val s = -sinTable[k * step]
                    val ur = re[i + k]
                    val ui = im[i + k]
                    val vr = re[i + k + half] * c - im[i + k + half] * s
                    val vi = re[i + k + half] * s + im[i + k + half] * c
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + half] = ur - vr; im[i + k + half] = ui - vi
                }
                i += len
            }
            len = len shl 1
        }
    }

    companion object {
        /** FFT size giving roughly 30 Hz resolution at the given sample rate. */
        fun sizeFor(sampleRate: Int): Int {
            var n = 64
            while (n < sampleRate / 32) n = n shl 1
            return n
        }
    }
}
