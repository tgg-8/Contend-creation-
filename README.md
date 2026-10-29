# Tareks Content-Pipeline

Clips in Google Drive werfen, und fertig geschnittene Kurzvideos mit Untertiteln, Texten und Metricool-Entwürfen kommen raus. Veröffentlicht wird erst, wenn du in Metricool auf „Planen“ tippst.

- Wie alles zusammenhängt: [docs/BAUPLAN.md](docs/BAUPLAN.md)
- Was du noch selbst erledigen musst: [docs/TODO.md](docs/TODO.md)
- Aufbau einer Idee-Datei: [docs/IDEE_VORLAGE.md](docs/IDEE_VORLAGE.md)
- Aufträge für die Claude-Aufgaben: [aufgaben/](aufgaben/)

---

## 1. Einrichten (einmalig, ca. 30 Minuten)

### 1.1 Google Drive auf dem Rechner
1. Installiere **Google Drive für Desktop** und melde dich an.
2. Lege in „Meine Ablage“ diese Ordner an:
   ```
   Content/
     Ideen/
     Rohmaterial/
     Fertig/
   ```
3. Stell den Ordner `Content` auf **offline verfügbar** (Rechtsklick > „Offline verfügbar“) bzw. auf „Spiegeln“. Dann liegen die Clips wirklich auf dem Rechner, und die Pipeline kann sie lesen.

### 1.2 Programme installieren
Du brauchst **Python** (ab Version 3.10) und **ffmpeg**.

**Mac** (im Programm „Terminal“):
```bash
# Homebrew, falls noch nicht da: https://brew.sh
brew install python ffmpeg
```

**Windows** (in „PowerShell“):
```powershell
winget install Python.Python.3.12
winget install Gyan.FFmpeg
```

### 1.3 Projekt holen und vorbereiten
```bash
git clone https://github.com/tgg-8/Contend-creation-.git
cd Contend-creation-
python3 -m venv .venv                 # eigener "Werkzeugkasten" nur für dieses Projekt (Windows: python statt python3)
source .venv/bin/activate             # Windows: .venv\Scripts\activate
pip install -r requirements.txt       # installiert Whisper (Spracherkennung)
```

### 1.4 Einstellungen
1. Kopiere `.env.example` und nenne die Kopie `.env`.
2. Trag bei `CONTENT_ORDNER` den Pfad zu deinem Drive-Ordner `Content` ein.
   - Mac: im Finder den Ordner `Content` mit gedrückter Wahltaste rechtsklicken > „Als Pfadname kopieren“.
   - Windows: im Explorer in die Adresszeile klicken und den Pfad kopieren.
3. Fertig. Passwörter oder Tokens braucht die Pipeline nicht.

## 2. Erst testen: ein einziger Beispielclip

```bash
python -m pipeline test /Pfad/zu/einem/clip.mp4
```
Optional mit Texten: `--idee docs/IDEE_VORLAGE.md` anhängen.

Beim ersten Mal lädt Whisper sein Sprachmodell (ca. 500 MB), das dauert ein paar Minuten. Das Ergebnis landet in `test_ausgabe/Content/Fertig/…`. Drive und Metricool werden dabei nicht angefasst. Schau dir die Videos an: Passen Schnitt, Hook und Untertitel? Erst danach geht es weiter.

## 3. Normaler Ablauf

### Clips hochladen
1. Montags kommen 4 Ideen als Dateien in `Content/Ideen`, z. B. `2026-11-02_whoop-schlaf.md`.
2. Filme die Idee. Lege in `Content/Rohmaterial` einen Ordner mit **genau demselben Namen** an: `2026-11-02_whoop-schlaf`.
3. Wirf alle Clips und Fotos hinein, direkt vom Handy über die Drive-App. Sie werden nach Dateinamen sortiert aneinandergehängt.
   - Ohne Idee geht es auch: Ordner einfach `JJJJ-MM-TT_irgendwas` nennen. Dann schreibt Claude die Texte aus dem, was du im Video sagst.
   - iPhone-Fotos im Format HEIC werden übersprungen (Einstellungen > Kamera > Formate > „Maximale Kompatibilität“).

### Pipeline laufen lassen
Auf dem Rechner, im Projektordner:
```bash
source .venv/bin/activate      # Windows: .venv\Scripts\activate
python -m pipeline wache
```
Das Fenster offen lassen. Alle 2 Minuten schaut die Pipeline nach neuen Ordnern. Beenden mit `Strg+C`.
Wer lieber einmal von Hand startet: `python -m pipeline lauf`.

### Freigeben
1. Abends legt die Claude-Aufgabe „Entwürfe anlegen“ alles als **Entwurf** in Metricool an, jeweils zur besten Zeit.
2. Du bekommst eine Push-Nachricht mit Vorschau und Links.
3. In Metricool: Entwurf öffnen, Video und Text prüfen, auf **„Planen“** tippen. Erst dann geht er raus.
4. TikTok-Story: Datei aus dem Link in der Nachricht in der TikTok-App posten (geht nicht über Metricool).

## 4. Wenn etwas schiefgeht

- **Protokoll lesen:** `Content/Fertig/<Name>/protokoll.txt` (oder `logs/` im Projektordner). Ganz unten steht die Zusammenfassung: erkannt, geschnitten, angelegt, fehlgeschlagen.
- **`fehler.txt` im Fertig-Ordner:** Die Verarbeitung ist abgebrochen. Grund lesen, Problem beheben, `fehler.txt` löschen, dann versucht es die Pipeline neu.
- **Einen Beitrag neu schneiden:** den Ordner in `Content/Fertig` löschen.
- **Längen oder Textgrenzen ändern:** `pipeline/plattformen.py`.

## 5. Für Neugierige

```bash
python -m unittest discover tests     # alle Tests (der Gesamttest braucht ffmpeg)
```
Die Dateien in `pipeline/` sind kommentiert. Die Reihenfolge des Ablaufs steht in `pipeline/verarbeitung.py`.
