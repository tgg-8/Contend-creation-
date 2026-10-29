package app.claudering

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/** Baut den kurzen, freundlichen Text für die Sprechblase. */
object BubbleText {

    private val plenty = listOf(
        "Du hast noch richtig viel Luft. Leg los! 🚀",
        "Alles entspannt, frag ruhig drauflos.",
        "Volle Kraft voraus, Kapitän! ⛵",
    )
    private val medium = listOf(
        "Läuft gut! Vielleicht ein bisschen bündeln. 🧺",
        "Halbzeit-Snack? Du machst das super.",
        "Kurze, klare Prompts sparen Nutzung. ✨",
    )
    private val low = listOf(
        "Jetzt lieber die wichtigen Fragen zuerst. 💛",
        "Fast leer. Gönn dir eine kleine Pause. ☕",
        "Sparflamme an. Bald ist wieder aufgeladen!",
    )

    fun build(usage: Usage?, now: Long = System.currentTimeMillis()): String {
        if (usage == null) {
            return "Hi! 👋\nTrag in der App deine Nutzung ein,\ndann zeig ich sie dir hier."
        }
        if (usage.isReset(now)) {
            return "Frisch aufgeladen! ✨\nDein Limit sollte zurückgesetzt sein.\nTrag kurz den neuen Stand ein."
        }

        val percent = usage.percentLeft
        val (emoji, quotes) = when {
            percent >= 50 -> "🌱" to plenty
            percent >= 20 -> "🌼" to medium
            else -> "🍂" to low
        }
        val quote = quotes[((now / 60_000L) % quotes.size).toInt()]

        return buildString {
            append("Noch $percent % übrig $emoji\n")
            append("Reset in ${formatDuration(usage.resetAt - now)} (${formatTime(usage.resetAt, now)})\n")
            append(quote)
            append("\nStand von ${clock(usage.savedAt)}")
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalMinutes = ceil(ms / 60_000.0).toLong().coerceAtLeast(1)
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60
        return when {
            days > 0 -> "$days T $hours Std"
            hours > 0 -> "$hours Std $minutes Min"
            else -> "$minutes Min"
        }
    }

    private fun formatTime(at: Long, now: Long): String {
        val pattern = if (at - now > 20 * 60 * 60 * 1000L) "EE, HH:mm" else "HH:mm"
        return SimpleDateFormat(pattern, Locale.GERMANY).format(Date(at))
    }

    private fun clock(at: Long) = SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(at))
}
