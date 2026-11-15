package app.mono.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.mono.launcher.data.AppEntry
import app.mono.launcher.data.AppRepository
import app.mono.launcher.data.Favorite
import app.mono.launcher.data.Prefs
import app.mono.launcher.data.Search
import app.mono.launcher.data.SearchItem
import app.mono.launcher.ui.Fmt
import app.mono.launcher.ui.AppDrawer
import app.mono.launcher.ui.HomeScreen
import app.mono.launcher.ui.SettingsScreen
import app.mono.launcher.ui.WallpaperBackground
import app.mono.launcher.ui.WallpaperSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Welche Ansicht gerade sichtbar ist. */
sealed interface Screen {
    data object Home : Screen
    data object Drawer : Screen
    data object Settings : Screen
    data class PickFavorite(val index: Int) : Screen
}

/**
 * Die einzige Activity. Sie ist als HOME registriert (siehe AndroidManifest.xml)
 * und schaltet zwischen Startbildschirm, App-Liste und Einstellungen um.
 */
class MainActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository
    private lateinit var search: Search

    private val screen = mutableStateOf<Screen>(Screen.Home)
    /** Wird bei jedem onResume erhöht → Bildschirmzeit, Termin, Berechtigungen neu lesen. */
    private val refreshTick = mutableIntStateOf(0)

    private var permissionRequestedAt = 0L
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            // Wurde eine Berechtigung früher dauerhaft abgelehnt, zeigt Android keinen Dialog mehr
            // und antwortet sofort. Dann die App-Info öffnen, wo man sie von Hand erlauben kann.
            val answeredInstantly = SystemClock.elapsedRealtime() - permissionRequestedAt < 400
            if (result.containsValue(false) && answeredInstantly) openAppDetails()
            refreshTick.intValue++
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Randlos mit hellen Symbolen in Status- und Navigationsleiste (schwarzer Hintergrund).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        prefs = Prefs(applicationContext)
        repo = AppRepository(applicationContext).also { it.start() }
        search = Search(applicationContext)

        // Beim allerersten Start einmal nach Standort (Wetter), Kalender und Kontakten (Suche) fragen.
        if (!prefs.flag("first_permissions")) {
            prefs.setFlag("first_permissions")
            askPermissions(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.READ_CONTACTS,
            )
        }

        setContent {
            val apps by repo.apps.collectAsState()
            val tick = refreshTick.intValue
            val settings = prefs.settings

            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Crossfade(targetState = screen.value, animationSpec = tween(160), label = "screen") { current ->
                    when (current) {
                        Screen.Home -> HomeScreen(
                            prefs = prefs,
                            favoriteLabels = settings.favorites.map { it.label },
                            refreshTick = tick,
                            onOpenDrawer = { screen.value = Screen.Drawer },
                            onOpenSettings = { screen.value = Screen.Settings },
                            onFavoriteClick = { i -> openFavorite(i) },
                            onFavoriteLongPress = { i -> screen.value = Screen.PickFavorite(i) },
                            onRequestLocation = { askPermissions(Manifest.permission.ACCESS_COARSE_LOCATION) },
                            background = { WallpaperBackground(settings) },
                        )

                        Screen.Drawer -> {
                            // Shortcuts einmal pro Öffnen laden, danach nur filtern.
                            val shortcuts by produceState(emptyList<ShortcutInfo>()) {
                                value = withContext(Dispatchers.IO) { search.loadShortcuts() }
                            }
                            AppDrawer(
                                apps = apps,
                                autoOpenDelayMs = settings.autoOpenDelayMs,
                                onLaunch = { launchApp(it) },
                                onLongPress = { repo.openAppInfo(it) },
                                onClose = { screen.value = Screen.Home },
                                searchProvider = { q -> searchExtras(q, shortcuts, apps, settings.germanClock) },
                                onExtraRun = { screen.value = Screen.Home },
                            )
                        }

                        is Screen.PickFavorite -> AppDrawer(
                            apps = apps,
                            autoOpenDelayMs = -1, // beim Auswählen nie automatisch
                            placeholder = "App für Favorit ${current.index + 1} wählen",
                            onLaunch = { app ->
                                prefs.setFavorite(current.index, Favorite(app.label, app.packageName, app.className))
                                screen.value = Screen.Home
                            },
                            onClose = { screen.value = Screen.Home },
                        )

                        Screen.Settings -> SettingsScreen(
                            prefs = prefs,
                            refreshTick = tick,
                            onClose = { screen.value = Screen.Home },
                            onRequestPermissions = { askPermissions(*it) },
                            wallpaperSection = { WallpaperSettings(prefs) },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTick.intValue++
    }

    /** Home-Taste/-Geste, während der Launcher schon offen ist → zurück zum Startbildschirm. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            screen.value = Screen.Home
        }
    }

    override fun onDestroy() {
        repo.stop()
        super.onDestroy()
    }

    /** Reihenfolge unter den App-Treffern: Kontakte, Verknüpfungen, Termine, dann Web/In-App-Suche. */
    private fun searchExtras(q: String, shortcuts: List<ShortcutInfo>, apps: List<AppEntry>, german: Boolean): List<SearchItem> {
        val labels = apps.associate { it.packageName to it.label }
        return search.contacts(q) +
            search.shortcuts(shortcuts, q, labels) +
            search.events(q, german) { e, g -> "Termin · " + Fmt.eventLong(e, g) } +
            search.webAndAppSearches(q)
    }

    private fun launchApp(app: AppEntry) {
        if (!repo.launch(app)) Toast.makeText(this, "Konnte ${app.label} nicht öffnen", Toast.LENGTH_SHORT).show()
        screen.value = Screen.Home
    }

    /** Favorit öffnen; ist die App nicht installiert, direkt die Auswahl anzeigen. */
    private fun openFavorite(index: Int) {
        val fav = prefs.settings.favorites[index]
        val app = repo.resolve(fav)
        if (app != null) launchApp(app) else {
            Toast.makeText(this, "${fav.label} ist nicht installiert – bitte App wählen", Toast.LENGTH_SHORT).show()
            screen.value = Screen.PickFavorite(index)
        }
    }

    private fun askPermissions(vararg p: String) {
        permissionRequestedAt = SystemClock.elapsedRealtime()
        permissionLauncher.launch(arrayOf(*p))
    }

    private fun openAppDetails() {
        runCatching {
            startActivity(
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", packageName, null))
            )
        }
    }
}
