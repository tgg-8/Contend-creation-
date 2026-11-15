package app.mono.launcher.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mono.launcher.data.Prefs
import app.mono.launcher.data.Settings
import app.mono.launcher.data.Wallpaper
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val MAX_DIM = 90

/** Hintergrund des Startbildschirms: Bild in Graustufen + schwarze Abdunklung darüber. */
@Composable
fun WallpaperBackground(settings: Settings) {
    if (!settings.wallpaperEnabled) return
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, settings.wallpaperVersion) {
        value = Wallpaper.load(context)?.asImageBitmap()
    }
    val img = image ?: return
    // Graustufenfilter zusätzlich bei der Anzeige: bleibt garantiert schwarz-weiß.
    val grayscale = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    Image(
        bitmap = img,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        colorFilter = grayscale,
    )
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = settings.wallpaperDimPercent / 100f)))
}

/** Abschnitt "Hintergrund" in den Einstellungen. */
@Composable
fun WallpaperSettings(prefs: Prefs) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = prefs.settings
    var busy by remember { mutableStateOf(false) }

    // Android Photo Picker: keine Speicherberechtigung nötig, man gibt nur das gewählte Bild frei.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val ok = Wallpaper.import(context, uri)
            busy = false
            if (ok) {
                prefs.update { it.copy(wallpaperEnabled = true, wallpaperVersion = System.currentTimeMillis()) }
            } else {
                Toast.makeText(context, "Bild konnte nicht geladen werden", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Section("Hintergrund")
    SettingRow("Eigenes Bild", if (busy) "wird geladen …" else "aus Galerie wählen") {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val hasImage = s.wallpaperVersion > 0 && Wallpaper.exists(context)
    SettingRow("Hintergrund", if (s.wallpaperEnabled && hasImage) "Bild (schwarz-weiß)" else "Schwarz") {
        if (hasImage) prefs.update { it.copy(wallpaperEnabled = !it.wallpaperEnabled) }
        else picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    DimSlider(s.wallpaperDimPercent) { v -> prefs.update { it.copy(wallpaperDimPercent = v) } }
}

/** Regler im Textblock-Stil: Antippen oder Ziehen auf dem Balken setzt die Abdunklung (0–90 %). */
@Composable
private fun DimSlider(value: Int, onChange: (Int) -> Unit) {
    // Anteil 0..1 aus der Tippposition → in 5-%-Schritten.
    fun fromX(x: Float, width: Int) = ((x / width.coerceAtLeast(1)).coerceIn(0f, 1f) * MAX_DIM / 5).roundToInt() * 5
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row {
            T("Abdunklung", size = 18.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            T("$value %", size = 16.sp, alpha = 0.7f)
        }
        // Die Breite des Balkens = Breite des Textes, daher kein fillMaxWidth.
        T(
            Fmt.bar(value / MAX_DIM.toFloat(), blocks = 24), size = 18.sp, mono = true, maxLines = 1,
            modifier = Modifier
                .pointerInput(Unit) { detectTapGestures { onChange(fromX(it.x, size.width)) } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume(); onChange(fromX(change.position.x, size.width))
                    }
                }
                .padding(vertical = 10.dp),
        )
    }
}
