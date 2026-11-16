# AGENTS.md

Current-state map for coding agents. Keep it a snapshot: edit entries, do not append history.

## Build

JDK 17, Android SDK platform 37. `./gradlew assembleDebug test`. No device here: unit tests only, Jonas tests on his phone.

## Modules

- `core/` (pure Kotlin, JUnit):
  - `BlocklistParser` reads adblock (`||domain^`), hosts and plain domain lists, line by line. `DomainNormalizer` lowercases, strips scheme/port/path/trailing dot, converts IDN to punycode and drops IPs, single-label and reserved names.
- `app/` (Android, Compose, Material 3): `MainActivity`, `ui/theme/AppTheme`.

## Spec and tickets

Spec `docs/spec.md`, UI concept B in `docs/ui-pocs/index.html`. Tickets are GitHub issues; plan in #13.
