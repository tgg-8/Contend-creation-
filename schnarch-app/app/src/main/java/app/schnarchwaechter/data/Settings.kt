package app.schnarchwaechter.data

import android.content.Context
import app.schnarchwaechter.core.ClipConfig
import app.schnarchwaechter.core.DetectionConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppSettings(
    /** 1 = low .. 5 = high */
    val sensitivity: Int = DetectionConfig.DEFAULT_SENSITIVITY,
    val clipsEnabled: Boolean = true,
    val maxClipsPerNight: Int = 6,
    val clipRetentionDays: Int = 30,
    /** dBFS + offset = approximate dB(A); null = not calibrated. */
    val calibrationOffsetDb: Double? = null,
    val morningNotification: Boolean = true,
    /** Dim the screen while the night mode screen is shown. */
    val dimNightScreen: Boolean = true,
    /** Extra partial wake lock (more reliable on some phones, costs almost nothing extra). */
    val extraWakeLock: Boolean = true,
) {
    fun detectionConfig() = DetectionConfig.forSensitivity(sensitivity)
    fun clipConfig() = ClipConfig(maxClipsPerNight = if (clipsEnabled) maxClipsPerNight else 0)
}

/** Small settings store on SharedPreferences (no extra library needed). */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = state

    val current: AppSettings get() = state.value

    fun update(change: (AppSettings) -> AppSettings) {
        val s = change(state.value)
        prefs.edit()
            .putInt("sensitivity", s.sensitivity)
            .putBoolean("clipsEnabled", s.clipsEnabled)
            .putInt("maxClips", s.maxClipsPerNight)
            .putInt("retentionDays", s.clipRetentionDays)
            .putFloat("calibration", s.calibrationOffsetDb?.toFloat() ?: Float.NaN)
            .putBoolean("morningNotification", s.morningNotification)
            .putBoolean("dimNightScreen", s.dimNightScreen)
            .putBoolean("extraWakeLock", s.extraWakeLock)
            .apply()
        state.value = s
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        val cal = prefs.getFloat("calibration", Float.NaN)
        return AppSettings(
            sensitivity = prefs.getInt("sensitivity", d.sensitivity),
            clipsEnabled = prefs.getBoolean("clipsEnabled", d.clipsEnabled),
            maxClipsPerNight = prefs.getInt("maxClips", d.maxClipsPerNight),
            clipRetentionDays = prefs.getInt("retentionDays", d.clipRetentionDays),
            calibrationOffsetDb = if (cal.isNaN()) null else cal.toDouble(),
            morningNotification = prefs.getBoolean("morningNotification", d.morningNotification),
            dimNightScreen = prefs.getBoolean("dimNightScreen", d.dimNightScreen),
            extraWakeLock = prefs.getBoolean("extraWakeLock", d.extraWakeLock),
        )
    }
}
