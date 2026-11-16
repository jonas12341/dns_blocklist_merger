# AGENTS.md

Current-state map for coding agents. Keep it a snapshot: edit entries, do not append history.

## Build

JDK 21 (Robolectric for SDK 36 needs it), Android SDK platform 37. `./gradlew assembleDebug test`. No device here: unit tests only, Jonas tests on his phone.

## Modules

- `core/` (pure Kotlin, JUnit):
  - `BlocklistParser` reads adblock (`||domain^`), hosts and plain domain lists, line by line. `DomainNormalizer` lowercases, strips scheme/port/path/trailing dot, converts IDN to punycode and drops IPs, single-label and reserved names.
  - `Selection.compute` builds the output file (spec A5/A6). Priority 1 is the highest; priority value = lowest priority number - p + 1. Highest-priority domains are always written, the rest by rank while it fits (`||domain^\n` bytes). With subdomain pruning a written parent replaces already written children. Returns `DomainInfo` per domain (rank, frequency, best priority, `DomainStatus`) for the UI.
- `app/` (Android, Compose, Material 3):
  - `App` / `AppContainer`: manual wiring of database, settings and repositories (`context.container`).
  - `data/`: Room `AppDatabase` (tables `blocklist`, `exception`, `output_domain`, `output_state`; schema JSON in `app/schemas/`). The domains of each list's last good download are files in `filesDir/lists/<id>.txt` (`DomainFileStore`), not table rows. `BlocklistRepository`: list CRUD and download status, exceptions, `recompute(settings)` (runs `Selection`, stores per-domain rows and the output numbers), `renderOutput()`, paging of domains. `SettingsRepository`: DataStore settings with spec defaults and range checks; no password there.
  - `ui/theme/AppTheme`.
- Tests: `core` with plain JUnit; `app` with Robolectric (`@Config(sdk = [36])`, in-memory Room).

## Spec and tickets

Spec `docs/spec.md`, UI concept B in `docs/ui-pocs/index.html`. Tickets are GitHub issues; plan in #13.
