package app.mono.launcher.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.mono.launcher.data.CalendarEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Einheitlicher weißer Text, unser einziges Text-Element (kein Material-Design nötig). */
@Composable
fun T(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 18.sp,
    alpha: Float = 1f,
    weight: FontWeight = FontWeight.Normal,
    mono: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = Color.White.copy(alpha = alpha),
            fontSize = size,
            fontWeight = weight,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        softWrap = maxLines != 1,
    )
}

/** Antippen/gedrückt halten ohne Ripple-Effekt (ruhiges Schwarz-Weiß-Design). */
fun Modifier.tap(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier =
    this.combinedClickable(
        interactionSource = null,
        indication = null,
        onLongClick = onLongClick,
        onClick = onClick,
    )

/** Datum/Uhrzeit-Formatierung: Englisch 12 h (Standard) oder Deutsch 24 h. */
object Fmt {
    private val zone get() = ZoneId.systemDefault()

    fun time(ms: Long, german: Boolean): String {
        val f = if (german) DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
        else DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        return f.format(Instant.ofEpochMilli(ms).atZone(zone))
    }

    /** "Sunday, 15th November" bzw. "Sonntag, 15. November" */
    fun date(ms: Long, german: Boolean): String {
        val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        return if (german) {
            DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN).format(d)
        } else {
            val day = d.dayOfMonth
            val suffix = if (day in 11..13) "th" else when (day % 10) {
                1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th"
            }
            DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH).format(d) + ", " + day + suffix + " " +
                DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH).format(d)
        }
    }

    /** "9:30 PM Visualisierung", an anderen Tagen mit Wochentag davor ("Mon 9:30 AM …"). */
    fun event(e: CalendarEvent, german: Boolean, now: Long): String {
        val start = Instant.ofEpochMilli(e.begin).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day = start.toLocalDate()
        val prefix = when {
            day == today -> ""
            day == today.plusDays(1) -> if (german) "Morgen " else "Tomorrow "
            else -> DateTimeFormatter.ofPattern(if (german) "EE " else "EEE ", if (german) Locale.GERMAN else Locale.ENGLISH).format(day)
        }
        return prefix + time(e.begin, german) + " " + e.title
    }

    /** Für Suchergebnisse: "Mo., 17. Nov. 9:30" bzw. "Mon, 17 Nov 9:30 AM". */
    fun eventLong(e: CalendarEvent, german: Boolean): String {
        val d: LocalDate = Instant.ofEpochMilli(e.begin).atZone(zone).toLocalDate()
        val datePart = if (german) DateTimeFormatter.ofPattern("EE, d. MMM", Locale.GERMAN).format(d)
        else DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH).format(d)
        return if (e.allDay) datePart else datePart + " " + time(e.begin, german)
    }

    /** Textbalken: ▓ gefüllt, ░ leer. */
    fun bar(fraction: Float, blocks: Int = 20): String {
        val filled = (fraction.coerceIn(0f, 1f) * blocks).toInt()
        return "▓".repeat(filled) + "░".repeat(blocks - filled)
    }
}
