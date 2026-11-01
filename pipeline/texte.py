"""Baut aus der Idee die Texte pro Plattform und prüft sie gegen die Plattform-Grenzen.

Die Texte selbst schreibt die Claude-Aufgabe (im Stil aus dem content-skript-Skill). Dieses Modul
erfindet nichts dazu, sondern setzt zusammen, kürzt notfalls und warnt bei Problemen.
"""

from .idee import hashtags
from .plattformen import GRENZEN

# Formulierungen, die schnell nach KI klingen. Nur ein Hinweis im Protokoll, nichts wird automatisch geändert.
KI_FLOSKELN = [
    "—", "tauche ein", "entdecke die welt", "in der heutigen", "gamechanger", "game-changer",
    "entfessle", "lass uns gemeinsam", "auf das nächste level", "ultimative",
    "revolutionär", "es ist wichtig zu beachten",
]


def _kuerze(text: str, grenze: int) -> str:
    if len(text) <= grenze:
        return text
    gekuerzt = text[: grenze - 1].rsplit(" ", 1)[0]
    return gekuerzt.rstrip(" ,.;:-") + "…"


def _mit_hashtags(text: str, tags: list[str]) -> str:
    return (text.strip() + ("\n\n" + " ".join(tags) if tags else "")).strip()


# Wörter, an denen man erkennt, dass der Verkaufshinweis als eigene Werbung gekennzeichnet ist
EIGENWERBUNG_WOERTER = ("eigen", "werbung", "anzeige")


def verkaufsdaten(idee: dict[str, str], hinweis: str, link: str, protokoll) -> dict | None:
    """Entscheidet, ob an diesen Beitrag ein Hinweis auf dein eigenes Produkt kommt.

    Nur wenn die Idee-Datei unter "## Verkaufshinweis" mit "ja" beginnt UND in der .env ein
    VERKAUFS_HINWEIS steht. Solange der Shop nicht live ist, bleibt die .env leer und es passiert nichts.
    """
    if not idee.get("verkaufshinweis", "").strip().lower().startswith("ja"):
        return None
    hinweis, link = hinweis.strip(), link.strip()
    if not hinweis:
        protokoll.info("Die Idee wünscht einen Verkaufshinweis, aber VERKAUFS_HINWEIS in der .env ist leer. Er wird weggelassen.")
        return None
    if not any(wort in hinweis.lower() for wort in EIGENWERBUNG_WOERTER):
        protokoll.warnung("VERKAUFS_HINWEIS sagt nicht klar, dass es dein eigenes Produkt ist (z. B. „mein eigener Guide“)")
    if link and not link.startswith("https://"):
        protokoll.warnung(f"VERKAUFS_LINK sollte mit https:// beginnen und wird ignoriert: {link}")
        link = ""
    return {"hinweis": hinweis, "link": link}


def _mit_hinweis(text: str, hinweis: str) -> str:
    """Hängt den Verkaufshinweis als eigenen Absatz an (vor die Hashtags). Leere Texte bleiben leer."""
    if not text.strip() or not hinweis:
        return text
    return text.strip() + "\n\n" + hinweis


def baue_beitraege(idee: dict[str, str], dateien: dict[str, str], protokoll,
                   verkauf: dict | None = None) -> tuple[list[dict], list[str]]:
    """Gibt (Liste der Beiträge für Metricool, Liste fehlender Texte) zurück.

    `verkauf` kommt aus verkaufsdaten(): {"hinweis": ..., "link": ...} oder None.
    """
    fehlend: list[str] = []
    hinweis = (verkauf or {}).get("hinweis", "")
    link = (verkauf or {}).get("link", "")

    def feld(schluessel: str) -> str:
        wert = idee.get(schluessel, "").strip()
        if not wert:
            fehlend.append(schluessel)
        return wert

    def tags(schluessel: str, grenze: int) -> list[str]:
        liste = hashtags(idee.get(schluessel, ""))
        if len(liste) > grenze:
            protokoll.warnung(f"{schluessel}: {len(liste)} Hashtags, nur die ersten {grenze} werden genutzt")
        return liste[:grenze]

    def pruefe_laenge(name: str, text: str, grenze: int) -> str:
        if len(text) > grenze:
            protokoll.warnung(f"{name} ist {len(text)} Zeichen lang (Grenze {grenze}) und wurde gekürzt")
            return _kuerze(text, grenze)
        return text

    ig = pruefe_laenge("Instagram-Caption",
                       _mit_hashtags(_mit_hinweis(feld("instagram_caption"), hinweis),
                                     tags("instagram_hashtags", GRENZEN["instagram_hashtags"])),
                       GRENZEN["instagram_caption"])
    tk = pruefe_laenge("TikTok-Caption",
                       _mit_hashtags(_mit_hinweis(feld("tiktok_caption"), hinweis),
                                     tags("tiktok_hashtags", GRENZEN["tiktok_hashtags"])),
                       GRENZEN["tiktok_caption"])

    yt_titel = pruefe_laenge("YouTube-Titel", feld("youtube_titel"), GRENZEN["youtube_titel"])
    if len(yt_titel) > GRENZEN["youtube_titel_empfohlen"]:
        protokoll.warnung(f"YouTube-Titel hat {len(yt_titel)} Zeichen; auf dem Handy werden etwa 60 angezeigt")
    yt_text = pruefe_laenge("YouTube-Beschreibung",
                            _mit_hashtags(_mit_hinweis(idee.get("youtube_beschreibung", ""), hinweis),
                                          tags("youtube_hashtags", GRENZEN["youtube_hashtags"])),
                            GRENZEN["youtube_beschreibung"])

    pin_titel = pruefe_laenge("Pin-Titel", feld("pinterest_titel"), GRENZEN["pinterest_titel"])
    pin_text = pruefe_laenge("Pin-Beschreibung", feld("pinterest_beschreibung"), GRENZEN["pinterest_beschreibung"])

    hook = idee.get("hook_text", "").strip()
    if not hook:
        fehlend.append("hook_text")
    elif len(hook.split()) > GRENZEN["hook_text_woerter"]:
        protokoll.warnung(f"Hook-Text hat {len(hook.split())} Wörter – kürzer liest sich in 2 Sekunden besser")

    for name, text in (("Instagram", ig), ("TikTok", tk), ("YouTube", yt_titel + " " + yt_text),
                       ("Pinterest", pin_titel + " " + pin_text), ("Hook", hook)):
        treffer = [f for f in KI_FLOSKELN if f in text.lower()]
        if treffer:
            protokoll.warnung(f"{name}-Text klingt evtl. nach KI: {', '.join(treffer)}")

    beitraege = [
        {"plattform": "instagram", "typ": "REEL", "datei": dateien.get("instagram_reel"), "text": ig},
        {"plattform": "instagram", "typ": "STORY", "datei": dateien.get("story"), "text": ""},
        {"plattform": "tiktok", "typ": "VIDEO", "datei": dateien.get("tiktok"), "text": tk},
        {"plattform": "tiktok", "typ": "STORY", "datei": dateien.get("story"), "text": "", "manuell": True,
         "grund": "TikTok-Storys lassen sich über Metricool nicht planen – bitte in der TikTok-App posten"},
        {"plattform": "youtube", "typ": "short", "datei": dateien.get("youtube_short"),
         "titel": yt_titel, "text": yt_text, "madeForKids": False},
        {"plattform": "pinterest", "typ": "PIN", "datei": dateien.get("pinterest_pin"),
         "titel": pin_titel, "text": pin_text, "board": idee.get("pinterest_board", "").strip()},
    ]
    if hinweis:
        for b in beitraege:
            if b["plattform"] == "tiktok" and b["typ"] == "VIDEO":
                b["eigenwerbung"] = True   # TikTok-Kennzeichnung „Werbung für eigene Marke“
            if b["plattform"] == "pinterest" and link:
                b["link"] = link           # der Pin führt direkt zur Produktseite
    return [b for b in beitraege if b.get("datei")], sorted(set(fehlend))
