# dns_blocklist_merger

Android-App (ab Android 16), die DNS-Blocklisten aus dem Internet zusammenführt und eine einzige Ausgabedatei für die FRITZ!Box erzeugt.

Spezifikation: [docs/spec.md](docs/spec.md)

## Bauen

Voraussetzungen: JDK 21 (Robolectric-Tests mit Android 16 brauchen es) und ein Android SDK mit Platform 37 (`ANDROID_HOME` oder `local.properties` mit `sdk.dir=...`).

```bash
./gradlew assembleDebug test
```

Die Debug-APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`.

## Auf das Handy bringen (Debug-APK)

Mit USB- oder WLAN-Debugging:

```bash
./gradlew installDebug
# oder
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Ohne adb: `app-debug.apk` aufs Handy kopieren (Kabel, Cloud, Mail) und dort öffnen. Android fragt einmal nach der Erlaubnis „Unbekannte Apps installieren“ für die App, mit der die Datei geöffnet wird.

Die Debug-APK ist mit dem Debug-Schlüssel dieses Rechners signiert. Eine später installierte Release-Version lässt sich deshalb nicht als Update darüber spielen; vorher die Debug-Version deinstallieren.

## Erste Schritte in der App

1. Blocklisten-Adressen hinzufügen (z. B. `https://big.oisd.nl`) und oben auf „Aktualisieren“ tippen.
2. In den Einstellungen die FRITZ!Box-Freigabe eintragen, z. B. `smb://fritz.box/FRITZ.NAS/dnsfilter/blocklist.txt`, dazu Benutzer und Passwort, dann „Verbindung testen“. Das Verzeichnis muss es schon geben.
3. „Auf FRITZ!Box schreiben“ oder auf den täglichen Lauf warten.

## Aufbau

- `core/`: reine Kotlin-Logik ohne Android-Abhängigkeit (Parser, Auswahl). Tests laufen mit JUnit ohne Gerät.
- `app/`: Android-App mit Jetpack Compose und Material 3.
