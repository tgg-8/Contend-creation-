"""Kurzes Lauf-Protokoll: Was wurde erkannt, geschnitten, angelegt – und was ist fehlgeschlagen?"""

from datetime import datetime
from pathlib import Path

ERKANNT = "ERKANNT"
GESCHNITTEN = "GESCHNITTEN"
ANGELEGT = "ANGELEGT"
INFO = "INFO"
WARNUNG = "WARNUNG"
FEHLER = "FEHLER"


class Protokoll:
    def __init__(self, *dateien: Path):
        self.dateien = [d for d in dateien if d is not None]
        self.eintraege: list[tuple[str, str]] = []
        for datei in self.dateien:
            datei.parent.mkdir(parents=True, exist_ok=True)

    def _schreibe(self, stufe: str, text: str) -> None:
        zeile = f"{datetime.now():%Y-%m-%d %H:%M:%S} [{stufe}] {text}"
        print(zeile, flush=True)
        self.eintraege.append((stufe, text))
        for datei in self.dateien:
            with datei.open("a", encoding="utf-8") as f:
                f.write(zeile + "\n")

    def erkannt(self, text: str) -> None:
        self._schreibe(ERKANNT, text)

    def geschnitten(self, text: str) -> None:
        self._schreibe(GESCHNITTEN, text)

    def angelegt(self, text: str) -> None:
        self._schreibe(ANGELEGT, text)

    def info(self, text: str) -> None:
        self._schreibe(INFO, text)

    def warnung(self, text: str) -> None:
        self._schreibe(WARNUNG, text)

    def fehler(self, text: str) -> None:
        self._schreibe(FEHLER, text)

    def nur(self, stufe: str) -> list[str]:
        return [text for s, text in self.eintraege if s == stufe]

    def zusammenfassung(self) -> str:
        teile = ["", "===== Zusammenfassung ====="]
        for titel, stufe in (
            ("Erkannt", ERKANNT),
            ("Geschnitten", GESCHNITTEN),
            ("Angelegt", ANGELEGT),
            ("Warnungen", WARNUNG),
            ("Fehlgeschlagen", FEHLER),
        ):
            eintraege = self.nur(stufe)
            teile.append(f"{titel}: {len(eintraege)}")
            teile.extend(f"  - {e}" for e in eintraege)
        text = "\n".join(teile)
        print(text, flush=True)
        for datei in self.dateien:
            with datei.open("a", encoding="utf-8") as f:
                f.write(text + "\n")
        return text
