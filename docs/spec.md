# Spezifikation: dns_blocklist_merger

Stand: 2026-11-15

## Ziel

Android-App (ab Android 16). Sie lädt DNS-Blocklisten aus dem Internet, führt sie zusammen und erzeugt eine einzige Ausgabedatei für den DNS-Filter der FRITZ!Box.

Zielgerät: FRITZ!Box 7530 AX mit FRITZ!OS 8.50.

## Anforderungen

### A1 Blocklisten verwalten
- Die Oberfläche erlaubt die Eingabe von URLs zu Blocklisten.
- Jede Blockliste hat eine Priorität. Mehrere Blocklisten dürfen dieselbe Priorität haben.

### A2 Aktualisierung und Status
- Die Blocklisten werden automatisch einmal täglich im Hintergrund aktualisiert.
- Eine manuelle Aktualisierung ist jederzeit möglich.
- Pro Blockliste werden Datum und Uhrzeit der letzten Aktualisierung angezeigt.
- Bei fehlerhaftem Zugriff wird eine Fehlermeldung angezeigt.

### A3 Domain-Liste und Ausnahmen
- Die App zeigt eine Liste der aktuellen zu blockenden Domains.
- Einzelne Domains lassen sich von der Verarbeitung ausschließen.
- Domains lassen sich manuell als dauerhafte Ausnahme anlegen. Ausnahmen werden dauerhaft gespeichert und gelten bei jeder weiteren Erzeugung.

### A4 Settings
- Maximale Größe der Ausgabedatei in KB. Standard und Obergrenze: 4096 KB (4 MB). Grund: Der DNS-Filter der FRITZ!Box hat max. 8 MB internen Speicher frei, zum Austausch darf die Datei höchstens 4 MB groß sein.
- Rang-Formel (siehe A5) mit einstellbaren Parametern `a` und `b`.
- Schalter „Subdomains weglassen“ (siehe A6). Standard: aus.
- Zugang zum FRITZ!Box-Speicher: Pfad zum freigegebenen Verzeichnis, Benutzer und Passwort (siehe A7).

### A5 Auswahl der Einträge für die Ausgabedatei
- Aus der Priorität der Blocklisten und der Häufigkeit einer Domain über alle Blocklisten hinweg wird ein Rang berechnet.
- Einträge aus Blocklisten der höchsten Priorität werden immer geschrieben.
- Ist die maximale Größe danach noch nicht erreicht, werden weitere Einträge nach Rang (bester zuerst) aufgenommen, bis die maximale Größe erreicht ist.
- Ausgeschlossene Domains und Ausnahmen (A3) werden nie geschrieben.
- Standardformel: `Rang = a × Prioritätswert + b × Häufigkeit`. Startwerte `a = 10`, `b = 1`, beide in den Settings einstellbar.
- Der Prioritätswert ist bei der höchsten Priorität am größten. Die Häufigkeit ist die Anzahl der Blocklisten, in denen die Domain vorkommt.

### A6 Format der Ausgabedatei
- Adblock-Syntax: ein Eintrag pro Zeile im Format `||domain^`.
- Jeder Eintrag kommt genau einmal vor.
- Die Einträge sind nach Domain aufsteigend sortiert: 0–9 vor A–Z.
- Wenn der Schalter „Subdomains weglassen“ an ist, entfallen Subdomains, deren übergeordnete Domain bereits in der Datei steht (z. B. entfällt `ads.example.com`, wenn `example.com` geschrieben wird).

### A7 Bereitstellung der Datei
- Die App schreibt die Datei direkt in ein freigegebenes Verzeichnis auf dem Speicher der FRITZ!Box. Der Zugang ist mit Benutzer und Passwort geschützt.
- Pfad, Benutzer und Passwort sind in den Settings einstellbar.
- Zusätzlich kann die Datei manuell über den Android-Dateidialog gespeichert werden.

## Beispiel-Blocklisten

| URL | Format |
|---|---|
| https://big.oisd.nl | Adblock-Syntax (`\|\|domain^`) |
| https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/pro.txt | Adblock-Syntax (`\|\|domain^`) |
| https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts | Hosts-Datei (`0.0.0.0 domain`) |

## Abgeleitete Anforderungen (Vorschlag, noch nicht bestätigt)

- Der Parser erkennt mindestens drei Formate: Adblock-Syntax, Hosts-Datei und reine Domain-Liste. Kommentare (`#`, `!`) und leere Zeilen werden ignoriert.
- Domains werden normalisiert: Kleinschreibung, ohne Schema, Pfad, Port und abschließenden Punkt. Hosts-Einträge wie `localhost` oder `0.0.0.0` werden verworfen.
- Eine fehlgeschlagene Blockliste blockiert die Erzeugung nicht. Die App nutzt dann den zuletzt erfolgreich geladenen Stand dieser Liste und zeigt den Fehler an.
- Zugriff auf das freigegebene Verzeichnis per SMB (SMB2/3), die Netzwerkfreigabe der FRITZ!Box.
- Das Passwort wird verschlüsselt gespeichert (Android Keystore), nie im Klartext.

## Entscheidungen (Jonas, 2026-11-15)

| Nr. | Frage | Antwort |
|---|---|---|
| 1 | FRITZ!Box-Ziel und Limit | DNS-Filter; interner Speicher max. 8 MB frei, Datei zum Austausch max. 4 MB |
| 2 | Einheit der Maximalgröße | KB |
| 3 | Bereitstellung | Einstellbarer Pfad direkt auf FRITZ!Box-Speicher, zusätzlich Dateidialog |
| 4 | Gleiche Priorität | Mehrere Listen dürfen dieselbe Priorität haben |
| 5 | Auffüll-Reihenfolge | Rang aus Priorität und Häufigkeit, Formel über Settings einstellbar |
| 6 | Sortierung | 0..A–Z (aufsteigend) |
| 7 | Aktualisierung | Automatisch täglich, zusätzlich manuell |
| 8 | Subdomains | Weglassen, falls das keine Auswirkung auf das Filtern hat |
| 9 | Dauerhafte Ausnahmen | Domains manuell als Standard-Ausnahme anlegen, dauerhaft speichern |
| 10 | Standard-Rang-Formel | `a × Prioritätswert + b × Häufigkeit` passt |
| 11 | Subdomain-Verhalten | Über die Settings steuern |
| 12 | Zeilenformat | Adblock-Syntax |
| 13 | Zugriff FRITZ!Box-Speicher | Freigegebenes Verzeichnis, Zugang mit Benutzer und Passwort; FRITZ!Box 7530 AX, FRITZ!OS 8.50 |

## Offene Fragen

Keine.
