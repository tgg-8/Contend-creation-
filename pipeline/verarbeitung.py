"""Der komplette Ablauf für einen Rohmaterial-Ordner – vom Clip bis zur fertigen beitrag.json."""

import json
import os
import shutil
from datetime import date, datetime
from pathlib import Path

from . import analyse, render
from .einstellungen import PROJEKT, Einstellungen
from .idee import finde_ideendatei, lies_idee
from .ordner import FEHLER_MARKER, FERTIG_MARKER, Auftrag, ist_bild
from .plattformen import AUSGABEN
from .protokoll import FEHLER, WARNUNG, Protokoll
from .schnitt import an_wortgrenzen, behalte_abschnitte, bewerte, finde_hook, gesamtdauer, plane
from .texte import baue_beitraege
from .transkript import transkribiere
from .untertitel import ass_datei, gruppiere, neue_zeitachse, pin_ass, srt_datei


def verarbeite(auftrag: Auftrag, e: Einstellungen, transkribierer=transkribiere) -> bool:
    """Verarbeitet einen Ordner. Gibt True zurück, wenn beitrag.json geschrieben wurde."""
    ziel = e.fertig / auftrag.name
    arbeit = ziel / "_arbeit"
    arbeit.mkdir(parents=True, exist_ok=True)
    p = Protokoll(ziel / "protokoll.txt", PROJEKT / "logs" / f"{date.today()}.log")
    p.info(f"===== Lauf für {auftrag.name} =====")
    try:
        _ablauf(auftrag, e, ziel, arbeit, p, transkribierer)
        if not os.environ.get("PIPELINE_DEBUG"):
            shutil.rmtree(arbeit, ignore_errors=True)
        return True
    except Exception as fehler:  # alles abfangen, damit der Wächter weiterläuft
        p.fehler(f"Abbruch: {type(fehler).__name__}: {fehler}")
        (ziel / FEHLER_MARKER).write_text(
            f"Die Verarbeitung ist fehlgeschlagen ({datetime.now():%d.%m.%Y %H:%M}).\n"
            f"Grund: {fehler}\n\n"
            "Details stehen in protokoll.txt. Wenn du das Problem behoben hast, lösch diese Datei,\n"
            "dann versucht es die Pipeline beim nächsten Durchlauf noch einmal.\n",
            encoding="utf-8",
        )
        return False
    finally:
        p.zusammenfassung()


def _ablauf(auftrag: Auftrag, e: Einstellungen, ziel: Path, arbeit: Path, p: Protokoll, transkribierer) -> None:
    p.erkannt(f"{auftrag.name}: {len(auftrag.dateien)} Datei(en): " + ", ".join(d.name for d in auftrag.dateien))

    # 1. Idee suchen
    ideendatei, hinweis = finde_ideendatei(auftrag.ordner, e.ideen, auftrag.name, auftrag.datum)
    idee = lies_idee(ideendatei) if ideendatei else {}
    if ideendatei:
        p.erkannt(f"Idee: {ideendatei.name} ({hinweis})")
    else:
        p.warnung(f"Keine Idee zugeordnet: {hinweis}. Die Claude-Aufgabe schreibt die Texte dann aus dem Transkript.")

    # 2. Alle Clips und Fotos auf 1080x1920 bringen und aneinanderhängen
    teile = []
    for nummer, datei in enumerate(auftrag.dateien):
        try:
            info = analyse.probe(datei)
            bild = ist_bild(datei)
            if not bild and analyse.dauer(info) < 0.3:
                p.warnung(f"{datei.name} ist kürzer als 0,3 s – übersprungen")
                continue
            breite, hoehe = analyse.bildgroesse(info)
            teil = arbeit / f"teil_{nummer:02d}.mp4"
            render.vereinheitliche(datei, teil, breite, hoehe, e.querformat, bild, analyse.hat_audio(info))
            teile.append(teil)
        except analyse.FfmpegFehler as fehler:
            p.fehler(f"{datei.name} konnte nicht gelesen werden und wird übersprungen: {fehler}")
    if not teile:
        raise RuntimeError("Kein verwendbares Material im Ordner")

    quelle = arbeit / "quelle.mp4"
    render.verbinde(teile, quelle)
    gesamt = analyse.dauer(analyse.probe(quelle))
    p.info(f"Rohmaterial zusammen: {gesamt:.1f} s")

    # 3. Sprache erkennen (für Untertitel und damit nie mitten im Satz geschnitten wird)
    woerter = transkribierer(quelle, e.whisper_modell, p)
    hat_untertitel = woerter is not None
    woerter = woerter or []
    if woerter:
        (ziel / "transkript.txt").write_text(" ".join(w.text for w in woerter) + "\n", encoding="utf-8")

    # 4. Bewerten, Pausen raus, Hook finden
    bewertung = bewerte(gesamt, analyse.lautheit(quelle, arbeit), analyse.bewegung(quelle, arbeit), woerter)
    abschnitte = an_wortgrenzen(behalte_abschnitte(bewertung), woerter, gesamt)
    hook = finde_hook(bewertung, woerter)
    p.info(f"Nach Pausen-Schnitt: {gesamtdauer(abschnitte):.1f} s in {len(abschnitte)} Abschnitt(en)")
    p.info(f"Hook: {hook.start:.1f}–{hook.ende:.1f} s wird nach vorn gezogen" if hook
           else "Hook: stärkster Moment ist schon am Anfang (oder Material zu kurz)")

    musik = e.musik_datei
    if musik and not musik.exists():
        p.warnung(f"Musikdatei {musik} nicht gefunden – Video bleibt ohne Musik")
        musik = None

    # 5. Pro Plattform schneiden und rendern
    dateien: dict[str, str] = {}
    schon_gerendert: dict[tuple, str] = {}
    hook_text = idee.get("hook_text", "").strip()
    for ausgabe in AUSGABEN:
        try:
            schnitt = plane(abschnitte, hook, ausgabe.ziel_sekunden, bewertung, woerter)
            schluessel = tuple((round(a.start, 2), round(a.ende, 2)) for a in schnitt) + (ausgabe.hook_text, ausgabe.untertitel)
            gruppen = gruppiere(neue_zeitachse(woerter, schnitt)) if (ausgabe.untertitel and woerter) else []
            if schluessel in schon_gerendert:
                shutil.copyfile(ziel / schon_gerendert[schluessel], ziel / ausgabe.datei)
            else:
                text_oben = hook_text if ausgabe.hook_text else ""
                ass_name = None
                if gruppen or text_oben:
                    ass_name = f"{ausgabe.name}.ass"
                    hook_dauer = min(3.0, max(2.0, hook.dauer if hook else 2.0))
                    (arbeit / ass_name).write_text(ass_datei(gruppen, e.schrift, text_oben, hook_dauer), encoding="utf-8")
                render.rendere(quelle, schnitt, arbeit / ausgabe.datei, ass_name, musik, e.musik_lautstaerke)
                shutil.move(str(arbeit / ausgabe.datei), ziel / ausgabe.datei)
                schon_gerendert[schluessel] = ausgabe.datei
            if gruppen:
                (ziel / f"{ausgabe.name}.srt").write_text(srt_datei(gruppen), encoding="utf-8")
            dateien[ausgabe.name] = ausgabe.datei
            p.geschnitten(
                f"{ausgabe.datei}: {gesamtdauer(schnitt):.1f} s, {len(schnitt)} Abschnitt(e)"
                + (", Hook vorn" if hook else "") + (", Untertitel" if gruppen else "")
                + (", Hook-Text" if hook_text and ausgabe.hook_text else "") + (", mit Musik" if musik else ", ohne Musik")
            )
        except Exception as fehler:
            p.fehler(f"{ausgabe.datei} konnte nicht erstellt werden: {fehler}")
    if not dateien:
        raise RuntimeError("Kein einziges Video konnte erstellt werden")
    if not hat_untertitel:
        p.warnung("Videos haben keine Untertitel (Spracherkennung nicht verfügbar)")

    # 6. Vorschaubild und Pinterest-Bild
    try:
        zeit = hook.start + hook.dauer / 2 if hook else min(1.0, gesamt / 2)
        render.standbild(quelle, zeit, ziel / "vorschau.jpg")
        pin_titel = idee.get("pin_text") or idee.get("pinterest_titel", "")
        pin_ass_name = None
        if pin_titel:
            pin_ass_name = "pin.ass"
            (arbeit / pin_ass_name).write_text(pin_ass(pin_titel, e.schrift), encoding="utf-8")
        render.pinterest_bild(ziel / "vorschau.jpg", arbeit / "pinterest_pin.jpg", pin_ass_name)
        shutil.move(str(arbeit / "pinterest_pin.jpg"), ziel / "pinterest_pin.jpg")
        dateien["pinterest_pin"] = "pinterest_pin.jpg"
        p.geschnitten("vorschau.jpg und pinterest_pin.jpg (1000x1500" + (", mit Titel)" if pin_titel else ")"))
    except Exception as fehler:
        p.fehler(f"Vorschau-/Pinterest-Bild konnte nicht erstellt werden: {fehler}")

    # 7. Texte zusammensetzen und alles in beitrag.json schreiben (die Datei kommt zuletzt = Zeichen für "fertig")
    beitraege, fehlend = baue_beitraege(idee, dateien, p)
    if fehlend:
        p.warnung("Fehlende Texte: " + ", ".join(fehlend) + " – die Claude-Aufgabe ergänzt sie aus dem Transkript")

    beitrag = {
        "version": 1,
        "name": auftrag.name,
        "datum": auftrag.datum,
        "erstellt": datetime.now().isoformat(timespec="seconds"),
        "status": "bereit" if not fehlend else "texte_fehlen",
        "idee_datei": ideendatei.name if ideendatei else None,
        "thema": idee.get("thema", ""),
        "hook_text": hook_text,
        "geplant": idee.get("geplant", ""),
        "transkript": " ".join(w.text for w in woerter),
        "fehlende_texte": fehlend,
        "dateien": dateien,
        "beitraege": beitraege,
        "warnungen": p.nur(WARNUNG),
        "fehler": p.nur(FEHLER),
    }
    (ziel / FERTIG_MARKER).write_text(json.dumps(beitrag, ensure_ascii=False, indent=2), encoding="utf-8")
    p.info(f"Fertig: {ziel}")
