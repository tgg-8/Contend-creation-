package app.schnarchwaechter.core

/**
 * All tunable thresholds of the snore detection in ONE place.
 *
 * Levels are in dB relative to the measured noise floor (the floor is measured at the start of every
 * night and then slowly tracked), so nothing here depends on the absolute loudness of the room or on
 * the (uncalibrated) microphone gain.
 *
 * To tune with real recordings: run `./gradlew :core:analyzeWav --args="night.wav"` and adjust the
 * values below (see TUNING.md).
 */
data class DetectionConfig(
    /** Analysis frame length. 100 ms = 10 cheap level values per second. */
    val frameMs: Int = 100,

    // ---- Stage 1 (always on, very cheap) -------------------------------------------------------
    /** Lower/upper edge of the "snore band" (most snoring energy lies roughly in 100-1000 Hz). */
    val bandLowHz: Double = 100.0,
    val bandHighHz: Double = 1000.0,
    /** Length of the ambient noise measurement at the start of the night. */
    val calibrationSeconds: Int = 30,
    /** Percentile of frame levels that is used as noise floor (robust against short noises). */
    val floorPercentile: Double = 0.2,
    /** The floor is re-estimated every [floorUpdateSeconds] (adapts to fan/heater switching on/off). */
    val floorUpdateSeconds: Int = 60,
    /** The floor may rise at most this much per update (so long snoring does not "become" the floor). */
    val floorMaxRiseDb: Double = 3.0,
    /** A burst starts when the band level is this many dB above the band floor ... */
    val burstOnsetDb: Double = 8.0,
    /** ... and ends when it drops below this (hysteresis). */
    val burstReleaseDb: Double = 5.0,

    // ---- Stage 2 (only runs for bursts found by stage 1) ---------------------------------------
    /** Share of the burst's excess energy that must lie inside the snore band. */
    val minBandRatio: Double = 0.45,
    /** Spectral centroid of the burst (above floor spectrum) must be below this. */
    val maxCentroidHz: Double = 1200.0,
    /** Plausible duration of a single snore sound (one inhalation). */
    val minBurstSec: Double = 0.3,
    val maxBurstSec: Double = 3.5,
    /** Breathing rhythm: time between the starts of consecutive snores. */
    val minRhythmSec: Double = 2.0,
    val maxRhythmSec: Double = 9.0,
    /** Max. coefficient of variation of the intervals (0 = metronome, 1 = chaos). */
    val maxRhythmCv: Double = 0.45,
    /** Number of rhythmic snores needed before an episode is confirmed. */
    val burstsToConfirm: Int = 3,

    // ---- Episodes -----------------------------------------------------------------------------
    /** An episode ends when no snore was heard for this long. */
    val episodeGapSec: Double = 30.0,
    /** Shorter episodes are discarded. */
    val minEpisodeSec: Double = 20.0,

    // ---- Noticeable moments (clips) -------------------------------------------------------------
    /** A silence of at least this length directly after loud snoring is reported as "breathing pause". */
    val pauseMinSec: Double = 10.0,
    /** The snoring before such a pause must be at least this loud (above floor). */
    val pauseLoudDb: Double = 12.0,
    /** Snore bursts at least this much above the floor are candidates for a "very loud" clip. */
    val loudClipDb: Double = 25.0,
    /** Episodes longer than this trigger a "long episode" clip (once per episode). */
    val longEpisodeMinutes: Double = 20.0,
) {
    companion object {
        const val DEFAULT_SENSITIVITY = 3

        /** Maps the user-facing sensitivity 1 (low) .. 5 (high) to concrete thresholds. */
        fun forSensitivity(level: Int): DetectionConfig {
            val l = level.coerceIn(1, 5)
            val onset = doubleArrayOf(12.0, 10.0, 8.0, 6.5, 5.0)[l - 1]
            return DetectionConfig(
                burstOnsetDb = onset,
                burstReleaseDb = onset - 3.0,
                minBandRatio = doubleArrayOf(0.60, 0.52, 0.45, 0.40, 0.35)[l - 1],
                maxRhythmCv = doubleArrayOf(0.35, 0.40, 0.45, 0.50, 0.55)[l - 1],
                maxCentroidHz = doubleArrayOf(1000.0, 1100.0, 1200.0, 1400.0, 1600.0)[l - 1],
            )
        }
    }
}

/** Settings of the clip recorder. */
data class ClipConfig(
    val maxClipsPerNight: Int = 6,
    val preRollSec: Double = 12.0,
    val postRollSec: Double = 5.0,
    val maxClipSec: Double = 25.0,
    /** Minimum time between two clips, so one loud phase does not use up all clips. */
    val cooldownSec: Double = 120.0,
)
