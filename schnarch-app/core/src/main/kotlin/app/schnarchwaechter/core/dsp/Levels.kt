package app.schnarchwaechter.core.dsp

import kotlin.math.log10
import kotlin.math.pow

/** Lowest level we report; digital silence would otherwise be -infinity. */
const val MIN_DB = -120.0

/** Mean square of samples normalised to [-1, 1] -> dBFS (0 dBFS = full scale). */
fun powerToDb(meanSquare: Double): Double =
    if (meanSquare <= 1e-12) MIN_DB else (10.0 * log10(meanSquare)).coerceAtLeast(MIN_DB)

fun dbToPower(db: Double): Double = 10.0.pow(db / 10.0)

/** Energy average of dB values (the physically correct way to average levels). */
fun energyMeanDb(values: DoubleArray, count: Int = values.size): Double {
    if (count == 0) return MIN_DB
    var sum = 0.0
    for (i in 0 until count) sum += dbToPower(values[i])
    return powerToDb(sum / count)
}

/** Percentile (0..1) of the first [count] values; sorts a copy. */
fun percentile(values: DoubleArray, count: Int, p: Double): Double {
    if (count == 0) return MIN_DB
    val copy = values.copyOf(count)
    copy.sort()
    val idx = ((count - 1) * p.coerceIn(0.0, 1.0)).toInt()
    return copy[idx]
}
