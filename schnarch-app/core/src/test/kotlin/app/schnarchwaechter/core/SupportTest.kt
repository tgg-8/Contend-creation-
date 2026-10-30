package app.schnarchwaechter.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.ZoneId
import app.schnarchwaechter.core.tools.WavFile

class MinuteAggregatorTest {
    @Test
    fun `aggregates frames per wall-clock minute`() {
        val agg = MinuteAggregator()
        val results = mutableListOf<MinuteStats>()
        // 30 s into a minute, 150 s of frames at -50 dB with one -20 dB frame.
        for (i in 0 until 1500) {
            val t = 30_000L + i * 100L
            agg.addFrame(t, if (i == 700) -20.0 else -50.0, -55.0)?.let { results += it }
        }
        agg.flush()?.let { results += it }
        assertEquals(listOf(0L, 60_000L, 120_000L), results.map { it.startMs })
        assertEquals(-50.0, results[0].avgDb, 0.01)
        assertEquals(-20.0, results[1].maxDb, 0.01)
        assertTrue(results[1].avgDb > -50.0)
        assertEquals(-55.0, results[2].floorDb, 0.01)
        assertNull(agg.flush())
    }
}

class ClipPolicyTest {
    private fun cand(score: Double, t: Long) = ClipCandidate(ClipReason.LOUD, score, t, t - 5000, t + 5000, 30.0)

    @Test
    fun `keeps at most N clips and replaces only weaker ones`() {
        val policy = ClipPolicy(ClipConfig(maxClipsPerNight = 2, cooldownSec = 0.0))
        var id = 1L
        fun take(score: Double, t: Long): ClipPolicy.Decision {
            val c = cand(score, t)
            val d = policy.decide(c)
            if (d is ClipPolicy.Decision.Capture) policy.onSaved(id++, c.score)
            return d
        }
        assertEquals(ClipPolicy.Decision.Capture(null), take(1.2, 0))
        assertEquals(ClipPolicy.Decision.Capture(null), take(1.5, 20_000))
        assertEquals(ClipPolicy.Decision.Skip, take(1.1, 40_000))
        assertEquals(ClipPolicy.Decision.Capture(1L), take(1.8, 60_000))
        assertEquals(2, policy.clipCount)
    }

    @Test
    fun `cooldown blocks clips shortly after each other`() {
        val policy = ClipPolicy(ClipConfig(maxClipsPerNight = 5, cooldownSec = 120.0))
        assertTrue(policy.decide(cand(1.5, 0)) is ClipPolicy.Decision.Capture)
        assertEquals(ClipPolicy.Decision.Skip, policy.decide(cand(1.9, 60_000)))
        assertTrue(policy.decide(cand(1.4, 130_000)) is ClipPolicy.Decision.Capture)
    }

    @Test
    fun `a more noticeable moment breaks the cooldown`() {
        val policy = ClipPolicy(ClipConfig(maxClipsPerNight = 5, cooldownSec = 120.0))
        assertTrue(policy.decide(cand(1.5, 0)) is ClipPolicy.Decision.Capture)
        assertTrue(policy.decide(cand(3.2, 20_000)) is ClipPolicy.Decision.Capture)
    }

    @Test
    fun `clips that are still being recorded count towards the limit`() {
        val policy = ClipPolicy(ClipConfig(maxClipsPerNight = 1, cooldownSec = 0.0))
        assertTrue(policy.decide(cand(1.5, 0)) is ClipPolicy.Decision.Capture)
        assertEquals(ClipPolicy.Decision.Skip, policy.decide(cand(3.5, 20_000)))
        policy.onFailed(null, null)
        assertTrue(policy.decide(cand(1.2, 40_000)) is ClipPolicy.Decision.Capture)
    }

    @Test
    fun `zero clips allowed means never record`() {
        val policy = ClipPolicy(ClipConfig(maxClipsPerNight = 0))
        assertEquals(ClipPolicy.Decision.Skip, policy.decide(cand(4.0, 0)))
    }
}

class AnomalyMonitorTest {
    private val config = DetectionConfig()
    private fun burst(t: Long, above: Double) = SnoreBurst(t, t + 1000, -60 + above, above)

    @Test
    fun `only unusually loud snores become loud candidates`() {
        val m = AnomalyMonitor(config, ClipConfig())
        assertNull(m.onSnoreBurst(burst(0, 15.0), 0))
        assertEquals(ClipReason.LOUD, m.onSnoreBurst(burst(4000, 30.0), 0)?.reason)
        // After many snores at 30 dB, 30 dB is normal for this night and no longer noticeable.
        for (i in 2 until 40) m.onSnoreBurst(burst(i * 4000L, 30.0), 0)
        assertNull(m.onSnoreBurst(burst(200_000, 30.0), 0))
        assertEquals(ClipReason.LOUD, m.onSnoreBurst(burst(204_000, 40.0), 0)?.reason)
    }

    @Test
    fun `long episode triggers once`() {
        val m = AnomalyMonitor(config.copy(longEpisodeMinutes = 1.0), ClipConfig())
        assertNull(m.onSnoreBurst(burst(30_000, 10.0), 0))
        assertEquals(ClipReason.LONG_EPISODE, m.onSnoreBurst(burst(61_000, 10.0), 0)?.reason)
        assertNull(m.onSnoreBurst(burst(90_000, 10.0), 0))
    }

    @Test
    fun `pause outranks everything and covers the silence`() {
        val m = AnomalyMonitor(config, ClipConfig())
        val c = m.onPause(BreathingPause(100_000, 115_000, 20.0))
        assertTrue(c.score > 3.0)
        assertTrue(c.fromMs < 100_000 && c.toMs > 115_000)
    }
}

class AudioRingBufferTest {
    @Test
    fun `returns the newest samples in order across the wrap-around`() {
        val ring = AudioRingBuffer(5)
        ring.write(shortArrayOf(1, 2, 3))
        assertArrayEquals(shortArrayOf(2, 3), ring.latest(2))
        ring.write(shortArrayOf(4, 5, 6, 7))
        assertArrayEquals(shortArrayOf(3, 4, 5, 6, 7), ring.latest(10))
        ring.write(ShortArray(12) { (100 + it).toShort() })
        assertArrayEquals(shortArrayOf(107, 108, 109, 110, 111), ring.latest(5))
    }
}

class NightStatsTest {
    private fun ep(startMin: Int, endMin: Int) = SnoreEpisode(startMin * 60_000L, endMin * 60_000L, 10, -30.0, -32.0, 25.0, 0)

    @Test
    fun `summary sums episodes and computes share`() {
        val minutes = (0 until 480).map { MinuteStats(it * 60_000L, if (it == 100) -30.0 else -50.0, if (it == 100) -20.0 else -45.0, -55.0) }
        val s = NightStatsCalculator.summarize(0, 480 * 60_000L, minutes, listOf(ep(60, 90), ep(200, 212)), pauseCount = 1)
        assertEquals(2, s.episodeCount)
        assertEquals(42 * 60_000L, s.snoreMs)
        assertEquals(42.0 / 480, s.snoreShare, 1e-9)
        assertEquals(35.0, s.maxAboveFloorDb, 0.01)
        assertTrue(s.avgAboveFloorDb in 5.0..6.0)
        val loud = NightStatsCalculator.loudestMoments(minutes)
        assertEquals(100 * 60_000L, loud.first { it.aboveFloorDb > 30 }.timeMs)
    }

    @Test
    fun `previous week compares only the 7 days before`() {
        val day = 24 * 3_600_000L
        fun n(d: Int, episodes: Int) = NightSummary(d * day, d * day + 8 * 3_600_000L, episodes, episodes * 60_000L, -50.0, -30.0, 5.0, 20.0, 0)
        val all = listOf(n(1, 100), n(5, 4), n(8, 6), n(10, 2))
        val avg = Trends.previousWeek(n(10, 2), all)!!
        assertEquals(2, avg.nights)
        assertEquals(5.0, avg.episodes, 1e-9)
        assertNull(Trends.previousWeek(n(1, 100), all))
        assertTrue(Trends.weekly(all, ZoneId.of("UTC")).isNotEmpty())
    }
}

class CsvExportTest {
    @Test
    fun `csv uses semicolons and german decimal comma`() {
        val n = NightSummary(0, 8 * 3_600_000L, 3, 30 * 60_000L, -45.25, -20.0, 7.5, 30.0, 1, 2)
        val csv = CsvExport.nights(listOf(n), ZoneId.of("UTC")).lines()
        assertTrue(csv[0].startsWith("Beginn;Ende;"))
        assertEquals("1970-01-01 00:00;1970-01-01 08:00;480;3;30,0;6,3;7,5;30,0;-45,3;-20,0;1;2", csv[1])
    }
}

class WavFileTest {
    @Test
    fun `reads 16 bit stereo wav and mixes down`() {
        val samples = shortArrayOf(100, 300, -200, -400)
        val bb = ByteBuffer.allocate(44 + samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()).putInt(36 + samples.size * 2).put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(2).putInt(8000).putInt(32000).putShort(4).putShort(16)
        bb.put("data".toByteArray()).putInt(samples.size * 2)
        samples.forEach { bb.putShort(it) }
        val wav = WavFile.read(ByteArrayInputStream(bb.array()))
        assertEquals(8000, wav.sampleRate)
        assertArrayEquals(shortArrayOf(200, -300), wav.samples)
    }
}
