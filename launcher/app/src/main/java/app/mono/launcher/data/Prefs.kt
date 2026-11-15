package app.mono.launcher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Eine Favoriten-Position auf dem Startbildschirm. className = null → erste Start-Activity des Pakets. */
data class Favorite(val label: String, val packageName: String, val className: String?)

/** Alle Einstellungen des Launchers in einem unveränderlichen Objekt (gut für Compose). */
data class Settings(
    val germanClock: Boolean = false,       // false = englisch + 12 h (wie im Screenshot)
    val autoOpenDelayMs: Int = 300,         // -1 = Auto-Öffnen aus
    val wallpaperEnabled: Boolean = false,
    val wallpaperDimPercent: Int = 40,      // Abdunklung 0..90 %
    val wallpaperVersion: Long = 0,         // ändert sich bei neuem Bild → neu laden
    val favorites: List<Favorite> = DEFAULT_FAVORITES,
)

/** Deine aktuellen Favoriten als Startwert (Paketnamen der offiziellen Play-Store-Apps). */
val DEFAULT_FAVORITES = listOf(
    Favorite("Aufgaben", "com.google.android.apps.tasks", null),
    Favorite("Anrufe", "com.google.android.dialer", null),
    Favorite("Sheets", "com.google.android.apps.docs.editors.sheets", null),
    Favorite("Trello", "com.trello", null),
    Favorite("Yazio", "com.yazio.android", null),
)

/** Speichert Einstellungen in SharedPreferences und stellt sie als Compose-State bereit. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("mono_settings", Context.MODE_PRIVATE)

    var settings by mutableStateOf(load())
        private set

    private fun load(): Settings {
        val favs = DEFAULT_FAVORITES.indices.map { i ->
            sp.getString("fav$i", null)?.let(::decodeFavorite) ?: DEFAULT_FAVORITES[i]
        }
        return Settings(
            germanClock = sp.getBoolean("german_clock", false),
            autoOpenDelayMs = sp.getInt("auto_open_delay", 300),
            wallpaperEnabled = sp.getBoolean("wallpaper_enabled", false),
            wallpaperDimPercent = sp.getInt("wallpaper_dim", 40),
            wallpaperVersion = sp.getLong("wallpaper_version", 0),
            favorites = favs,
        )
    }

    fun update(block: (Settings) -> Settings) {
        val s = block(settings)
        sp.edit().apply {
            putBoolean("german_clock", s.germanClock)
            putInt("auto_open_delay", s.autoOpenDelayMs)
            putBoolean("wallpaper_enabled", s.wallpaperEnabled)
            putInt("wallpaper_dim", s.wallpaperDimPercent)
            putLong("wallpaper_version", s.wallpaperVersion)
            s.favorites.forEachIndexed { i, f -> putString("fav$i", encodeFavorite(f)) }
        }.apply()
        settings = s
    }

    fun setFavorite(index: Int, fav: Favorite) = update { s ->
        s.copy(favorites = s.favorites.toMutableList().also { it[index] = fav })
    }

    /** Einmal-Flags, z. B. "Berechtigungen beim ersten Start schon gefragt". */
    fun flag(name: String): Boolean = sp.getBoolean("flag_$name", false)
    fun setFlag(name: String) = sp.edit().putBoolean("flag_$name", true).apply()

    /** Direkter Zugriff für kleine Caches (z. B. Wetter). */
    val raw: SharedPreferences get() = sp

    private fun encodeFavorite(f: Favorite) = listOf(f.label, f.packageName, f.className ?: "").joinToString("\u001F")
    private fun decodeFavorite(s: String): Favorite? {
        val p = s.split("\u001F")
        if (p.size != 3) return null
        return Favorite(p[0], p[1], p[2].ifEmpty { null })
    }
}
