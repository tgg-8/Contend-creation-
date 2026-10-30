package app.schnarchwaechter.core

import app.schnarchwaechter.core.dsp.MIN_DB
import app.schnarchwaechter.core.dsp.dbToPower
import app.schnarchwaechter.core.dsp.powerToDb
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** The key numbers of one night (what the morning summary and the history show). */
data class NightSummary(
    val startMs: Long,
    val endMs: Long,
    val episodeCount: Int,
    val snoreMs: Long,
    val avgDb: Double,
    val maxDb: Double,
    val avgAboveFloorDb: Double,
    val maxAboveFloorDb: Double,
    val pauseCount: Int,
    val clipCount: Int = 0,
) {
    val durationMs: Long get() = endMs - startMs
    val snoreShare: Double get() = if (durationMs > 0) snoreMs.toDouble() / durationMs else 0.0
}

data class LoudMoment(val timeMs: Long, val maxDb: Double, val aboveFloorDb: Double)

object NightStatsCalculator {
    fun summarize(
        startMs: Long,
        endMs: Long,
        minutes: List<MinuteStats>,
        episodes: List<SnoreEpisode>,
        pauseCount: Int,
        clipCount: Int = 0,
    ): NightSummary {
        var power = 0.0
        var floorPower = 0.0
        var maxDb = MIN_DB
        var maxAbove = 0.0
        for (m in minutes) {
            power += dbToPower(m.avgDb)
            floorPower += dbToPower(m.floorDb)
            maxDb = maxOf(maxDb, m.maxDb)
            maxAbove = maxOf(maxAbove, m.maxDb - m.floorDb)
        }
        val avgDb = if (minutes.isEmpty()) MIN_DB else powerToDb(power / minutes.size)
        val avgFloor = if (minutes.isEmpty()) MIN_DB else powerToDb(floorPower / minutes.size)
        return NightSummary(
            startMs = startMs,
            endMs = endMs,
            episodeCount = episodes.size,
            snoreMs = episodes.sumOf { it.durationMs },
            avgDb = avgDb,
            maxDb = maxDb,
            avgAboveFloorDb = maxOf(0.0, avgDb - avgFloor),
            maxAboveFloorDb = maxAbove,
            pauseCount = pauseCount,
            clipCount = clipCount,
        )
    }

    /** The [count] loudest minutes, at least [minGapMs] apart so they are really different moments. */
    fun loudestMoments(minutes: List<MinuteStats>, count: Int = 3, minGapMs: Long = 10 * 60_000L): List<LoudMoment> {
        val result = mutableListOf<LoudMoment>()
        for (m in minutes.sortedByDescending { it.maxDb - it.floorDb }) {
            if (result.size == count) break
            if (result.any { kotlin.math.abs(it.timeMs - m.startMs) < minGapMs }) continue
            result += LoudMoment(m.startMs, m.maxDb, m.maxDb - m.floorDb)
        }
        return result.sortedBy { it.timeMs }
    }
}

/** Averages over a group of nights. */
data class NightAverages(
    val nights: Int,
    val episodes: Double,
    val snoreMinutes: Double,
    val snoreShare: Double,
    val avgAboveFloorDb: Double,
)

data class WeekTrend(val weekStartMs: Long, val averages: NightAverages)

object Trends {
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    fun averages(nights: List<NightSummary>): NightAverages? {
        if (nights.isEmpty()) return null
        return NightAverages(
            nights = nights.size,
            episodes = nights.map { it.episodeCount.toDouble() }.average(),
            snoreMinutes = nights.map { it.snoreMs / 60_000.0 }.average(),
            snoreShare = nights.map { it.snoreShare }.average(),
            avgAboveFloorDb = nights.map { it.avgAboveFloorDb }.average(),
        )
    }

    /** Averages of the nights in the 7 days before [current] (null if there are none). */
    fun previousWeek(current: NightSummary, all: List<NightSummary>): NightAverages? =
        averages(all.filter { it.startMs < current.startMs && it.startMs >= current.startMs - 7 * DAY_MS })

    /** Averages per calendar week (Monday-based), newest first, only weeks with data. */
    fun weekly(all: List<NightSummary>, zone: ZoneId, maxWeeks: Int = 8): List<WeekTrend> =
        all.groupBy { weekStart(it.startMs - 12 * 60 * 60 * 1000L, zone) } // a night belongs to the day it started
            .map { (week, nights) -> WeekTrend(week, averages(nights)!!) }
            .sortedByDescending { it.weekStartMs }
            .take(maxWeeks)

    fun weekStart(timeMs: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(timeMs).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone).toInstant().toEpochMilli()
}
