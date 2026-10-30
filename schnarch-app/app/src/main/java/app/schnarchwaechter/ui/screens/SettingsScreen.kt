package app.schnarchwaechter.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.schnarchwaechter.ui.AppViewModel
import app.schnarchwaechter.ui.BodyText
import app.schnarchwaechter.ui.InfoCard
import app.schnarchwaechter.ui.theme.AppColors
import app.schnarchwaechter.util.Fmt
import app.schnarchwaechter.util.Permissions
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var storageBytes by remember { mutableLongStateOf(0L) }
    LaunchedEffect(settings.clipRetentionDays) { storageBytes = vm.clipStorageBytes() }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    val exportNights = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.exportCsv(it, minutes = false) { ok -> toast(if (ok) "Export gespeichert" else "Export fehlgeschlagen") } }
    }
    val exportMinutes = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.exportCsv(it, minutes = true) { ok -> toast(if (ok) "Export gespeichert" else "Export fehlgeschlagen") } }
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Einstellungen", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Text)

        InfoCard(title = "Empfindlichkeit der Erkennung") {
            val labels = listOf("Sehr niedrig", "Niedrig", "Mittel", "Hoch", "Sehr hoch")
            Text(labels[settings.sensitivity - 1], color = AppColors.Text, fontWeight = FontWeight.Medium)
            Slider(
                value = settings.sensitivity.toFloat(),
                onValueChange = { v -> vm.updateSettings { it.copy(sensitivity = v.roundToInt().coerceIn(1, 5)) } },
                valueRange = 1f..5f,
                steps = 3,
            )
            BodyText(
                "Höher erkennt auch leises Schnarchen, zählt aber eher andere Geräusche mit. " +
                    "Niedriger zählt nur deutliches Schnarchen. Gilt ab der nächsten Nacht.",
                AppColors.Muted,
            )
        }

        InfoCard(title = "Audioclips") {
            SwitchRow("Clips bei auffälligen Momenten speichern", settings.clipsEnabled) { on ->
                vm.updateSettings { it.copy(clipsEnabled = on) }
            }
            if (settings.clipsEnabled) {
                Text("Höchstens ${settings.maxClipsPerNight} Clips pro Nacht", color = AppColors.Text)
                Slider(
                    value = settings.maxClipsPerNight.toFloat(),
                    onValueChange = { v -> vm.updateSettings { it.copy(maxClipsPerNight = v.roundToInt().coerceIn(1, 8)) } },
                    valueRange = 1f..8f,
                    steps = 6,
                )
            }
            Text("Clips automatisch löschen nach (Tagen)", color = AppColors.Text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 14, 30, 90).forEach { days ->
                    FilterChip(
                        selected = settings.clipRetentionDays == days,
                        onClick = {
                            vm.updateSettings { it.copy(clipRetentionDays = days) }
                            vm.applyRetention()
                        },
                        label = { Text("$days") },
                    )
                }
            }
            BodyText(
                "Clips sind 10–25 s lang, komprimiert (~40 KB) und liegen nur im internen Speicher der App. " +
                    "Belegt: ${Fmt.decimal(storageBytes / 1024.0 / 1024.0, 1)} MB.",
                AppColors.Muted,
            )
        }

        InfoCard(title = "Nacht & Akku") {
            SwitchRow("Morgenzusammenfassung als Benachrichtigung", settings.morningNotification) { on ->
                vm.updateSettings { it.copy(morningNotification = on) }
            }
            SwitchRow("Bildschirm im Nachtmodus stark abdunkeln", settings.dimNightScreen) { on ->
                vm.updateSettings { it.copy(dimNightScreen = on) }
            }
            SwitchRow("Zusätzliche Wach-Sperre (zuverlässiger)", settings.extraWakeLock) { on ->
                vm.updateSettings { it.copy(extraWakeLock = on) }
            }
            BodyText(
                "Die Wach-Sperre hält nur den Prozessor bereit, der Bildschirm bleibt aus. Während das Mikrofon " +
                    "aufnimmt, ist er ohnehin teilweise wach – sie kostet daher kaum zusätzlichen Strom, verhindert aber " +
                    "auf manchen Handys, dass die Messung stockt.",
                AppColors.Muted,
            )
            OutlinedButton(onClick = {
                runCatching { context.startActivity(Permissions.batterySettingsIntent()) }
            }) { Text("Akku-Einstellungen öffnen") }
        }

        CalibrationCard(
            offset = settings.calibrationOffsetDb,
            onMeasure = { reference ->
                if (!Permissions.hasMicrophone(context)) {
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    null
                } else {
                    vm.measureLevel()?.let { measured ->
                        val offset = reference - measured
                        vm.updateSettings { it.copy(calibrationOffsetDb = offset) }
                        offset
                    }
                }
            },
            onReset = { vm.updateSettings { it.copy(calibrationOffsetDb = null) } },
            scope = scope,
        )

        InfoCard(title = "Daten") {
            BodyText("Export als CSV (Semikolon, deutsches Zahlenformat – öffnet sich direkt in Excel).", AppColors.Muted)
            OutlinedButton(onClick = { exportNights.launch("schnarchen_naechte.csv") }, modifier = Modifier.fillMaxWidth()) {
                Text("Nächte als CSV exportieren")
            }
            OutlinedButton(onClick = { exportMinutes.launch("schnarchen_minutenwerte.csv") }, modifier = Modifier.fillMaxWidth()) {
                Text("Minutenwerte als CSV exportieren")
            }
            Button(
                onClick = { confirmDeleteAll = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Bad.copy(alpha = 0.2f), contentColor = AppColors.Bad),
            ) { Text("Alle Daten löschen") }
        }

        InfoCard(title = "Datenschutz") {
            BodyText(
                "Die App hat keine Internetberechtigung. Messwerte und Clips bleiben auf diesem Handy, " +
                    "es gibt keine Cloud, keine Werbung und kein Tracking. Kein Backup in die Cloud.",
                AppColors.Muted,
            )
            BodyText("Schnarchwächter ist kein Medizinprodukt und stellt keine Diagnose.", AppColors.Muted)
        }
        Spacer(Modifier.padding(8.dp))
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteAll = false
                    vm.deleteAll {
                        storageBytes = 0
                        toast("Alle Daten gelöscht")
                    }
                }) { Text("Alles löschen", color = AppColors.Bad) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Abbrechen") } },
            title = { Text("Alle Daten löschen?") },
            text = { Text("Alle Nächte, Werte und Clips werden endgültig gelöscht. Die Einstellungen bleiben.") },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AppColors.Text, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CalibrationCard(
    offset: Double?,
    onMeasure: suspend (Double) -> Double?,
    onReset: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    var reference by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    InfoCard(title = "Kalibrierung (optional)") {
        BodyText(
            "Ohne Kalibrierung zeigt die App ehrliche relative Werte („+18 dB über Grundrauschen“). Wenn du ein " +
                "Schallpegel-Messgerät hast, kannst du grobe absolute Werte freischalten: Erzeuge ein gleichmäßiges " +
                "Geräusch (z. B. Ventilator), lies den Wert am Messgerät ab, trage ihn ein und halte beide Geräte " +
                "nebeneinander.",
            AppColors.Muted,
        )
        Text(
            if (offset == null) "Status: nicht kalibriert" else "Status: kalibriert (Versatz ${Fmt.decimal(offset)} dB)",
            color = AppColors.Text,
        )
        OutlinedTextField(
            value = reference,
            onValueChange = { reference = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5) },
            label = { Text("Wert am Messgerät in dB") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !busy && reference.replace(',', '.').toDoubleOrNull() != null,
                onClick = {
                    val ref = reference.replace(',', '.').toDouble()
                    busy = true
                    message = "Messe 5 Sekunden …"
                    scope.launch {
                        val result = onMeasure(ref)
                        message = if (result == null) "Messung nicht möglich (Mikrofon?)." else "Kalibriert."
                        busy = false
                    }
                },
            ) { Text("5 s messen") }
            if (offset != null) OutlinedButton(onClick = onReset) { Text("Zurücksetzen") }
        }
        message?.let { BodyText(it, AppColors.Primary) }
    }
}
