"""Misst das Material mit ffmpeg: Länge, Ausrichtung, Lautstärke und Bewegung über die Zeit."""

import json
import math
import subprocess
from pathlib import Path


class FfmpegFehler(RuntimeError):
    pass


def ausfuehren(befehl: list[str], cwd: Path | None = None) -> str:
    """Startet ffmpeg/ffprobe und gibt die Ausgabe zurück. Bei Fehler: letzte Zeilen der Meldung."""
    ergebnis = subprocess.run(befehl, cwd=cwd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if ergebnis.returncode != 0:
        rest = "\n".join(ergebnis.stderr.strip().splitlines()[-8:])
        raise FfmpegFehler(f"{Path(befehl[0]).name} fehlgeschlagen:\n{rest}")
    return ergebnis.stdout


def pruefe_ffmpeg() -> None:
    for programm in ("ffmpeg", "ffprobe"):
        try:
            ausfuehren([programm, "-version"])
        except FileNotFoundError:
            raise SystemExit(f"'{programm}' wurde nicht gefunden. Bitte ffmpeg installieren (siehe README).")


def probe(pfad: Path) -> dict:
    return json.loads(ausfuehren([
        "ffprobe", "-v", "error", "-print_format", "json", "-show_streams", "-show_format", str(pfad),
    ]))


def dauer(info: dict) -> float:
    try:
        return float(info["format"]["duration"])
    except (KeyError, ValueError):
        return 0.0


def hat_audio(info: dict) -> bool:
    return any(s.get("codec_type") == "audio" for s in info.get("streams", []))


def bildgroesse(info: dict) -> tuple[int, int]:
    """Breite/Höhe so, wie das Video angezeigt wird (Handy-Drehung berücksichtigt)."""
    video = next((s for s in info.get("streams", []) if s.get("codec_type") == "video"), None)
    if not video:
        raise FfmpegFehler("Keine Videospur gefunden")
    breite, hoehe = int(video["width"]), int(video["height"])
    drehung = 0
    for daten in video.get("side_data_list", []) or []:
        if "rotation" in daten:
            drehung = int(float(daten["rotation"]))
    drehung = drehung or int(video.get("tags", {}).get("rotate", 0) or 0)
    if abs(drehung) % 180 == 90:
        breite, hoehe = hoehe, breite
    return breite, hoehe


def lies_metadaten(text: str, schluessel: str) -> list[tuple[float, float]]:
    """Wertet die Ausgabe von ffmpegs metadata=print aus: Liste von (Zeit, Wert)."""
    werte: list[tuple[float, float]] = []
    zeit = None
    for zeile in text.splitlines():
        if "pts_time:" in zeile:
            try:
                zeit = float(zeile.split("pts_time:")[1].split()[0])
            except (IndexError, ValueError):
                zeit = None
        elif zeile.startswith(schluessel + "=") and zeit is not None:
            roh = zeile.split("=", 1)[1].strip()
            try:
                wert = float(roh)
            except ValueError:
                wert = float("nan")
            if math.isnan(wert) or math.isinf(wert):
                wert = -70.0 if "r128" in schluessel else 0.0
            werte.append((zeit, wert))
    return werte


def lautheit(quelle: Path, arbeit: Path) -> list[tuple[float, float]]:
    """Kurzzeit-Lautheit (LUFS, etwa alle 0,1 s). Leise = Pause."""
    datei = arbeit / "lautheit.txt"
    ausfuehren([
        "ffmpeg", "-hide_banner", "-nostats", "-y", "-i", str(quelle), "-vn",
        "-af", f"ebur128=metadata=1,ametadata=mode=print:key=lavfi.r128.M:file={datei.name}",
        "-f", "null", "-",
    ], cwd=arbeit)
    return lies_metadaten(datei.read_text(encoding="utf-8", errors="replace"), "lavfi.r128.M")


def bewegung(quelle: Path, arbeit: Path) -> list[tuple[float, float]]:
    """Bildänderung von Frame zu Frame (0 = Standbild, hoch = viel Action)."""
    datei = arbeit / "bewegung.txt"
    ausfuehren([
        "ffmpeg", "-hide_banner", "-nostats", "-y", "-i", str(quelle), "-an",
        "-vf", f"scale=180:320,select='gte(scene\\,0)',metadata=mode=print:key=lavfi.scene_score:file={datei.name}",
        "-f", "null", "-",
    ], cwd=arbeit)
    return lies_metadaten(datei.read_text(encoding="utf-8", errors="replace"), "lavfi.scene_score")
