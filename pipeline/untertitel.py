"""Untertitel und Hook-Text als ASS-Datei (das Format, das ffmpeg ins Bild brennen kann).

- Untertitel: 1–3 Wörter auf einmal, groß, weiß mit schwarzem Rand – gut lesbar auf dem Handy.
- Position: im sicheren Bereich, also nicht unter den Like-Buttons rechts und nicht unter der Caption unten.
- Hook-Text: oben in den ersten Sekunden.
"""

from .plattformen import BREITE, HOEHE, SICHER_LINKS, SICHER_OBEN, SICHER_RECHTS, SICHER_UNTEN
from .schnitt import Abschnitt, Wort


def neue_zeitachse(woerter: list[Wort], schnitt: list[Abschnitt]) -> list[Wort]:
    """Rechnet Wortzeiten vom Rohmaterial auf das geschnittene Video um (Hook-Wörter kommen dabei doppelt vor)."""
    neu: list[Wort] = []
    versatz = 0.0
    for a in schnitt:
        for w in woerter:
            if w.start >= a.start - 0.01 and w.start < a.ende:
                start = versatz + (w.start - a.start)
                ende = versatz + (min(w.ende, a.ende) - a.start)
                neu.append(Wort(max(0.0, start), max(start + 0.05, ende), w.text))
        versatz += a.dauer
    return neu


def gruppiere(woerter: list[Wort], max_woerter: int = 3, max_zeichen: int = 18,
              max_dauer: float = 1.8) -> list[tuple[float, float, str]]:
    """Fasst Wörter zu kurzen Einblendungen zusammen. Satzzeichen beenden eine Gruppe."""
    gruppen: list[tuple[float, float, str]] = []
    aktuell: list[Wort] = []

    def abschliessen():
        if aktuell:
            text = " ".join(w.text for w in aktuell).strip()
            gruppen.append((aktuell[0].start, aktuell[-1].ende, text))
            aktuell.clear()

    for w in woerter:
        text = w.text.strip()
        if not text:
            continue
        w = Wort(w.start, w.ende, text)
        if aktuell:
            laenge = len(" ".join(x.text for x in aktuell)) + 1 + len(text)
            zu_lang = laenge > max_zeichen or len(aktuell) >= max_woerter or w.ende - aktuell[0].start > max_dauer
            pause = w.start - aktuell[-1].ende > 0.6
            if zu_lang or pause:
                abschliessen()
        aktuell.append(w)
        if text[-1] in ".!?,;:":
            abschliessen()
    abschliessen()

    # Keine Überlappungen, jede Einblendung mindestens 0,35 s sichtbar
    fertig = []
    for i, (start, ende, text) in enumerate(gruppen):
        naechster = gruppen[i + 1][0] if i + 1 < len(gruppen) else None
        ende = max(ende, start + 0.35)
        if naechster is not None:
            ende = min(ende, naechster)
        fertig.append((start, ende, text))
    return fertig


def zeit_ass(sekunden: float) -> str:
    centi = int(round(max(0.0, sekunden) * 100))
    h, rest = divmod(centi, 360000)
    m, rest = divmod(rest, 6000)
    s, cs = divmod(rest, 100)
    return f"{h}:{m:02d}:{s:02d}.{cs:02d}"


def zeit_srt(sekunden: float) -> str:
    milli = int(round(max(0.0, sekunden) * 1000))
    h, rest = divmod(milli, 3600000)
    m, rest = divmod(rest, 60000)
    s, ms = divmod(rest, 1000)
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


def _sauber(text: str) -> str:
    """Zeichen entfernen, die im ASS-Format eine Sonderbedeutung haben."""
    return text.replace("\\", "").replace("{", "(").replace("}", ")").replace("\n", " ")


def ass_datei(gruppen: list[tuple[float, float, str]], schrift: str, hook_text: str = "",
              hook_dauer: float = 0.0, breite: int = BREITE, hoehe: int = HOEHE) -> str:
    kopf = f"""[Script Info]
ScriptType: v4.00+
PlayResX: {breite}
PlayResY: {hoehe}
WrapStyle: 0
ScaledBorderAndShadow: yes

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Untertitel,{schrift},76,&H00FFFFFF,&H00FFFFFF,&H00000000,&H64000000,-1,0,0,0,100,100,0,0,1,6,2,2,{SICHER_LINKS},{SICHER_RECHTS},{SICHER_UNTEN},1
Style: Hook,{schrift},84,&H00FFFFFF,&H00FFFFFF,&H33000000,&H96000000,-1,0,0,0,100,100,0,0,3,14,0,8,{SICHER_LINKS},{SICHER_RECHTS},{SICHER_OBEN},1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
"""
    zeilen = []
    if hook_text and hook_dauer > 0:
        zeilen.append(f"Dialogue: 1,{zeit_ass(0)},{zeit_ass(hook_dauer)},Hook,,0,0,0,,{_sauber(hook_text)}")
    for start, ende, text in gruppen:
        zeilen.append(f"Dialogue: 0,{zeit_ass(start)},{zeit_ass(ende)},Untertitel,,0,0,0,,{_sauber(text)}")
    return kopf + "\n".join(zeilen) + "\n"


def pin_ass(titel: str, schrift: str, breite: int = 1000, hoehe: int = 1500) -> str:
    """Titel-Overlay für das Pinterest-Bild (unteres Drittel, Kasten für gute Lesbarkeit)."""
    return f"""[Script Info]
ScriptType: v4.00+
PlayResX: {breite}
PlayResY: {hoehe}
WrapStyle: 0
ScaledBorderAndShadow: yes

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Pin,{schrift},78,&H00FFFFFF,&H00FFFFFF,&H32000000,&H32000000,-1,0,0,0,100,100,0,0,3,18,0,2,70,70,140,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,{zeit_ass(0)},{zeit_ass(10)},Pin,,0,0,0,,{_sauber(titel)}
"""


def srt_datei(gruppen: list[tuple[float, float, str]]) -> str:
    """Normale Untertiteldatei, z. B. zum zusätzlichen Hochladen bei YouTube."""
    bloecke = [f"{i}\n{zeit_srt(s)} --> {zeit_srt(e)}\n{t}\n" for i, (s, e, t) in enumerate(gruppen, 1)]
    return "\n".join(bloecke)
