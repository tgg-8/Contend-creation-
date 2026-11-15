package app.mono.launcher

import android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED
import android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED
import android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED
import android.app.usage.UsageEvents.Event.SCREEN_NON_INTERACTIVE
import app.mono.launcher.data.ScreenTime
import app.mono.launcher.data.ScreenTime.UsageEvent
import app.mono.launcher.data.Wallpaper
import app.mono.launcher.data.Weather
import app.mono.launcher.data.normalizeForSearch
import app.mono.launcher.ui.Fmt
import app.mono.launcher.ui.filterByKey
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** Tests der reinen Logik (laufen auf dem PC, ohne Handy). */
class LogicTest {
    private val own = "app.mono.launcher"
    private fun ev(type: Int, pkg: String, t: Long, cls: String = "Main") = UsageEvent(type, pkg, cls, t)

    @Test fun screenTime_simpleSession() {
        val e = listOf(ev(ACTIVITY_RESUMED, "a", 10), ev(ACTIVITY_PAUSED, "a", 70), ev(ACTIVITY_STOPPED, "a", 71))
        assertEquals(60, ScreenTime.foregroundMillis(e, 0, 1000, own))
    }

    @Test fun screenTime_overlapCountedOnce() {
        // Geteilter Bildschirm: A 0–100, B 50–150 → 150, nicht 200.
        val e = listOf(
            ev(ACTIVITY_RESUMED, "a", 0), ev(ACTIVITY_RESUMED, "b", 50),
            ev(ACTIVITY_PAUSED, "a", 100), ev(ACTIVITY_PAUSED, "b", 150),
        )
        assertEquals(150, ScreenTime.foregroundMillis(e, 0, 1000, own))
    }

    @Test fun screenTime_launcherNotCounted() {
        val e = listOf(
            ev(ACTIVITY_RESUMED, own, 0), ev(ACTIVITY_PAUSED, own, 100),
            ev(ACTIVITY_RESUMED, "a", 100), ev(ACTIVITY_PAUSED, "a", 130),
        )
        assertEquals(30, ScreenTime.foregroundMillis(e, 0, 1000, own))
    }

    @Test fun screenTime_screenOffEndsSession() {
        val e = listOf(ev(ACTIVITY_RESUMED, "a", 0), ev(SCREEN_NON_INTERACTIVE, "android", 40), ev(ACTIVITY_PAUSED, "a", 500))
        assertEquals(40, ScreenTime.foregroundMillis(e, 0, 1000, own))
    }

    @Test fun screenTime_appOpenSinceBeforeMidnight() {
        val e = listOf(ev(ACTIVITY_PAUSED, "a", 1030), ev(ACTIVITY_RESUMED, "b", 2000), ev(ACTIVITY_PAUSED, "b", 2010))
        assertEquals(40, ScreenTime.foregroundMillis(e, 1000, 5000, own))
    }

    @Test fun screenTime_stillOpenNow() {
        val e = listOf(ev(ACTIVITY_RESUMED, "a", 100))
        assertEquals(900, ScreenTime.foregroundMillis(e, 0, 1000, own))
    }

    @Test fun screenTime_format() {
        assertEquals("27m", ScreenTime.format(27 * 60_000L + 59_000))
        assertEquals("2h 5m", ScreenTime.format((125) * 60_000L))
    }

    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun clock_englishAnd24h() {
        val t = ms(2026, 11, 15, 11, 31)
        assertEquals("11:31 AM", Fmt.time(t, german = false))
        assertEquals("Sunday, 15th November", Fmt.date(t, german = false))
        assertEquals("11:31", Fmt.time(t, german = true))
        assertEquals("Sonntag, 15. November", Fmt.date(t, german = true))
        assertEquals("9:30 PM", Fmt.time(ms(2026, 11, 15, 21, 30), german = false))
    }

    @Test fun clock_ordinals() {
        val days = mapOf(1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th", 11 to "11th", 12 to "12th", 13 to "13th", 21 to "21st", 22 to "22nd", 23 to "23rd", 31 to "31st")
        for ((d, s) in days) assertEquals(s, Fmt.date(ms(2026, 12, d, 8, 0), false).split(", ")[1].split(" ")[0])
    }

    @Test fun progressBar() {
        assertEquals("▓▓▓▓▓░░░░░", Fmt.bar(0.5f, 10))
        assertEquals("░░░░░░░░░░", Fmt.bar(0f, 10))
        assertEquals("▓▓▓▓▓▓▓▓▓▓", Fmt.bar(1f, 10))
    }

    @Test fun search_filterAndAutoOpenUniqueness() {
        val apps = listOf("Google Maps", "Sheets", "Signal", "Snapchat", "Spotify", "YouTube Music")
        assertEquals(listOf("Snapchat"), filterByKey(apps, "sna") { normalizeForSearch(it) })
        assertEquals(listOf("Sheets", "Signal", "Snapchat", "Spotify", "Google Maps", "YouTube Music"), filterByKey(apps, "s") { normalizeForSearch(it) })
        assertEquals(listOf("Google Maps"), filterByKey(apps, "map") { normalizeForSearch(it) })   // Wortanfang
        assertEquals(listOf("YouTube Music"), filterByKey(apps, "mus") { normalizeForSearch(it) })
        assertEquals(apps, filterByKey(apps, "  ") { normalizeForSearch(it) })                    // leer → alle
        assertEquals("cafe", normalizeForSearch("Café"))
    }

    @Test fun weatherGerman() {
        assertEquals("Klarer Himmel", Weather.describe(0))
        assertEquals("Leichter Regen", Weather.describe(61))
    }

    @Test fun wallpaperCrop() {
        // Pixel 9 Pro: 1280 x 2856. Querformat-Foto 4000x3000 → mittiger Hochkant-Streifen.
        val (x, y, w, h) = Wallpaper.centerCrop(4000, 3000, 1280, 2856)
        assertEquals(3000, h); assertEquals(0, y)
        assertEquals(1345, w); assertEquals((4000 - 1345) / 2, x)
        // Sehr hohes Bild → oben/unten abschneiden.
        val (x2, y2, w2, h2) = Wallpaper.centerCrop(1000, 4000, 1280, 2856)
        assertEquals(0, x2); assertEquals(1000, w2); assertEquals(2231, h2); assertEquals((4000 - 2231) / 2, y2)
    }
}
