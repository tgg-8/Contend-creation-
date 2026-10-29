package app.claudering

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/** Das kleine Rädchen: weicher Kreis, Ring mit Fortschritt, Zahl in der Mitte. */
class RingView(context: Context) : View(context) {

    /** Verbleibende Nutzung in Prozent, oder null wenn noch nichts eingetragen ist. */
    var percentLeft: Int? = null
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#F5FFFFFF")
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.5f * density
        color = Color.parseColor("#E9E3F3")
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.5f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6E5E80")
        textAlign = Paint.Align.CENTER
        textSize = 12f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val inset = trackPaint.strokeWidth / 2f + 2f * density
        oval.set(inset, inset, width - inset, height - inset)

        canvas.drawCircle(cx, cy, cx - 1f * density, fillPaint)
        canvas.drawArc(oval, 0f, 360f, false, trackPaint)

        val percent = percentLeft
        if (percent != null && percent > 0) {
            arcPaint.color = colorFor(percent)
            canvas.drawArc(oval, -90f, 360f * percent / 100f, false, arcPaint)
        }

        val label = percent?.toString() ?: "?"
        val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, cx, textY, textPaint)
    }

    private fun colorFor(percent: Int): Int = when {
        percent >= 50 -> Color.parseColor("#8FD3B6") // Minze
        percent >= 20 -> Color.parseColor("#F7C59F") // Pfirsich
        else -> Color.parseColor("#F4A3B4")          // Rosé
    }
}
