package app.schnarchwaechter.data

import android.content.Context
import app.schnarchwaechter.core.BreathingPause
import app.schnarchwaechter.core.ClipRequest
import app.schnarchwaechter.core.CsvExport
import app.schnarchwaechter.core.LoudMoment
import app.schnarchwaechter.core.MinuteStats
import app.schnarchwaechter.core.NightAverages
import app.schnarchwaechter.core.NightStatsCalculator
import app.schnarchwaechter.core.SnoreEpisode
import app.schnarchwaechter.core.Trends
import app.schnarchwaechter.service.ClipEncoder
import app.schnarchwaechter.util.BatteryState
import java.io.File
import java.io.OutputStream
import java.time.ZoneId

/** Everything a night detail screen needs. */
data class NightDetail(
    val night: NightEntity,
    val minutes: List<MinuteStats>,
    val episodes: List<SnoreEpisode>,
    val pauses: List<PauseEntity>,
    val loudest: List<LoudMoment>,
    val previousWeek: NightAverages?,
)

class NightRepository(context: Context, db: AppDatabase) {
    val dao = db.nightDao()
    private val clipDir = File(context.filesDir, "clips").apply { mkdirs() }

    suspend fun startNight(startMs: Long, sampleRate: Int, settings: AppSettings, battery: BatteryState): Long =
        dao.insertNight(
            NightEntity(
                startMs = startMs, sampleRate = sampleRate, sensitivity = settings.sensitivity,
                calibrationOffsetDb = settings.calibrationOffsetDb, batteryStartPct = battery.percent,
                chargingAtStart = battery.charging,
            ),
        )

    suspend fun saveMinutes(nightId: Long, minutes: List<MinuteStats>) =
        dao.insertMinutes(minutes.map { MinuteEntity(nightId, it.startMs, it.avgDb, it.maxDb, it.floorDb) })

    suspend fun saveEpisode(nightId: Long, e: SnoreEpisode) {
        dao.insertEpisode(EpisodeEntity(0, nightId, e.startMs, e.endMs, e.burstCount, e.maxDb, e.meanDb, e.maxAboveFloorDb, e.pauseCount))
    }

    suspend fun savePause(nightId: Long, p: BreathingPause) {
        dao.insertPause(PauseEntity(0, nightId, p.startMs, p.endMs, p.precedingAboveFloorDb))
    }

    /** Compresses and stores a clip. Returns its id, or null if it could not be written. */
    suspend fun saveClip(nightId: Long, req: ClipRequest): Long? {
        val base = "night${nightId}_${req.startMs}"
        var file = File(clipDir, "$base.m4a")
        if (!ClipEncoder.writeM4a(req.pcm, req.sampleRate, file)) {
            file = File(clipDir, "$base.wav")
            if (!ClipEncoder.writeWav(req.pcm, req.sampleRate, file)) return null
        }
        val id = dao.insertClip(
            ClipEntity(
                nightId = nightId, triggerMs = req.candidate.triggerMs, startMs = req.startMs,
                durationMs = req.pcm.size * 1000L / req.sampleRate, reason = req.candidate.reason.name,
                score = req.candidate.score, value = req.candidate.value, filePath = file.absolutePath,
                sizeBytes = file.length(),
            ),
        )
        req.replaceId?.let { dao.clip(it)?.let { old -> deleteClip(old) } }
        return id
    }

    /** Computes the summary and closes the night. */
    suspend fun finishNight(
        nightId: Long,
        endMs: Long,
        battery: BatteryState?,
        appCpuMs: Long?,
        interrupted: Boolean = false,
    ): NightEntity {
        val night = dao.night(nightId) ?: error("night $nightId missing")
        val minutes = dao.minutes(nightId).map { it.toStats() }
        val episodes = dao.episodes(nightId).map { it.toEpisode() }
        val pauses = dao.pauses(nightId).size
        val clips = dao.clips(nightId).size
        val s = NightStatsCalculator.summarize(night.startMs, endMs, minutes, episodes, pauses, clips)
        val done = night.copy(
            endMs = endMs, episodeCount = s.episodeCount, snoreMs = s.snoreMs, avgDb = s.avgDb, maxDb = s.maxDb,
            avgAboveFloorDb = s.avgAboveFloorDb, maxAboveFloorDb = s.maxAboveFloorDb, pauseCount = s.pauseCount,
            clipCount = s.clipCount, batteryEndPct = battery?.percent, chargingAtEnd = battery?.charging ?: false,
            appCpuMs = appCpuMs, interrupted = interrupted,
        )
        dao.updateNight(done)
        return done
    }

    /**
     * Nights that were never finished (app killed by the system, phone switched off) are closed at
     * the time of their last saved minute and marked as interrupted.
     */
    suspend fun recoverInterruptedNights(startedBeforeMs: Long) {
        for (night in dao.openNights()) {
            if (night.startMs >= startedBeforeMs) continue
            val last = dao.lastMinuteStart(night.id)
            finishNight(night.id, (last ?: night.startMs) + 60_000, null, null, interrupted = true)
        }
    }

    suspend fun loadDetail(nightId: Long): NightDetail? {
        val night = dao.night(nightId) ?: return null
        val minutes = dao.minutes(nightId).map { it.toStats() }
        val all = dao.finishedNights().map { it.toSummary() }
        return NightDetail(
            night = night,
            minutes = minutes,
            episodes = dao.episodes(nightId).map { it.toEpisode() },
            pauses = dao.pauses(nightId),
            loudest = NightStatsCalculator.loudestMoments(minutes),
            previousWeek = Trends.previousWeek(night.toSummary(), all),
        )
    }

    suspend fun deleteClip(clip: ClipEntity) {
        File(clip.filePath).delete()
        dao.deleteClip(clip.id)
        dao.refreshClipCount(clip.nightId)
    }

    suspend fun deleteNight(nightId: Long) {
        dao.clips(nightId).forEach { File(it.filePath).delete() }
        dao.deleteNight(nightId)
    }

    suspend fun deleteAll() {
        dao.allClips().forEach { File(it.filePath).delete() }
        dao.deleteAllNights()
        clipDir.listFiles()?.forEach { it.delete() }
    }

    /** Deletes clips older than [retentionDays] days. */
    suspend fun cleanupOldClips(retentionDays: Int) {
        val before = System.currentTimeMillis() - retentionDays * 24L * 3_600_000L
        dao.clipsOlderThan(before).forEach { deleteClip(it) }
    }

    suspend fun exportNightsCsv(out: OutputStream) {
        val nights = dao.finishedNights().sortedBy { it.startMs }.map { it.toSummary() }
        out.write(CsvExport.nights(nights, ZoneId.systemDefault()).toByteArray(Charsets.UTF_8))
    }

    suspend fun exportMinutesCsv(out: OutputStream) {
        val starts = dao.finishedNights().associate { it.id to it.startMs }
        val rows = dao.allMinutes().mapNotNull { m -> starts[m.nightId]?.let { it to m.toStats() } }
        out.write(CsvExport.minutes(rows, ZoneId.systemDefault()).toByteArray(Charsets.UTF_8))
    }
}
