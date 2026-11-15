package app.mono.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mono.launcher.data.CalendarEvent
import app.mono.launcher.data.CalendarRepo
import app.mono.launcher.data.Prefs
import app.mono.launcher.data.ScreenTime
import app.mono.launcher.data.Weather
import app.mono.launcher.data.WeatherInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/**
 * Startbildschirm (von oben nach unten): Uhr, Datum, Jahres-/Tagesbalken,
 * Akku + Bildschirmzeit + Wetter, nächster Termin, unten die 5 Favoriten.
 *
 * Gesten: Wischen nach rechts → App-Liste, lange auf freie Fläche drücken → Einstellungen.
 */
@Composable
fun HomeScreen(
    prefs: Prefs,
    favoriteLabels: List<String>,
    refreshTick: Int,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onFavoriteClick: (Int) -> Unit,
    onFavoriteLongPress: (Int) -> Unit,
    onRequestLocation: () -> Unit,
    background: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val settings = prefs.settings
    val german = settings.germanClock

    // Auf dem Startbildschirm tut "Zurück" nichts (sonst würde sich der Launcher schließen).
    BackHandler(enabled = true) {}

    // Aktuelle Zeit, minutengenau; beim Zurückkehren (refreshTick) sofort aktualisiert.
    val now by produceState(System.currentTimeMillis(), refreshTick) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000 - value % 60_000 + 50)
        }
    }

    val battery = rememberBatteryPercent(context)

    var screenTimeMs by remember { mutableStateOf<Long?>(null) }
    var hasUsageAccess by remember { mutableStateOf(true) }
    var nextEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var weather by remember { mutableStateOf<WeatherInfo?>(Weather.cached(prefs)) }
    var hasLocation by remember { mutableStateOf(Weather.hasPermission(context)) }

    // Bildschirmzeit + Termin: beim Öffnen und jede Minute neu lesen (läuft im Hintergrund-Thread).
    LaunchedEffect(refreshTick, now) {
        withContext(Dispatchers.IO) {
            hasUsageAccess = ScreenTime.hasAccess(context)
            screenTimeMs = ScreenTime.todayMillis(context)
            nextEvent = CalendarRepo.nextEvent(context)
        }
    }
    // Wetter: höchstens alle 30 Minuten wirklich aus dem Netz (Cache in Weather).
    LaunchedEffect(refreshTick, now / (10 * 60_000)) {
        hasLocation = Weather.hasPermission(context)
        Weather.refresh(context, prefs)?.let { weather = it }
    }

    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    val openDrawer by rememberUpdatedState(onOpenDrawer)
    val openSettings by rememberUpdatedState(onOpenSettings)

    Box(
        Modifier
            .fillMaxSize()
            // Reihenfolge wichtig: zuerst Long-Press, dann Wischen (Wischen bricht Long-Press ab).
            .pointerInput(Unit) { detectTapGestures(onLongPress = { openSettings() }) }
            .pointerInput(Unit) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = { if (dx > swipeThreshold) openDrawer() },
                    onHorizontalDrag = { change, amount -> dx += amount; change.consume() },
                )
            }
    ) {
        background()

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 32.dp)
        ) {
            Spacer(Modifier.height(48.dp))
            T(Fmt.time(now, german), size = 64.sp, weight = FontWeight.Light, maxLines = 1)
            T(Fmt.date(now, german), size = 20.sp, alpha = 0.9f, maxLines = 1)

            Spacer(Modifier.height(36.dp))
            val (year, day) = progress(now)
            T("Jahr im Gange ${(year * 100).toInt()}%", size = 16.sp)
            T(Fmt.bar(year), size = 16.sp, mono = true, maxLines = 1)
            Spacer(Modifier.height(12.dp))
            T("Tag im Gange ${(day * 100).toInt()}%", size = 16.sp)
            T(Fmt.bar(day), size = 16.sp, mono = true, maxLines = 1)

            Spacer(Modifier.height(28.dp))
            Row {
                if (battery >= 0) T("$battery%", size = 18.sp)
                Spacer(Modifier.width(16.dp))
                // Bildschirmzeit: Tippen öffnet Digital Wellbeing bzw. die Freigabe-Seite.
                val st = screenTimeMs
                if (hasUsageAccess && st != null) {
                    T(ScreenTime.format(st), size = 18.sp, modifier = Modifier.tap { ScreenTime.openDashboard(context) })
                } else {
                    T("Zeit ?", size = 18.sp, alpha = 0.6f, modifier = Modifier.tap { ScreenTime.openUsageAccessSettings(context) })
                }
                Spacer(Modifier.width(16.dp))
                val w = weather
                when {
                    !hasLocation -> T("Wetter ?", size = 18.sp, alpha = 0.6f, modifier = Modifier.tap(onClick = onRequestLocation))
                    w != null -> T("${w.temperatureC}°C ${w.description}", size = 18.sp, maxLines = 1)
                }
            }

            nextEvent?.let { ev ->
                Spacer(Modifier.height(12.dp))
                T(
                    Fmt.event(ev, german, now), size = 18.sp, maxLines = 1,
                    modifier = Modifier.tap { CalendarRepo.open(context, ev) },
                )
            }

            Spacer(Modifier.weight(1f))

            // Favoriten unten, gut mit dem Daumen erreichbar.
            favoriteLabels.forEachIndexed { i, label ->
                T(
                    label, size = 34.sp, maxLines = 1,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .tap(onLongClick = { onFavoriteLongPress(i) }) { onFavoriteClick(i) },
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** Anteil des Jahres und des Tages, der schon vergangen ist (0..1). Berücksichtigt Sommerzeit. */
private fun progress(now: Long): Pair<Float, Float> {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val yearStart = date.withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val yearEnd = date.withDayOfYear(1).plusYears(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return (now - yearStart).toFloat() / (yearEnd - yearStart) to (now - dayStart).toFloat() / (dayEnd - dayStart)
}

/** Akkustand in Prozent, live über den System-Broadcast ACTION_BATTERY_CHANGED. */
@Composable
private fun rememberBatteryPercent(context: Context): Int {
    var percent by remember { mutableIntStateOf(-1) }
    DisposableEffect(Unit) {
        fun read(i: Intent?) {
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            if (level >= 0 && scale > 0) percent = level * 100 / scale
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = read(i)
        }
        read(context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    return percent
}
