package app.schnarchwaechter.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** German formatting helpers for the UI. */
object Fmt {
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
    private val date = DateTimeFormatter.ofPattern("EEE, dd.MM.", Locale.GERMAN)
    private val dateLong = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
    private val dayMonth = DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMAN)

    private fun zoned(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

    fun time(ms: Long): String = time.format(zoned(ms))
    fun date(ms: Long): String = date.format(zoned(ms))
    fun dateLong(ms: Long): String = dateLong.format(zoned(ms))
    fun dayMonth(ms: Long): String = dayMonth.format(zoned(ms))

    /** "7 h 05 min", "45 min", "30 s" */
    fun duration(ms: Long): String {
        val totalMin = (ms / 60_000.0).roundToLong()
        return when {
            ms < 60_000 -> "${(ms / 1000.0).roundToInt()} s"
            totalMin < 60 -> "$totalMin min"
            else -> "${totalMin / 60} h ${"%02d".format(totalMin % 60)} min"
        }
    }

    fun minutes(ms: Long): String = "${(ms / 60_000.0).roundToInt()} min"

    fun percent(share: Double): String = String.format(Locale.GERMAN, "%.0f %%", share * 100)

    fun decimal(v: Double, digits: Int = 1): String = String.format(Locale.GERMAN, "%.${digits}f", v)

    /** Relative level, e.g. "+18 dB". */
    fun relDb(db: Double): String = "+${db.roundToInt()} dB"

    /** Approximate absolute level if the user calibrated, e.g. "≈ 52 dB". */
    fun calibratedDb(dbfs: Double, offset: Double?): String? = offset?.let { "≈ ${(dbfs + it).roundToInt()} dB" }
}
