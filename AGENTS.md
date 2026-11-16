# AGENTS.md

Current-state map for coding agents. Keep it a snapshot: edit entries, do not append history.

## Build

JDK 17, Android SDK platform 37. `./gradlew assembleDebug test`. No device here: unit tests only, Jonas tests on his phone.

## Modules

- `core/` (pure Kotlin, JUnit):
  - `BlocklistParser` reads adblock (`||domain^`), hosts and plain domain lists, line by line. `DomainNormalizer` lowercases, strips scheme/port/path/trailing dot, converts IDN to punycode and drops IPs, single-label and reserved names.
  - `Selection.compute` builds the output file (spec A5/A6). Priority 1 is the highest; priority value = lowest priority number - p + 1. Highest-priority domains are always written, the rest by rank while it fits (`||domain^\n` bytes). With subdomain pruning a written parent replaces already written children. Returns `DomainInfo` per domain (rank, frequency, best priority, `DomainStatus`) for the UI.
- `app/` (Android, Compose, Material 3): `MainActivity`, `ui/theme/AppTheme`.

## Spec and tickets

Spec `docs/spec.md`, UI concept B in `docs/ui-pocs/index.html`. Tickets are GitHub issues; plan in #13.
