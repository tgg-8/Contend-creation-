"""Startpunkt. Aufruf aus dem Projektordner:

    python -m pipeline test mein_clip.mp4      # Testlauf mit einem Beispielclip (fasst Drive nicht an)
    python -m pipeline lauf                    # einmal nach neuen Ordnern schauen und verarbeiten
    python -m pipeline wache                   # dauerhaft laufen und alle paar Minuten schauen
"""

import argparse
import shutil
import sys
import time
from datetime import date
from pathlib import Path

from . import einstellungen
from .analyse import pruefe_ffmpeg
from .ordner import finde_neue_auftraege
from .protokoll import Protokoll
from .verarbeitung import verarbeite


def ein_durchlauf(e: einstellungen.Einstellungen, pause: float = 5.0) -> int:
    p = Protokoll(einstellungen.PROJEKT / "logs" / f"{date.today()}.log")
    auftraege = finde_neue_auftraege(e, p, pause)
    if not auftraege:
        p.info("Nichts Neues im Rohmaterial-Ordner")
    for auftrag in auftraege:
        verarbeite(auftrag, e)
    return len(auftraege)


def testlauf(clips: list[Path], idee: Path | None) -> None:
    """Baut einen kleinen Content-Ordner unter test_ausgabe/ und verarbeitet nur diese Clips."""
    basis = einstellungen.PROJEKT / "test_ausgabe" / "Content"
    name = f"{date.today()}_test"
    roh = basis / "Rohmaterial" / name
    if (basis / "Fertig" / name).exists():
        shutil.rmtree(basis / "Fertig" / name)
    shutil.rmtree(roh, ignore_errors=True)
    roh.mkdir(parents=True)
    (basis / "Ideen").mkdir(parents=True, exist_ok=True)
    for clip in clips:
        if not clip.exists():
            raise SystemExit(f"Datei nicht gefunden: {clip}")
        shutil.copy2(clip, roh / clip.name)
    if idee:
        shutil.copyfile(idee, basis / "Ideen" / f"{name}.md")

    e = einstellungen.lade(content_ordner=basis)
    e.ruhezeit = 0
    ein_durchlauf(e, pause=0)
    print(f"\nErgebnis liegt in: {basis / 'Fertig' / name}")


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="python -m pipeline", description="Tareks Content-Pipeline")
    befehle = parser.add_subparsers(dest="befehl", required=True)
    befehle.add_parser("lauf", help="einmal nach neuen Rohmaterial-Ordnern schauen")
    befehle.add_parser("wache", help="dauerhaft laufen (Strg+C beendet)")
    test = befehle.add_parser("test", help="Testlauf mit eigenen Clips, ohne Drive")
    test.add_argument("clips", nargs="+", type=Path)
    test.add_argument("--idee", type=Path, help="optionale Idee-Datei (.md) für die Texte")
    args = parser.parse_args(argv)

    pruefe_ffmpeg()
    if args.befehl == "test":
        testlauf(args.clips, args.idee)
        return

    e = einstellungen.lade()
    if args.befehl == "lauf":
        ein_durchlauf(e)
        return

    print(f"Wächter läuft. Schaue alle {e.pruef_intervall} s in {e.rohmaterial} nach. Beenden mit Strg+C.")
    try:
        while True:
            ein_durchlauf(e)
            time.sleep(e.pruef_intervall)
    except KeyboardInterrupt:
        print("\nWächter beendet.")


if __name__ == "__main__":
    main(sys.argv[1:])
