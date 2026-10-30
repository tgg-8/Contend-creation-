package app.schnarchwaechter.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.schnarchwaechter.core.MinuteStats
import app.schnarchwaechter.core.NightSummary
import app.schnarchwaechter.core.SnoreEpisode

/** One measured night. The summary columns are filled when the night ends. */
@Entity(tableName = "nights")
data class NightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMs: Long,
    val endMs: Long? = null,
    val sampleRate: Int,
    val sensitivity: Int,
    /** Calibration offset (dBFS -> approx. dB) at the time of the measurement, null = uncalibrated. */
    val calibrationOffsetDb: Double? = null,
    val episodeCount: Int = 0,
    val snoreMs: Long = 0,
    val avgDb: Double = 0.0,
    val maxDb: Double = 0.0,
    val avgAboveFloorDb: Double = 0.0,
    val maxAboveFloorDb: Double = 0.0,
    val pauseCount: Int = 0,
    val clipCount: Int = 0,
    val batteryStartPct: Int? = null,
    val batteryEndPct: Int? = null,
    val chargingAtStart: Boolean = false,
    val chargingAtEnd: Boolean = false,
    /** CPU time the app process used during the night (measured by the app itself). */
    val appCpuMs: Long? = null,
    /** True if the app was ended by the system before "Nacht beenden" was pressed. */
    val interrupted: Boolean = false,
) {
    fun toSummary() = NightSummary(
        startMs, endMs ?: startMs, episodeCount, snoreMs, avgDb, maxDb, avgAboveFloorDb, maxAboveFloorDb,
        pauseCount, clipCount,
    )
}

@Entity(
    tableName = "minutes",
    primaryKeys = ["nightId", "startMs"],
    foreignKeys = [ForeignKey(entity = NightEntity::class, parentColumns = ["id"], childColumns = ["nightId"], onDelete = ForeignKey.CASCADE)],
)
data class MinuteEntity(
    val nightId: Long,
    val startMs: Long,
    val avgDb: Double,
    val maxDb: Double,
    val floorDb: Double,
) {
    fun toStats() = MinuteStats(startMs, avgDb, maxDb, floorDb)
}

@Entity(
    tableName = "episodes",
    indices = [Index("nightId")],
    foreignKeys = [ForeignKey(entity = NightEntity::class, parentColumns = ["id"], childColumns = ["nightId"], onDelete = ForeignKey.CASCADE)],
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nightId: Long,
    val startMs: Long,
    val endMs: Long,
    val burstCount: Int,
    val maxDb: Double,
    val meanDb: Double,
    val maxAboveFloorDb: Double,
    val pauseCount: Int,
) {
    fun toEpisode() = SnoreEpisode(startMs, endMs, burstCount, maxDb, meanDb, maxAboveFloorDb, pauseCount)
}

/** Noticeable silences after loud snoring ("Atempause?"). */
@Entity(
    tableName = "pauses",
    indices = [Index("nightId")],
    foreignKeys = [ForeignKey(entity = NightEntity::class, parentColumns = ["id"], childColumns = ["nightId"], onDelete = ForeignKey.CASCADE)],
)
data class PauseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nightId: Long,
    val startMs: Long,
    val endMs: Long,
    val precedingAboveFloorDb: Double,
)

@Entity(
    tableName = "clips",
    indices = [Index("nightId")],
    foreignKeys = [ForeignKey(entity = NightEntity::class, parentColumns = ["id"], childColumns = ["nightId"], onDelete = ForeignKey.CASCADE)],
)
data class ClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nightId: Long,
    val triggerMs: Long,
    val startMs: Long,
    val durationMs: Long,
    /** LOUD, LONG_EPISODE or PAUSE */
    val reason: String,
    val score: Double,
    val value: Double,
    val filePath: String,
    val sizeBytes: Long,
)
