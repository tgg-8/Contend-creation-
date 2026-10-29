"""Findet neue Rohmaterial-Ordner, die fertig synchronisiert sind und noch nicht verarbeitet wurden.

Aufbau in Google Drive:
    Content/Rohmaterial/2026-11-03_whoop-schlaf/   <- ein Ordner = ein Beitrag
        clip1.mp4, clip2.mov, foto.jpg ...         <- werden nach Dateiname sortiert aneinandergehängt
    Content/Fertig/2026-11-03_whoop-schlaf/        <- hier landet das Ergebnis
"""

import re
import time
from dataclasses import dataclass
from pathlib import Path

from .einstellungen import Einstellungen

VIDEO_ENDUNGEN = {".mp4", ".mov", ".m4v", ".avi", ".mkv", ".webm"}
BILD_ENDUNGEN = {".jpg", ".jpeg", ".png", ".webp"}
NICHT_UNTERSTUETZT = {".heic", ".heif"}  # iPhone-Fotoformat, kann ffmpeg nicht überall lesen

ORDNER_MUSTER = re.compile(r"^(\d{4}-\d{2}-\d{2})")

# Ergebnisdateien, an denen man den Stand eines Beitrags erkennt
FERTIG_MARKER = "beitrag.json"
FEHLER_MARKER = "fehler.txt"


@dataclass
class Auftrag:
    name: str           # Ordnername, z. B. "2026-11-03_whoop-schlaf"
    datum: str          # "2026-11-03"
    ordner: Path        # Rohmaterial-Ordner
    dateien: list[Path]


def natuerlich_sortiert(pfade: list[Path]) -> list[Path]:
    """clip2 vor clip10 – so wie man es erwarten würde."""
    def schluessel(p: Path):
        return [int(t) if t.isdigit() else t.lower() for t in re.split(r"(\d+)", p.name)]
    return sorted(pfade, key=schluessel)


def wird_ignoriert(pfad: Path) -> bool:
    name = pfad.name
    return (
        name.startswith(".")
        or name.startswith("~$")
        or name.lower() in ("desktop.ini", "thumbs.db")
        or pfad.suffix.lower() in (".tmp", ".part", ".crdownload", ".gdoc", ".gsheet")
    )


def medien_in(ordner: Path) -> tuple[list[Path], list[Path]]:
    """Gibt (verwendbare Medien, nicht unterstützte Dateien) zurück."""
    medien, unbekannt = [], []
    for pfad in ordner.iterdir():
        if not pfad.is_file() or wird_ignoriert(pfad):
            continue
        endung = pfad.suffix.lower()
        if endung in VIDEO_ENDUNGEN or endung in BILD_ENDUNGEN:
            medien.append(pfad)
        elif endung in NICHT_UNTERSTUETZT or endung not in (".md", ".txt"):
            unbekannt.append(pfad)
    return natuerlich_sortiert(medien), unbekannt


def ist_bild(pfad: Path) -> bool:
    return pfad.suffix.lower() in BILD_ENDUNGEN


def ist_stabil(dateien: list[Path], ruhezeit: int, pause: float = 5.0) -> bool:
    """Fertig synchronisiert = länger nicht geändert UND Größe bleibt während einer kurzen Pause gleich."""
    jetzt = time.time()
    if any(jetzt - d.stat().st_mtime < ruhezeit for d in dateien):
        return False
    groessen = [d.stat().st_size for d in dateien]
    if pause:
        time.sleep(pause)
    return groessen == [d.stat().st_size for d in dateien] and all(g > 0 for g in groessen)


def finde_neue_auftraege(e: Einstellungen, protokoll, pause: float = 5.0) -> list[Auftrag]:
    if not e.rohmaterial.exists():
        protokoll.warnung(f"Ordner fehlt: {e.rohmaterial}")
        return []

    auftraege = []
    for ordner in sorted(p for p in e.rohmaterial.iterdir() if p.is_dir()):
        treffer = ORDNER_MUSTER.match(ordner.name)
        if not treffer:
            continue
        ziel = e.fertig / ordner.name
        if (ziel / FERTIG_MARKER).exists() or (ziel / FEHLER_MARKER).exists():
            continue  # schon erledigt (oder fehlgeschlagen und wartet auf dich)
        medien, unbekannt = medien_in(ordner)
        if not medien:
            continue
        if not ist_stabil(medien, e.ruhezeit, pause):
            protokoll.info(f"{ordner.name}: Dateien werden noch synchronisiert, nächster Versuch später")
            continue
        for datei in unbekannt:
            protokoll.warnung(
                f"{ordner.name}: '{datei.name}' wird übersprungen (Format nicht unterstützt"
                + ("; iPhone: Einstellungen > Kamera > Formate > 'Maximale Kompatibilität'" if datei.suffix.lower() in NICHT_UNTERSTUETZT else "")
                + ")"
            )
        auftraege.append(Auftrag(ordner.name, treffer.group(1), ordner, medien))
    return auftraege
