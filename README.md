# dns_blocklist_merger

Android-App (ab Android 16), die DNS-Blocklisten aus dem Internet zusammenführt und eine einzige Ausgabedatei für die FRITZ!Box erzeugt.

Spezifikation: [docs/spec.md](docs/spec.md)

## Bauen

Voraussetzungen: JDK 21 (Robolectric-Tests mit Android 16 brauchen es) und ein Android SDK mit Platform 37 (`ANDROID_HOME` oder `local.properties` mit `sdk.dir=...`).

```bash
./gradlew assembleDebug test
```

Die Debug-APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`.

## Aufbau

- `core/`: reine Kotlin-Logik ohne Android-Abhängigkeit (Parser, Auswahl). Tests laufen mit JUnit ohne Gerät.
- `app/`: Android-App mit Jetpack Compose und Material 3.
