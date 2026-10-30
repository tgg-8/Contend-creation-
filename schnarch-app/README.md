# Schnarchwächter 🌙

Android-App, die nachts misst, **wann** und **wie laut** du schnarchst. Sie ist auf möglichst geringen
Stromverbrauch ausgelegt. Alles bleibt auf dem Handy: keine Internetberechtigung, keine Cloud,
keine Werbung.

> Keine medizinische Diagnose. Wenn dir etwas Sorgen macht (z. B. häufige Atempausen, starke
> Tagesmüdigkeit), sprich mit einer Ärztin oder einem Arzt.

---

## 1. App aufs Handy bringen (ohne Computer, empfohlen)

1. Öffne auf dem Handy im Browser die **Releases** des GitHub-Repos
   (`github.com/tgg-8/Contend-creation-/releases`) und such den neuesten Eintrag
   **„Schnarchwächter (Build …)“**.
2. Tippe auf **`Schnarchwaechter.apk`**. Die Datei wird heruntergeladen.
3. Öffne die Datei (Benachrichtigung „Download abgeschlossen“ oder App „Dateien“ → Downloads).
4. Android fragt: *„Aus dieser Quelle installieren?“* → **Einstellungen** → Schalter bei
   „Dieser Quelle vertrauen“ an → zurück → **Installieren**.
5. Falls „Play Protect“ warnt („Unbekannte App“): **Trotzdem installieren**. Die App ist nur nicht aus
   dem Play Store, weil sie für dich gebaut wurde.

Neue Versionen installierst du genauso, einfach **drüber**. Deine Nächte bleiben erhalten
(alle Builds sind mit demselben Debug-Schlüssel signiert).

### Alternative: per USB-Kabel (für Bastler)

1. Am Handy: *Einstellungen → Über das Telefon* → 7× auf **Build-Nummer** tippen → Entwickleroptionen an.
2. *Einstellungen → System → Entwickleroptionen* → **USB-Debugging** an.
3. Handy per USB an den PC, am Handy „USB-Debugging zulassen“ bestätigen.
4. Am PC (mit installierten Android Platform-Tools): `adb install -r Schnarchwaechter.apk`

---

## 2. Wichtige Einstellungen am Handy (einmalig)

| Was | Warum | Wo |
|---|---|---|
| **Mikrofon erlauben** | Ohne geht nichts | Beim ersten Start → „Erlauben“ (oder Checkliste in der App) |
| **Benachrichtigungen erlauben** | Anzeige „Messung läuft“ + Morgenzusammenfassung | Checkliste in der App |
| **Akku-Optimierung aus** für Schnarchwächter | Sonst beendet Android die Messung evtl. nachts | Checkliste → „Ausnehmen“ → „Zulassen“ |
| **Samsung:** App nicht „schlafen legen“ | Samsung beendet Apps sehr aggressiv | *Einstellungen → Akku → Hintergrundnutzungslimits* → Schnarchwächter bei „Nie schlafende Apps“ hinzufügen |
| **Xiaomi/Redmi/POCO:** Autostart + „Keine Einschränkungen“ | MIUI/HyperOS beendet Apps | *App-Info → Akku-Sparmodus → Keine Einschränkungen* |
| **Huawei/Honor:** App-Start manuell verwalten | gleiches Problem | *Akku → App-Start → Schnarchwächter → manuell, alles an* |

Für andere Marken hilft die Seite *dontkillmyapp.com* (im Browser öffnen, Hersteller wählen).

---

## 3. Die erste Nacht testen

**Vorab kurzer Probelauf (5 Minuten, tagsüber):**
1. App öffnen → Checkliste grün machen → **Nacht starten**.
2. 30 Sekunden still sein (die App misst das Grundrauschen).
3. Bildschirm ausschalten. Dann 2–3 Minuten lang **gleichmäßig „schnarchen“**: alle 4 Sekunden
   ein tiefes, sägendes Geräusch von 1 Sekunde, etwa 50 cm vom Handy entfernt.
4. Einmal laut in die Hände klatschen und die Tür zuschlagen (das darf **nicht** als Schnarchen zählen).
5. Bildschirm an → **Nacht beenden** → Die Zusammenfassung sollte etwa 1 Episode zeigen, und im
   „Zeitverlauf“ siehst du die Kurve.

**Echte Nacht:**
- Handy 0,5–1 m neben den Kopf, Unterkante (Mikrofon) zum Bett, nicht unter Kissen/Decke.
- Am besten ans Ladegerät (siehe unten). Flugmodus spart zusätzlich Strom.
- Morgens **Nacht beenden** → Zusammenfassung ansehen, Clips anhören.
- Wenn morgens „Messung unterbrochen“ steht: Akku-Einstellungen aus Abschnitt 2 prüfen.

---

## 4. Was die App macht (Funktionen)

- **Start:** großer Knopf „Nacht starten“ + Checkliste (Mikrofon, Benachrichtigungen,
  Akku-Optimierung, Tipps zur Handy-Position).
- **Nachtmodus:** schwarzer Bildschirm mit gedimmter Uhr, stark abgedunkelt, aktualisiert sich
  höchstens 1× pro Minute und gar nicht, wenn der Bildschirm aus ist.
- **Morgenzusammenfassung** (auch als Benachrichtigung): Messdauer, Anzahl Episoden, Schnarchdauer
  und Anteil an der Nacht, durchschnittliche und lauteste Lautstärke, lauteste Zeitpunkte, auffällige
  Stillen, Clips, Vergleich mit der Vorwoche, Akku-Verbrauch und Rechenzeit der App.
- **Zeitverlauf:** Diagramm der Nacht (Lautstärke pro Minute über die Uhrzeit, Schnarch-Episoden
  als Flächen, Clips ▼ und auffällige Stillen ● als Markierungen).
- **Verlauf:** alle Nächte, Balkendiagramm der letzten 14 Nächte, Wochenmittelwerte und Trend.
- **Einstellungen:** Empfindlichkeit (5 Stufen), Clips an/aus, max. Clips pro Nacht, automatische
  Löschung nach 7/14/30/90 Tagen (Standard 30), CSV-Export (Nächte / Minutenwerte), alles löschen,
  optionale Kalibrierung.

### Über die dB-Werte
Handy-Mikrofone sind nicht geeicht. Die App zeigt deshalb **relative Lautstärke**: „+18 dB“ heißt
18 dB lauter als das Grundrauschen deines Zimmers. Wenn du ein Schallpegel-Messgerät hast, kannst du
in den Einstellungen kalibrieren. Dann erscheinen zusätzlich grobe Werte wie „≈ 52 dB“. Auch die
sind nur Schätzungen.

### Audioclips
Standardmäßig speichert die App **nur Zahlen**. Ein Clip (10–25 s, inkl. der Sekunden davor aus
einem kleinen Ringpuffer im Arbeitsspeicher) entsteht nur bei:
- **sehr lautem Schnarchen**: mindestens 25 dB über dem Grundrauschen und deutlich lauter als der
  Rest der Nacht,
- **langer Episode**: über 20 Minuten am Stück,
- **auffälliger Stille**: mindestens 10 s Ruhe direkt nach lautem Schnarchen, danach Schnarchen
  (*kann* eine Atempause sein, muss aber nicht).

Höchstens 6 Clips pro Nacht (einstellbar 1–8). Wenn das Limit erreicht ist, ersetzt ein
auffälligerer Moment den unauffälligsten Clip. Clips liegen AAC-komprimiert (~40 KB) nur im
App-internen Speicher. Andere Apps kommen nicht daran, und es gibt kein Cloud-Backup.

---

## 5. Stromsparen: was ich gemacht habe

| Maßnahme | Wirkung |
|---|---|
| **8 kHz, mono, 16 Bit** (Fallback 16 kHz usw., falls das Handy 8 kHz nicht kann) | Schnarchen liegt bei 100–1000 Hz, 8 kHz erfasst bis 4 kHz. 6× weniger Daten als 48 kHz |
| **Blöcke von 2 s**, interner Puffer 6 s | Der App-Thread wacht nur alle 2 s auf, rechnet ~1 ms und schläft wieder |
| **Zweistufige Erkennung** | Stufe 1 (immer): Pegel + Bandenergie, ~15 Multiplikationen pro Sample. Stufe 2 (FFT, Rhythmus) nur bei auffälligen Geräuschen: im Test bei **1,4 %** der Analyse-Frames |
| **Minutenwerte, Datenbank alle 10 min** | ~50 Schreibvorgänge pro Nacht statt ~300 000 Einzelwerte |
| **Keine UI-Arbeit bei Bildschirm aus** | Uhr/Diagramme laufen nur, wenn sichtbar; Benachrichtigung wird nie aktualisiert |
| **Nachtmodus** schwarz + Helligkeit 1 % | Falls du doch draufschaust |
| **Wake Lock**: nur ein *partieller* (Bildschirm bleibt aus), mit 14-h-Sicherheits-Timeout, wird beim Beenden freigegeben, abschaltbar | Während das Mikrofon läuft, hält Androids Audiosystem den Prozessor sowieso teilweise wach. Der Lock verhindert nur Aussetzer auf manchen Handys |
| **Kein Internet, kein GPS, keine Sensoren** | Nichts anderes weckt das Handy |
| Effekte wie automatische Verstärkung aus | Bessere Messwerte, nicht mehr Strom |

**Gemessen** (ehrlich gesagt: nur auf einem PC, nicht auf einem Handy):
die komplette Erkennung braucht **~0,8 s Rechenzeit pro Stunde Audio** auf dem PC (Kotlin/JVM).
Ein Handy-Sparkern ist grob 5–10× langsamer, also etwa **0,5–1 Minute CPU pro Nacht**. Den
größten Teil des Verbrauchs macht deshalb nicht die App, sondern dass Mikrofon und Audiosystem die
ganze Nacht aktiv sind. Das lässt sich bei einer Dauermessung nicht vermeiden.

**Erwartung (nicht gemessen):** etwa **5–15 % Akku pro 8-Stunden-Nacht**, je nach Handy.

### Selbst nachmessen
- **In der App:** Die Zusammenfassung zeigt *Akku Start → Ende* und die *Rechenzeit der App*
  (gemessen mit Android selbst). Für eine saubere Messung eine Nacht **ohne** Ladegerät messen.
- **Am Handy:** *Einstellungen → Akku → Akkunutzung*, Schnarchwächter suchen (Anteil der letzten 24 h).
- **Am PC mit adb (genauer):**
  ```
  adb shell dumpsys batterystats --reset        # abends, Handy NICHT am Ladegerät
  # ... Nacht messen ...
  adb shell dumpsys batterystats app.schnarchwaechter > akku.txt
  ```
  In `akku.txt` stehen u. a. „Wake lock“, „Audio“ und „Estimated power use“ für die App.

### Ladegerät: ja oder nein?
- **Mit Ladegerät** ist es am sorglosesten, und es verfälscht die Messung nicht. Nur wenn das
  Netzteil hörbar summt/fiept, lieber ein anderes nehmen oder etwas weiter weg legen.
- **Ohne Ladegerät** geht es auch, wenn der Akku abends **über ~40 %** ist. Prüfe nach der ersten
  Nacht in der Zusammenfassung, wie viel verbraucht wurde, und entscheide dann.

---

## 6. Wie die Schnarch-Erkennung funktioniert

1. **Grundrauschen:** In den ersten 30 s misst die App den leisen Pegel des Zimmers (20 %-Perzentil).
   Danach wird er jede Minute nachgeführt: Er darf sofort sinken, aber nur langsam steigen. So stört
   ein Ventilator nicht, und langes Schnarchen wird nicht zum „Grundrauschen“. Stummgeschaltetes
   Mikrofon (z. B. während eines Anrufs) wird ignoriert.
2. **Stufe 1 (immer, billig):** alle 100 ms Gesamtpegel und Energie im Schnarchband 100–1000 Hz.
   Steigt die Bandenergie 8 dB über ihr Grundrauschen, beginnt ein „Geräusch“.
3. **Stufe 2 (nur bei Geräuschen):** Ein Geräusch zählt als Schnarcher, wenn es
   0,3–3,5 s dauert (Tür = zu kurz, Fernseher = zu lang), mindestens 45 % seiner Energie im
   Schnarchband liegt und der spektrale Schwerpunkt (per FFT, Grundrauschen abgezogen) unter 1200 Hz
   liegt (Sprechen, Husten, Klatschen sind höher bzw. breitbandig).
4. **Atemrhythmus:** Erst 3 Schnarcher im Abstand von 2–9 s mit einigermaßen regelmäßigem Takt
   starten eine **Episode**. Sie endet, wenn 30 s kein Schnarcher kommt. Episoden unter 20 s werden
   verworfen.

Alle Schwellwerte stehen an **einer Stelle**: `core/src/main/kotlin/.../DetectionConfig.kt`.
Die Empfindlichkeit in der App verschiebt die wichtigsten davon.

### Tests
`core/src/test` prüft die Erkennung mit **synthetischen Signalen**: Stille, Zimmerrauschen,
Ventilator (auch beim Einschalten mitten in der Nacht), stummes Mikrofon, Türknallen, unregelmäßiger
Husten, rhythmische breitbandige Geräusche, zu schneller Rhythmus, zu kurzes Schnarchen,
Schnarchen auf einem Ventilator, sehr leises Schnarchen, 16 kHz, Atempausen, Clip-Limit,
CSV-Format usw. (37 Tests, alle grün).

**Ehrlich:** Synthetisches „Schnarchen“ ist nicht echtes Schnarchen. Echte Aufnahmen würden die
Erkennung deutlich verbessern, vor allem gegen Fehlalarme durch Partner, Haustiere oder Straßenlärm.

### Mit echten Aufnahmen justieren
1. Eine Nacht oder einen Ausschnitt mit einer Diktiergerät-App aufnehmen und als **WAV** (16 Bit)
   exportieren, z. B. mit Audacity: *Datei → Exportieren → WAV, 16-bit PCM*.
2. Am PC im Ordner `schnarch-app`:
   ```
   ./gradlew :core:analyzeWav --args="aufnahme.wav 3"
   ```
   (die `3` ist die Empfindlichkeit 1–5). Ausgabe: jeder erkannte Schnarcher mit Uhrzeit und
   Lautstärke, Episoden, auffällige Stillen und **wie viele Geräusche aus welchem Grund verworfen**
   wurden (Dauer / Frequenzband / Spektrum).
3. Werte in `DetectionConfig.kt` anpassen, erneut laufen lassen, bis es passt. Die Tests
   (`./gradlew :core:test`) zeigen, ob dabei etwas anderes kaputtgeht.

---

## 7. Für Entwickler

```
schnarch-app/
├── core/   reine Kotlin-Logik (ohne Android) + Tests + WAV-Analysewerkzeug
│   ├── SnoreDetector.kt      zweistufige Erkennung, Episoden, Stillen
│   ├── NoiseFloorTracker.kt  adaptives Grundrauschen
│   ├── NightSession.kt       verbindet Erkennung, Minuten, Ringpuffer, Clips
│   ├── Clips.kt              auffällige Momente, Clip-Auswahl, Ringpuffer
│   ├── MinuteAggregator.kt / NightStats.kt / CsvExport.kt
│   └── DetectionConfig.kt    ALLE Schwellwerte
└── app/    Android: Foreground-Service (Mikrofon), Room, Compose-UI (Deutsch)
```

- Build: `./gradlew :core:test :app:assembleDebug` (JDK 17+, Android SDK 35). Auf GitHub baut
  `.github/workflows/schnarch-app.yml` die APK automatisch bei jedem Push.
- minSdk 26 (Android 8), targetSdk 35 (Android 15). Foreground-Service-Typ `microphone`.
- Kein `INTERNET`-Recht (im Manifest aktiv entfernt; der CI-Build prüft das).

## 8. Ideen für später

1. **Echte Aufnahmen sammeln** (mit Einverständnis) und die Schwellwerte damit justieren, evtl.
   ein winziges Modell (z. B. 20 kB-Entscheidungsbaum auf den vorhandenen Merkmalen), das nur in
   Stufe 2 läuft.
2. **Partner-Unterscheidung:** Richtung/Abstand ist mit einem Mikrofon schwer, aber die Lautstärke
   relativ zum Grundrauschen pro Nacht lernen könnte helfen.
3. **Rückenlage/Seitenlage** grob über den Lagesensor erkennen (Handy am Körper) – kostet Strom.
4. **Wecker-Integration:** automatisch starten/stoppen zu festen Zeiten.
5. **Notizen pro Nacht** (Alkohol, Sport, Erkältung, Schlafposition) und Vergleich, was das Schnarchen
   beeinflusst. Das ist für einen Sportler spannend.
6. **Export der Clips** (z. B. für einen Arzttermin) über das Teilen-Menü.
7. **Release-Build** mit eigenem Signaturschlüssel und R8 für eine kleinere APK.
