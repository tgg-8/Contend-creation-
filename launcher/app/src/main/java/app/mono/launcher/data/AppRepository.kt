package app.mono.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** Eine startbare App aus der App-Liste. */
data class AppEntry(
    val label: String,
    val packageName: String,
    val className: String,
    val user: UserHandle,
) {
    val key: String get() = "$packageName/$className"
    /** Vereinfachter Suchschlüssel: klein, ohne Akzente ("Café" → "cafe"). */
    val searchKey: String = normalizeForSearch(label)
}

/** Kleinbuchstaben und ohne Akzente/Umlaut-Punkte, damit "ue", "ü" usw. tolerant gefunden werden. */
fun normalizeForSearch(s: String): String =
    Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .trim()

/**
 * Liest alle installierten Apps über die offizielle LauncherApps-API und
 * aktualisiert die Liste automatisch bei Installation/Deinstallation/Update.
 */
class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    // Android ruft diese Methoden auf, sobald sich installierte Pakete ändern.
    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }

    fun start() {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
    }

    fun stop() {
        launcherApps.unregisterCallback(callback)
        scope.cancel()
    }

    fun reload() {
        scope.launch { _apps.value = load() }
    }

    private fun load(): List<AppEntry> {
        val collator = Collator.getInstance(Locale.GERMAN).apply { strength = Collator.PRIMARY }
        // Nur das Hauptprofil; Arbeitsprofil/Privater Bereich werden bewusst nicht angezeigt.
        return launcherApps.getActivityList(null, Process.myUserHandle())
            .filter { it.componentName.packageName != context.packageName } // den Launcher selbst ausblenden
            .map {
                AppEntry(
                    label = it.label.toString(),
                    packageName = it.componentName.packageName,
                    className = it.componentName.className,
                    user = it.user,
                )
            }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    /** Findet die passende App zu einem Favoriten (oder null, wenn nicht installiert). */
    fun resolve(fav: Favorite): AppEntry? {
        val list = _apps.value
        return list.firstOrNull { it.packageName == fav.packageName && it.className == fav.className }
            ?: list.firstOrNull { it.packageName == fav.packageName }
    }

    /** Startet eine App. Gibt false zurück, wenn das nicht ging (z. B. gerade deinstalliert). */
    fun launch(app: AppEntry): Boolean = try {
        launcherApps.startMainActivity(ComponentName(app.packageName, app.className), app.user, null, null)
        true
    } catch (e: Exception) {
        false
    }

    /** Öffnet die System-Seite "App-Info" (dort kann man deinstallieren, Berechtigungen ändern …). */
    fun openAppInfo(app: AppEntry) {
        runCatching {
            launcherApps.startAppDetailsActivity(ComponentName(app.packageName, app.className), app.user, null, null)
        }
    }
}
