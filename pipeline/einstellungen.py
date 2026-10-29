"""Liest die Einstellungen aus der .env-Datei (und aus Umgebungsvariablen)."""

import os
from dataclasses import dataclass
from pathlib import Path

PROJEKT = Path(__file__).resolve().parent.parent


def lies_env_datei(pfad: Path) -> dict[str, str]:
    """Einfacher .env-Leser: eine Zeile = SCHLUESSEL=Wert, '#' leitet Kommentare ein."""
    werte: dict[str, str] = {}
    if not pfad.exists():
        return werte
    for zeile in pfad.read_text(encoding="utf-8").splitlines():
        zeile = zeile.strip()
        if not zeile or zeile.startswith("#") or "=" not in zeile:
            continue
        schluessel, wert = zeile.split("=", 1)
        werte[schluessel.strip()] = wert.strip().strip('"').strip("'")
    return werte


@dataclass
class Einstellungen:
    content_ordner: Path
    whisper_modell: str = "small"
    querformat: str = "blur"
    musik_datei: Path | None = None
    musik_lautstaerke: float = 0.12
    schrift: str = "Arial"
    pruef_intervall: int = 120
    # So lange (Sekunden) darf sich eine Datei nicht mehr ändern, bevor sie als fertig synchronisiert gilt
    ruhezeit: int = 60

    @property
    def rohmaterial(self) -> Path:
        return self.content_ordner / "Rohmaterial"

    @property
    def ideen(self) -> Path:
        return self.content_ordner / "Ideen"

    @property
    def fertig(self) -> Path:
        return self.content_ordner / "Fertig"


def lade(env_pfad: Path | None = None, content_ordner: Path | None = None) -> Einstellungen:
    env = lies_env_datei(env_pfad or PROJEKT / ".env")

    def wert(schluessel: str, standard: str = "") -> str:
        return os.environ.get(schluessel) or env.get(schluessel) or standard

    ordner = content_ordner or (Path(wert("CONTENT_ORDNER")).expanduser() if wert("CONTENT_ORDNER") else None)
    if ordner is None:
        raise SystemExit(
            "CONTENT_ORDNER fehlt. Kopiere .env.example nach .env und trage den Pfad zu deinem "
            "Google-Drive-Ordner 'Content' ein."
        )

    musik = wert("MUSIK_DATEI")
    querformat = wert("QUERFORMAT", "blur").lower()
    if querformat not in ("blur", "crop"):
        raise SystemExit(f"QUERFORMAT muss 'blur' oder 'crop' sein, nicht '{querformat}'.")

    return Einstellungen(
        content_ordner=ordner,
        whisper_modell=wert("WHISPER_MODELL", "small"),
        querformat=querformat,
        musik_datei=Path(musik).expanduser() if musik else None,
        musik_lautstaerke=float(wert("MUSIK_LAUTSTAERKE", "0.12")),
        schrift=wert("SCHRIFT", "Arial"),
        pruef_intervall=int(wert("PRUEF_INTERVALL", "120")),
    )
