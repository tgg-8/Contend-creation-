"""Tests für die reine Logik (ohne ffmpeg). Start: python -m unittest discover tests"""

import tempfile
import unittest
from pathlib import Path

from pipeline.idee import finde_ideendatei, hashtags, lies_idee
from pipeline.protokoll import Protokoll
from pipeline.schnitt import (Abschnitt, Bewertung, Wort, an_wortgrenzen, behalte_abschnitte, bewerte,
                              finde_hook, gesamtdauer, plane)
from pipeline.texte import baue_beitraege
from pipeline.untertitel import ass_datei, gruppiere, neue_zeitachse, zeit_ass, zeit_srt


class StummesProtokoll(Protokoll):
    def _schreibe(self, stufe, text):
        self.eintraege.append((stufe, text))


def bewertung_aus(punkte_bewegung, laut=None, sprache=None):
    n = len(punkte_bewegung)
    laut = laut or [0.5] * n
    sprache = sprache or [False] * n
    punkte = [0.5 * b + 0.3 * l + (0.4 if s else 0) for b, l, s in zip(punkte_bewegung, laut, sprache)]
    return Bewertung(n * 0.5, punkte_bewegung, laut, sprache, punkte)


class SchnittTests(unittest.TestCase):
    def test_pause_in_der_mitte_wird_entfernt(self):
        # 4 s Action, 3 s Stille ohne Bewegung, 4 s Action
        bew = [0.8] * 8 + [0.0] * 6 + [0.8] * 8
        laut = [0.6] * 8 + [0.0] * 6 + [0.6] * 8
        abschnitte = behalte_abschnitte(bewertung_aus(bew, laut))
        self.assertEqual(abschnitte, [Abschnitt(0.0, 4.0), Abschnitt(7.0, 11.0)])

    def test_sprache_bleibt_immer_drin(self):
        bew = [0.0] * 10
        laut = [0.0] * 10
        sprache = [False] * 4 + [True] * 2 + [False] * 4
        abschnitte = behalte_abschnitte(bewertung_aus(bew, laut, sprache))
        self.assertEqual(abschnitte, [Abschnitt(2.0, 3.0)])

    def test_alles_still_behaelt_alles(self):
        abschnitte = behalte_abschnitte(bewertung_aus([0.0] * 10, [0.0] * 10))
        self.assertEqual(abschnitte, [Abschnitt(0.0, 5.0)])

    def test_kein_schnitt_mitten_im_wort(self):
        woerter = [Wort(1.8, 2.4, "Sprint")]
        neu = an_wortgrenzen([Abschnitt(0.0, 2.0)], woerter, 10.0)
        self.assertAlmostEqual(neu[0].ende, 2.48)

    def test_hook_findet_staerksten_moment(self):
        bew = [0.1] * 10 + [1.0] * 4 + [0.1] * 10
        hook = finde_hook(bewertung_aus(bew), [])
        self.assertEqual(hook, Abschnitt(5.0, 7.0))

    def test_kein_hook_wenn_bester_moment_schon_vorne(self):
        bew = [1.0] * 4 + [0.1] * 20
        self.assertIsNone(finde_hook(bewertung_aus(bew), []))

    def test_plane_haelt_ziellaenge_ein_und_hook_zuerst(self):
        b = bewertung_aus([0.5] * 120)  # 60 s Material
        abschnitte = [Abschnitt(0, 20), Abschnitt(25, 45), Abschnitt(50, 60)]
        hook = Abschnitt(30, 32)
        schnitt = plane(abschnitte, hook, 25, b, [])
        self.assertEqual(schnitt[0], hook)
        self.assertLessEqual(gesamtdauer(schnitt), 25.01)
        self.assertGreater(gesamtdauer(schnitt), 20)

    def test_plane_laesst_kurzes_material_in_ruhe(self):
        b = bewertung_aus([0.5] * 20)
        abschnitte = [Abschnitt(0, 10)]
        self.assertEqual(plane(abschnitte, None, 25, b, []), abschnitte)

    def test_bewerte_ignoriert_harte_clip_wechsel(self):
        b = bewerte(2.0, [(0.1, -20.0)], [(0.2, 0.9), (1.2, 0.05)], [])
        self.assertEqual(b.bewegung[0], 0.0)
        self.assertGreater(b.bewegung[2], 0.9)


class UntertitelTests(unittest.TestCase):
    def test_zeitachse_nach_schnitt(self):
        woerter = [Wort(1.0, 1.5, "Hallo"), Wort(10.0, 10.5, "Ziel")]
        schnitt = [Abschnitt(9.5, 11.0), Abschnitt(0.5, 2.0)]   # Hook zuerst, dann Anfang
        neu = neue_zeitachse(woerter, schnitt)
        self.assertEqual([w.text for w in neu], ["Ziel", "Hallo"])
        self.assertAlmostEqual(neu[0].start, 0.5)
        self.assertAlmostEqual(neu[1].start, 1.5 + 0.5)

    def test_gruppen_kurz_und_ohne_ueberlappung(self):
        woerter = [Wort(i * 0.3, i * 0.3 + 0.25, w) for i, w in enumerate("Heute laufen wir zehn mal sechzig Meter.".split())]
        gruppen = gruppiere(woerter)
        self.assertTrue(all(len(t.split()) <= 3 for _, _, t in gruppen))
        for (s1, e1, _), (s2, _, _) in zip(gruppen, gruppen[1:]):
            self.assertLessEqual(e1, s2)

    def test_zeitformate(self):
        self.assertEqual(zeit_ass(61.234), "0:01:01.23")
        self.assertEqual(zeit_srt(3661.5), "01:01:01,500")

    def test_ass_enthaelt_hook_und_entschaerft_klammern(self):
        text = ass_datei([(0.0, 1.0, "Los {geht}")], "Arial", "Mein Hook", 2.0)
        self.assertIn("Hook,,0,0,0,,Mein Hook", text)
        self.assertIn("Los (geht)", text)


class IdeeTests(unittest.TestCase):
    def test_idee_lesen(self):
        with tempfile.TemporaryDirectory() as ordner:
            pfad = Path(ordner) / "2026-11-03_whoop.md"
            pfad.write_text(
                "# Idee\n\n## Thema\nWhoop-Schlaf\n\n## Hook-Text\nIch hab 4 Stunden geschlafen\n\n"
                "## Instagram Hashtags\n#sprint #whoop, leichtathletik #sprint\n\n## Pin-Titel\nBesser schlafen\n",
                encoding="utf-8",
            )
            idee = lies_idee(pfad)
        self.assertEqual(idee["thema"], "Whoop-Schlaf")
        self.assertEqual(idee["hook_text"], "Ich hab 4 Stunden geschlafen")
        self.assertEqual(idee["pinterest_titel"], "Besser schlafen")
        self.assertEqual(hashtags(idee["instagram_hashtags"]), ["#sprint", "#whoop", "#leichtathletik"])

    def test_ideendatei_zuordnung(self):
        with tempfile.TemporaryDirectory() as ordner:
            basis = Path(ordner)
            roh, ideen = basis / "roh", basis / "ideen"
            roh.mkdir(), ideen.mkdir()
            (ideen / "2026-11-03_a.md").write_text("x")
            self.assertEqual(finde_ideendatei(roh, ideen, "2026-11-03", "2026-11-03")[0].name, "2026-11-03_a.md")
            (ideen / "2026-11-03_b.md").write_text("x")
            self.assertIsNone(finde_ideendatei(roh, ideen, "2026-11-03", "2026-11-03")[0])
            self.assertEqual(finde_ideendatei(roh, ideen, "2026-11-03_b", "2026-11-03")[0].name, "2026-11-03_b.md")
            (roh / "idee.md").write_text("x")
            self.assertEqual(finde_ideendatei(roh, ideen, "2026-11-03_b", "2026-11-03")[0].name, "idee.md")


class TexteTests(unittest.TestCase):
    def test_hashtag_grenze_und_fehlende_texte(self):
        p = StummesProtokoll()
        idee = {"instagram_caption": "Tag 3 Reha.", "instagram_hashtags": "#a #b #c #d #e #f #g"}
        dateien = {"instagram_reel": "r.mp4", "story": "s.mp4", "tiktok": "t.mp4"}
        beitraege, fehlend = baue_beitraege(idee, dateien, p)
        reel = next(b for b in beitraege if b["typ"] == "REEL")
        self.assertTrue(reel["text"].endswith("#a #b #c #d #e"))
        self.assertIn("tiktok_caption", fehlend)
        self.assertTrue(any(b["plattform"] == "tiktok" and b.get("manuell") for b in beitraege))
        self.assertFalse(any(b["plattform"] == "youtube" for b in beitraege))  # kein Video -> kein Eintrag

    def test_ki_floskel_warnung(self):
        p = StummesProtokoll()
        baue_beitraege({"instagram_caption": "Tauche ein in mein Training — los"}, {"instagram_reel": "r.mp4"}, p)
        self.assertTrue(any("KI" in t for t in p.nur("WARNUNG")))


if __name__ == "__main__":
    unittest.main()
