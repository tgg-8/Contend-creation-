package app.schnarchwaechter.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.schnarchwaechter.core.MinuteStats
import app.schnarchwaechter.core.SnoreEpisode
import app.schnarchwaechter.data.ClipEntity
import app.schnarchwaechter.data.PauseEntity
import app.schnarchwaechter.ui.theme.AppColors
import app.schnarchwaechter.util.Fmt
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil

/**
 * Loudness of the night (dB above noise floor, per minute) over clock time, with snore episodes as
 * shaded areas, noticeable silences (orange) and clips (turquoise) as markers. Only drawn while the
 * screen is visible; nothing is computed in the background.
 */
@Composable
fun NightChart(
    startMs: Long,
    endMs: Long,
    minutes: List<MinuteStats>,
    episodes: List<SnoreEpisode>,
    pauses: List<PauseEntity>,
    clips: List<ClipEntity>,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = AppColors.Muted, fontSize = 10.sp)
    val maxRel = remember(minutes) {
        val m = minutes.maxOfOrNull { it.maxDb - it.floorDb } ?: 0.0
        maxOf(30.0, ceil(m / 10.0) * 10.0)
    }
    val hours = remember(startMs, endMs) { fullHours(startMs, endMs) }

    Canvas(modifier.fillMaxWidth().height(250.dp)) {
        val left = 30.dp.toPx()
        val right = 8.dp.toPx()
        val top = 16.dp.toPx()
        val bottom = 20.dp.toPx()
        val w = size.width - left - right
        val h = size.height - top - bottom
        val span = (endMs - startMs).coerceAtLeast(60_000L).toFloat()
        fun x(t: Long) = left + ((t - startMs) / span).coerceIn(0f, 1f) * w
        fun y(rel: Double) = top + h - (rel.coerceIn(0.0, maxRel) / maxRel).toFloat() * h

        // dB grid
        var db = 0.0
        while (db <= maxRel) {
            val yy = y(db)
            drawLine(AppColors.SurfaceHigh, Offset(left, yy), Offset(left + w, yy), strokeWidth = 1f)
            drawText(measurer, "+${db.toInt()}", Offset(0f, yy - 7.dp.toPx()), labelStyle)
            db += 10.0
        }
        // hour ticks (thin out labels if the night is long)
        val step = if (hours.size > 8) 2 else 1
        hours.forEachIndexed { i, t ->
            val xx = x(t)
            drawLine(AppColors.SurfaceHigh, Offset(xx, top), Offset(xx, top + h), strokeWidth = 1f)
            if (i % step == 0) drawText(measurer, Fmt.time(t), Offset(xx - 14.dp.toPx(), top + h + 4.dp.toPx()), labelStyle)
        }
        // snore episodes
        for (e in episodes) {
            val x0 = x(e.startMs)
            val x1 = maxOf(x(e.endMs), x0 + 2f)
            drawRect(AppColors.Primary.copy(alpha = 0.22f), Offset(x0, top), Size(x1 - x0, h))
        }
        // max and average per minute
        if (minutes.size >= 2) {
            val maxPath = Path()
            val avgPath = Path()
            minutes.forEachIndexed { i, m ->
                val xx = x(m.startMs + 30_000)
                val ym = y(m.maxDb - m.floorDb)
                val ya = y(m.avgDb - m.floorDb)
                if (i == 0) {
                    maxPath.moveTo(xx, ym); avgPath.moveTo(xx, ya)
                } else {
                    maxPath.lineTo(xx, ym); avgPath.lineTo(xx, ya)
                }
            }
            drawPath(maxPath, AppColors.Secondary.copy(alpha = 0.35f), style = Stroke(width = 1.dp.toPx()))
            drawPath(avgPath, AppColors.Secondary, style = Stroke(width = 2.dp.toPx()))
        }
        // markers
        for (p in pauses) drawCircle(AppColors.Warning, 4.dp.toPx(), Offset(x(p.startMs), top - 6.dp.toPx() + 2f))
        for (c in clips) {
            val xx = x(c.triggerMs)
            val path = Path().apply {
                moveTo(xx - 5.dp.toPx(), top - 12.dp.toPx())
                lineTo(xx + 5.dp.toPx(), top - 12.dp.toPx())
                lineTo(xx, top - 3.dp.toPx())
                close()
            }
            drawPath(path, AppColors.Secondary)
        }
    }
}

private fun fullHours(startMs: Long, endMs: Long): List<Long> {
    val zone = ZoneId.systemDefault()
    var t = Instant.ofEpochMilli(startMs).atZone(zone).withMinute(0).withSecond(0).withNano(0).plusHours(1)
    val out = mutableListOf<Long>()
    while (t.toInstant().toEpochMilli() < endMs && out.size < 30) {
        out += t.toInstant().toEpochMilli()
        t = t.plusHours(1)
    }
    return out
}

/** Simple bar chart: snore minutes of the last nights (oldest left). */
@Composable
fun SnoreBars(values: List<Pair<Long, Double>>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = AppColors.Muted, fontSize = 9.sp)
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        if (values.isEmpty()) return@Canvas
        val bottom = 16.dp.toPx()
        val h = size.height - bottom
        val maxV = maxOf(10.0, values.maxOf { it.second })
        val slot = size.width / values.size
        val barW = slot * 0.6f
        values.forEachIndexed { i, (start, v) ->
            val bh = (v / maxV).toFloat() * (h - 4.dp.toPx())
            val x0 = i * slot + (slot - barW) / 2
            drawRect(AppColors.Primary, Offset(x0, h - bh), Size(barW, maxOf(bh, 1f)))
            if (values.size <= 7 || i % 2 == (values.size - 1) % 2) {
                drawText(measurer, Fmt.dayMonth(start), Offset(x0 - 4.dp.toPx(), h + 2.dp.toPx()), labelStyle)
            }
        }
    }
}
