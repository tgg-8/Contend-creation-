package app.mono.launcher.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mono.launcher.data.Prefs
import app.mono.launcher.data.ScreenTime

/** Mögliche Verzögerungen für Auto-Öffnen; -1 = aus. */
private val AUTO_OPEN_STEPS = listOf(-1, 0, 150, 300, 500, 800)

/**
 * Einstellungen im gleichen schlichten Stil. Jede Zeile: links Bezeichnung, rechts Wert.
 * Antippen ändert den Wert bzw. öffnet die passende System-Seite.
 */
@Composable
fun SettingsScreen(
    prefs: Prefs,
    refreshTick: Int,
    onClose: () -> Unit,
    onRequestPermissions: (Array<String>) -> Unit,
    wallpaperSection: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val s = prefs.settings

    BackHandler { onClose() }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp)
    ) {
        Spacer(Modifier.height(32.dp))
        T("Einstellungen", size = 32.sp)
        Spacer(Modifier.height(24.dp))

        Section("Anzeige")
        SettingRow("Uhr & Datum", if (s.germanClock) "Deutsch · 24 h" else "English · 12 h") {
            prefs.update { it.copy(germanClock = !it.germanClock) }
        }

        Section("App-Suche")
        SettingRow(
            "Eindeutige App automatisch öffnen",
            if (s.autoOpenDelayMs < 0) "aus" else "nach ${s.autoOpenDelayMs} ms",
        ) {
            val i = AUTO_OPEN_STEPS.indexOf(s.autoOpenDelayMs)
            prefs.update { it.copy(autoOpenDelayMs = AUTO_OPEN_STEPS[(i + 1) % AUTO_OPEN_STEPS.size]) }
        }

        wallpaperSection()

        Section("Berechtigungen")
        SettingRow("Nutzungszugriff (Bildschirmzeit)", if (ScreenTime.hasAccess(context)) "erteilt" else "erteilen") {
            ScreenTime.openUsageAccessSettings(context)
        }
        PermissionRow("Standort grob (Wetter)", Manifest.permission.ACCESS_COARSE_LOCATION, ::granted, onRequestPermissions)
        PermissionRow("Kalender", Manifest.permission.READ_CALENDAR, ::granted, onRequestPermissions)
        PermissionRow("Kontakte (Suche)", Manifest.permission.READ_CONTACTS, ::granted, onRequestPermissions)

        Section("Launcher")
        val roleManager = context.getSystemService(RoleManager::class.java)
        val isDefault = roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true
        SettingRow("Standard-Startbildschirm", if (isDefault) "ja" else "festlegen") {
            // Offizieller Weg (Android 10+): System-Dialog "Standard-Startbildschirm-App".
            val intent = if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && !isDefault)
                roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            else Intent(AndroidSettings.ACTION_HOME_SETTINGS)
            runCatching { roleLauncher.launch(intent) }
        }
        SettingRow("Favorit ändern", "lange auf Favorit drücken") {}

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
fun Section(title: String) {
    Spacer(Modifier.height(24.dp))
    T(title.uppercase(), size = 13.sp, alpha = 0.5f)
    Spacer(Modifier.height(4.dp))
}

@Composable
fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp).tap(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(label, size = 18.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        T(value, size = 16.sp, alpha = 0.7f, maxLines = 1)
    }
}

@Composable
private fun PermissionRow(
    label: String,
    permission: String,
    granted: (String) -> Boolean,
    onRequest: (Array<String>) -> Unit,
) {
    val context = LocalContext.current
    val ok = granted(permission)
    SettingRow(label, if (ok) "erteilt" else "erteilen") {
        if (ok) {
            // Bereits erteilt: App-Info öffnen, falls man sie wieder entziehen möchte.
            runCatching {
                context.startActivity(
                    Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.fromParts("package", context.packageName, null))
                )
            }
        } else onRequest(arrayOf(permission))
    }
}
