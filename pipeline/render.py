"""Alles, was mit ffmpeg Videos und Bilder erzeugt."""

import shutil
from pathlib import Path

from .analyse import ausfuehren
from .plattformen import BREITE, FPS, HOEHE
from .schnitt import Abschnitt

KODIERUNG_VIDEO = ["-c:v", "libx264", "-pix_fmt", "yuv420p", "-r", str(FPS)]
KODIERUNG_AUDIO = ["-c:a", "aac", "-ar", "48000", "-ac", "2"]


def hochformat_filter(breite: int, hoehe: int, querformat: str) -> str:
    """Bringt jedes Material auf 1080x1920.

    Hochformat: füllend skalieren und Ränder abschneiden.
    Querformat: entweder Mitte ausschneiden ("crop") oder scharfes Bild auf unscharfem Hintergrund ("blur").
    """
    fuellen = f"scale={BREITE}:{HOEHE}:force_original_aspect_ratio=increase,crop={BREITE}:{HOEHE}"
    if breite / hoehe <= 0.75 or querformat == "crop":
        return f"{fuellen},setsar=1"
    return (
        f"split=2[hg][vg];[hg]{fuellen},boxblur=luma_radius=30:luma_power=2,eq=brightness=-0.08[hgb];"
        f"[vg]scale={BREITE}:{HOEHE}:force_original_aspect_ratio=decrease[vgs];"
        f"[hgb][vgs]overlay=(W-w)/2:(H-h)/2,setsar=1"
    )


def vereinheitliche(eingang: Path, ziel: Path, breite: int, hoehe: int, querformat: str,
                    ist_bild: bool, hat_ton: bool, bild_sekunden: float = 2.5) -> None:
    """Ein Clip oder Foto -> 1080x1920, 30 fps, Stereo-Ton (bei Fotos/stummen Clips: Stille)."""
    vf = hochformat_filter(breite, hoehe, querformat)
    if ist_bild:
        # Foto: leichter Zoom ("Ken Burns"), damit es nicht wie ein Standbild wirkt
        frames = int(bild_sekunden * FPS)
        vf += f",zoompan=z='min(zoom+0.0012,1.08)':d={frames}:x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':s={BREITE}x{HOEHE}:fps={FPS}"
        befehl = ["ffmpeg", "-hide_banner", "-y", "-i", str(eingang),
                  "-f", "lavfi", "-t", str(bild_sekunden), "-i", "anullsrc=r=48000:cl=stereo"]
    else:
        befehl = ["ffmpeg", "-hide_banner", "-y", "-i", str(eingang)]
        if not hat_ton:
            befehl += ["-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo"]
    vf += f",fps={FPS},format=yuv420p"
    ton = "1:a:0" if (ist_bild or not hat_ton) else "0:a:0"
    befehl += [
        "-filter_complex", f"[0:v:0]{vf}[v]", "-map", "[v]", "-map", ton,
        *KODIERUNG_VIDEO, "-preset", "veryfast", "-crf", "17", *KODIERUNG_AUDIO, "-b:a", "192k",
        *(["-t", str(bild_sekunden)] if ist_bild else ["-shortest"]), str(ziel),
    ]
    ausfuehren(befehl)


def verbinde(teile: list[Path], ziel: Path) -> None:
    """Hängt die vereinheitlichten Teile ohne Qualitätsverlust aneinander."""
    if len(teile) == 1:
        shutil.copyfile(teile[0], ziel)
        return
    liste = ziel.with_suffix(".txt")
    liste.write_text("".join(f"file '{t.name}'\n" for t in teile), encoding="utf-8")
    ausfuehren(["ffmpeg", "-hide_banner", "-y", "-f", "concat", "-safe", "0", "-i", liste.name,
                "-c", "copy", ziel.name], cwd=ziel.parent)


def rendere(quelle: Path, schnitt: list[Abschnitt], ziel: Path, ass_name: str | None,
            musik: Path | None = None, musik_lautstaerke: float = 0.12) -> None:
    """Schneidet laut Schnittliste, brennt Untertitel ein und gleicht die Lautstärke an.

    ffmpeg läuft im Zielordner, damit der Pfad zur ASS-Datei keine Sonderzeichen braucht (wichtig für Windows).
    """
    teile = []
    for i, a in enumerate(schnitt):
        blende = min(0.03, a.dauer / 4)
        teile.append(
            f"[0:v]trim=start={a.start:.3f}:end={a.ende:.3f},setpts=PTS-STARTPTS[v{i}];"
            f"[0:a]atrim=start={a.start:.3f}:end={a.ende:.3f},asetpts=PTS-STARTPTS,"
            f"afade=t=in:st=0:d={blende:.3f},afade=t=out:st={a.dauer - blende:.3f}:d={blende:.3f}[a{i}]"
        )
    paare = "".join(f"[v{i}][a{i}]" for i in range(len(schnitt)))
    graph = ";".join(teile) + f";{paare}concat=n={len(schnitt)}:v=1:a=1[vc][ac];"
    graph += f"[vc]ass={ass_name}[vaus];" if ass_name else "[vc]null[vaus];"
    # -14 LUFS ist die übliche Lautheit auf Social Media
    graph += "[ac]loudnorm=I=-14:TP=-1.5:LRA=11,aresample=48000[an]"

    befehl = ["ffmpeg", "-hide_banner", "-y", "-i", str(quelle)]
    if musik:
        befehl += ["-stream_loop", "-1", "-i", str(musik)]
        graph += (f";[1:a]volume={musik_lautstaerke},aresample=48000[mu];"
                  f"[an][mu]amix=inputs=2:duration=first:dropout_transition=0:normalize=0[aaus]")
    else:
        graph += ";[an]anull[aaus]"

    befehl += [
        "-filter_complex", graph, "-map", "[vaus]", "-map", "[aaus]",
        *KODIERUNG_VIDEO, "-preset", "medium", "-crf", "20", "-profile:v", "high",
        "-maxrate", "12M", "-bufsize", "24M",
        *KODIERUNG_AUDIO, "-b:a", "160k", "-movflags", "+faststart", ziel.name,
    ]
    ausfuehren(befehl, cwd=ziel.parent)


def standbild(quelle: Path, zeit: float, ziel: Path) -> None:
    """Ein einzelnes Bild aus dem Video (Vorschau, 1080x1920)."""
    ausfuehren(["ffmpeg", "-hide_banner", "-y", "-ss", f"{zeit:.2f}", "-i", str(quelle),
                "-frames:v", "1", "-q:v", "2", str(ziel)])


def pinterest_bild(vorschau: Path, ziel: Path, ass_name: str | None) -> None:
    """Pinterest mag 2:3. Mitte aus dem 9:16-Bild schneiden, auf 1000x1500, optional Titel drauf."""
    vf = f"crop={BREITE}:{int(BREITE * 1.5)}:0:(ih-{int(BREITE * 1.5)})/2,scale=1000:1500"
    if ass_name:
        vf += f",ass={ass_name}"
    ausfuehren(["ffmpeg", "-hide_banner", "-y", "-i", str(vorschau), "-vf", vf,
                "-frames:v", "1", "-q:v", "2", ziel.name], cwd=ziel.parent)
