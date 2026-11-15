package app.mono.launcher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Instances

/** Ein einzelner Termin (bei Serien: eine konkrete Wiederholung). */
data class CalendarEvent(val eventId: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean)

/**
 * Liest Termine aus dem Android-Kalenderspeicher (dort landen auch die Google-Kalender-Termine,
 * die auf dem Gerät synchronisiert werden). Nur lesend, nichts verlässt das Gerät.
 */
object CalendarRepo {
    private const val DAY = 24 * 60 * 60 * 1000L

    private val PROJECTION = arrayOf(
        Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY,
    )

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /**
     * Nächster Termin der kommenden 7 Tage, der noch nicht vorbei ist.
     * Ganztägige und abgelehnte Termine werden übersprungen, ebenso ausgeblendete Kalender.
     */
    fun nextEvent(context: Context): CalendarEvent? {
        if (!hasPermission(context)) return null
        val now = System.currentTimeMillis()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now)
            ContentUris.appendId(it, now + 7 * DAY)
        }.build()
        val selection = "${Instances.ALL_DAY} = 0 AND ${Instances.VISIBLE} = 1 AND " +
            "${Instances.SELF_ATTENDEE_STATUS} != ${Attendees.ATTENDEE_STATUS_DECLINED} AND ${Instances.END} > ?"
        return runCatching {
            context.contentResolver.query(uri, PROJECTION, selection, arrayOf(now.toString()), "${Instances.BEGIN} ASC")
                ?.use { c -> if (c.moveToFirst()) read(c) else null }
        }.getOrNull()
    }

    /** Volltextsuche in Terminen (30 Tage zurück bis 1 Jahr voraus). */
    fun search(context: Context, query: String, limit: Int = 5): List<CalendarEvent> {
        if (!hasPermission(context) || query.isBlank()) return emptyList()
        val now = System.currentTimeMillis()
        val uri = Instances.CONTENT_SEARCH_URI.buildUpon().also {
            ContentUris.appendId(it, now - 30 * DAY)
            ContentUris.appendId(it, now + 365 * DAY)
            it.appendPath(query)
        }.build()
        val out = ArrayList<CalendarEvent>()
        runCatching {
            context.contentResolver.query(uri, PROJECTION, "${Instances.VISIBLE} = 1", null, "${Instances.BEGIN} ASC")
                ?.use { c ->
                    // Kommende Termine zuerst, danach vergangene.
                    val all = ArrayList<CalendarEvent>()
                    while (c.moveToNext()) all += read(c)
                    val (future, past) = all.partition { it.end > now }
                    out += (future + past.reversed()).distinctBy { it.eventId to it.begin }.take(limit)
                }
        }
        return out
    }

    private fun read(c: android.database.Cursor) = CalendarEvent(
        eventId = c.getLong(0),
        title = c.getString(1)?.ifBlank { null } ?: "(Ohne Titel)",
        begin = c.getLong(2),
        end = c.getLong(3),
        allDay = c.getInt(4) == 1,
    )

    /** Öffnet den Termin in der Kalender-App. */
    fun open(context: Context, event: CalendarEvent) {
        val intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
