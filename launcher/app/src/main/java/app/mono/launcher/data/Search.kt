package app.mono.launcher.data

import android.Manifest
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Process
import android.provider.ContactsContract
import android.widget.Toast

/** Ein Suchergebnis außerhalb der App-Liste: Titel, kleine Herkunftsangabe, Aktion beim Antippen. */
data class SearchItem(val key: String, val title: String, val source: String, val run: () -> Unit)

/**
 * Erweiterte Suche. Ehrlicher Hinweis: Android hat KEINE universelle Suche in fremden Apps.
 * Möglich ist nur, was eine App über offizielle Intents / Links / Such-APIs anbietet:
 *  - Websuche:   Intent.ACTION_WEB_SEARCH (öffnet Google-App bzw. Browser)
 *  - YouTube:    Intent.ACTION_SEARCH an die YouTube-App, sonst youtube.com/results-Link
 *  - Maps:       geo:0,0?q=…  (offiziell dokumentierter Google-Maps-Intent)
 *  - Play Store: market://search?q=…  (offiziell dokumentierter Play-Link)
 *  - Google Fotos: KEIN öffentlicher Such-Intent → App öffnen + Suchbegriff in die Zwischenablage
 *  - Kontakte:   ContactsProvider (Berechtigung READ_CONTACTS)
 *  - Shortcuts:  LauncherApps.getShortcuts (nur wenn dieser Launcher Standard ist)
 *  - Termine:    CalendarContract (Berechtigung READ_CALENDAR)
 */
class Search(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val pm = context.packageManager

    private companion object {
        const val YOUTUBE = "com.google.android.youtube"
        const val MAPS = "com.google.android.apps.maps"
        const val PLAY = "com.android.vending"
        const val PHOTOS = "com.google.android.apps.photos"
    }

    private fun installed(pkg: String) = runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess

    // ---------- Kontakte ----------

    fun contacts(query: String, limit: Int = 4): List<SearchItem> {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query))
        val proj = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        )
        val out = ArrayList<SearchItem>()
        runCatching {
            context.contentResolver.query(uri, proj, null, null, null)?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val id = c.getLong(0)
                    val lookup = c.getString(1)
                    val name = c.getString(2) ?: continue
                    out += SearchItem("contact:$id", name, "Kontakt") {
                        start(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(id, lookup)))
                    }
                }
            }
        }
        return out
    }

    // ---------- App-Verknüpfungen (Shortcuts) ----------

    /** Alle Shortcuts einmal laden (beim Öffnen der Suche), danach nur noch filtern. */
    fun loadShortcuts(): List<ShortcutInfo> {
        // Nur der aktuelle Standard-Launcher darf Shortcuts lesen.
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val q = LauncherApps.ShortcutQuery().setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
        )
        return runCatching { launcherApps.getShortcuts(q, Process.myUserHandle()).orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.isEnabled }
    }

    fun shortcuts(all: List<ShortcutInfo>, query: String, appLabels: Map<String, String>, limit: Int = 5): List<SearchItem> {
        val q = normalizeForSearch(query)
        if (q.isEmpty()) return emptyList()
        return all.asSequence()
            .filter { s ->
                normalizeForSearch(s.shortLabel?.toString().orEmpty()).contains(q) ||
                    normalizeForSearch(s.longLabel?.toString().orEmpty()).contains(q)
            }
            .distinctBy { it.`package` + "/" + it.id }
            .take(limit)
            .map { s ->
                SearchItem("shortcut:${s.`package`}/${s.id}", s.shortLabel.toString(), appLabels[s.`package`] ?: "Verknüpfung") {
                    runCatching { launcherApps.startShortcut(s, null, null) }
                        .onFailure { toast("Verknüpfung konnte nicht geöffnet werden") }
                }
            }
            .toList()
    }

    // ---------- Termine ----------

    fun events(query: String, german: Boolean, format: (CalendarEvent, Boolean) -> String): List<SearchItem> =
        CalendarRepo.search(context, query).map { e ->
            SearchItem("event:${e.eventId}:${e.begin}", e.title, format(e, german)) { CalendarRepo.open(context, e) }
        }

    // ---------- Web und In-App-Suche ----------

    fun webAndAppSearches(query: String): List<SearchItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<SearchItem>()
        out += SearchItem("web", q, "Im Web suchen") { webSearch(q) }
        if (installed(YOUTUBE)) out += SearchItem("yt", q, "YouTube") { youtube(q) }
        if (installed(MAPS)) out += SearchItem("maps", q, "Google Maps") { maps(q) }
        if (installed(PLAY)) out += SearchItem("play", q, "Play Store") { playStore(q) }
        if (installed(PHOTOS)) out += SearchItem("photos", q, "Google Fotos · Begriff wird kopiert") { photos(q) }
        return out
    }

    private fun webSearch(q: String) {
        val ok = start(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q))
        if (!ok) start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
    }

    private fun youtube(q: String) {
        val ok = start(Intent(Intent.ACTION_SEARCH).setPackage(YOUTUBE).putExtra(SearchManager.QUERY, q))
        if (!ok) start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))))
    }

    private fun maps(q: String) {
        val ok = start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q))).setPackage(MAPS))
        if (!ok) start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(q))))
    }

    private fun playStore(q: String) {
        val ok = start(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(q) + "&c=apps")).setPackage(PLAY))
        if (!ok) start(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=" + Uri.encode(q) + "&c=apps")))
    }

    /** Google Fotos hat keinen öffentlichen Such-Intent: Begriff kopieren und App öffnen. */
    private fun photos(q: String) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Suche", q))
        val launch = pm.getLaunchIntentForPackage(PHOTOS)
        if (launch != null && start(launch)) {
            toast("Suchbegriff kopiert. In Google Fotos ins Suchfeld einfügen")
        }
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) {
        false
    }

    private fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
}
