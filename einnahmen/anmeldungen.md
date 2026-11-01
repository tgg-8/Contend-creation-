# Anmeldungen und Konten: Schritt für Schritt

Konten legst du selbst an. Ich gebe dir nur die Texte und sage dir, was du wo ankreuzen musst. Passwörter gehören in deinen Passwort-Manager, nie in dieses Projekt.

Das hier ist keine Rechts- oder Steuerberatung. Wo eine Fachperson besser ist, steht es dabei.

## 0. Okay vom OSP (vor allem anderen)

Für den Bundeskader ist die DLV-Athletenvereinbarung Pflicht, und Verstöße können den Kaderstatus kosten ([DLV-Kaderbildungsrichtlinien 2026/27](https://www.leichtathletik.de/fileadmin/user_upload/003_Nationalmannschaften/01_Olympisch/05_Nominierung_Kaderbildung/Kaderbildung/2026-2027_DLV-Kaderbildungsrichtlinien.pdf)). Frag deinen Trainer oder den Laufbahnberater am OSP:

- Darf ich einen eigenen Trainings-Guide und Video-Analysen gegen Geld anbieten?
- Darf ich Affiliate-Links für Ausrüstung setzen? Gibt es Marken, die ich wegen Verbandssponsoren nicht nennen soll?
- Darf ich Bilder vom OSP-Gelände oder in Verbandskleidung dafür nutzen?

Und: Verkauf keine Pläne deines Trainers. Der Guide besteht nur aus deinem eigenen Wissen.

## 1. Fachperson (optional, aber einmal sinnvoll)

Stell alle Fragen in einem Termin. Eine Steuerberater-Erstberatung darf bis zu 190 € plus USt kosten ([§ 21 StBVV](https://www.gesetze-im-internet.de/stbgebv/__21.html)), die Gründungsberatung der IHK ist ein günstigerer erster Anlauf.

1. Sind ein selbst geschriebener Trainings-Guide und Technik-Analysen gewerblich oder freiberuflich? Brauche ich eine Gewerbeanmeldung?
2. Was muss ich als Kleinunternehmer bei Digistore24 und bei Plattformen im Ausland (Amazon-Partnerprogramm) umsatzsteuerlich melden?
3. Welche Adresse darf ich im Impressum angeben (Internat, Eltern, Adress-Service)?

## 2. Gewerbeanmeldung (ca. 20 Min., falls nötig)

- Wo: beim Gewerbeamt deines Wohnorts. Wohnst du offiziell in Potsdam, geht es online; die Gebühr liegt laut Gründerportal ab ca. 36,60 € ([firma.de](https://www.firma.de/gewerbeamt/potsdam/)). Nimm das offizielle Portal der Stadt, keine Vermittler mit Extragebühr.
- Nebenerwerb: ja
- Tätigkeit (Vorschlag): „Verkauf eigener digitaler Trainingsinhalte und Online-Videoanalysen im Sportbereich; Vermittlung von Produkten im Internet (Affiliate-Marketing)“
- Danach wirst du automatisch IHK-Mitglied. Bei einem Gewerbeertrag bis 5.200 € im Jahr zahlst du keinen Beitrag ([§ 3 IHKG](https://www.gesetze-im-internet.de/ihkg/__3.html)).

## 3. Finanzamt über ELSTER (ca. 40 Min. plus Wartezeit)

1. Leg ein ELSTER-Benutzerkonto auf [elster.de](https://www.elster.de) an. Die Aktivierung dauert ein paar Tage, also früh starten.
2. Füll innerhalb eines Monats nach dem Start den „Fragebogen zur steuerlichen Erfassung“ für ein Einzelunternehmen aus ([§ 138 AO](https://www.gesetze-im-internet.de/ao_1977/__138.html)). Die wichtigsten Felder:
   - Kleinunternehmerregelung: **ja** (Vorjahr max. 25.000 €, laufendes Jahr max. 100.000 € Umsatz, [§ 19 UStG](https://www.gesetze-im-internet.de/ustg_1980/__19.html))
   - Gewinnermittlung: Einnahmen-Überschuss-Rechnung
   - Voraussichtlicher Umsatz und Gewinn im ersten Jahr: ehrlich klein schätzen, z. B. 300 € Umsatz und 250 € Gewinn
3. Die Steuernummer kommt per Post. Die brauchst du für Digistore24.

## 4. Digistore24 (ca. 30 Min.)

- Verkäuferkonto anlegen. Bei den Steuerangaben: Kleinunternehmer, Steuernummer eintragen, sobald sie da ist.
- Auszahlung per SEPA auf dein Konto. Ausgezahlt wird am 7. des Monats ab 50 € Guthaben ([Digistore24](https://www.digistore24.com/de/blog/der-digistore24-auszahlungs-fahrplan/)).
- **Wichtig für die Automatik:** E-Mail-Benachrichtigung bei jedem Verkauf einschalten. Ohne diese Mails kann die Aufgabe „Einnahmen erfassen“ nichts eintragen.
- Produkte:
  - „8-Wochen-Speed-Plan“ (Arbeitstitel), 14,90 €, Download-PDF
  - „Video-Analyse deines Sprints“, 24,90 €, ab Monat 2. Die Lieferung ist ein PDF mit dem Link zum Formular, siehe [vorlagen/video-analyse.md](vorlagen/video-analyse.md)

## 5. Impressum-Seite und Link in Bio (ca. 15 Min.)

- Eine öffentliche Notion-Seite mit Impressum und Datenschutzinfo, Vorlage in [vorlagen/impressum-datenschutz.md](vorlagen/impressum-datenschutz.md). Adresse erst eintragen, wenn Punkt 1.3 geklärt ist.
- In die Instagram-Bio: Link zu einer kleinen Linkseite mit Produkt und Impressum.

## 6. Pipeline einschalten (5 Min.)

In der `.env` auf deinem Rechner:

```
VERKAUFS_HINWEIS=Mein eigener 8-Wochen-Speed-Plan: Link in Bio
VERKAUFS_LINK=https://www.digistore24.com/product/DEINE-NUMMER
```

Ab dann bekommt jede Woche höchstens ein Beitrag den Hinweis. Solange die Zeile leer ist, passiert nichts.
