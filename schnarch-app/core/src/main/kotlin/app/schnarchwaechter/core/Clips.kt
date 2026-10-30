package app.schnarchwaechter.core

import app.schnarchwaechter.core.dsp.percentile

enum class ClipReason { LOUD, LONG_EPISODE, PAUSE }

/**
 * A moment that might be worth a short audio clip. [fromMs]..[toMs] is the wanted time range
 * (may start in the past, the ring buffer supplies the audio before the trigger).
 * [score]: PAUSE (3..4) > LONG_EPISODE (2..3) > LOUD (1..2); within a kind, more extreme = higher.
 */
data class ClipCandidate(
    val reason: ClipReason,
    val score: Double,
    val triggerMs: Long,
    val fromMs: Long,
    val toMs: Long,
    /** dB above floor (LOUD), minutes (LONG_EPISODE) or seconds of silence (PAUSE). */
    val value: Double,
)

/** Turns detector results into clip candidates ("noticeable moments"). */
class AnomalyMonitor(private val detection: DetectionConfig, private val clip: ClipConfig) {
    private val peaks = DoubleArray(4096)
    private var peakCount = 0
    private var longTriggeredFor = Long.MIN_VALUE

    fun onSnoreBurst(burst: SnoreBurst, episodeStartMs: Long): ClipCandidate? {
        val above = burst.peakAboveFloorDb
        val loudEnough = above >= detection.loudClipDb &&
            (peakCount < 20 || above >= percentile(peaks, minOf(peakCount, peaks.size), 0.9) + 3.0)
        peaks[peakCount % peaks.size] = above
        peakCount++

        val episodeMinutes = (burst.endMs - episodeStartMs) / 60_000.0
        if (episodeStartMs != longTriggeredFor && episodeMinutes >= detection.longEpisodeMinutes) {
            longTriggeredFor = episodeStartMs
            return ClipCandidate(
                ClipReason.LONG_EPISODE, 2.0 + minOf(1.0, episodeMinutes / 60.0), burst.endMs,
                burst.endMs - (clip.preRollSec * 1000).toLong(), burst.endMs + (clip.postRollSec * 1000).toLong(),
                episodeMinutes,
            )
        }
        if (loudEnough) {
            return ClipCandidate(
                ClipReason.LOUD, 1.0 + minOf(1.0, above / 60.0), burst.endMs,
                burst.startMs - 6_000, burst.endMs + (clip.postRollSec * 1000).toLong(), above,
            )
        }
        return null
    }

    fun onPause(pause: BreathingPause): ClipCandidate = ClipCandidate(
        ClipReason.PAUSE, 3.0 + minOf(1.0, pause.durationSec / 60.0), pause.endMs,
        pause.startMs - 4_000, pause.endMs + (clip.postRollSec * 1000).toLong(), pause.durationSec,
    )
}

/**
 * Decides whether a candidate becomes a clip: at most [ClipConfig.maxClipsPerNight] per night, a
 * cooldown between clips (which only a more noticeable kind of moment may break), and when full, a new clip only
 * replaces the least noticeable one.
 */
class ClipPolicy(private val config: ClipConfig) {
    sealed interface Decision {
        data object Skip : Decision
        /** Record it; afterwards delete clip [replaceId] (if not null). */
        data class Capture(val replaceId: Long?) : Decision
    }

    private val saved = mutableListOf<Pair<Long, Double>>()
    private var inFlight = 0
    private var blockedUntilMs = Long.MIN_VALUE
    private var blockingScore = 0.0

    /** Saved clips plus clips that are still being recorded. */
    val clipCount: Int get() = saved.size + inFlight

    fun decide(candidate: ClipCandidate): Decision {
        if (config.maxClipsPerNight <= 0) return Decision.Skip
        // The cooldown only gives way to a moment of a more noticeable kind (e.g. pause after loud).
        if (candidate.triggerMs < blockedUntilMs && candidate.score < blockingScore + 1.0) return Decision.Skip
        val decision = if (clipCount < config.maxClipsPerNight) {
            Decision.Capture(null)
        } else {
            val weakest = saved.minByOrNull { it.second } ?: return Decision.Skip
            if (candidate.score <= weakest.second) return Decision.Skip
            saved.remove(weakest) // reserved for replacement
            Decision.Capture(weakest.first)
        }
        inFlight++
        blockedUntilMs = candidate.toMs + (config.cooldownSec * 1000).toLong()
        blockingScore = candidate.score
        return decision
    }

    fun onSaved(id: Long, score: Double) {
        inFlight--
        saved += id to score
    }

    /** Recording failed: put back the clip that was meant to be replaced (if any). */
    fun onFailed(replacedId: Long?, replacedScore: Double?) {
        inFlight--
        if (replacedId != null && replacedScore != null) saved += replacedId to replacedScore
    }
}

/** Fixed-size ring buffer of the most recent audio samples (for the seconds before a trigger). */
class AudioRingBuffer(val capacity: Int) {
    private val data = ShortArray(capacity)
    private var pos = 0
    private var filled = 0

    val size: Int get() = filled

    fun write(buf: ShortArray, offset: Int = 0, length: Int = buf.size - offset) {
        var src = offset
        var remaining = length
        if (remaining >= capacity) {
            src += remaining - capacity
            remaining = capacity
        }
        while (remaining > 0) {
            val n = minOf(remaining, capacity - pos)
            System.arraycopy(buf, src, data, pos, n)
            pos = (pos + n) % capacity
            src += n
            remaining -= n
        }
        filled = minOf(capacity, filled + length)
    }

    /** Copies the newest [count] samples (clamped to what is available) into a new array. */
    fun latest(count: Int): ShortArray {
        val n = minOf(count, filled)
        val out = ShortArray(n)
        var start = pos - n
        if (start < 0) start += capacity
        val first = minOf(n, capacity - start)
        System.arraycopy(data, start, out, 0, first)
        if (first < n) System.arraycopy(data, 0, out, first, n - first)
        return out
    }
}
