# Vorlage für eine Idee-Datei

Die Claude-Aufgabe „Montags-Content-Ideen“ legt pro Idee eine Datei nach diesem Muster an:
`Content/Ideen/2026-11-02_whoop-schlaf.md` (normale Textdatei, **kein** Google-Doc).

Die Überschriften (`## …`) müssen genau so heißen, damit die Pipeline sie findet.
Fehlt ein Abschnitt, ist das kein Problem: Die Aufgabe „Entwürfe anlegen“ ergänzt ihn später aus dem Transkript.

```markdown
# Whoop zeigt: 4 Stunden Schlaf vor dem Training

## Thema
Was schlechter Schlaf mit meiner Sprintzeit macht (Aufhänger: neue Schlafstudie, Quelle: …)

## Ablauf
1. Whoop-Screen mit Schlafwert zeigen
2. 30-m-Sprint filmen, Zeit einblenden
3. Fazit in die Kamera

## Hook-Text
4 Stunden Schlaf. Dann Sprinttraining.

## Instagram Caption
Gestern nur 4 Stunden geschlafen und heute trotzdem auf die Bahn. Mein Whoop war nicht begeistert 😅
Was meint ihr, wie viel langsamer war ich?

## Instagram Hashtags
#sprint #leichtathletik #whoop #schlaf #training

## TikTok Caption
4h Schlaf vs. Sprinttraining. Wer gewinnt? 😴🏃‍♂️

## TikTok Hashtags
#sprint #leichtathletik #whoop #fyp

## YouTube Titel
4 Stunden Schlaf vor dem Sprinttraining 😴

## YouTube Beschreibung
Was Schlafmangel mit meiner Sprintzeit macht. Ich bin Tarek, 18, Sprinter an der Sportschule Potsdam.

## YouTube Hashtags
#shorts #sprint #leichtathletik

## Pinterest Board
Sprinttraining

## Pin-Titel
Schlaf und Sprinttraining: Was 4 Stunden Schlaf ausmachen

## Pin-Beschreibung
Wie wichtig Schlaf für Sprinter ist, ehrlich getestet mit Whoop-Daten. Tipps für bessere Regeneration im Leistungssport.

## Pin-Text
4 h Schlaf vs. Sprint
```

`Pin-Text` ist der kurze Text, der groß auf das Pinterest-Bild kommt. Fehlt er, nimmt die Pipeline den Pin-Titel.
