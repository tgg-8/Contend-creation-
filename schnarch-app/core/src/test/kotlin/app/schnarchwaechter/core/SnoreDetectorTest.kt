package app.schnarchwaechter.core

import app.schnarchwaechter.core.Synth.broadbandBursts
import app.schnarchwaechter.core.Synth.concat
import app.schnarchwaechter.core.Synth.fanNoise
import app.schnarchwaechter.core.Synth.mix
import app.schnarchwaechter.core.Synth.silence
import app.schnarchwaechter.core.Synth.snoring
import app.schnarchwaechter.core.Synth.whiteNoise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class SnoreDetectorTest {
    private val sr = 8000

    /** Quiet room: a little microphone self-noise. */
    private fun room(sec: Double, seed: Long = 10) = whiteNoise(sr, sec, -68.0, seed)

    @Test
    fun `digital silence gives no episodes and no stage-2 work`() {
        val run = DetectorRun(silence(sr, 300.0), sr)
        assertTrue(run.detector.isCalibrated)
        assertEquals(0, run.episodes.size)
        assertEquals(0, run.detector.stats.stage2Runs)
    }

    @Test
    fun `quiet room noise gives no episodes`() {
        val run = DetectorRun(room(600.0), sr)
        assertEquals(0, run.episodes.size)
        assertEquals(0, run.detector.stats.stage2Runs)
    }

    @Test
    fun `constant fan noise is learned as background, not snoring`() {
        val run = DetectorRun(mix(room(600.0), fanNoise(sr, 600.0, -40.0)), sr)
        assertEquals(0, run.episodes.size)
        assertEquals(-40.0, run.floorDb, 2.0)
    }

    @Test
    fun `fan switching on in the middle of the night is absorbed`() {
        val signal = concat(room(180.0), mix(room(420.0, 11), fanNoise(sr, 420.0, -42.0)))
        val run = DetectorRun(signal, sr)
        assertEquals(0, run.episodes.size)
    }

    @Test
    fun `level measurement matches the RMS of the test signal`() {
        val run = DetectorRun(whiteNoise(sr, 60.0, -40.0), sr)
        val mean = run.frameLevels.drop(10).average()
        assertEquals(-40.0, mean, 0.5)
    }

    @Test
    fun `five minutes of rhythmic snoring are one episode of about five minutes`() {
        val signal = concat(room(60.0), mix(room(300.0, 12), snoring(sr, 300.0, -30.0)), room(120.0, 13))
        val run = DetectorRun(signal, sr)
        assertEquals(1, run.episodes.size)
        val ep = run.episodes[0]
        assertEquals(300.0, ep.durationMs / 1000.0, 15.0)
        assertEquals(60.0, ep.startMs / 1000.0, 5.0)
        assertTrue("bursts: ${ep.burstCount}", ep.burstCount in 65..80)
        assertEquals(0, run.pauses.size)
        // Stage 2 only ran for the snores, not for the ~4800 quiet frames.
        assertTrue(run.detector.stats.stage2Runs <= run.detector.stats.bursts)
    }

    @Test
    fun `snoring is found on top of a fan`() {
        val len = 480.0
        val signal = mix(room(len), fanNoise(sr, len, -45.0), concat(silence(sr, 60.0), snoring(sr, 300.0, -30.0)))
        val run = DetectorRun(signal, sr)
        assertEquals(1, run.episodes.size)
        assertEquals(300.0, run.episodes[0].durationMs / 1000.0, 20.0)
    }

    @Test
    fun `thresholds are relative - quiet snoring with a quiet microphone is still found`() {
        val signal = mix(whiteNoise(sr, 400.0, -85.0), concat(silence(sr, 60.0), snoring(sr, 300.0, -55.0)))
        val run = DetectorRun(signal, sr)
        assertEquals(1, run.episodes.size)
    }

    @Test
    fun `two snoring phases with a long break are two episodes`() {
        val signal = concat(
            room(60.0, 1), mix(room(180.0, 2), snoring(sr, 180.0, -32.0, seed = 5)),
            room(180.0, 3), mix(room(240.0, 4), snoring(sr, 240.0, -28.0, seed = 6)), room(60.0, 7),
        )
        val run = DetectorRun(signal, sr)
        assertEquals(2, run.episodes.size)
        assertTrue(run.episodes[1].maxDb > run.episodes[0].maxDb)
    }

    @Test
    fun `door bangs (short loud noises) are not snoring`() {
        val starts = (0 until 15).map { 45.0 + it * 37.0 }
        val signal = mix(room(620.0), broadbandBursts(sr, 620.0, starts, 0.12, -10.0))
        val run = DetectorRun(signal, sr)
        assertEquals(0, run.episodes.size)
        assertTrue(run.detector.stats.rejectedDuration > 0)
    }

    @Test
    fun `irregular coughing is not snoring`() {
        val r = Random(7)
        var t = 40.0
        val starts = mutableListOf<Double>()
        while (t < 580) {
            starts += t
            t += if (r.nextBoolean()) 0.6 + r.nextDouble() else 15 + r.nextDouble() * 40
        }
        val signal = mix(room(600.0), broadbandBursts(sr, 600.0, starts, 0.35, -20.0))
        val run = DetectorRun(signal, sr)
        assertEquals(0, run.episodes.size)
    }

    @Test
    fun `rhythmic but broadband sounds are rejected by the band check`() {
        val starts = (0 until 70).map { 40.0 + it * 4.0 }
        val signal = mix(room(330.0), broadbandBursts(sr, 330.0, starts, 1.2, -25.0))
        val run = DetectorRun(signal, sr)
        assertEquals(0, run.episodes.size)
        assertTrue(run.detector.stats.rejectedBand + run.detector.stats.rejectedSpectrum > 50)
    }

    @Test
    fun `three snores alone are too short for an episode`() {
        val signal = concat(room(60.0), mix(room(12.0, 2), snoring(sr, 12.0, -30.0)), room(90.0, 3))
        val run = DetectorRun(signal, sr)
        assertEquals(0, run.episodes.size)
    }

    @Test
    fun `sounds faster than breathing are not snoring`() {
        val signal = concat(room(60.0), mix(room(240.0, 2), snoring(sr, 240.0, -30.0, intervalSec = 1.1, burstSec = 0.5, jitterSec = 0.05)))
        val run = DetectorRun(signal, sr)
        assertEquals(0, run.episodes.size)
    }

    @Test
    fun `long silence right after loud snoring is reported as pause`() {
        // Breaths 40..43 are missing -> about 4*4 s + 2.8 s of silence inside the snoring.
        val snore = snoring(sr, 360.0, -25.0, jitterSec = 0.1, skipBreaths = setOf(40, 41, 42, 43))
        val signal = concat(room(60.0), mix(room(360.0, 2), snore), room(60.0, 3))
        val run = DetectorRun(signal, sr)
        assertEquals(1, run.episodes.size)
        assertEquals(1, run.pauses.size)
        assertEquals(18.8, run.pauses[0].durationSec, 2.0)
        assertEquals(1, run.episodes[0].pauseCount)
    }

    @Test
    fun `short skipped breath is not a pause`() {
        val snore = snoring(sr, 300.0, -25.0, jitterSec = 0.1, skipBreaths = setOf(30))
        val run = DetectorRun(concat(room(60.0), mix(room(300.0, 2), snore)), sr)
        assertEquals(0, run.pauses.size)
    }

    @Test
    fun `works at 16 kHz as well`() {
        val rate = 16000
        val signal = concat(whiteNoise(rate, 60.0, -68.0), mix(whiteNoise(rate, 300.0, -68.0, 2), snoring(rate, 300.0, -30.0)))
        val run = DetectorRun(signal, rate)
        assertEquals(1, run.episodes.size)
    }

    @Test
    fun `lower sensitivity ignores faint snoring that high sensitivity finds`() {
        val signal = concat(room(60.0), mix(room(300.0, 2), snoring(sr, 300.0, -64.0)))
        val low = DetectorRun(signal, sr, DetectionConfig.forSensitivity(1))
        val high = DetectorRun(signal, sr, DetectionConfig.forSensitivity(5))
        assertEquals(0, low.episodes.size)
        assertEquals(1, high.episodes.size)
    }
}
