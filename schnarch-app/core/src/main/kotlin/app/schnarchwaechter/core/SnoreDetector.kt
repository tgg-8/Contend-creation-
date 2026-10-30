package app.schnarchwaechter.core

import app.schnarchwaechter.core.dsp.SnoreBandFilter
import app.schnarchwaechter.core.dsp.Spectrum
import app.schnarchwaechter.core.dsp.dbToPower
import app.schnarchwaechter.core.dsp.powerToDb
import kotlin.math.sqrt

/** One single snore sound (usually one inhalation). Levels in dBFS / dB above floor. */
data class SnoreBurst(
    val startMs: Long,
    val endMs: Long,
    val peakDb: Double,
    val peakAboveFloorDb: Double,
)

/** A connected period of detected snoring. */
data class SnoreEpisode(
    val startMs: Long,
    val endMs: Long,
    val burstCount: Int,
    val maxDb: Double,
    val meanDb: Double,
    val maxAboveFloorDb: Double,
    val pauseCount: Int,
) {
    val durationMs: Long get() = endMs - startMs
}

/** Unusually long silence directly after loud snoring, followed by snoring again. NOT a diagnosis. */
data class BreathingPause(
    val startMs: Long,
    val endMs: Long,
    val precedingAboveFloorDb: Double,
) {
    val durationSec: Double get() = (endMs - startMs) / 1000.0
}

/** Callbacks of [SnoreDetector]. Called on the thread that calls [SnoreDetector.process]. */
interface DetectorListener {
    /** Every analysis frame (10x per second). [floorDb] is NaN during calibration. */
    fun onFrame(timeMs: Long, levelDb: Double, floorDb: Double) {}
    fun onCalibrated(floorDb: Double) {}
    fun onEpisodeStarted(startMs: Long) {}
    /** A confirmed snore inside an episode. */
    fun onSnoreBurst(burst: SnoreBurst, episodeStartMs: Long) {}
    fun onEpisodeEnded(episode: SnoreEpisode) {}
    /** Episode was confirmed but turned out shorter than [DetectionConfig.minEpisodeSec]. */
    fun onEpisodeDiscarded(startMs: Long) {}
    fun onPause(pause: BreathingPause) {}
}

/** Counters that show how often the expensive stage 2 actually ran (useful for tuning & power). */
class DetectorStats {
    var frames = 0L
    var bursts = 0
    var rejectedDuration = 0
    var rejectedBand = 0
    var rejectedSpectrum = 0
    var stage2Runs = 0
    var snoreCandidates = 0
    var floorSpectrumUpdates = 0
}

/**
 * Two-stage snore detector.
 *
 * Stage 1 (every sample, cheap): RMS level and band-pass energy (100-1000 Hz) per 100 ms frame,
 * adaptive noise floor, and a burst state machine (sound rises clearly above the floor in the band).
 *
 * Stage 2 (only when stage 1 found a burst): plausibility of duration, share of energy in the snore
 * band, spectral centroid via FFT, and the breathing rhythm of consecutive bursts (every 2-9 s,
 * reasonably regular). Only rhythmic, low-frequency bursts form an episode. A fan (constant noise),
 * a door (single short bang), coughing (irregular, broadband) or talking (broadband) are rejected.
 */
class SnoreDetector(
    val sampleRate: Int,
    val config: DetectionConfig,
    private val startTimeMs: Long,
    private val listener: DetectorListener,
) {
    val stats = DetectorStats()

    private val frameLen = sampleRate * config.frameMs / 1000
    private val framesPerSecond = 1000.0 / config.frameMs
    private val filter = SnoreBandFilter(sampleRate, config.bandLowHz, config.bandHighHz)
    private val calibrationFrames = (config.calibrationSeconds * framesPerSecond).toInt().coerceAtLeast(1)
    private val updateFrames = (config.floorUpdateSeconds * framesPerSecond).toInt().coerceAtLeast(1)
    private val totalFloor = NoiseFloorTracker(calibrationFrames, updateFrames, config.floorPercentile, config.floorMaxRiseDb)
    private val bandFloor = NoiseFloorTracker(calibrationFrames, updateFrames, config.floorPercentile, config.floorMaxRiseDb)

    // Short sample history so stage 2 can look at the burst after it ended.
    private val historyLen = ((config.maxBurstSec + 1.0) * sampleRate).toInt()
    private val history = ShortArray(historyLen)
    private var historyPos = 0

    private val spectrum = Spectrum(Spectrum.sizeFor(sampleRate))
    private val floorSpectrum = DoubleArray(spectrum.bins)
    private val scratchSpectrum = DoubleArray(spectrum.bins)
    private var floorSpectrumFrames = 0
    private var quietFrames = 0

    private var sampleIndex = 0L
    private var frameFill = 0
    private var frameSumSq = 0.0
    private var frameBandSq = 0.0

    // Burst state (stage 1)
    private var inBurst = false
    private var burstStartSample = 0L
    private var burstPeakDb = 0.0
    private var burstBandExcess = 0.0
    private var burstTotalExcess = 0.0

    // Rhythm / episode state (stage 2)
    private val chain = ArrayDeque<SnoreBurst>()
    private var episode: EpisodeBuilder? = null

    val isCalibrated: Boolean get() = totalFloor.isCalibrated
    val floorDb: Double get() = totalFloor.floorDb
    val isInEpisode: Boolean get() = episode != null

    /** Time (ms) of the end of the last processed sample. */
    val nowMs: Long get() = timeOf(sampleIndex)

    fun process(buf: ShortArray, offset: Int = 0, length: Int = buf.size - offset) {
        for (i in offset until offset + length) {
            val s = buf[i]
            history[historyPos] = s
            historyPos = if (historyPos + 1 == historyLen) 0 else historyPos + 1
            val x = s / 32768.0
            frameSumSq += x * x
            val y = filter.process(x)
            frameBandSq += y * y
            sampleIndex++
            if (++frameFill == frameLen) {
                endFrame()
                frameFill = 0
                frameSumSq = 0.0
                frameBandSq = 0.0
            }
        }
    }

    /** Call at the end of the night: closes an open burst and episode. */
    fun finish() {
        if (inBurst) endBurst(sampleIndex)
        finishEpisode()
    }

    private fun timeOf(sample: Long): Long = startTimeMs + sample * 1000L / sampleRate

    private fun historySample(absIndex: Long): Double {
        val back = (sampleIndex - absIndex).toInt() // 1 = newest sample
        var idx = historyPos - back
        if (idx < 0) idx += historyLen
        return history[idx] / 32768.0
    }

    private fun endFrame() {
        stats.frames++
        val frameStart = sampleIndex - frameLen
        val timeMs = timeOf(frameStart)
        val totalPow = frameSumSq / frameLen
        val bandPow = frameBandSq / frameLen
        val levelDb = powerToDb(totalPow)
        val bandDb = powerToDb(bandPow)

        if (!totalFloor.isCalibrated) {
            totalFloor.add(levelDb)
            bandFloor.add(bandDb)
            accumulateFrameSpectrum(floorSpectrum)
            floorSpectrumFrames++
            if (totalFloor.isCalibrated) {
                for (k in floorSpectrum.indices) floorSpectrum[k] /= floorSpectrumFrames
                listener.onCalibrated(totalFloor.floorDb)
            }
            listener.onFrame(timeMs, levelDb, Double.NaN)
            return
        }
        // Digital silence (microphone muted by the system, e.g. during a phone call) must not drag
        // the noise floor down, otherwise detection would be blind for a while afterwards.
        if (levelDb > DIGITAL_SILENCE_DB) {
            totalFloor.add(levelDb)
            bandFloor.add(bandDb)
        }
        listener.onFrame(timeMs, levelDb, totalFloor.floorDb)

        val excess = bandDb - bandFloor.floorDb
        if (inBurst && excess < config.burstReleaseDb) {
            endBurst(frameStart)
        } else if (!inBurst && excess >= config.burstOnsetDb) {
            inBurst = true
            burstStartSample = frameStart
            burstPeakDb = levelDb
            burstBandExcess = 0.0
            burstTotalExcess = 0.0
        }
        if (inBurst) {
            if (levelDb > burstPeakDb) burstPeakDb = levelDb
            burstBandExcess += maxOf(0.0, bandPow - dbToPower(bandFloor.floorDb))
            burstTotalExcess += maxOf(0.0, totalPow - dbToPower(totalFloor.floorDb))
        } else if (excess < config.burstReleaseDb) {
            // Quiet frame: occasionally refresh the floor spectrum (one FFT every ~5 s of quiet).
            if (++quietFrames % 50 == 0) {
                scratchSpectrum.fill(0.0)
                accumulateFrameSpectrum(scratchSpectrum)
                for (k in floorSpectrum.indices) floorSpectrum[k] = 0.9 * floorSpectrum[k] + 0.1 * scratchSpectrum[k]
                stats.floorSpectrumUpdates++
            }
        }
        checkTimeouts(timeMs)
    }

    private fun accumulateFrameSpectrum(acc: DoubleArray) {
        val n = spectrum.size
        if (n > frameLen) return
        spectrum.accumulatePower(0, { historySample(sampleIndex - n + it) }, acc)
    }

    private fun endBurst(endSample: Long) {
        inBurst = false
        stats.bursts++
        val startMs = timeOf(burstStartSample)
        val endMs = timeOf(endSample)
        val durSec = (endSample - burstStartSample).toDouble() / sampleRate
        val burst = SnoreBurst(startMs, endMs, burstPeakDb, burstPeakDb - totalFloor.floorDb)

        val isSnore = when {
            durSec < config.minBurstSec || durSec > config.maxBurstSec -> { stats.rejectedDuration++; false }
            burstTotalExcess <= 0.0 || burstBandExcess / burstTotalExcess < config.minBandRatio -> { stats.rejectedBand++; false }
            spectralCentroid(burstStartSample, endSample) > config.maxCentroidHz -> { stats.rejectedSpectrum++; false }
            else -> true
        }
        if (isSnore) {
            stats.snoreCandidates++
            onSnoreCandidate(burst)
        } else {
            episode?.let { if (it.firstOtherSoundMs == null) it.firstOtherSoundMs = startMs }
        }
    }

    /** Stage 2: centroid (Hz) of the burst spectrum above the floor spectrum. */
    private fun spectralCentroid(fromSample: Long, toSample: Long): Double {
        stats.stage2Runs++
        val n = spectrum.size
        val oldest = sampleIndex - historyLen + 1
        val from = maxOf(fromSample, oldest)
        val available = (toSample - from).toInt()
        if (available < n) return 0.0
        val windows = minOf(8, available / n)
        val step = if (windows > 1) (available - n) / (windows - 1) else 0
        scratchSpectrum.fill(0.0)
        for (w in 0 until windows) {
            val start = from + w.toLong() * step
            spectrum.accumulatePower(0, { historySample(start + it) }, scratchSpectrum)
        }
        val binHz = sampleRate.toDouble() / n
        var weighted = 0.0
        var total = 0.0
        for (k in 0 until spectrum.bins) {
            val f = k * binHz
            if (f < 50.0) continue
            val p = scratchSpectrum[k] / windows - floorSpectrum[k]
            if (p > 0) {
                weighted += f * p
                total += p
            }
        }
        return if (total > 0) weighted / total else 0.0
    }

    private fun onSnoreCandidate(burst: SnoreBurst) {
        val ep = episode
        if (ep != null) {
            // Unusually long silence after loud snoring, then snoring again?
            val silenceEnd = ep.firstOtherSoundMs ?: burst.startMs
            val silenceSec = (silenceEnd - ep.lastEndMs) / 1000.0
            val loudness = ep.recentAboveFloorDb()
            if (silenceSec >= config.pauseMinSec && loudness >= config.pauseLoudDb) {
                ep.pauses++
                listener.onPause(BreathingPause(ep.lastEndMs, silenceEnd, loudness))
            }
            ep.add(burst)
            listener.onSnoreBurst(burst, ep.startMs)
            return
        }
        chain.lastOrNull()?.let { prev ->
            val interval = (burst.startMs - prev.startMs) / 1000.0
            if (interval < config.minRhythmSec || interval > config.maxRhythmSec) chain.clear()
        }
        chain.addLast(burst)
        while (chain.size > config.burstsToConfirm) chain.removeFirst()
        if (chain.size >= config.burstsToConfirm && rhythmCv() <= config.maxRhythmCv) {
            val newEp = EpisodeBuilder(chain.first().startMs)
            episode = newEp
            listener.onEpisodeStarted(newEp.startMs)
            for (b in chain) {
                newEp.add(b)
                listener.onSnoreBurst(b, newEp.startMs)
            }
            chain.clear()
        }
    }

    private fun rhythmCv(): Double {
        val n = chain.size - 1
        if (n < 1) return Double.MAX_VALUE
        val intervals = DoubleArray(n) { (chain[it + 1].startMs - chain[it].startMs).toDouble() }
        val mean = intervals.average()
        if (mean <= 0) return Double.MAX_VALUE
        var v = 0.0
        for (x in intervals) v += (x - mean) * (x - mean)
        return sqrt(v / n) / mean
    }

    private fun checkTimeouts(nowMs: Long) {
        val ep = episode
        if (ep != null && nowMs - ep.lastEndMs > config.episodeGapSec * 1000) finishEpisode()
        val last = chain.lastOrNull()
        if (last != null && nowMs - last.startMs > config.maxRhythmSec * 1000) chain.clear()
    }

    private fun finishEpisode() {
        val ep = episode ?: return
        episode = null
        chain.clear()
        val built = ep.build()
        if (built.durationMs >= config.minEpisodeSec * 1000) listener.onEpisodeEnded(built)
        else listener.onEpisodeDiscarded(ep.startMs)
    }

    private companion object {
        const val DIGITAL_SILENCE_DB = -100.0
    }

    private class EpisodeBuilder(val startMs: Long) {
        var lastEndMs = startMs
        var count = 0
        var maxDb = -200.0
        var maxAbove = -200.0
        var powerSum = 0.0
        var pauses = 0
        var firstOtherSoundMs: Long? = null
        private val recent = DoubleArray(3)

        fun add(b: SnoreBurst) {
            recent[count % recent.size] = b.peakAboveFloorDb
            count++
            lastEndMs = b.endMs
            maxDb = maxOf(maxDb, b.peakDb)
            maxAbove = maxOf(maxAbove, b.peakAboveFloorDb)
            powerSum += dbToPower(b.peakDb)
            firstOtherSoundMs = null
        }

        fun recentAboveFloorDb(): Double {
            val n = minOf(count, recent.size)
            if (n == 0) return 0.0
            var p = 0.0
            for (i in 0 until n) p += dbToPower(recent[i])
            return powerToDb(p / n)
        }

        fun build() = SnoreEpisode(startMs, lastEndMs, count, maxDb, powerToDb(powerSum / maxOf(1, count)), maxAbove, pauses)
    }
}
