package app.mono.launcher.data

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.Settings
import java.util.Calendar

/**
 * Heutige Bildschirmzeit aus den offiziellen Nutzungsdaten (UsageStatsManager).
 *
 * Methode: Wir gehen alle Ereignisse seit Mitternacht durch und zählen die Zeit, in der
 * mindestens eine App-Activity im Vordergrund war (ACTIVITY_RESUMED bis ACTIVITY_PAUSED).
 * Bildschirm aus beendet jede laufende Zählung. Der Launcher selbst zählt nicht mit,
 * so wie Digital Wellbeing den Startbildschirm nicht als App-Nutzung zählt.
 * Digital Wellbeing hat eigene, nicht veröffentlichte Regeln. Kleine Abweichungen
 * (meist wenige Minuten) sind daher möglich.
 */
object ScreenTime {
    const val WELLBEING_PACKAGE = "com.google.android.apps.wellbeing"

    fun hasAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /** Bildschirmzeit heute in Millisekunden, oder null ohne Berechtigung. */
    fun todayMillis(context: Context): Long? {
        if (!hasAccess(context)) return null
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(start, end) ?: return 0L

        val list = ArrayList<UsageEvent>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            list += UsageEvent(e.eventType, e.packageName, e.className ?: "", e.timeStamp)
        }
        return foregroundMillis(list, start, end, context.packageName)
    }

    /** Ein Nutzungsereignis in einfacher Form (damit die Berechnung testbar ist). */
    data class UsageEvent(val type: Int, val packageName: String, val className: String, val time: Long)

    /**
     * Zeit im Bereich [start, end], in der mindestens eine fremde App-Activity sichtbar war.
     * Überlappungen (z. B. geteilter Bildschirm) werden nur einmal gezählt.
     */
    fun foregroundMillis(events: List<UsageEvent>, start: Long, end: Long, ownPackage: String): Long {
        val resumed = HashSet<String>()   // Activities, die gerade im Vordergrund sind
        val seen = HashSet<String>()      // Activities, die heute schon ein Ereignis hatten
        var activeSince = 0L
        var total = 0L

        for (e in events) {
            val t = e.time
            when (e.type) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (e.packageName == ownPackage) continue
                    val k = e.packageName + "/" + e.className
                    seen += k
                    if (resumed.isEmpty()) activeSince = t
                    resumed += k
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (e.packageName == ownPackage) continue
                    val k = e.packageName + "/" + e.className
                    if (resumed.remove(k)) {
                        if (resumed.isEmpty()) total += t - activeSince
                    } else if (k !in seen && total == 0L && resumed.isEmpty()) {
                        // App lief schon vor Mitternacht: ab Mitternacht zählen.
                        total += t - start
                    }
                    seen += k
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> {
                    if (resumed.isNotEmpty()) {
                        total += t - activeSince
                        resumed.clear()
                    }
                }
            }
        }
        if (resumed.isNotEmpty()) total += end - activeSince
        return total.coerceAtLeast(0)
    }

    /** "27m", "1h 5m" */
    fun format(ms: Long): String {
        val minutes = ms / 60_000
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    /** Öffnet die Systemseite, auf der man "Nutzungszugriff" für diese App erlaubt. */
    fun openUsageAccessSettings(context: Context) {
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        val list = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        for (i in listOf(direct, list)) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
            } catch (_: Exception) { }
        }
    }

    /**
     * Öffnet die Bildschirmzeit-Übersicht von Digital Wellbeing.
     * Google bietet dafür KEINEN offiziellen Intent. Reihenfolge:
     *  1. Die Hauptseite von Digital Wellbeing (inoffiziell bekannter Klassenname auf Pixel-Geräten,
     *     zeigt oben das Bildschirmzeit-Diagramm). Kann Google jederzeit ändern.
     *  2. Der App-Eintrag von Digital Wellbeing, falls "Symbol in App-Liste anzeigen" aktiv ist.
     *  3. Die Android-Einstellungen (dort: "Digital Wellbeing & Jugendschutz").
     */
    fun openDashboard(context: Context) {
        val candidates = listOfNotNull(
            Intent().setComponent(
                ComponentName(WELLBEING_PACKAGE, "$WELLBEING_PACKAGE.settings.TopLevelSettingsActivity")
            ),
            context.packageManager.getLaunchIntentForPackage(WELLBEING_PACKAGE),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (i in candidates) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
            } catch (_: Exception) { }
        }
    }
}
