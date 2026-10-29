"""Liest die Content-Idee (Markdown-Datei) aus dem Ordner Content/Ideen.

Die Idee-Datei hat feste Überschriften (## ...). Beispiel siehe docs/IDEE_VORLAGE.md.
Zuordnung zum Rohmaterial-Ordner, in dieser Reihenfolge:
  1. eine Datei "idee.md" direkt im Rohmaterial-Ordner
  2. Content/Ideen/<gleicher Name wie der Rohmaterial-Ordner>.md
  3. genau eine Idee-Datei mit demselben Datum am Anfang des Namens
"""

import re
import unicodedata
from pathlib import Path

# Überschrift (normalisiert) -> interner Schlüssel. Die erste passende Regel gewinnt.
FELDER = [
    ("thema", "thema"),
    ("hooktext", "hook_text"),
    ("hook", "hook_text"),
    ("instagramcaption", "instagram_caption"),
    ("instagramhashtags", "instagram_hashtags"),
    ("tiktokcaption", "tiktok_caption"),
    ("tiktokhashtags", "tiktok_hashtags"),
    ("youtubetitel", "youtube_titel"),
    ("youtubebeschreibung", "youtube_beschreibung"),
    ("youtubehashtags", "youtube_hashtags"),
    ("pinterestboard", "pinterest_board"),
    ("pinteresttitel", "pinterest_titel"),
    ("pinterestbeschreibung", "pinterest_beschreibung"),
    ("pintitel", "pinterest_titel"),
    ("pinbeschreibung", "pinterest_beschreibung"),
    ("pintext", "pin_text"),
    ("geplant", "geplant"),
]


def normalisiere(text: str) -> str:
    text = text.lower().replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
    text = unicodedata.normalize("NFKD", text)
    return re.sub(r"[^a-z0-9]", "", text)


def lies_idee(pfad: Path) -> dict[str, str]:
    felder: dict[str, str] = {}
    aktuell = None
    zeilen: list[str] = []

    def speichern():
        if aktuell and aktuell not in felder:
            felder[aktuell] = "\n".join(zeilen).strip()

    for zeile in pfad.read_text(encoding="utf-8").splitlines():
        if zeile.startswith("## "):
            speichern()
            ueberschrift = normalisiere(zeile[3:])
            aktuell = next((k for muster, k in FELDER if ueberschrift.startswith(muster)), None)
            zeilen = []
        elif aktuell:
            zeilen.append(zeile)
    speichern()
    return {k: v for k, v in felder.items() if v}


def hashtags(text: str) -> list[str]:
    """'#sprint, #training leichtathletik' -> ['#sprint', '#training', '#leichtathletik'] (ohne Doppelte)."""
    ergebnis: list[str] = []
    for wort in re.split(r"[\s,;]+", text or ""):
        wort = wort.strip().lstrip("-•*").strip()
        if not wort:
            continue
        tag = "#" + wort.lstrip("#")
        if len(tag) > 1 and tag.lower() not in (t.lower() for t in ergebnis):
            ergebnis.append(tag)
    return ergebnis


def finde_ideendatei(rohordner: Path, ideen_ordner: Path, name: str, datum: str) -> tuple[Path | None, str]:
    """Gibt (Pfad oder None, Hinweis) zurück."""
    eigene = rohordner / "idee.md"
    if eigene.exists():
        return eigene, "idee.md im Rohmaterial-Ordner"
    if not ideen_ordner.exists():
        return None, f"Ordner {ideen_ordner} fehlt"
    kandidaten = [p for p in ideen_ordner.iterdir() if p.suffix.lower() in (".md", ".txt") and p.is_file()]
    for p in kandidaten:
        if p.stem == name:
            return p, "gleicher Name wie der Rohmaterial-Ordner"
    gleiches_datum = [p for p in kandidaten if p.stem.startswith(datum)]
    if len(gleiches_datum) == 1:
        return gleiches_datum[0], "einzige Idee mit demselben Datum"
    if len(gleiches_datum) > 1:
        namen = ", ".join(sorted(p.name for p in gleiches_datum))
        return None, f"mehrere Ideen mit Datum {datum} ({namen}) – benenne den Rohmaterial-Ordner wie die passende Datei"
    gdocs = [p for p in ideen_ordner.iterdir() if p.suffix.lower() == ".gdoc"]
    if gdocs:
        return None, "nur Google-Docs gefunden; die Idee muss als normale Textdatei (.md) gespeichert sein"
    return None, "keine passende Idee-Datei gefunden"
