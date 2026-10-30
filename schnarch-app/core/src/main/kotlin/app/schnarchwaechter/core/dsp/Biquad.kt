package app.schnarchwaechter.core.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Second order IIR filter section (Direct Form I), coefficients after the RBJ audio EQ cookbook. */
class Biquad private constructor(
    private val b0: Double, private val b1: Double, private val b2: Double,
    private val a1: Double, private val a2: Double,
) {
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x
        y2 = y1; y1 = y
        return y
    }

    companion object {
        fun lowPass(sampleRate: Int, freq: Double, q: Double): Biquad {
            val w0 = 2 * PI * freq / sampleRate
            val alpha = sin(w0) / (2 * q)
            val c = cos(w0)
            val a0 = 1 + alpha
            return Biquad((1 - c) / 2 / a0, (1 - c) / a0, (1 - c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
        }

        fun highPass(sampleRate: Int, freq: Double, q: Double): Biquad {
            val w0 = 2 * PI * freq / sampleRate
            val alpha = sin(w0) / (2 * q)
            val c = cos(w0)
            val a0 = 1 + alpha
            return Biquad((1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
        }
    }
}

/**
 * Band pass made of a 2nd order Butterworth high pass and a 4th order Butterworth low pass.
 * Costs ~15 multiplications per sample, which is negligible at 8 kHz.
 */
class SnoreBandFilter(sampleRate: Int, lowHz: Double, highHz: Double) {
    private val hp = Biquad.highPass(sampleRate, lowHz, 0.7071)
    private val upper = minOf(highHz, sampleRate * 0.45)
    private val lp1 = Biquad.lowPass(sampleRate, upper, 0.5412)
    private val lp2 = Biquad.lowPass(sampleRate, upper, 1.3066)

    fun process(x: Double): Double = lp2.process(lp1.process(hp.process(x)))
}
