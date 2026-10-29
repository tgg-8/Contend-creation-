"""Deutsche Spracherkennung mit Whisper (faster-whisper), komplett lokal und kostenlos.

Beim allerersten Lauf lädt Whisper das Sprachmodell einmalig herunter (bei "small" ca. 500 MB).
"""

from pathlib import Path

from .schnitt import Wort


def transkribiere(datei: Path, modell: str, protokoll) -> list[Wort] | None:
    """Gibt die erkannten Wörter mit Zeitstempeln zurück, oder None, wenn Whisper nicht verfügbar ist."""
    try:
        from faster_whisper import WhisperModel
    except ImportError:
        protokoll.warnung("Untertitel übersprungen: faster-whisper ist nicht installiert (pip install -r requirements.txt)")
        return None

    protokoll.info(f"Spracherkennung läuft (Modell '{modell}') …")
    whisper = WhisperModel(modell, device="auto", compute_type="int8")
    segmente, _ = whisper.transcribe(
        str(datei), language="de", word_timestamps=True,
        vad_filter=True,               # überspringt Stellen ohne Sprache (Wind, Spikes auf der Bahn …)
        condition_on_previous_text=False,
    )
    woerter = [Wort(w.start, w.end, w.word.strip()) for s in segmente for w in (s.words or []) if w.word.strip()]
    protokoll.info(f"Spracherkennung fertig: {len(woerter)} Wörter erkannt")
    return woerter
