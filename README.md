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

## Installation aus dem GitHub-Release

1. Unter [Releases](https://github.com/jonas12341/dns_blocklist_merger/releases) die neueste `dns_blocklist_merger-<version>.apk` auf dem Handy herunterladen.
2. Datei öffnen. Android fragt einmal nach „Unbekannte Apps installieren“ für Browser oder Dateimanager.
3. Updates: neuere APK aus dem nächsten Release genauso öffnen. Sie installiert sich über die alte Version, die Daten bleiben erhalten.

Eine installierte Debug-Version vorher deinstallieren: Sie ist mit einem anderen Schlüssel signiert.

## Release bauen

Der Release-Schlüssel liegt außerhalb des Repos. Pfad und Passwörter stehen in `~/.gradle/gradle.properties` oder in Umgebungsvariablen:

```properties
DNSBM_RELEASE_STORE_FILE=/pfad/zu/release.jks
DNSBM_RELEASE_STORE_PASSWORD=...
DNSBM_RELEASE_KEY_ALIAS=dnsbm-release
DNSBM_RELEASE_KEY_PASSWORD=...
```

Dann `versionCode` und `versionName` in `app/build.gradle.kts` erhöhen und bauen:

```bash
./gradlew assembleRelease
gh release create v<version> app/build/outputs/apk/release/app-release.apk#dns_blocklist_merger-<version>.apk
```

Ohne diesen Schlüssel lassen sich keine Updates über eine installierte Release-Version spielen.

## Erste Schritte in der App

1. Blocklisten-Adressen hinzufügen (z. B. `https://big.oisd.nl`) und oben auf „Aktualisieren“ tippen.
2. In den Einstellungen die FRITZ!Box-Freigabe eintragen, z. B. `smb://fritz.box/FRITZ.NAS/dnsfilter/blocklist.txt`, dazu Benutzer und Passwort, dann „Verbindung testen“. `//fritz.box/...` geht auch. Endet der Pfad auf einen vorhandenen Ordner, schreibt die App `blocklist.txt` hinein. Das Verzeichnis muss es schon geben. In der FRITZ!Box muss die Freigabe als Netzlaufwerk (SMB) eingeschaltet sein und der Benutzer Zugang zu NAS-Inhalten mit Schreibrecht haben. Die App spricht nur SMB2/3 (FRITZ!OS 7 oder neuer).
3. „Auf FRITZ!Box schreiben“ oder auf den täglichen Lauf warten.

## Aufbau

- `core/`: reine Kotlin-Logik ohne Android-Abhängigkeit (Parser, Auswahl). Tests laufen mit JUnit ohne Gerät.
- `app/`: Android-App mit Jetpack Compose und Material 3.
