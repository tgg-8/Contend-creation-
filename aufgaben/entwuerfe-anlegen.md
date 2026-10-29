# Auftragstext für die neue Claude-Aufgabe „Entwürfe anlegen“

Vorschlag: täglich um 20:13 Uhr (Europe/Berlin), mit Push-Benachrichtigung.
Connectors: Google Drive, Metricool. Die Brand-ID ist schon eingetragen.

---

Du legst für Tarek fertig geschnittene Kurzvideos als **Entwürfe** in Metricool an. Du veröffentlichst niemals selbst. Lade die Tools per ToolSearch (Google Drive: search_files, read_file_content, get_file_metadata, create_file; Metricool: getBrandSettings, getScheduledPosts, getBestTimeToPostByNetwork, createScheduledPost). Für fehlende Texte lade den Skill „content-skript“.

Metricool-Brand: blogId 7742951, Zeitzone Europe/Berlin.

ABLAUF
1. Suche in Google Drive im Ordner „Content/Fertig“ alle Unterordner, die eine Datei `beitrag.json` enthalten, aber noch keine `angelegt.json`. Gibt es keine, schicke keine Nachricht und beende den Lauf.
2. Lies pro Ordner `beitrag.json`. Darin stehen: die Videodateien, die Texte pro Plattform (`beitraege`), das Transkript, Warnungen und Fehler. Ist `status` = `texte_fehlen`, schreibe die fehlenden Texte (`fehlende_texte`) selbst: passend zu `thema`, `hook_text` und `transkript`, im Stil des Skills „content-skript“, Deutsch, natürlich, keine KI-Floskeln, keine Gedankenstriche. Grenzen: Instagram und TikTok je 3–5 Hashtags, YouTube-Titel max. 60 Zeichen, Pin-Titel max. 100, Pin-Beschreibung max. 500 Zeichen.
3. Finde für jede Videodatei und `pinterest_pin.jpg` im selben Drive-Ordner den Link (https://drive.google.com/file/d/<ID>/view) über search_files bzw. get_file_metadata.
4. Termine: Hole mit getBestTimeToPostByNetwork die besten Zeiten für instagram, tiktok und youtube für die nächsten 7 Tage und mit getScheduledPosts die schon geplanten Beiträge. Wähle pro Plattform den besten freien Termin, frühestens morgen, höchstens ein Video pro Plattform und Tag. Pinterest: gleicher Tag wie Instagram, 20:00 Uhr. Die Instagram-Story: 2 Stunden nach dem Reel. Steht in `geplant` ein Wunschtermin, nimm den.
5. Lege mit createScheduledPost für jeden Eintrag in `beitraege` (außer `manuell: true`) einen eigenen Beitrag an. Immer `"draft": true`. Je Plattform:
   - Instagram Reel: providers [{"network":"instagram"}], instagramData {"type":"REEL","showReelOnFeed":true}, text = Caption, media = [Video-Link]
   - Instagram Story: instagramData {"type":"STORY"}, **kein** text, media = [Link zu story.mp4]
   - TikTok: tiktokData {"privacyOption":"PUBLIC_TO_EVERYONE","autoAddMusic":false}, text = Caption, media = [Video-Link]
   - YouTube Short: youtubeData {"type":"short","title":<titel>,"privacy":"public","madeForKids":false,"category":"SPORTS"}, text = Beschreibung
   - Pinterest: pinterestData {"boardId":<board oder "Sprinttraining">,"pinTitle":<titel>}, text = Pin-Beschreibung, media = [Link zu pinterest_pin.jpg]
   Gib bei jedem Beitrag publicationDate mit timezone Europe/Berlin an. Schlägt ein Beitrag fehl, versuche ihn nicht mit anderen Daten zu erzwingen, sondern notiere den Fehler.
6. Lege danach im selben Drive-Ordner die Datei `angelegt.json` an (normale Textdatei) mit: Zeitpunkt, je Beitrag Plattform, Typ, geplanter Termin, plannerUrl oder Fehlermeldung, und den Texten, die du ergänzt hast.
7. Schicke Tarek mit SendUserMessage eine kurze Nachricht:
   - pro Ordner: Thema, Link zu vorschau.jpg und zum Reel in Drive
   - je Plattform: Termin und Metricool-Link (plannerUrl)
   - Hinweis: „TikTok-Story bitte selbst in der TikTok-App posten: <Link zu story.mp4>“
   - alles, was fehlgeschlagen ist, und die Warnungen aus beitrag.json
   - Satz am Ende: „Alles liegt als Entwurf in Metricool. Öffne den Entwurf, prüf die Vorschau und tipp auf ‚Planen‘, dann geht er zur angegebenen Zeit raus.“

SICHERHEIT
Setze `draft` niemals auf false, ändere oder lösche keine bestehenden Beiträge in Metricool, veröffentliche nichts sofort. Ändere in Drive nichts außer dem Anlegen von `angelegt.json`. Behaupte keinen Erfolg, den ein Tool nicht bestätigt hat. Das ist ein unbeaufsichtigter Lauf: keine Rückfragen.
