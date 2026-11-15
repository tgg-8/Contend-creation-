package app.mono.launcher.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class WeatherInfo(val temperatureC: Int, val description: String)

/**
 * Wetter über Open-Meteo (kostenlos, ohne API-Key).
 * Gesendet werden nur auf 2 Nachkommastellen gerundete Koordinaten (~1 km).
 * Ergebnis wird 30 Minuten zwischengespeichert.
 */
object Weather {
    private const val CACHE_MS = 30 * 60 * 1000L

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Zuletzt gespeichertes Wetter, egal wie alt (für sofortige Anzeige). */
    fun cached(prefs: Prefs): WeatherInfo? {
        val sp = prefs.raw
        if (!sp.contains("weather_time")) return null
        return WeatherInfo(sp.getInt("weather_temp", 0), describe(sp.getInt("weather_code", -1)))
    }

    /** Holt das Wetter neu, wenn der Cache älter als 30 Minuten ist. */
    suspend fun refresh(context: Context, prefs: Prefs): WeatherInfo? = withContext(Dispatchers.IO) {
        val sp = prefs.raw
        val age = System.currentTimeMillis() - sp.getLong("weather_time", 0)
        if (age in 0 until CACHE_MS) return@withContext cached(prefs)
        if (!hasPermission(context)) return@withContext cached(prefs)

        val loc = coarseLocation(context) ?: return@withContext cached(prefs)
        try {
            val url = URL(
                String.format(
                    Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code&timezone=auto",
                    loc.latitude, loc.longitude
                )
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 10_000
            }
            val body = try {
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val current = JSONObject(body).getJSONObject("current")
            val temp = current.getDouble("temperature_2m").roundToInt()
            val code = current.getInt("weather_code")
            sp.edit().putInt("weather_temp", temp).putInt("weather_code", code)
                .putLong("weather_time", System.currentTimeMillis()).apply()
            WeatherInfo(temp, describe(code))
        } catch (e: Exception) {
            cached(prefs) // offline o. Ä.: alten Wert behalten
        }
    }

    /** Grober Standort ohne Google-Play-Bibliothek, nur über den System-LocationManager. */
    @SuppressLint("MissingPermission")
    private suspend fun coarseLocation(context: Context): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val last = providers
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.elapsedRealtimeNanos }
        val lastAgeMs = last?.let { (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000 }
        if (last != null && lastAgeMs != null && lastAgeMs < 3 * 60 * 60 * 1000L) return last

        val provider = providers.firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return last
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                try {
                    lm.getCurrentLocation(provider, signal, context.mainExecutor) { loc ->
                        if (cont.isActive) cont.resume(loc ?: last)
                    }
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(last)
                }
            }
        } ?: last
    }

    /** WMO-Wettercodes (von Open-Meteo dokumentiert) auf Deutsch. */
    fun describe(code: Int): String = when (code) {
        0 -> "Klarer Himmel"
        1 -> "Überwiegend klar"
        2 -> "Teilweise bewölkt"
        3 -> "Bedeckt"
        45 -> "Nebel"
        48 -> "Reifnebel"
        51 -> "Leichter Nieselregen"
        53 -> "Nieselregen"
        55 -> "Starker Nieselregen"
        56, 57 -> "Gefrierender Nieselregen"
        61 -> "Leichter Regen"
        63 -> "Regen"
        65 -> "Starker Regen"
        66, 67 -> "Gefrierender Regen"
        71 -> "Leichter Schneefall"
        73 -> "Schneefall"
        75 -> "Starker Schneefall"
        77 -> "Schneegriesel"
        80 -> "Leichte Regenschauer"
        81 -> "Regenschauer"
        82 -> "Heftige Regenschauer"
        85 -> "Leichte Schneeschauer"
        86 -> "Schneeschauer"
        95 -> "Gewitter"
        96, 99 -> "Gewitter mit Hagel"
        else -> ""
    }
}
