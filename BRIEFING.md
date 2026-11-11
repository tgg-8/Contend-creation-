# BRIEFING: Eigener Whoop-Connector, der ohne mich läuft

## Wer ich bin und was ich will
Ich bin Leistungssportler (Sprint) und trage ein Whoop. Ich möchte einen kleinen, eigenen Whoop-Connector auf Basis der offiziellen Whoop Developer API (developer.whoop.com). Er soll meine Whoop-Daten regelmäßig abholen, sinnvoll auswerten und das Ergebnis dort ablegen, wo es mir im Alltag am meisten bringt. Nach der Einrichtung soll alles komplett selbstständig laufen, ohne dass ich irgendetwas anstoßen muss.

Du bist dabei mein autonomer Entwickler. Du planst, baust, testest, richtest ein und betreibst das Ganze selbst. Du darfst eigenen Code, eigene Artefakte und ein eigenes GitHub-Repository dafür anlegen. Sprache, Struktur und Hosting wählst du selbst – entscheidend sind Einfachheit, Zuverlässigkeit, Sicherheit und dass es dauerhaft ohne mich läuft.

## Pflicht: OODA vor jeder größeren Entscheidung
Bevor du etwas Wichtiges tust (Architektur, Sprache, Hosting, Speicherort der Tokens, Zielort der Auswertung, Zeitplan, zusätzliche Features), gehst du die OODA-Loop durch und trägst das Ergebnis kurz in DECISIONS.md ein:

1. Observe: Was ist da? Prüfe deine verfügbaren Tools, Connectors, Skills, Zugänge und GitHub-Rechte. Prüfe, ob es schon eine Whoop-Verbindung gibt und was sie kann. Lies die aktuelle offizielle Whoop-API-Dokumentation (API-Version, OAuth-Ablauf, Scopes, Endpunkte, Pagination, Rate Limits, Webhooks), statt aus dem Gedächtnis zu arbeiten.
2. Orient: Was bringt der Schritt wirklich? Bewerte den Nutzen (1–10), den Aufwand (niedrig/mittel/hoch) und das Risiko (niedrig/mittel/hoch). Was ist der Mehrwert gegenüber dem, was schon existiert?
3. Decide: Wähle die einfachste Lösung, die sicher und dauerhaft ohne mich funktioniert. Nenne kurz die verworfenen Alternativen und warum.
4. Act: Setze um. Wenn du auf ein Hindernis oder eine neue Erkenntnis stößt, starte einen neuen OODA-Durchlauf.

Baue nichts, nur weil es geht. Wenn ein Feature wenig bringt, lass es weg und notiere das in DECISIONS.md.

## Was der Connector können soll
- Daten über die offizielle Whoop Developer API abrufen: Recovery, Schlaf, Cycles/Strain, Workouts, Profil und Körpermessung.
- Neue Daten seit dem letzten Lauf erkennen, Pagination korrekt behandeln und keine Duplikate erzeugen.
- Eine kurze, verständliche Auswertung erzeugen: Was sagen Recovery, Schlaf und Strain über meinen Zustand, und welche Tendenz zeigt sich über die letzten Tage? Nur Aussagen, die die Daten wirklich hergeben – keine erfundenen Werte und keine medizinischen Diagnosen.
- Das Ergebnis automatisch an einem sinnvollen Zielort ablegen (zum Beispiel Notion, Google Sheet oder eine Datei im Repo). Den Zielort wählst du per OODA und begründest ihn.
- Optional, nur wenn die OODA-Bewertung klar dafür spricht: Webhooks statt reinem Zeitplan.

## Technische Leitplanken
- Nutze den offiziellen OAuth-2.0-Ablauf von Whoop. Prüfe in der aktuellen Doku, welche Scopes nötig sind, auch den Scope für Refresh-Tokens, und fordere nur Lesezugriff an.
- Prüfe in der Doku, ob Refresh-Tokens bei jeder Erneuerung wechseln. Falls ja, muss der neue Refresh-Token nach jedem Lauf sicher und dauerhaft gespeichert werden, sonst bricht die Automatisierung nach dem ersten Lauf ab. Löse dieses Problem ausdrücklich und teste es live.
- Der automatische Betrieb muss ohne meinen Rechner laufen, zum Beispiel über GitHub Actions mit Zeitplan im privaten Repo oder eine andere kostenlose, zuverlässige Lösung. Prüfe dabei Einschränkungen wie die Deaktivierung von Zeitplänen bei Inaktivität und baue eine Gegenmaßnahme ein.
- Fehlerbehandlung: abgelaufene oder ungültige Tokens, Rate Limits (429) mit Backoff, API-Ausfälle und Netzwerkfehler mit Wiederholungen, leere Zeiträume ohne Daten.
- Bei einem endgültig fehlgeschlagenen Lauf werde ich automatisch benachrichtigt, mit einer kurzen, verständlichen Fehlermeldung und dem nächsten Schritt.
- Logs ohne Tokens, Secrets oder unnötige Gesundheitsdetails.
- Code sauber, klein und wartbar, mit Linting und automatisierten Tests (gemockte API-Antworten plus ein echter Live-Test).

## Was du selbst machst und wann du mich brauchst
- Mach alles selbst, was mit deinen Tools möglich ist: Repo anlegen, Code schreiben, testen, Workflow und Zeitplan einrichten, Secrets setzen, Doku schreiben, Fehler beheben, Live-Lauf prüfen. Wenn ein Browser verfügbar ist, nutze ihn für Schritte im Web.
- Manche Schritte kann nur ich machen, zum Beispiel die Whoop-Developer-App anlegen, Client-ID und Secret holen, die Login-Einwilligung im Browser bestätigen oder mich bei GitHub anmelden. Erledige vorher alles andere und sammle dann alle offenen Schritte in EINER nummerierten Klick-für-Klick-Anleitung (genaue Seiten, Felder, Werte wie die Redirect-URI und was ich dir danach zurückgebe). Frag mich nicht einzeln zwischendurch.
- Frag nur nach, wenn eine Antwort den Ablauf wirklich verändert. Triff sonst vernünftige Annahmen und notiere sie in DECISIONS.md.

## Sicherheit und harte Grenzen
- Das Repo ist und bleibt privat. Meine Gesundheitsdaten werden nirgends veröffentlicht und nicht an Dritte gesendet, außer an den gewählten Zielort.
- Tokens, Client-ID und Secret gehören niemals in Code, Logs oder Git-Historie, sondern in Secrets bzw. sichere Speicher. Lege eine .gitignore an und mach einen Secret-Scan.
- Nur Lesezugriff auf Whoop, nur die minimal nötigen Rechte auf allen Diensten.
- Lösche nichts, was du nicht selbst angelegt hast. Nutze keine kostenpflichtigen Dienste.
- Erfinde keine Endpunkte, Daten, Testergebnisse oder Erfolge. Wenn etwas nicht funktioniert oder sich nicht prüfen lässt, sag es ehrlich.
- Wenn etwas nach drei ernsthaften Lösungsversuchen weiterhin scheitert, dokumentiere es in DECISIONS.md und nimm den nächstbesten Weg.

## Dokumentation im Repo
- README.md: Was der Connector macht, wie er aufgebaut ist und wo das Ergebnis landet.
- RUNBOOK.md: Einrichtung, Betrieb, typische Fehler und ihre Lösung, wie ich Tokens erneuere, wie ich alles pausiere, stoppe oder komplett lösche.
- DECISIONS.md: Alle OODA-Einträge mit Nutzen, Aufwand, Risiko, Entscheidung und verworfenen Alternativen.

## Abschlussbericht
Gib am Ende im Chat einen kurzen Bericht auf Deutsch aus, locker und verständlich:
1. Was jetzt läuft und wie oft.
2. Wo ich die Auswertung finde.
3. Wie ich merke, wenn etwas schiefgeht.
4. Welche Entscheidungen den größten Nutzen gebracht haben.
5. Was ich noch selbst tun muss (falls etwas offen ist).
