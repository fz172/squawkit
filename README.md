# SquawkIt

**SquawkIt** is a cross-platform aviation logbook and fleet-management app for aircraft owners and
mechanics. It keeps maintenance records, inspection compliance, and open squawks in one place —
designed to feel like a well-made instrument, not an app trying to impress.

> **Naming note:** The user-facing brand is **SquawkIt**. The codebase still uses the original
> **WingsLog** name for internal identifiers (Kotlin package `dev.fanfly.wingslog`, Gradle module
> names, the Firebase project `wingslog-9ca4e`). This is intentional — renaming the published app
> identity would break Play Store / App Store / Firebase registration.

## Platforms

One shared Kotlin Multiplatform + Compose Multiplatform codebase ships to:

- **Android** (`app/`) — minSdk 33
- **iOS** (`iosApp/`) — Xcode project wrapping the shared framework
- **Web** (`webApp/`) — Kotlin/JS, served as a Firebase-hosted site

## Features

- **Fleet & aircraft** — manage a fleet of aircraft with airframe, engine, and propeller details.
- **Maintenance logs** — record maintenance events with a maintenance-overview summary per aircraft.
- **Inspection compliance** — track recurring maintenance tasks with automatic **due-status**
  computation (OVERDUE / DUE SOON), surfaced at the top of every list.
- **Squawks** — log defects and discrepancies (OPEN → ADDRESSED → DISMISSED), including AOG
  (aircraft-on-ground) alerts, surfaced as an aircraft overview tab.
- **Technicians** — manage technician profiles and certificates; sign-off attribution on work.
- **Attachments** — attach files/images to records, with background upload/download
  (behind the `attachmentUploadEnabled` feature-lab flag).
- **Logbook export** — export logs to PDF / CSV / XLSX, bundled as a ZIP, with optional email
  delivery via a Cloud Function.
- **Cloud sync & backup** — local-first storage backed up to the cloud, with Cloud Sync and
  Sync-on-Cellular toggles.
- **Adaptive layout** — a single adaptive shell reflows from phone (bottom nav) to tablet/web
  (navigation rail + sidebar + multi-pane detail).
- **Auth** — Google and Apple sign-in via Firebase Auth.

## Architecture

**MVVM + StateFlow, Koin DI, local-first storage, background sync.** In one line:

> A SQLDelight **`EntityStore`** is the single source of truth for every read and write; a background
> **Firestore sync engine** is the *only* Firestore client, pushing local changes and pulling remote
> ones. There is no Firestore in the UI read path.

- **Local-first (R1, shipped):** all reads/writes go through `core/storage`'s SQLDelight entity
  store. Feature managers never touch Firestore directly.
- **Sync engine (`feature/sync`):** hydration, pull subscriptions, a push worker, tombstone GC, and
  blob upload/download (Android WorkManager, iOS `URLSession` background tasks).
- **Models:** Wire-generated protobuf types (`core/model`).
- **Backend:** Firebase Cloud Functions (TypeScript, Functions v2, Node 22) in
  `backend/firebase/functions/` — e.g. export email delivery. Not a Gradle module.

### Module layout

```
app/              Android entry point
composeApp/       Shared Compose UI + central Koin init
webApp/           Kotlin/JS web host (OPFS SQLite storage)
iosApp/           Xcode project
core/             model · ui · auth · firebase · storage · datetime · appinfo · analytics · nav
feature/          fleet · aircraft · logs · tasks · squawk · technician · attachment ·
                  export · sync · settings · userprofile · featurelab · stresstest
backend/          Firebase Cloud Functions (TypeScript)
```

Each feature follows a canonical submodule pattern — `model` / `datamanager` / `sharedassets` /
`viewing` / `update` (`feature/tasks` is the reference). See [AGENTS.md](AGENTS.md) for the full
dependency rules and module map.

## Tech stack

| Area | Choice |
|------|--------|
| Language | Kotlin 2.4.0 (Multiplatform) |
| UI | Compose Multiplatform 1.11.1 |
| Build | Gradle / AGP 9.2.1 |
| DI | Koin 4.2.2 |
| Local storage | SQLDelight 2.3.2 |
| Backend | Firebase (Auth, Firestore, Storage, Analytics, Cloud Functions; BOM 34.15.0) via GitLive KMP SDK |
| Models | Wire / protobuf |

## Design system

SquawkIt uses a deliberate aviation identity (no dynamic color):

- **Palette:** Aviation Blue (instrument-panel reference) primary, Instrument Amber accent used
  sparingly, semantic forest-green / dark-amber status colors.
- **Typography:** Space Grotesk for headlines, **JetBrains Mono** for technical data (tail numbers,
  serials, tach times — character alignment is semantic), system sans for body.

See [DESIGN.md](DESIGN.md) and [PRODUCT.md](PRODUCT.md) for the full brand and product principles.

## Build & run

```bash
./gradlew assembleDebug                              # Android debug APK
./gradlew assembleDogfoodDebug                       # Dogfood APK (includes fake data generator)
./gradlew lint                                       # Lint
./gradlew testDebugUnitTest                          # All Android unit tests
./gradlew :feature:fleet:viewing:testDebugUnitTest   # Single-module tests
./gradlew :composeApp:iosSimulatorArm64Test          # iOS simulator tests (local only)
./gradlew :webApp:jsBrowserDevelopmentWebpack        # Web development bundle
```

For iOS, open `iosApp/` in Xcode and run the **iosAppDogfood** scheme.

CI runs lint → `assembleDebug` → `testDebugUnitTest` on every push and requires a
`GOOGLE_SERVICES_JSON` secret. iOS is not built on CI.

## Documentation

- [AGENTS.md](AGENTS.md) — authoritative build/architecture/conventions guide
- [CLAUDE.md](CLAUDE.md) — quick orientation for AI assistants
- [PRODUCT.md](PRODUCT.md) / [DESIGN.md](DESIGN.md) — product and design principles
- [`docs/`](docs/) — per-feature PRDs and design docs (storage, sync, squawks, export, attachments,
  technician, analytics, web/adaptive layout), each with an implementation-status note
