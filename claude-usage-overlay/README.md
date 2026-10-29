# Claude-Rädchen 🌸

Ein kleines, pastellfarbenes Rädchen, das als Overlay über anderen Apps (z. B. Claude) schwebt
und zeigt, wie viel von deinem Pro-Limit noch übrig ist. Antippen zeigt eine Sprechblase.

**Wichtig:** Die Werte trägst du selbst ein (aus Claude → Einstellungen → Nutzung).
Es gibt keine offizielle Schnittstelle für die Pro-Nutzung, und die App liest nichts heimlich aus.
Alle Daten bleiben lokal auf deinem Handy.

## Installieren
1. Auf GitHub unter **Releases** den neuesten Eintrag „Claude-Rädchen (Build …)“ öffnen.
2. `ClaudeRing.apk` antippen → herunterladen → öffnen.
3. Android fragt, ob dein Browser Apps installieren darf → einmal erlauben → **Installieren**.
   (Google Play Protect warnt evtl., weil die App nicht aus dem Play Store kommt. „Trotzdem installieren“.)

## Benutzen
1. App „Claude-Rädchen“ öffnen.
2. **Erlaubnis erteilen** → „Claude-Rädchen“ → „Über anderen Apps einblenden“ einschalten → zurück.
3. In Claude unter Einstellungen → Nutzung nachschauen und eintragen:
   - **Genutzt (%)**, z. B. 38
   - **Zurückgesetzt in** Stunden + Minuten
4. **Speichern & Rädchen zeigen**.
5. Rädchen **antippen** = Sprechblase (geht nach 5 Sek. von selbst weg oder per Tipp).
   **Ziehen** = verschieben (Position wird gemerkt).
6. Ausblenden: in der App „Rädchen ausblenden“ oder in der Benachrichtigung „Ausblenden“.

Nach dem Reset-Zeitpunkt zeigt das Rädchen 100 % und erinnert dich, neue Werte einzutragen.

## Anpassen
| Was | Wo |
|---|---|
| Sprüche in der Blase | `app/src/main/java/app/claudering/BubbleText.kt` |
| Farben des Rings | `RingView.kt` → `colorFor` |
| Größe des Rädchens / Blasen-Dauer | `OverlayService.kt` → `RING_DP`, `BUBBLE_MS` |
| Farben der App | `app/src/main/res/values/colors.xml` |

Jede Änderung in diesem Ordner, die auf GitHub landet, baut automatisch eine neue APK
(Reiter **Actions**, danach unter **Releases**). Neue Version einfach über die alte installieren,
deine Werte bleiben erhalten.

`app/debug.keystore` ist ein reiner Test-Schlüssel mit dem öffentlichen Standard-Passwort
`android`. Er ist kein Geheimnis und sorgt nur dafür, dass Updates über die alte Version passen.
