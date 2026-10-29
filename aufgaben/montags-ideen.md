# Neuer Auftragstext für die Claude-Aufgabe „Montags-Content-Ideen“

Was sich gegenüber dem alten Auftrag ändert:
- 4 statt 3 Ideen (passend zu 3–4 Videos pro Woche)
- jede Idee wird als eigene Textdatei in Google Drive gespeichert (`Content/Ideen/`), damit die Pipeline sie lesen kann
- fertige Texte pro Plattform (Caption, Hashtags, YouTube-Titel, Pin-Titel …) im Stil des Skills `content-skript`
- kein Hinweis mehr auf CapCut; geschnitten wird automatisch

---

Entwirf vier Ideen für Kurzvideos (Instagram Reels, TikTok, YouTube Shorts, Pinterest) auf Basis der Nachrichten der vergangenen 7 Tage, und speichere jede Idee als eigene Datei in Google Drive.

Kontext: Ich bin Tarek, 18, Sprinter (U20, 400 m/200 m) an der Sportschule Potsdam und Athlet am Olympiastützpunkt, betreibe auch Bobsport. Instagram: @Tarek.Athletics. Meine Nische ist ein Mix aus Reha und Training; ich nutze Whoop- und Trainingsdaten. Zielgruppe: 16–22 Jahre. Der Content soll catchy und natürlich wirken, nicht nach KI. Lade für Hooks, Captions und Hashtags den Skill „content-skript“ und halte dich an seinen Stil.

Vorgehen:
1. Recherchiere per Websuche aktuelle Nachrichten aus den letzten 7 Tagen zu diesen drei Themenfeldern (jeweils eigene Suchen, deutsch- und englischsprachige Quellen):
   - Training & Sportwissenschaft (Krafttraining, Sprinttraining, neue Studien, Wearables wie Whoop, Regeneration)
   - Leichtathletik/Sprint (Wettkämpfe, Rekorde, Athleten, DLV, World Athletics)
   - Reha & Verletzungen (Comebacks, Sportmedizin, Verletzungsprävention)
2. Wähle die vier Nachrichten mit dem besten Potenzial für meine Zielgruppe aus, möglichst aus unterschiedlichen Themenfeldern. Nur echte, belegbare Nachrichten aus der vergangenen Woche verwenden, nichts erfinden.
3. Schreibe pro Idee eine Datei im Markdown-Format mit genau diesen Überschriften:
   `# Titel`, `## Thema` (Aufhänger in 1–2 Sätzen mit Quellenlink), `## Ablauf` (Szenen für 15–45 Sekunden, die ich selbst filmen kann, gern mit Bezug zu meinem Training, meiner Reha oder meinen Whoop-Daten), `## Hook-Text` (max. 8 Wörter, wird in den ersten 2 Sekunden eingeblendet), `## Instagram Caption`, `## Instagram Hashtags` (genau 3–5), `## TikTok Caption`, `## TikTok Hashtags` (3–5), `## YouTube Titel` (max. 60 Zeichen), `## YouTube Beschreibung` (1–2 Sätze), `## YouTube Hashtags` (max. 3, mit #shorts), `## Pinterest Board` (immer: Sprinttraining), `## Pin-Titel` (max. 100 Zeichen, suchfreundlich), `## Pin-Beschreibung` (max. 500 Zeichen, natürliche Suchbegriffe, keine Hashtags), `## Pin-Text` (max. 5 Wörter für das Bild).
4. Speichere jede Idee mit dem Google-Drive-Connector als **normale Textdatei** (MIME-Typ text/markdown oder text/plain, **kein Google-Doc**) im Ordner „Content/Ideen“. Dateiname: `JJJJ-MM-TT_stichwort.md` mit dem heutigen Datum und einem kurzen Stichwort in Kleinbuchstaben ohne Leerzeichen und Umlaute, z. B. `2026-11-02_whoop-schlaf.md`. Existiert der Ordner nicht, lege ihn an.
5. Schreibe alles auf Deutsch, locker und ansprechend, mit korrekter Rechtschreibung und Zeichensetzung. Keine Gedankenstriche, keine typischen KI-Floskeln.

Das ist ein unbeaufsichtigter Lauf: keine Rückfragen stellen. Nichts posten, nichts versenden. Lege nur die vier Ideen-Dateien in „Content/Ideen“ an und ändere oder lösche sonst nichts in meinen Apps. Schicke mir am Ende mit SendUserMessage eine kurze Übersicht: die vier Dateinamen, je ein Satz zur Idee, und die Quellenliste.
