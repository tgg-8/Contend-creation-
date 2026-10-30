package app.schnarchwaechter.core

import app.schnarchwaechter.core.dsp.MIN_DB
import app.schnarchwaechter.core.dsp.dbToPower
import app.schnarchwaechter.core.dsp.powerToDb

/** Level summary of one wall-clock minute (dBFS). */
data class MinuteStats(
    val startMs: Long,
    val avgDb: Double,
    val maxDb: Double,
    val floorDb: Double,
)

/**
 * Collapses the 600 frame levels of a minute into one row, so the database only sees ~500 rows per
 * night instead of ~300 000 values.
 */
class MinuteAggregator {
    private var minuteStart = Long.MIN_VALUE
    private var powerSum = 0.0
    private var frames = 0
    private var maxDb = MIN_DB
    private var minDb = 0.0
    private var floorDb = Double.NaN

    /** Adds one frame; returns the finished previous minute when a new minute begins. */
    fun addFrame(timeMs: Long, levelDb: Double, floor: Double): MinuteStats? {
        val minute = timeMs - Math.floorMod(timeMs, 60_000L)
        var finished: MinuteStats? = null
        if (minute != minuteStart) {
            finished = flush()
            minuteStart = minute
        }
        powerSum += dbToPower(levelDb)
        frames++
        if (levelDb > maxDb) maxDb = levelDb
        if (frames == 1 || levelDb < minDb) minDb = levelDb
        if (!floor.isNaN()) floorDb = floor
        return finished
    }

    /** Finishes the current (possibly partial) minute. */
    fun flush(): MinuteStats? {
        if (frames == 0) return null
        val stats = MinuteStats(minuteStart, powerToDb(powerSum / frames), maxDb, if (floorDb.isNaN()) minDb else floorDb)
        powerSum = 0.0
        frames = 0
        maxDb = MIN_DB
        floorDb = Double.NaN
        return stats
    }
}
