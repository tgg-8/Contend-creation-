package app.schnarchwaechter.core

import app.schnarchwaechter.core.dsp.percentile

/**
 * Estimates the background noise level (fan, heater, street) from a low percentile of recent frame
 * levels. First a calibration window at night start, then periodic updates. The floor may fall
 * immediately but only rise slowly, so persistent snoring does not raise the floor much.
 */
class NoiseFloorTracker(
    private val calibrationFrames: Int,
    private val updateFrames: Int,
    private val percentileValue: Double,
    private val maxRiseDb: Double,
) {
    private val buffer = DoubleArray(maxOf(calibrationFrames, updateFrames))
    private var count = 0

    /** Current floor in dBFS, NaN until calibration has finished. */
    var floorDb: Double = Double.NaN
        private set

    val isCalibrated: Boolean get() = !floorDb.isNaN()

    /** Adds one frame level; returns true if the floor estimate was (re)computed. */
    fun add(levelDb: Double): Boolean {
        buffer[count++] = levelDb
        val target = if (isCalibrated) updateFrames else calibrationFrames
        if (count < target) return false
        val p = percentile(buffer, count, percentileValue)
        floorDb = when {
            !isCalibrated -> p
            p <= floorDb -> p
            else -> minOf(p, floorDb + maxRiseDb)
        }
        count = 0
        return true
    }
}
