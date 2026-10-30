package app.schnarchwaechter.core

import app.schnarchwaechter.core.Synth.concat
import app.schnarchwaechter.core.Synth.mix
import app.schnarchwaechter.core.Synth.snoring
import app.schnarchwaechter.core.Synth.toPcm
import app.schnarchwaechter.core.Synth.whiteNoise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NightSessionTest {
    private val sr = 8000

    private class FakeSink : SessionSink {
        lateinit var session: NightSession
        val minuteBatches = mutableListOf<List<MinuteStats>>()
        val episodes = mutableListOf<SnoreEpisode>()
        val pauses = mutableListOf<BreathingPause>()
        val clips = mutableMapOf<Long, ClipRequest>()
        private var nextId = 1L

        override fun saveMinutes(minutes: List<MinuteStats>) {
            minuteBatches += minutes
        }

        override fun saveEpisode(episode: SnoreEpisode) {
            episodes += episode
        }

        override fun savePause(pause: BreathingPause) {
            pauses += pause
        }

        override fun saveClip(request: ClipRequest) {
            val id = nextId++
            clips[id] = request
            request.replaceId?.let { clips.remove(it) }
            session.onClipResult(request, id)
        }
    }

    private fun runNight(signal: DoubleArray, clip: ClipConfig = ClipConfig(), config: DetectionConfig = DetectionConfig()): FakeSink {
        val sink = FakeSink()
        val session = NightSession(0, sr, config, clip, sink)
        sink.session = session
        val pcm = toPcm(signal)
        val block = 2 * sr
        var i = 0
        val buf = ShortArray(block)
        while (i < pcm.size) {
            val n = minOf(block, pcm.size - i)
            System.arraycopy(pcm, i, buf, 0, n)
            session.process(buf, n)
            i += n
        }
        session.finish()
        return sink
    }

    @Test
    fun `minutes are written in batches and no audio is kept for a quiet night`() {
        val sink = runNight(whiteNoise(sr, 25 * 60.0, -60.0))
        assertEquals(listOf(10, 10, 5), sink.minuteBatches.map { it.size })
        assertTrue(sink.clips.isEmpty())
        assertTrue(sink.episodes.isEmpty())
    }

    @Test
    fun `a breathing pause after loud snoring produces one clip that contains the silence`() {
        val snore = snoring(sr, 360.0, -25.0, jitterSec = 0.1, skipBreaths = setOf(40, 41, 42, 43))
        val sink = runNight(concat(whiteNoise(sr, 60.0, -68.0), mix(whiteNoise(sr, 360.0, -68.0, 2), snore)))
        assertEquals(1, sink.pauses.size)
        val pauseClips = sink.clips.values.filter { it.candidate.reason == ClipReason.PAUSE }
        assertEquals(1, pauseClips.size)
        val clip = pauseClips[0]
        val clipEnd = clip.startMs + clip.pcm.size * 1000L / sr
        assertTrue("clip ${clip.startMs}..$clipEnd", clip.startMs <= sink.pauses[0].startMs && clipEnd >= sink.pauses[0].endMs)
        assertTrue(clip.pcm.size <= 25 * sr)
    }

    @Test
    fun `clip limit per night is respected and the most noticeable ones are kept`() {
        // Six phases of loud snoring, each louder than the one before, with enough time in between.
        val parts = mutableListOf(whiteNoise(sr, 60.0, -68.0))
        for (k in 0 until 6) {
            parts += mix(whiteNoise(sr, 60.0, -68.0, k + 10L), snoring(sr, 60.0, -40.0 + 4 * k, seed = k + 20L))
            parts += whiteNoise(sr, 150.0, -68.0, k + 30L)
        }
        val sink = runNight(concat(*parts.toTypedArray()), ClipConfig(maxClipsPerNight = 2, cooldownSec = 60.0))
        assertEquals(2, sink.clips.size)
        val kept = sink.clips.values.map { it.candidate.value }
        assertTrue("kept $kept", kept.all { it > 35.0 })
    }

    @Test
    fun `no clips when clips are disabled`() {
        val snore = snoring(sr, 360.0, -15.0, skipBreaths = setOf(40, 41, 42, 43))
        val sink = runNight(concat(whiteNoise(sr, 60.0, -68.0), mix(whiteNoise(sr, 360.0, -68.0, 2), snore)), ClipConfig(maxClipsPerNight = 0))
        assertTrue(sink.clips.isEmpty())
        assertEquals(1, sink.episodes.size)
    }
}
