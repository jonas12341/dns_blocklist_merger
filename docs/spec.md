# Spezifikation: dns_blocklist_merger

Stand: 2026-11-15

## Ziel

Android-App (ab Android 16). Sie lädt DNS-Blocklisten aus dem Internet, führt sie zusammen und erzeugt eine einzige Ausgabedatei für die FRITZ!Box.

## Anforderungen (Vorgabe Jonas)

### A1 Blocklisten verwalten
- Die Oberfläche erlaubt die Eingabe von URLs zu Blocklisten.
- Die Blocklisten lassen sich priorisieren.

### A2 Status der Blocklisten
- Pro Blockliste werden Datum und Uhrzeit der letzten Aktualisierung angezeigt.
- Bei fehlerhaftem Zugriff wird eine Fehlermeldung angezeigt.

### A3 Domain-Liste und Ausschlüsse
- Die App zeigt eine Liste der aktuellen zu blockenden DNS-Einträge (Domains).
- Einzelne Domains lassen sich von der Verarbeitung ausschließen.

### A4 Settings
- Eine maximale Größe der Ausgabedatei ist einstellbar.

### A5 Auswahl der Einträge für die Ausgabedatei
- Die Auswahl berücksichtigt die Priorität der Blocklisten und die Häufigkeit, mit der eine Domain über alle Blocklisten hinweg vorkommt.
- Einträge aus Blocklisten der höchsten Priorität werden immer geschrieben.
- Ist die maximale Größe danach noch nicht erreicht, werden weitere Einträge nach Priorität + Häufigkeit aufgenommen, bis die maximale Größe erreicht ist.
- Ausgeschlossene Domains (A3) werden nie geschrieben.

### A6 Format der Ausgabedatei
- Jeder Eintrag kommt genau einmal vor.
- Die Einträge sind alphabetisch absteigend sortiert.

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

## Offene Fragen

1. **FRITZ!Box-Format und -Limit:** Wohin kommt die Datei in der FRITZ!Box (z. B. Kindersicherung, Filterliste „Gesperrte Internetseiten“)? Welches Zeilenformat und welche Größenbegrenzung gelten dort? Davon hängt der Standardwert für die maximale Größe ab.
2. **Einheit der maximalen Größe:** Anzahl Einträge oder Bytes/KB?
3. **Bereitstellung der Datei:** Speichern über den Android-Dateidialog, Teilen-Menü, oder etwas anderes (z. B. direkter Upload zur FRITZ!Box)?
4. **„Höchste Priorität“:** Nur die eine Liste auf Platz 1, oder können mehrere Listen dieselbe Priorität haben?
5. **Reihenfolge beim Auffüllen:** Vorschlag: zuerst nach Priorität der besten Liste, in der die Domain vorkommt (hoch vor niedrig), dann nach Häufigkeit (häufig vor selten). „Aufsteigend“ in der Vorgabe ist so gelesen, dass die Einträge in dieser Rangfolge nacheinander aufgenommen werden. Stimmt das?
6. **Sortierung:** „Alphabetisch absteigend“ heißt Z vor A. Ist das so gewollt, oder ist A vor Z gemeint?
7. **Aktualisierung:** Nur manuell, oder auch automatisch im Hintergrund (z. B. täglich)?
8. **Subdomains:** Wenn `example.com` geschrieben wird, sollen `ads.example.com` usw. entfallen, um Platz zu sparen?
