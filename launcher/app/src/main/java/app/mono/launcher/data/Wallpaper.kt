package app.mono.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Eigenes Hintergrundbild. Das gewählte Bild wird EINMAL verarbeitet und in den
 * privaten App-Speicher kopiert (files/wallpaper.jpg):
 *  1. auf Displaygröße verkleinert,
 *  2. mittig auf das Seitenverhältnis des Displays zugeschnitten,
 *  3. in Graustufen umgewandelt.
 * Die Abdunklung passiert erst bei der Anzeige (Regler in den Einstellungen).
 */
object Wallpaper {
    private fun file(context: Context) = File(context.filesDir, "wallpaper.jpg")

    fun exists(context: Context) = file(context).exists()

    suspend fun import(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            // Volle Displaygröße in Pixeln (inkl. Status-/Navigationsleiste).
            val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            val tw = bounds.width()
            val th = bounds.height()

            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                // So weit verkleinern, dass das Bild das Display gerade noch ganz füllt (nie vergrößern).
                val scale = max(tw.toFloat() / w, th.toFloat() / h).coerceAtMost(1f)
                decoder.setTargetSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }

            // Mittig auf das Display-Seitenverhältnis zuschneiden.
            val (x, y, cw, ch) = centerCrop(decoded.width, decoded.height, tw, th)
            val cropped = Bitmap.createBitmap(decoded, x, y, cw, ch)

            // Graustufen: Sättigung auf 0.
            val gray = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
            val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) }
            Canvas(gray).drawBitmap(cropped, 0f, 0f, paint)

            val tmp = File(context.filesDir, "wallpaper.tmp")
            tmp.outputStream().use { gray.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            tmp.renameTo(file(context))
        }.getOrDefault(false)
    }

    /** Ausschnitt (x, y, Breite, Höhe) eines Bildes bw×bh, der mittig das Seitenverhältnis tw:th hat. */
    fun centerCrop(bw: Int, bh: Int, tw: Int, th: Int): List<Int> {
        val targetRatio = tw.toFloat() / th
        val (cw, ch) = if (bw.toFloat() / bh > targetRatio) {
            (bh * targetRatio).roundToInt().coerceIn(1, bw) to bh   // Bild zu breit → links/rechts abschneiden
        } else {
            bw to (bw / targetRatio).roundToInt().coerceIn(1, bh)   // Bild zu hoch → oben/unten abschneiden
        }
        return listOf((bw - cw) / 2, (bh - ch) / 2, cw, ch)
    }

    suspend fun load(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        val f = file(context)
        if (f.exists()) runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull() else null
    }
}
