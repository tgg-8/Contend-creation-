package app.schnarchwaechter.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * CSV export with ';' as separator and ',' as decimal mark, so German Excel/LibreOffice opens it
 * directly. All dB values are relative (dBFS or dB above the noise floor), see README.
 */
object CsvExport {
    private val dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun nights(nights: List<NightSummary>, zone: ZoneId): String = buildString {
        appendLine("Beginn;Ende;Dauer_min;Episoden;Schnarchen_min;Schnarchanteil_Prozent;Durchschnitt_dB_ueber_Grundrauschen;Max_dB_ueber_Grundrauschen;Durchschnitt_dBFS;Max_dBFS;Auffaellige_Stillen;Clips")
        for (n in nights) {
            appendLine(
                listOf(
                    time(n.startMs, zone), time(n.endMs, zone), num(n.durationMs / 60_000.0, 0),
                    n.episodeCount.toString(), num(n.snoreMs / 60_000.0, 1), num(n.snoreShare * 100, 1),
                    num(n.avgAboveFloorDb, 1), num(n.maxAboveFloorDb, 1), num(n.avgDb, 1), num(n.maxDb, 1),
                    n.pauseCount.toString(), n.clipCount.toString(),
                ).joinToString(";"),
            )
        }
    }

    fun minutes(rows: List<Pair<Long, MinuteStats>>, zone: ZoneId): String = buildString {
        appendLine("Nacht_Beginn;Minute;Durchschnitt_dBFS;Max_dBFS;Grundrauschen_dBFS;Durchschnitt_dB_ueber_Grundrauschen")
        for ((nightStart, m) in rows) {
            appendLine(
                listOf(
                    time(nightStart, zone), time(m.startMs, zone), num(m.avgDb, 1), num(m.maxDb, 1),
                    num(m.floorDb, 1), num(m.avgDb - m.floorDb, 1),
                ).joinToString(";"),
            )
        }
    }

    private fun time(ms: Long, zone: ZoneId) = dateTime.format(Instant.ofEpochMilli(ms).atZone(zone))

    private fun num(v: Double, decimals: Int) = String.format(Locale.GERMANY, "%.${decimals}f", v)
}
