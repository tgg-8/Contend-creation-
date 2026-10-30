package app.schnarchwaechter.core

/** Audio of a noticeable moment that should be stored as a clip. */
class ClipRequest(
    val pcm: ShortArray,
    val sampleRate: Int,
    val startMs: Long,
    val candidate: ClipCandidate,
    /** Clip to delete once this one was saved (only the most noticeable clips are kept). */
    val replaceId: Long?,
)

/** Where a night's results go (database / files in the app, lists in tests). */
interface SessionSink {
    fun saveMinutes(minutes: List<MinuteStats>)
    fun saveEpisode(episode: SnoreEpisode)
    fun savePause(pause: BreathingPause)
    /** Store the clip, then report back with [NightSession.onClipResult] (from any thread). */
    fun saveClip(request: ClipRequest)
}

/**
 * Glue for one night: feeds audio blocks to the detector, aggregates levels per minute and writes
 * them in batches, keeps a small ring buffer for the seconds before a noticeable moment and turns
 * selected moments into clips. [process] must always be called from the same (audio) thread.
 */
class NightSession(
    val startMs: Long,
    val sampleRate: Int,
    detection: DetectionConfig,
    private val clipConfig: ClipConfig,
    private val sink: SessionSink,
    /** Minutes are written to the database in batches of this size (fewer wake-ups of the storage). */
    private val flushEveryMinutes: Int = 10,
) : DetectorListener {
    val detector = SnoreDetector(sampleRate, detection, startMs, this)
    private val aggregator = MinuteAggregator()
    private val ring = AudioRingBuffer((clipConfig.maxClipSec * sampleRate).toInt())
    private val monitor = AnomalyMonitor(detection, clipConfig)
    private val policy = ClipPolicy(clipConfig)
    private val savedScores = HashMap<Long, Double>()
    private val pendingMinutes = ArrayList<MinuteStats>()
    private var pendingClip: PendingClip? = null

    var episodeCount = 0
        private set
    var pauseCount = 0
        private set

    fun process(buf: ShortArray, length: Int) {
        pendingClip?.let { clip ->
            clip.append(buf, length)
            if (clip.isComplete) {
                pendingClip = null
                sink.saveClip(clip.toRequest())
            }
        }
        ring.write(buf, 0, length)
        detector.process(buf, 0, length)
        if (pendingMinutes.size >= flushEveryMinutes) flushMinutes()
    }

    /** End of night: close everything that is still open and write it. */
    fun finish() {
        detector.finish()
        aggregator.flush()?.let { pendingMinutes += it }
        flushMinutes()
        pendingClip?.let { clip ->
            pendingClip = null
            if (clip.filled >= 3 * sampleRate) sink.saveClip(clip.toRequest()) else rejectClip(clip)
        }
    }

    fun onClipResult(request: ClipRequest, savedId: Long?) = synchronized(policy) {
        if (savedId != null) {
            policy.onSaved(savedId, request.candidate.score)
            savedScores[savedId] = request.candidate.score
            request.replaceId?.let { savedScores.remove(it) }
        } else {
            policy.onFailed(request.replaceId, request.replaceId?.let { savedScores[it] })
        }
    }

    private fun rejectClip(clip: PendingClip) = onClipResult(clip.toRequest(), null)

    private fun flushMinutes() {
        if (pendingMinutes.isEmpty()) return
        sink.saveMinutes(ArrayList(pendingMinutes))
        pendingMinutes.clear()
    }

    override fun onFrame(timeMs: Long, levelDb: Double, floorDb: Double) {
        aggregator.addFrame(timeMs, levelDb, floorDb)?.let { pendingMinutes += it }
    }

    override fun onSnoreBurst(burst: SnoreBurst, episodeStartMs: Long) {
        monitor.onSnoreBurst(burst, episodeStartMs)?.let { considerClip(it) }
    }

    override fun onEpisodeEnded(episode: SnoreEpisode) {
        episodeCount++
        sink.saveEpisode(episode)
    }

    override fun onPause(pause: BreathingPause) {
        pauseCount++
        sink.savePause(pause)
        considerClip(monitor.onPause(pause))
    }

    private fun considerClip(candidate: ClipCandidate) {
        val pending = pendingClip
        if (pending != null && candidate.score < pending.candidate.score + 1.0) return
        val decision = synchronized(policy) { policy.decide(candidate) }
        if (decision !is ClipPolicy.Decision.Capture) return
        if (pending != null) {
            // A more noticeable moment interrupts the running clip; keep what we have so far.
            pendingClip = null
            if (pending.filled >= 3 * sampleRate) sink.saveClip(pending.toRequest()) else rejectClip(pending)
        }
        val now = detector.nowMs
        val maxMs = (clipConfig.maxClipSec * 1000).toLong()
        val preMs = (now - candidate.fromMs).coerceIn(0, maxMs)
        val postMs = (candidate.toMs - now).coerceIn(0, maxMs - preMs)
        val pre = ring.latest((preMs * sampleRate / 1000).toInt())
        val total = pre.size + (postMs * sampleRate / 1000).toInt()
        val clip = PendingClip(ShortArray(total), now - pre.size * 1000L / sampleRate, candidate, decision.replaceId)
        clip.append(pre, pre.size)
        if (clip.isComplete) sink.saveClip(clip.toRequest()) else pendingClip = clip
    }

    private inner class PendingClip(
        val pcm: ShortArray,
        val startMs: Long,
        val candidate: ClipCandidate,
        val replaceId: Long?,
    ) {
        var filled = 0
            private set
        val isComplete: Boolean get() = filled >= pcm.size

        fun append(buf: ShortArray, length: Int) {
            val n = minOf(length, pcm.size - filled)
            System.arraycopy(buf, 0, pcm, filled, n)
            filled += n
        }

        fun toRequest() = ClipRequest(if (isComplete) pcm else pcm.copyOf(filled), sampleRate, startMs, candidate, replaceId)
    }
}
