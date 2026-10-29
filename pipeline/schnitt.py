"""Die Schnitt-Logik: Pausen und schwache Stellen raus, stärkster Moment als Hook nach vorn, passende Länge.

Alles hier ist reine Rechnerei ohne ffmpeg, damit man es gut testen kann.
Die Zeitachse wird in Stücke von 0,5 Sekunden ("Bins") eingeteilt. Jedes Stück bekommt eine Punktzahl
aus Bewegung, Lautstärke und Sprache.
"""

import math
from dataclasses import dataclass

BIN = 0.5


@dataclass(frozen=True)
class Abschnitt:
    start: float
    ende: float

    @property
    def dauer(self) -> float:
        return self.ende - self.start


@dataclass(frozen=True)
class Wort:
    start: float
    ende: float
    text: str


@dataclass
class Bewertung:
    dauer: float
    bewegung: list[float]   # 0..1
    laut: list[float]       # 0..1
    sprache: list[bool]
    punkte: list[float]


def _begrenze(x: float) -> float:
    return max(0.0, min(1.0, x))


def perzentil(werte: list[float], anteil: float) -> float:
    if not werte:
        return 0.0
    sortiert = sorted(werte)
    return sortiert[min(len(sortiert) - 1, int(anteil * (len(sortiert) - 1) + 0.5))]


def _mittel_pro_bin(werte: list[tuple[float, float]], anzahl: int, standard: float) -> list[float]:
    summen, zaehler = [0.0] * anzahl, [0] * anzahl
    for zeit, wert in werte:
        i = int(zeit / BIN)
        if 0 <= i < anzahl:
            summen[i] += wert
            zaehler[i] += 1
    return [summen[i] / zaehler[i] if zaehler[i] else standard for i in range(anzahl)]


def bewerte(dauer: float, lautheit: list[tuple[float, float]], bewegung: list[tuple[float, float]],
            woerter: list[Wort]) -> Bewertung:
    anzahl = max(1, math.ceil(dauer / BIN))

    # Lautheit: -50 LUFS (still) .. -10 LUFS (laut) -> 0..1
    laut = [_begrenze((l + 50) / 40) for l in _mittel_pro_bin(lautheit, anzahl, -70.0)]

    # Bewegung: harte Schnitte zwischen Clips (sehr hohe Werte) zählen nicht als Action
    roh = _mittel_pro_bin([(t, w if w <= 0.35 else 0.0) for t, w in bewegung], anzahl, 0.0)
    hoch = max(perzentil(roh, 0.95), 0.03)
    bew = [_begrenze(r / hoch) for r in roh]

    sprache = [False] * anzahl
    for w in woerter:
        for i in range(int(w.start / BIN), min(anzahl, math.ceil(w.ende / BIN))):
            sprache[i] = True

    punkte = [0.5 * b + 0.3 * l + (0.4 if s else 0.0) for b, l, s in zip(bew, laut, sprache)]
    return Bewertung(dauer, bew, laut, sprache, punkte)


def verschmelze(abschnitte: list[Abschnitt], luecke: float = 0.0) -> list[Abschnitt]:
    ergebnis: list[Abschnitt] = []
    for a in sorted(abschnitte, key=lambda a: a.start):
        if ergebnis and a.start <= ergebnis[-1].ende + luecke:
            ergebnis[-1] = Abschnitt(ergebnis[-1].start, max(ergebnis[-1].ende, a.ende))
        else:
            ergebnis.append(a)
    return ergebnis


def behalte_abschnitte(b: Bewertung) -> list[Abschnitt]:
    """Wirft Pausen (leise UND kaum Bewegung) und schwache Stellen raus. Gesprochenes bleibt immer drin."""
    anzahl = len(b.punkte)
    oben = perzentil(b.punkte, 0.9)
    behalten = []
    for i in range(anzahl):
        pause = b.laut[i] < 0.25 and b.bewegung[i] < 0.15
        schwach = b.punkte[i] < 0.35 * oben
        behalten.append(b.sprache[i] or not (pause or schwach))

    # einzelne Lücken von 0,5 s schließen, damit es nicht hektisch springt
    for i in range(1, anzahl - 1):
        if not behalten[i] and behalten[i - 1] and behalten[i + 1]:
            behalten[i] = True

    abschnitte, start = [], None
    for i, ja in enumerate(behalten + [False]):
        if ja and start is None:
            start = i
        elif not ja and start is not None:
            laenge = i - start
            if laenge > 1 or any(b.sprache[start:i]):   # Einzelstücke ohne Sprache sind meist Zufall
                abschnitte.append(Abschnitt(start * BIN, min(b.dauer, i * BIN)))
            start = None

    return abschnitte or [Abschnitt(0.0, b.dauer)]


def an_wortgrenzen(abschnitte: list[Abschnitt], woerter: list[Wort], dauer: float) -> list[Abschnitt]:
    """Schnitte nie mitten im Wort: Grenzen bis zum Wortanfang/-ende verschieben."""
    neu = []
    for a in abschnitte:
        start, ende = a.start, a.ende
        for w in woerter:
            if w.start < start < w.ende:
                start = w.start
            if w.start < ende < w.ende:
                ende = w.ende
        neu.append(Abschnitt(max(0.0, start - 0.05), min(dauer, ende + 0.08)))
    return verschmelze(neu)


def finde_hook(b: Bewertung, woerter: list[Wort], laenge: float = 2.0, max_laenge: float = 3.0) -> Abschnitt | None:
    """Stärkster 2-Sekunden-Moment (vor allem Action und Lautstärke). None, wenn er eh schon vorne ist."""
    if b.dauer < 6:
        return None
    k = int(laenge / BIN)
    werte = [0.6 * bw + 0.4 * l + (0.2 if s else 0.0) for bw, l, s in zip(b.bewegung, b.laut, b.sprache)]
    if len(werte) < k:
        return None
    beste = max(range(len(werte) - k + 1), key=lambda i: sum(werte[i:i + k]))
    start, ende = beste * BIN, min(b.dauer, (beste + k) * BIN)
    if start < 1.0:
        return None
    for w in woerter:
        if w.start < ende < w.ende and w.ende - start <= max_laenge:
            ende = w.ende
        if w.start < start < w.ende and ende - w.start <= max_laenge:
            start = w.start
    return Abschnitt(start, ende)


def _dichte(a: Abschnitt, b: Bewertung) -> float:
    von, bis = int(a.start / BIN), max(int(a.start / BIN) + 1, math.ceil(a.ende / BIN))
    stuecke = b.punkte[von:bis]
    return sum(stuecke) / len(stuecke) if stuecke else 0.0


def _vor_wort(zeit: float, woerter: list[Wort]) -> float:
    for w in woerter:
        if w.start < zeit < w.ende:
            return w.start
    return zeit


def plane(abschnitte: list[Abschnitt], hook: Abschnitt | None, ziel: float, b: Bewertung,
          woerter: list[Wort]) -> list[Abschnitt]:
    """Endgültige Schnittliste für eine Plattform: Hook zuerst, dann der Rest, höchstens `ziel` Sekunden."""
    koerper = list(abschnitte)
    hook_dauer = hook.dauer if hook else 0.0

    def gesamt() -> float:
        return hook_dauer + sum(a.dauer for a in koerper)

    # 1. Schwächste Abschnitte rauswerfen, solange danach noch genug übrig bleibt
    while gesamt() > ziel and len(koerper) > 1:
        schwaechster = min(koerper, key=lambda a: _dichte(a, b))
        if gesamt() - schwaechster.dauer < 0.6 * ziel:
            break
        koerper.remove(schwaechster)

    # 2. Immer noch zu lang: von hinten kürzen, möglichst vor einem Wort
    i = len(koerper) - 1
    while gesamt() > ziel + 0.01 and i >= 0:
        a = koerper[i]
        ueberschuss = gesamt() - ziel
        if a.dauer - ueberschuss >= 1.0:
            ende = _vor_wort(a.ende - ueberschuss, woerter)
            if ende - a.start < 1.0:
                ende = a.ende - ueberschuss
            koerper[i] = Abschnitt(a.start, ende)
        else:
            koerper.pop(i)
        i -= 1

    return ([hook] if hook else []) + koerper


def gesamtdauer(schnitt: list[Abschnitt]) -> float:
    return sum(a.dauer for a in schnitt)
