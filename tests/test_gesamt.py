"""Gesamttest mit künstlich erzeugtem Material (braucht ffmpeg, aber kein Whisper und kein Drive)."""

import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from pipeline import analyse
from pipeline.einstellungen import Einstellungen
from pipeline.ordner import finde_neue_auftraege
from pipeline.plattformen import AUSGABEN
from pipeline.schnitt import Wort
from pipeline.verarbeitung import verarbeite
from tests.test_logik import StummesProtokoll


def ffmpeg(*args):
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", *args], check=True)


def falsches_whisper(datei, modell, protokoll):
    # tut so, als hätte Whisper am Anfang zwei Sätze erkannt
    texte = "Heute ist Sprinttag. Zehn mal sechzig Meter, volle Pulle!".split()
    return [Wort(0.5 + i * 0.35, 0.8 + i * 0.35, t) for i, t in enumerate(texte)]


@unittest.skipUnless(shutil.which("ffmpeg"), "ffmpeg nicht installiert")
class GesamtTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.content = self.tmp / "Content"
        roh = self.content / "Rohmaterial" / "2026-11-03_sprinttag"
        roh.mkdir(parents=True)
        (self.content / "Ideen").mkdir()
        # 1) Querformat mit viel Bewegung und Ton
        ffmpeg("-f", "lavfi", "-i", "testsrc2=size=1280x720:rate=30:duration=8",
               "-f", "lavfi", "-i", "sine=frequency=440:duration=8", "-shortest", str(roh / "clip1.mp4"))
        # 2) Standbild ohne Ton = Pause, soll rausfliegen
        ffmpeg("-f", "lavfi", "-i", "color=c=gray:size=1080x1920:rate=30:duration=5", str(roh / "clip2.mp4"))
        # 3) Hochformat mit Bewegung und lautem Ton
        ffmpeg("-f", "lavfi", "-i", "testsrc2=size=720x1280:rate=30:duration=8",
               "-f", "lavfi", "-i", "sine=frequency=880:duration=8,volume=4", "-shortest", str(roh / "clip3.mp4"))
        # 4) ein Foto
        ffmpeg("-f", "lavfi", "-i", "testsrc=size=1200x800", "-frames:v", "1", str(roh / "foto4.jpg"))
        (self.content / "Ideen" / "2026-11-03_sprinttag.md").write_text(
            "## Hook-Text\n60 Meter in 6,9?\n\n## Instagram Caption\nSprinttag. Wie schnell seid ihr?\n\n"
            "## Instagram Hashtags\n#sprint #leichtathletik\n\n## Pin-Titel\nSprinttraining für Anfänger\n",
            encoding="utf-8",
        )

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_kompletter_lauf(self):
        e = Einstellungen(content_ordner=self.content, ruhezeit=0)
        auftraege = finde_neue_auftraege(e, StummesProtokoll(), pause=0)
        self.assertEqual(len(auftraege), 1)
        self.assertTrue(verarbeite(auftraege[0], e, transkribierer=falsches_whisper))

        fertig = self.content / "Fertig" / "2026-11-03_sprinttag"
        beitrag = json.loads((fertig / "beitrag.json").read_text(encoding="utf-8"))
        self.assertEqual(beitrag["fehler"], [])
        for ausgabe in AUSGABEN:
            info = analyse.probe(fertig / ausgabe.datei)
            self.assertEqual(analyse.bildgroesse(info), (1080, 1920), ausgabe.datei)
            self.assertLessEqual(analyse.dauer(info), ausgabe.ziel_sekunden + 0.5, ausgabe.datei)
            self.assertTrue(analyse.hat_audio(info))
        # Material: 8 + 5 + 8 + 2,5 = 23,5 s; die 5 s Pause muss deutlich gekürzt sein
        self.assertLess(analyse.dauer(analyse.probe(fertig / "youtube_short.mp4")), 21.5)
        self.assertTrue((fertig / "pinterest_pin.jpg").exists())
        self.assertTrue((fertig / "instagram_reel.srt").exists())
        self.assertFalse((fertig / "_arbeit").exists())
        self.assertIn("tiktok_caption", beitrag["fehlende_texte"])
        # Ordner gilt danach als erledigt
        self.assertEqual(finde_neue_auftraege(e, StummesProtokoll(), pause=0), [])


if __name__ == "__main__":
    unittest.main()
