"""Welche Videos pro Plattform entstehen und welche Text-Grenzen gelten.

Die Ziellängen sind Obergrenzen: Ist das Material kürzer, bleibt es kürzer.
Wer etwas ändern will, ändert es nur hier.
"""

from dataclasses import dataclass

BREITE, HOEHE = 1080, 1920
FPS = 30


@dataclass(frozen=True)
class Ausgabe:
    name: str           # interner Name
    datei: str          # Dateiname im Fertig-Ordner
    ziel_sekunden: float
    hook_text: bool = True   # Hook-Text in den ersten Sekunden einblenden?
    untertitel: bool = True


# Kurz und knackig performt auf Reels/TikTok am besten, Shorts dürfen etwas länger sein,
# eine Story ist ein schneller Teaser (eine Story-Kachel).
AUSGABEN = [
    Ausgabe("instagram_reel", "instagram_reel.mp4", ziel_sekunden=25),
    Ausgabe("tiktok", "tiktok.mp4", ziel_sekunden=30),
    Ausgabe("youtube_short", "youtube_short.mp4", ziel_sekunden=40),
    Ausgabe("story", "story.mp4", ziel_sekunden=15),
]

# Text-Grenzen der Plattformen (harte Grenzen bzw. sinnvolle Obergrenzen)
GRENZEN = {
    "instagram_caption": 2200,
    "instagram_hashtags": 5,      # Instagram erlaubt nur noch wenige Hashtags pro Beitrag
    "tiktok_caption": 2200,
    "tiktok_hashtags": 5,
    "youtube_titel": 100,
    "youtube_titel_empfohlen": 60,
    "youtube_beschreibung": 5000,
    "youtube_hashtags": 3,
    "pinterest_titel": 100,
    "pinterest_beschreibung": 500,
    "hook_text_woerter": 9,
}

# Sicherer Bereich für Text: oben Profil/Suche, rechts Like-Buttons, unten Caption und Ton.
# Werte in Pixeln bei 1080x1920.
SICHER_OBEN = 300      # Hook-Text beginnt unterhalb davon
SICHER_UNTEN = 640     # Untertitel enden oberhalb davon (also bei y = 1280)
SICHER_LINKS = 90
SICHER_RECHTS = 180    # rechts mehr Platz wegen der Buttons
