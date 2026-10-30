package app.schnarchwaechter.ui.screens

import android.media.MediaPlayer
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.schnarchwaechter.data.AppSettings
import app.schnarchwaechter.data.ClipEntity
import app.schnarchwaechter.data.NightDetail
import app.schnarchwaechter.ui.AppViewModel
import app.schnarchwaechter.ui.BodyText
import app.schnarchwaechter.ui.Disclaimer
import app.schnarchwaechter.ui.InfoCard
import app.schnarchwaechter.ui.LegendItem
import app.schnarchwaechter.ui.StatTile
import app.schnarchwaechter.ui.TileRow
import app.schnarchwaechter.ui.theme.AppColors
import app.schnarchwaechter.util.Fmt
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun NightDetailScreen(nightId: Long, vm: AppViewModel, onBack: () -> Unit) {
    var detail by remember(nightId) { mutableStateOf<NightDetail?>(null) }
    var loaded by remember(nightId) { mutableStateOf(false) }
    LaunchedEffect(nightId) {
        detail = vm.detail(nightId)
        loaded = true
    }
    val clips by remember(nightId) { vm.clips(nightId) }.collectAsStateWithLifecycle(emptyList())
    val settings by vm.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    val player = remember { ClipPlayer() }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = AppColors.Text) }
            Text(
                detail?.let { Fmt.dateLong(it.night.startMs) } ?: "Nacht",
                color = AppColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Nacht löschen", tint = AppColors.Muted) }
        }
        TabRow(selectedTabIndex = tab, containerColor = AppColors.Background, contentColor = AppColors.Primary) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Zusammenfassung") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Zeitverlauf") })
        }
        val d = detail
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                d == null && loaded -> BodyText("Diese Nacht gibt es nicht mehr.", AppColors.Muted)
                d == null -> BodyText("Lade …", AppColors.Muted)
                tab == 0 -> Summary(d, clips, settings, player, onDeleteClip = vm::deleteClip)
                else -> Timeline(d, clips)
            }
            Disclaimer()
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    player.stop()
                    vm.deleteNight(nightId, onBack)
                }) { Text("Löschen", color = AppColors.Bad) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
            title = { Text("Nacht löschen?") },
            text = { Text("Alle Werte und Clips dieser Nacht werden endgültig gelöscht.") },
        )
    }
}

@Composable
private fun Summary(d: NightDetail, clips: List<ClipEntity>, settings: AppSettings, player: ClipPlayer, onDeleteClip: (ClipEntity) -> Unit) {
    val n = d.night
    val end = n.endMs ?: n.startMs
    val duration = end - n.startMs
    val share = if (duration > 0) n.snoreMs.toDouble() / duration else 0.0
    val offset = n.calibrationOffsetDb

    if (n.interrupted) {
        InfoCard(title = "Messung unterbrochen", accent = AppColors.Warning) {
            BodyText(
                "Android hat die App vor dem Beenden geschlossen (oft wegen Akku-Optimierung). Die Werte reichen " +
                    "bis etwa ${Fmt.time(end)}. Tipp: Akku-Optimierung für die App ausschalten (Startbildschirm).",
            )
        }
    }
    TileRow {
        StatTile("Messdauer", Fmt.duration(duration), Modifier.weight(1f), sub = "${Fmt.time(n.startMs)} – ${Fmt.time(end)}")
        StatTile("Schnarch-Episoden", n.episodeCount.toString(), Modifier.weight(1f))
    }
    TileRow {
        StatTile("Schnarchen gesamt", Fmt.minutes(n.snoreMs), Modifier.weight(1f), sub = "${Fmt.percent(share)} der Nacht")
        StatTile(
            "Lautstärke", Fmt.relDb(n.avgAboveFloorDb), Modifier.weight(1f),
            sub = "Durchschnitt · max ${Fmt.relDb(n.maxAboveFloorDb)}" +
                (Fmt.calibratedDb(n.maxDb, offset)?.let { " ($it)" } ?: ""),
        )
    }

    if (d.loudest.isNotEmpty() && n.maxAboveFloorDb > 0) {
        InfoCard(title = "Lauteste Zeitpunkte") {
            d.loudest.sortedByDescending { it.aboveFloorDb }.forEach {
                val cal = Fmt.calibratedDb(it.maxDb, offset)?.let { c -> " · $c" } ?: ""
                BodyText("${Fmt.time(it.timeMs)} Uhr  ·  ${Fmt.relDb(it.aboveFloorDb)}$cal")
            }
        }
    }

    if (n.pauseCount > 0) {
        InfoCard(title = "Auffällige Stillen: ${n.pauseCount}", accent = AppColors.Warning) {
            BodyText(
                "${n.pauseCount}-mal folgte auf lautes Schnarchen eine Stille von mindestens 10 Sekunden, danach ging " +
                    "das Schnarchen weiter. Das kann eine Atempause sein, aber auch harmlos (z. B. Umdrehen, leises Atmen). " +
                    "Die App kann das nicht sicher unterscheiden.",
            )
            BodyText(
                "Wenn das öfter vorkommt oder du tagsüber sehr müde bist, lass es ärztlich abklären. " +
                    "Die gespeicherten Clips kannst du dafür vorspielen.",
                AppColors.Muted,
            )
        }
    }

    InfoCard(title = "Audioclips (${clips.size})", accent = AppColors.Secondary) {
        if (clips.isEmpty()) {
            BodyText(
                if (settings.clipsEnabled) "Keine Clips gespeichert – es gab keine besonders auffälligen Momente."
                else "Clips sind in den Einstellungen ausgeschaltet.",
                AppColors.Muted,
            )
        }
        clips.forEach { clip -> ClipRow(clip, player, onDeleteClip) }
        if (clips.isNotEmpty()) {
            BodyText(
                "Clips werden nach ${settings.clipRetentionDays} Tagen automatisch gelöscht (änderbar in den Einstellungen).",
                AppColors.Muted,
            )
        }
    }

    InfoCard(title = "Vergleich mit der Vorwoche") {
        val prev = d.previousWeek
        if (prev == null) {
            BodyText("Noch keine Nächte aus den 7 Tagen davor.", AppColors.Muted)
        } else {
            val diffMin = n.snoreMs / 60_000.0 - prev.snoreMinutes
            val trend = when {
                abs(diffMin) < 5 -> "etwa gleich viel"
                diffMin > 0 -> "${diffMin.roundToInt()} min mehr"
                else -> "${(-diffMin).roundToInt()} min weniger"
            }
            BodyText("Diese Nacht: ${Fmt.minutes(n.snoreMs)} Schnarchen, ${n.episodeCount} Episoden.")
            BodyText(
                "Vorwoche (${prev.nights} ${if (prev.nights == 1) "Nacht" else "Nächte"}): Ø ${Fmt.decimal(prev.snoreMinutes, 0)} min, " +
                    "Ø ${Fmt.decimal(prev.episodes)} Episoden.",
            )
            BodyText("Also $trend als im Schnitt der Vorwoche.", AppColors.Primary)
        }
    }

    InfoCard(title = "Messung & Akku") {
        val bStart = n.batteryStartPct
        val bEnd = n.batteryEndPct
        if (bStart != null && bEnd != null) {
            val charging = if (n.chargingAtStart || n.chargingAtEnd) " (am Ladegerät)" else ""
            BodyText("Akku: $bStart % → $bEnd %$charging")
            if (!n.chargingAtStart && !n.chargingAtEnd && duration > 3_600_000) {
                val perHour = (bStart - bEnd) / (duration / 3_600_000.0)
                BodyText("Das sind etwa ${Fmt.decimal(perHour)} % pro Stunde für das ganze Handy.", AppColors.Muted)
            }
        }
        n.appCpuMs?.let {
            BodyText(
                "Rechenzeit der App: ${Fmt.duration(it)} in ${Fmt.duration(duration)} " +
                    "(${Fmt.decimal(100.0 * it / duration.coerceAtLeast(1), 2)} % einer CPU).",
            )
        }
        BodyText("Abtastrate ${n.sampleRate} Hz · Empfindlichkeit ${n.sensitivity} von 5", AppColors.Muted)
    }

    InfoCard(title = "Was bedeuten die dB-Werte?") {
        BodyText(
            "Handy-Mikrofone sind nicht geeicht. Die App zeigt deshalb die relative Lautstärke: um wie viele dB es " +
                "lauter war als das Grundrauschen deines Zimmers. +10 dB wirkt ungefähr doppelt so laut.",
            AppColors.Muted,
        )
        if (offset != null) {
            BodyText("Werte mit „≈“ nutzen deine Kalibrierung und sind nur grobe Schätzungen.", AppColors.Muted)
        }
    }
}

@Composable
private fun ClipRow(clip: ClipEntity, player: ClipPlayer, onDelete: (ClipEntity) -> Unit) {
    val reason = when (clip.reason) {
        "PAUSE" -> "Stille nach lautem Schnarchen (${clip.value.roundToInt()} s)"
        "LONG_EPISODE" -> "Lange Episode (über ${clip.value.roundToInt()} min)"
        else -> "Sehr laut (${Fmt.relDb(clip.value)})"
    }
    val playing = player.playingId == clip.id
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { player.toggle(clip) }) {
            Icon(
                if (playing) Icons.Filled.Close else Icons.Filled.PlayArrow,
                if (playing) "Stopp" else "Abspielen", tint = AppColors.Secondary,
            )
        }
        Column(Modifier.weight(1f)) {
            Text("${Fmt.time(clip.triggerMs)} Uhr", color = AppColors.Text, fontWeight = FontWeight.Medium)
            Text("$reason · ${(clip.durationMs / 1000.0).roundToInt()} s", color = AppColors.Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = {
            if (playing) player.stop()
            onDelete(clip)
        }) { Icon(Icons.Filled.Delete, "Clip löschen", tint = AppColors.Muted) }
    }
}

@Composable
private fun Timeline(d: NightDetail, clips: List<ClipEntity>) {
    val n = d.night
    InfoCard(title = "Lautstärke im Verlauf der Nacht") {
        NightChart(n.startMs, n.endMs ?: n.startMs, d.minutes, d.episodes, d.pauses, clips)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendItem(AppColors.Secondary, "Ø pro Minute")
            LegendItem(AppColors.Primary.copy(alpha = 0.5f), "Schnarchen")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendItem(AppColors.Warning, "Auffällige Stille")
            LegendItem(AppColors.Secondary, "Clip (▼)")
        }
        BodyText("dB über dem Grundrauschen des Zimmers.", AppColors.Muted)
    }
    InfoCard(title = "Schnarch-Episoden (${d.episodes.size})") {
        if (d.episodes.isEmpty()) BodyText("Kein Schnarchen erkannt.", AppColors.Muted)
        d.episodes.forEach {
            BodyText("${Fmt.time(it.startMs)} – ${Fmt.time(it.endMs)}  ·  ${Fmt.duration(it.durationMs)}  ·  bis ${Fmt.relDb(it.maxAboveFloorDb)}")
        }
    }
    if (d.pauses.isNotEmpty()) {
        InfoCard(title = "Auffällige Stillen (${d.pauses.size})", accent = AppColors.Warning) {
            d.pauses.forEach {
                BodyText("${Fmt.time(it.startMs)} Uhr  ·  ${((it.endMs - it.startMs) / 1000.0).roundToInt()} s Stille")
            }
            BodyText("Keine Diagnose – siehe Hinweis in der Zusammenfassung.", AppColors.Muted)
        }
    }
}

/** Plays one clip at a time from the app's private storage. */
private class ClipPlayer {
    private var mediaPlayer: MediaPlayer? = null
    var playingId by mutableStateOf<Long?>(null)
        private set

    fun toggle(clip: ClipEntity) {
        if (playingId == clip.id) {
            stop()
            return
        }
        stop()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(clip.filePath)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
            playingId = clip.id
        } catch (e: Exception) {
            Log.w("ClipPlayer", "cannot play ${clip.filePath}", e)
            stop()
        }
    }

    fun stop() {
        mediaPlayer?.release()
        mediaPlayer = null
        playingId = null
    }
}
