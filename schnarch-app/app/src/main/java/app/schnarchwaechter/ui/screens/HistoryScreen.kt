package app.schnarchwaechter.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.schnarchwaechter.core.Trends
import app.schnarchwaechter.data.NightEntity
import app.schnarchwaechter.ui.AppViewModel
import app.schnarchwaechter.ui.BodyText
import app.schnarchwaechter.ui.InfoCard
import app.schnarchwaechter.ui.theme.AppColors
import app.schnarchwaechter.util.Fmt
import java.time.ZoneId

@Composable
fun HistoryScreen(vm: AppViewModel, onOpenNight: (Long) -> Unit) {
    val nights by vm.nights.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Verlauf", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Text) }
        if (nights.isEmpty()) {
            item { BodyText("Noch keine Nächte gemessen. Starte heute Abend deine erste Messung.", AppColors.Muted) }
        } else {
            item { WeeklyTrend(nights) }
        }
        items(nights, key = { it.id }) { night -> NightRow(night) { onOpenNight(night.id) } }
    }
}

@Composable
private fun WeeklyTrend(nights: List<NightEntity>) {
    val summaries = remember(nights) { nights.map { it.toSummary() } }
    val weeks = remember(summaries) { Trends.weekly(summaries, ZoneId.systemDefault(), maxWeeks = 4) }
    val bars = remember(nights) { nights.take(14).reversed().map { it.startMs to it.snoreMs / 60_000.0 } }
    InfoCard(title = "Wochentrend") {
        Text("Schnarchminuten der letzten ${bars.size} Nächte", color = AppColors.Muted, fontSize = 12.sp)
        SnoreBars(bars)
        weeks.forEachIndexed { i, w ->
            val a = w.averages
            val label = "Woche ab ${Fmt.date(w.weekStartMs)}"
            BodyText(
                "$label: Ø ${Fmt.decimal(a.snoreMinutes, 0)} min (${Fmt.percent(a.snoreShare)}), " +
                    "Ø ${Fmt.decimal(a.episodes)} Episoden · ${a.nights} ${if (a.nights == 1) "Nacht" else "Nächte"}",
                if (i == 0) AppColors.Text else AppColors.Muted,
            )
        }
        if (weeks.size >= 2) {
            val diff = weeks[0].averages.snoreMinutes - weeks[1].averages.snoreMinutes
            val text = when {
                diff <= -5 -> "Weniger Schnarchen als in der Woche davor. 👍"
                diff >= 5 -> "Mehr Schnarchen als in der Woche davor."
                else -> "Ungefähr gleich wie in der Woche davor."
            }
            BodyText(text, AppColors.Primary)
        }
    }
}

@Composable
private fun NightRow(night: NightEntity, onClick: () -> Unit) {
    val end = night.endMs ?: night.startMs
    val duration = end - night.startMs
    val share = if (duration > 0) night.snoreMs.toDouble() / duration else 0.0
    InfoCard(Modifier.clickable(onClick = onClick)) {
        Text(
            "${Fmt.date(night.startMs)}  ${Fmt.time(night.startMs)} – ${Fmt.time(end)}",
            color = AppColors.Text, fontWeight = FontWeight.Medium,
        )
        Text(
            "${night.episodeCount} Episoden · ${Fmt.minutes(night.snoreMs)} Schnarchen (${Fmt.percent(share)}) · max ${Fmt.relDb(night.maxAboveFloorDb)}",
            color = AppColors.Muted, fontSize = 13.sp,
        )
        val extras = buildList {
            if (night.pauseCount > 0) add("${night.pauseCount} auffällige Stillen")
            if (night.clipCount > 0) add("${night.clipCount} Clips")
            if (night.interrupted) add("unterbrochen")
        }
        if (extras.isNotEmpty()) Text(extras.joinToString(" · "), color = AppColors.Warning, fontSize = 12.sp)
    }
}
