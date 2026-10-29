# Bauplan

```
 Montag 6:40                Du (Handy/PC)                 Dein Rechner                     täglich 20:13
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────────────┐     ┌─────────────────────────┐
│ Claude-Aufgabe   │     │ Clips + Fotos in │     │ Pipeline (Python)        │     │ Claude-Aufgabe          │
│ „Montags-Ideen“  │──▶  │ Drive hochladen  │──▶  │ ffmpeg + Whisper         │──▶  │ „Entwürfe anlegen“      │
│ 4 Ideen als .md  │     │ Content/Rohmat./ │     │ schneidet & beschriftet  │     │ Metricool: Entwürfe     │
│ in Content/Ideen │     │ <Ideen-Name>/    │     │ → Content/Fertig/<Name>/ │     │ + Push an dich          │
└──────────────────┘     └──────────────────┘     └──────────────────────────┘     └───────────┬─────────────┘
                                                                                               ▼
                                                                         Du prüfst in Metricool und tippst „Planen“
```

## Teile und Werkzeuge

| Teil | Werkzeug | Was es macht |
|---|---|---|
| Ideen | Claude-Aufgabe „Montags-Content-Ideen“ + Google-Drive-Connector | legt jeden Montag 4 Ideen inkl. aller Texte als `.md` in `Content/Ideen` ab |
| Ablage | Google Drive für Desktop (synchronisierter Ordner) | dein Rechner sieht Drive wie einen normalen Ordner, ohne API-Schlüssel |
| Erkennen | `pipeline/ordner.py` | findet neue Ordner in `Content/Rohmaterial`, wartet, bis der Sync fertig ist |
| Schneiden | ffmpeg (`pipeline/render.py`, `schnitt.py`) | 9:16 in 1080×1920, Pausen und schwache Stellen raus, stärkster Moment als Hook nach vorn, Länge pro Plattform |
| Untertitel | faster-whisper, lokal (`transkript.py`, `untertitel.py`) | deutsche Untertitel, im sicheren Bereich, nie mitten im Wort geschnitten |
| Texte | Idee-Datei + `texte.py` | setzt Caption, Hashtags, Titel zusammen, prüft Grenzen, warnt bei KI-Floskeln |
| Paket | `beitrag.json` im Fertig-Ordner | alles, was Metricool braucht, in einer Datei |
| Entwürfe | Claude-Aufgabe „Entwürfe anlegen“ + Metricool-Connector | legt Entwürfe zur besten Zeit an, ergänzt fehlende Texte, schickt dir Push mit Vorschau |
| Freigabe | Metricool-App | du öffnest den Entwurf und tippst „Planen“, vorher geht nichts raus |

## Was pro Beitrag entsteht (`Content/Fertig/<Name>/`)

| Datei | Für | Länge |
|---|---|---|
| `instagram_reel.mp4` | Instagram Reel | bis 25 s |
| `tiktok.mp4` | TikTok | bis 30 s |
| `youtube_short.mp4` | YouTube Short | bis 40 s |
| `story.mp4` | Instagram-Story (über Metricool) + TikTok-Story (von Hand) | bis 15 s |
| `pinterest_pin.jpg` | Pinterest-Pin, 1000×1500 mit Titel | – |
| `vorschau.jpg` | Vorschau für dich | – |
| `*.srt`, `transkript.txt` | Untertitel als Datei, z. B. für YouTube | – |
| `beitrag.json` | Übergabe an die Claude-Aufgabe | – |
| `protokoll.txt` | was erkannt, geschnitten, fehlgeschlagen ist | – |
| `angelegt.json` | kommt von der Claude-Aufgabe: was in Metricool angelegt wurde | – |

## Warum so und nicht anders

- **Synchronisierter Drive-Ordner statt Drive-API:** keine Google-Cloud-Einrichtung, keine Tokens, funktioniert auch offline weiter.
- **Metricool über die Claude-Aufgabe statt Metricool-API im Skript:** Der Metricool-Connector ist bei dir schon verbunden und getestet. Die Metricool-API bräuchte einen eigenen Token und einen teureren Tarif.
- **Entwurf statt Freigabe-Workflow:** Der Metricool-Freigabe-Workflow braucht einen Tarif mit Team-Funktion. Ein Entwurf geht garantiert nicht raus, bis du ihn planst.
- **Keine Musik im Video:** So gibt es keine Urheberrechtsprobleme, und du kannst einen Plattform-Sound wählen (siehe unten). Eigene lizenzfreie Musik geht über `MUSIK_DATEI` in der `.env`.

## Was technisch nicht geht

| Funktion | Status | Beste Alternative |
|---|---|---|
| TikTok-Story über Metricool | geht nicht (Schnittstelle kennt keinen Story-Typ) | `story.mp4` liegt fertig in Drive, du postest sie in 10 Sekunden in der TikTok-App. Der Link steht in der Push-Nachricht. |
| Instagram-Story automatisch posten | unklar bei Creator-Konten | Beim ersten Entwurf testen. Falls es scheitert: In Metricool „Benachrichtigung statt Auto-Post“ wählen. |
| Plattform-Sound automatisch | geht nur bei Instagram-Business-Konten (Metricool-Sound-Auswahl) | Für einen Trend-Sound beim Planen in Metricool „Benachrichtigung“ statt „automatisch“ wählen: Metricool erinnert dich zur Zeit, du postest in der App und wählst dort den Sound. Sonst einfach ohne Musik. |
| Pinterest-Video-Pin | unklar | Bild-Pin mit Titel (funktioniert sicher) |
