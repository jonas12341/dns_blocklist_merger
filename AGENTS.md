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
  - `download/`: `BlocklistDownloader` (OkHttp, conditional GET with ETag/If-Modified-Since, streams into the parser, German error texts; empty body or no domains count as errors). `Refresher.refreshAll()` downloads every list, records success/304/error at the list (errors keep the last good domains) and recomputes the output.
  - `smb/`: `SmbTarget.parse` (`smb://host/share/dir/file`), `SmbWriter` (4 MB guard, credential check, German error texts via `messageFor`), `SmbjFiles` (smbj with BouncyCastle provider; writes `<file>.tmp` then renames onto the target; probe = create and delete a test file), `AesGcmCipher` + `PasswordStore` (password AES-GCM encrypted with an Android Keystore key, stored base64 in the settings DataStore; undecryptable reads as no password).
  - `OutputPublisher`: renders the output, writes it via `SmbWriter`, records `writtenAt`/`writeError`.
  - `AppController`: app-wide scope, runs refresh/recompute/write jobs one at a time (`busy` text, snackbar `messages`).
  - `ui/`: `AppNavigation` (routes home, domains, settings; ViewModels built with `viewModel { }` from the container), `SubScreen` (back-arrow scaffold), `Format.kt` (German number/date formats). `ui/home/`: start page after concept B (output card with usage meter, domains link, lists by priority with −/+ stepper, error box only at the list, add/remove list). `ui/domains/`: paged domain list (Room `PagingSource`, LIKE search, debounced) with status pills, exclude/allow, permanent exceptions card; every exception change triggers `AppController.recompute()`. `ui/settings/`: theme, size (slider + field, 5-4096 KB), weights a/b (0-1000) saved together with „Übernehmen“, subdomain switch, daily time (M3 `TimePicker`), refresh now, SMB path/user; size/weights/switch changes recompute the output. `ui/theme/`: `AppTheme` (dynamic colours, `dynamic = false` for previews/tests), `Pill`.
- UI tests: Robolectric + Compose test rule with `@GraphicsMode(NATIVE)`; `saveScreenshot(name)` writes `app/build/screenshots/<name>.png` for a look by eye.
- Tests: `core` with plain JUnit; `app` with Robolectric (`@Config(sdk = [36])`, in-memory Room).

## Spec and tickets

Spec `docs/spec.md`, UI concept B in `docs/ui-pocs/index.html`. Tickets are GitHub issues; plan in #13.
