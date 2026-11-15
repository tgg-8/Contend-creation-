# Mono – schlichter Schwarz-Weiß-Launcher

Eigener Android-Startbildschirm für das Pixel 9 Pro (Kotlin + Jetpack Compose, Android 13+).

## Bedienung

| Geste | Wirkung |
|---|---|
| Nach rechts wischen | App-Liste mit Suche |
| Lange auf freie Fläche drücken | Einstellungen |
| Favorit antippen / lange drücken | App öffnen / Favorit neu belegen |
| Bildschirmzeit antippen | Digital Wellbeing öffnen |
| Termin antippen | Termin im Kalender öffnen |
| App in der Liste lange drücken | App-Info (z. B. deinstallieren) |
| Zurück / Home-Geste | zurück zum Startbildschirm |

## Bauen

```bash
cd launcher
./gradlew assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # Logik-Tests
```

Oder den Ordner `launcher/` in Android Studio öffnen und auf ▶ drücken.

## Datenschutz

Alles bleibt auf dem Gerät. Ins Internet gehen nur:
- die Wetteranfrage an `api.open-meteo.com` (auf ~1 km gerundete Koordinaten),
- Suchanfragen, die du selbst antippst (Google, YouTube, Maps, Play Store).

## Ehrliche Grenzen

- **Bildschirmzeit:** berechnet aus den offiziellen Nutzungsereignissen (`UsageStatsManager`).
  Digital Wellbeing nutzt eigene, nicht veröffentlichte Regeln. Abweichungen von einigen Minuten sind möglich.
- **Digital-Wellbeing-Übersicht:** Es gibt keinen offiziellen Intent. Die App öffnet die
  Digital-Wellbeing-Hauptseite über einen inoffiziellen Klassennamen
  (`com.google.android.apps.wellbeing.settings.TopLevelSettingsActivity`). Klappt das nicht,
  öffnet sie Digital Wellbeing über dessen App-Symbol (falls eingeblendet) und sonst die Android-Einstellungen.
- **Google Fotos:** Es gibt keinen öffentlichen Such-Intent. Der Suchbegriff wird in die
  Zwischenablage kopiert und Google Fotos geöffnet. Du fügst ihn dort selbst ein.
- **YouTube-Suche** nutzt `ACTION_SEARCH` an die YouTube-App (verbreitet, aber nicht offiziell
  dokumentiert). Funktioniert das nicht, öffnet sich die YouTube-Suchseite als Link.
- **App-Verknüpfungen (Shortcuts)** liefert Android nur an den aktuellen Standard-Launcher.
- Arbeitsprofil und „Privater Bereich“ werden nicht angezeigt.
