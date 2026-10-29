# Graph Report - Contend-creation-  (2026-10-29)

## Corpus Check
- Corpus is ~9,350 words - fits in a single context window. You may not need a graph.

## Summary
- 250 nodes · 621 edges · 8 communities (7 shown, 1 thin omitted)
- Extraction: 92% EXTRACTED · 8% INFERRED · 0% AMBIGUOUS · INFERRED: 49 edges (avg confidence: 0.88)
- Token cost: 92,818 input · 0 output

## Community Hubs (Navigation)
- Einstellungen und Start
- Schnitt-Logik und Hook
- Claude-Aufgaben und Metricool
- Protokoll und Durchlauf
- Idee und Texte
- Video-Analyse mit ffmpeg
- Plattformen und Untertitel

## God Nodes (most connected - your core abstractions)
1. `_ablauf()` - 41 edges
2. `Abschnitt` - 24 edges
3. `Wort` - 19 edges
4. `Protokoll` - 18 edges
5. `README: Tareks Content-Pipeline` - 18 edges
6. `Bauplan` - 16 edges
7. `ausfuehren()` - 15 edges
8. `verarbeite()` - 15 edges
9. `Einstellungen` - 14 edges
10. `plane()` - 14 edges

## Surprising Connections (you probably didn't know these)
- `Ideen-Datei Format (Ueberschriften und Grenzen)` --semantically_similar_to--> `Idee-Datei-Vorlage (Markdown-Abschnitte)`  [INFERRED] [semantically similar]
  aufgaben/montags-ideen.md → docs/IDEE_VORLAGE.md
- `GesamtTest` --uses--> `Einstellungen`  [INFERRED]
  tests/test_gesamt.py → pipeline/einstellungen.py
- `StummesProtokoll` --inherits--> `Protokoll`  [EXTRACTED]
  tests/test_logik.py → pipeline/protokoll.py
- `SchnittTests` --uses--> `Abschnitt`  [INFERRED]
  tests/test_logik.py → pipeline/schnitt.py
- `UntertitelTests` --uses--> `Abschnitt`  [INFERRED]
  tests/test_logik.py → pipeline/schnitt.py

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **End-to-end Content-Flow (Ideen -> Pipeline -> Entwuerfe -> Freigabe)** — aufgaben_montags_ideen_task, readme_ordnername_konvention, pipeline_verarbeitung, aufgaben_entwuerfe_anlegen_beitrag_json, aufgaben_entwuerfe_anlegen_task, readme_freigabe_ablauf [EXTRACTED 1.00]
- **Videoverarbeitungs-Module (Erkennen, Schneiden, Untertitel, Texte)** — pipeline_ordner, pipeline_render, pipeline_schnitt, pipeline_transkript, pipeline_untertitel, pipeline_texte [EXTRACTED 1.00]
- **Schutz vor automatischem Veroeffentlichen** — docs_bauplan_entwurf_statt_freigabe, aufgaben_entwuerfe_anlegen_sicherheit, readme_freigabe_ablauf, docs_bauplan_metricool_via_claude [INFERRED 0.85]

## Communities (8 total, 1 thin omitted)

### Community 0 - "Einstellungen und Start"
Cohesion: 0.08
Nodes (15): pruefe_ffmpeg(), Einstellungen, lade(), lies_env_datei(), main(), testlauf(), Auftrag, finde_neue_auftraege() (+7 more)

### Community 1 - "Schnitt-Logik und Hook"
Cohesion: 0.11
Nodes (20): Abschnitt, an_wortgrenzen(), _begrenze(), behalte_abschnitte(), bewerte(), Bewertung, _dichte(), finde_hook() (+12 more)

### Community 2 - "Claude-Aufgaben und Metricool"
Cohesion: 0.08
Nodes (36): Auftrag: Entwuerfe anlegen, angelegt.json (Ergebnis-Marker), beitrag.json (Uebergabepaket), Metricool-Brand blogId 7742951, Metricool-Payloads je Plattform (Reel, Story, TikTok, Short, Pin), Push-Nachricht an Tarek (SendUserMessage), Claude-Aufgabe Entwuerfe anlegen, Auftrag: Montags-Content-Ideen (+28 more)

### Community 3 - "Protokoll und Durchlauf"
Cohesion: 0.12
Nodes (10): ein_durchlauf(), Protokoll, hochformat_filter(), pinterest_bild(), rendere(), standbild(), verbinde(), vereinheitliche() (+2 more)

### Community 4 - "Idee und Texte"
Cohesion: 0.10
Nodes (12): finde_ideendatei(), hashtags(), lies_idee(), normalisiere(), baue_beitraege(), pruefe_laenge(), tags(), _kuerze() (+4 more)

### Community 5 - "Video-Analyse mit ffmpeg"
Cohesion: 0.12
Nodes (12): ausfuehren(), bewegung(), bildgroesse(), dauer(), FfmpegFehler, hat_audio(), lautheit(), lies_metadaten() (+4 more)

### Community 6 - "Plattformen und Untertitel"
Cohesion: 0.15
Nodes (8): Plattform-Laengen (Reel 25s, TikTok 30s, Short 40s, Story 15s), Ausgabe, ass_datei(), pin_ass(), _sauber(), srt_datei(), zeit_ass(), zeit_srt()

## Knowledge Gaps
- **5 isolated node(s):** `fehler.txt (Abbruch-Marker)`, `Metricool-Brand blogId 7742951`, `Tarek Profil (Sprinter, @Tarek.Athletics, Zielgruppe 16-22)`, `Pin-Text (Fallback Pin-Titel)`, `graphifyy`
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 69 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **1 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `_ablauf()` connect `Protokoll und Durchlauf` to `Einstellungen und Start`, `Schnitt-Logik und Hook`, `Idee und Texte`, `Video-Analyse mit ffmpeg`, `Plattformen und Untertitel`?**
  _High betweenness centrality (0.167) - this node is a cross-community bridge._
- **Are the 10 inferred relationships involving `_ablauf()` (e.g. with `Einstellungen` and `.ideen()`) actually correct?**
  _`_ablauf()` has 10 INFERRED edges - model-reasoned connections that need verification._
- **What connects `fehler.txt (Abbruch-Marker)`, `Metricool-Brand blogId 7742951`, `Tarek Profil (Sprinter, @Tarek.Athletics, Zielgruppe 16-22)` to the rest of the system?**
  _5 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Einstellungen und Start` be split into smaller, more focused modules?**
  _Cohesion score 0.07673469387755102 - nodes in this community are weakly interconnected._
- **Why does `README: Tareks Content-Pipeline` connect `Claude-Aufgaben und Metricool` to `Einstellungen und Start`, `Plattformen und Untertitel`?**
  _High betweenness centrality (0.140) - this node is a cross-community bridge._
- **Are the 4 inferred relationships involving `Abschnitt` (e.g. with `rendere()` and `neue_zeitachse()`) actually correct?**
  _`Abschnitt` has 4 INFERRED edges - model-reasoned connections that need verification._
- **Should `Schnitt-Logik und Hook` be split into smaller, more focused modules?**
  _Cohesion score 0.10714285714285714 - nodes in this community are weakly interconnected._