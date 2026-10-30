package app.schnarchwaechter.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.schnarchwaechter.data.NightEntity
import app.schnarchwaechter.service.NightService
import app.schnarchwaechter.ui.AppViewModel
import app.schnarchwaechter.ui.Disclaimer
import app.schnarchwaechter.ui.InfoCard
import app.schnarchwaechter.ui.SectionTitle
import app.schnarchwaechter.ui.rememberMinuteTicker
import app.schnarchwaechter.ui.theme.AppColors
import app.schnarchwaechter.util.Fmt
import app.schnarchwaechter.util.Permissions

@Composable
fun StartScreen(vm: AppViewModel, service: NightService.State, onOpenNight: (Long) -> Unit) {
    val context = LocalContext.current
    var micOk by remember { mutableStateOf(Permissions.hasMicrophone(context)) }
    var micDenied by remember { mutableStateOf(false) }
    var notificationsOk by remember { mutableStateOf(Permissions.hasNotifications(context)) }
    var batteryOk by remember { mutableStateOf(Permissions.isIgnoringBatteryOptimizations(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        micOk = Permissions.hasMicrophone(context)
        notificationsOk = Permissions.hasNotifications(context)
        batteryOk = Permissions.isIgnoringBatteryOptimizations(context)
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        micOk = it
        micDenied = !it
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsOk = it
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        micOk = Permissions.hasMicrophone(context)
        batteryOk = Permissions.isIgnoringBatteryOptimizations(context)
    }
    val nights by vm.nights.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text("Schnarchwächter", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Text)
        Text(
            "Misst nachts, wann und wie laut du schnarchst. Alle Daten bleiben auf deinem Handy.",
            color = AppColors.Muted, fontSize = 14.sp,
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { NightService.start(context) },
            enabled = micOk,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary, contentColor = AppColors.Background),
        ) {
            Text("Nacht starten", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        if (!micOk) {
            Text(
                "Erlaube zuerst das Mikrofon (siehe unten).", color = AppColors.Warning, fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        SectionTitle("Checkliste vor dem Schlafen")
        CheckItem(
            ok = micOk,
            title = "Mikrofon erlaubt",
            text = "Nötig für die Messung. Ohne besonderen Anlass wird nichts aufgenommen, nur Zahlen gespeichert.",
            actionLabel = if (micDenied) "Einstellungen öffnen" else "Erlauben",
            onAction = {
                if (micDenied) settingsLauncher.launch(Permissions.appSettingsIntent(context))
                else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            CheckItem(
                ok = notificationsOk,
                title = "Benachrichtigungen erlaubt",
                text = "Für die Anzeige „Messung läuft“ und die Morgenzusammenfassung. Empfohlen.",
                actionLabel = "Erlauben",
                onAction = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )
        }
        CheckItem(
            ok = batteryOk,
            title = "Akku-Optimierung aus",
            text = "Sonst kann Android die Messung nachts beenden. Kostet selbst keinen Strom.",
            actionLabel = "Ausnehmen",
            onAction = {
                try {
                    settingsLauncher.launch(Permissions.batteryOptimizationIntent(context))
                } catch (e: ActivityNotFoundException) {
                    settingsLauncher.launch(Permissions.batterySettingsIntent())
                }
            },
        )
        CheckItem(
            ok = null,
            title = "Handy richtig hinlegen",
            text = "0,5 bis 1 m neben deinen Kopf, z. B. auf den Nachttisch. Unterkante (Mikrofon) zum Bett, " +
                "nicht unter Kissen oder Decke. Jede Nacht ungefähr gleich hinlegen, dann sind die Nächte vergleichbar.",
        )
        CheckItem(
            ok = null,
            title = "Strom sparen",
            text = "Am besten ans Ladegerät. Flugmodus spart zusätzlich Akku und stört die Messung nicht. " +
                "Nach dem Start misst die App 30 Sekunden das Grundrauschen, dann bitte leise sein.",
        )

        nights.firstOrNull()?.let { last ->
            SectionTitle("Letzte Nacht")
            LastNightCard(last) { onOpenNight(last.id) }
        }
        Disclaimer()
    }

    if (service is NightService.State.Failed) {
        AlertDialog(
            onDismissRequest = { NightService.acknowledge() },
            confirmButton = { TextButton(onClick = { NightService.acknowledge() }) { Text("OK") } },
            title = { Text("Messung nicht gestartet") },
            text = { Text(service.message) },
        )
    }
}

@Composable
private fun CheckItem(ok: Boolean?, title: String, text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        val (symbol, color) = when (ok) {
            true -> "✓" to AppColors.Good
            false -> "!" to AppColors.Warning
            null -> "i" to AppColors.Primary
        }
        Box(
            Modifier.size(28.dp).background(color.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(symbol, color = color, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = AppColors.Text, fontWeight = FontWeight.Medium)
            Text(text, color = AppColors.Muted, fontSize = 13.sp)
            if (ok == false && actionLabel != null) {
                OutlinedButton(onClick = onAction, modifier = Modifier.padding(top = 6.dp)) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun LastNightCard(night: NightEntity, onClick: () -> Unit) {
    InfoCard(Modifier.clickable(onClick = onClick)) {
        Text(Fmt.dateLong(night.startMs), color = AppColors.Text, fontWeight = FontWeight.Medium)
        Text(
            "${night.episodeCount} Episoden · ${Fmt.minutes(night.snoreMs)} Schnarchen · " +
                "${Fmt.duration((night.endMs ?: night.startMs) - night.startMs)} gemessen",
            color = AppColors.Muted, fontSize = 13.sp,
        )
        Text("Ansehen ›", color = AppColors.Primary, fontSize = 13.sp)
    }
}

/** Minimal black screen while measuring. Updates at most once per minute and only while visible. */
@Composable
fun NightModeScreen(state: NightService.State, vm: AppViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val now = rememberMinuteTicker()
    var confirmStop by remember { mutableStateOf(false) }

    DimWindow(enabled = settings.dimNightScreen)

    Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding().padding(24.dp)) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(Fmt.time(now), color = AppColors.NightText, fontSize = 72.sp, fontWeight = FontWeight.Light)
            val line = when (state) {
                is NightService.State.Running -> "Messung läuft seit ${Fmt.duration(now - state.startMs)}"
                NightService.State.Finishing -> "Nacht wird ausgewertet …"
                else -> "Messung startet …"
            }
            Text(line, color = AppColors.NightText, fontSize = 16.sp)
            if (state is NightService.State.Running && now - state.startMs < 2 * 60_000) {
                Text(
                    "Die ersten 30 Sekunden wird das Grundrauschen gemessen. Danach kannst du den Bildschirm ausschalten, " +
                        "die Messung läuft weiter.",
                    color = AppColors.NightText, fontSize = 13.sp, textAlign = TextAlign.Center,
                )
            }
        }
        if (state is NightService.State.Running) {
            OutlinedButton(
                onClick = { confirmStop = true },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Muted),
            ) { Text("Nacht beenden", fontSize = 16.sp) }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    NightService.stop(context)
                }) { Text("Beenden") }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Weiter messen") } },
            title = { Text("Nacht beenden?") },
            text = { Text("Die Messung wird gestoppt und ausgewertet.") },
        )
    }
}

/** Turns the screen brightness almost to zero while the night screen is shown. */
@Composable
private fun DimWindow(enabled: Boolean) {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(enabled) {
        val window = activity.window
        if (enabled) {
            window.attributes = window.attributes.apply { screenBrightness = 0.01f }
        }
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }
}
