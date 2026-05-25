# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## What This Is

WingsLog is a **Kotlin Multiplatform** app for aviation logbook and fleet management — aircraft
CRUD, maintenance logs, inspection compliance tracking, and due-status computation. Targets
Android (minSdk 33), iOS, and web sharing Compose Multiplatform UI.

The user-facing app is branded **Hopply**; codebase identifiers (`wingslog`, package `dev.fanfly.wingslog`, Gradle module names) still use the original WingsLog name.

The app uses a **local-first architecture** (R1 — shipped, default path): a SQLDelight entity store is the single source of truth for every read and write, and a Firestore sync engine pushes local changes and pulls remote ones in the background. There is **no Firestore in the UI read path** and **no rollout flag** — local-first is the only code path. Local-first **attachments** (R2) infrastructure has largely landed (local blob store + background upload/download), but the attachment UI is gated behind the `attachmentUploadEnabled` feature-lab flag. See `docs/storage_r1_design.md` and `docs/storage_r2_design.md`.

## Build & CI Commands

```bash
./gradlew assembleDebug                          # Build debug APK
./gradlew assembleDogfoodDebug                   # Build dogfood APK (includes fake data generator)
./gradlew lint                                   # Lint checks
./gradlew testDebugUnitTest                      # Run all Android unit tests
./gradlew :feature:fleet:viewing:testDebugUnitTest  # Run tests for a single module
./gradlew :composeApp:iosSimulatorArm64Test      # Run iOS simulator unit tests (local only)
./gradlew :webApp:jsBrowserDevelopmentWebpack    # Build the web app development bundle
```

For the iOS dogfood build, select the **iosAppDogfood** scheme in Xcode and run. See [Dogfood Builds](#dogfood-builds) below.

CI (`.github/workflows/ci.yml`) runs lint → assembleDebug → testDebugUnitTest on every push. iOS is **not** built on CI. CI requires `GOOGLE_SERVICES_JSON` secret to write `google-services.json` before building.

## Module Structure

```
app/                    # Android entry point (MainActivity, WingsLogApplication)
composeApp/             # Shared Compose UI — nested navigation sub-graphs rooted at AppEntry + central Koin init
webApp/                 # Kotlin/JS web host — shared feature UI with OPFS SQLite storage and Firebase JS startup
core/
  model/                # Wire-generated protobuf models (Aircraft, MaintenanceLog, Squawk, InspectionCard…)
  ui/                   # Material 3 theme, color tokens, shared Compose components (incl. DualSegmentedFilter, AvatarIcon)
  auth/                 # Firebase Auth with platform-specific implementations
  firebase/             # Centralized Firebase utilities — FirebaseDataExt (ByteArray↔Firebase Storage Data, expect/actual)
  storage/              # R1 local-first foundation — SQLDelight schema (entity, sync_cursor, sync_config, blob_object),
                        #   EntityStore, CollectionKind (8 kinds), EntityCodecRegistry, blob RemoteState
  datetime/             # Date/time utilities — WireInstantFactory, platform-specific formatters
  appinfo/              # Cross-platform app version/build info (AppInfo expect/actual)
  attachments/          # ORPHANED — superseded by feature/attachment; de-registered from settings.gradle.kts
                        #   (stale build files remain, slated for deletion). Do NOT add to here.
feature/
  fleet/                # Fleet dashboard (canonical layout, no update — dashboard is read-only)
    model/              #   Aircraft-related domain types
    datamanager/        #   FleetManager: observes aircraft via EntityStore<Aircraft> Flow, CRUD
    sharedassets/       #   Strings, drawables shared across fleet UI
    viewing/            #   DashboardScreen + AircraftDashboardCard + FleetDashboardViewModel
  aircraft/             # Aircraft detail view (tab-based; not part of fleet/ module)
    dashboard/          #   AircraftOverviewScreen, 4 tab composables (Overview → Squawks → Tasks → Logs),
                        #   AircraftOverviewViewModel, AircraftDashboardModule, AircraftTab enum
  logs/                 # Maintenance log UI (canonical layout)
    datamanager/        #   MaintenanceLogManager: CRUD for logs and maintenance overview
    sharedassets/       #   Strings, shared helpers
    viewing/            #   MaintenanceLogCard, MaintenanceLogDetailSheet, list ViewModel
    update/             #   MaintenanceLogFormScreen, EditAircraftScreen, form ViewModels
  tasks/                # Maintenance task inspection compliance (canonical layout)
    model/              #   DueMetadata, MaintenanceTaskWithStatus, domain enums
    datamanager/        #   TaskDataManager + TaskDueManager (two interfaces), Koin module
    sharedassets/       #   Strings, drawables
    viewing/            #   TaskCard, TaskDetailSheet (read-only composables)
    update/             #   AddTaskScreen, EditTaskScreen, ViewModels, form sections
  squawk/               # Aircraft squawk (defect/discrepancy) tracking (canonical layout) — surfaced as Aircraft Overview tab 2
    model/              #   SquawkWithStatus, SquawkStatus (OPEN / ADDRESSED / DISMISSED)
    datamanager/        #   SquawkManager (CRUD + markAddressed + dismissSquawk/reopenSquawk) over EntityStore<Squawk>
    sharedassets/       #   Strings, priority colors, dismiss-reason labels
    viewing/            #   SquawkCard, SquawkDetailSheet, SquawkPickerSheet, AogAlertSection
    update/             #   SquawkFormScreen (2-tab add/edit), LogPickerSheet, DismissSquawkDialog, SquawkFormViewModel
  technician/           # Technician management
    datamanager/        #   TechnicianManager
    manage/             #   Combined list + edit screens + ViewModels (TechnicianListScreen, EditTechnicianScreen)
    sharedassets/       #   CertificateInputFields, TechnicianPickerSheet, strings
  attachment/           # File/image attachment feature (R2 — substantially implemented; UI behind attachmentUploadEnabled flag)
    model/              #   AttachmentStatus, AttachmentWithState, BlobSyncState, PendingAttachment
    datamanager/        #   AttachmentManager, LocalBlobStore (SQLDelight blob_object), AttachmentOpener, QuotaChecker
    sharedassets/       #   Strings, type icons
    viewing/            #   AttachmentRow, AttachmentSection
  export/               # Logbook export (datamanager + sharedassets + update; no model/viewing)
    datamanager/        #   ExportManager (exportLogs Flow, listExports, deleteExport, retryDelivery, resendDelivery);
                        #   ExportManagerImpl reads local EntityStore via feature managers; PDF/CSV/XLSX writers + ZipFileWriter;
                        #   ExportHistoryRemoteRepository (Firebase Storage + Firestore manifest); ExportDeliveryBackend
                        #   → requestExportDelivery Cloud Function (email delivery)
    sharedassets/       #   Strings for selection / progress / history / delivery
    update/             #   ExportSelectionScreen, ExportHistoryScreen, ExportViewModel, ExportHistoryViewModel, ExportFileSharer
  sync/                 # Local-first sync engine (R1 implementation — the only Firestore client)
    data/               #   SyncEngine, HydrationRunner, PullListener, PushWorker, SyncCursorStore, SyncPreferences,
                        #   FirestorePullSubscription, FirestoreRemoteFetcher, FirestoreSyncWriter, SyncDocWire, TombstoneGc,
                        #   blob/ — BlobUploadDriver, BlobDownloadDriver, BlobDeleteDriver,
                        #   Android WorkManager workers, iOS URLSessionUploadScheduler
    settings/           #   SyncSettingsScreen, SyncSettingsViewModel (Cloud Sync + Sync-on-Cellular toggles)
    sharedassets/       #   Sync-related shared strings/drawables
  featurelab/           # Firestore-backed feature flags (FeatureLab CollectionKind, synced like other entities)
    datamanager/        #   FeatureLabManager, FeatureFlags, Koin module
  settings/             # App settings screen (flat module, no submodule structure); also hosts FeatureLab screen + Export logs row
  userprofile/          # Profile edit UI + database (legacy; unification with Technician in progress)
    database/           #   UserProfileManager (Firestore)
    sharedassets/       #   Strings, anonymous user icon
    userprofilecard/    #   Profile card composable
  stresstest/           # Fake data generator for UI stress testing (dogfood only)
                        #   FakeDataGenerator, StressTestScreen, StressTestViewModel, StressTestModule
    config/             #   StressTestPlugin — shared composable UI + route registration used by
                        #   both platforms' thin wiring layers (see Dogfood Builds below)
backend/                # Firebase Cloud Functions (TypeScript, Functions v2, Node 22) — NOT a Gradle module
  firebase/functions/   #   health_probe; requestExportDelivery (export email delivery: signed URL + mailer + Firestore manifest)
```

## Canonical Feature Module Pattern

`feature/tasks` is the current reference implementation. Every new feature module should follow this submodule layout:

```
feature/foobar/
  model/           # Domain models and enums specific to this feature (data classes only)
  datamanager/     # Manager interface + impl + Koin module (repository + business logic)
  sharedassets/    # Strings, drawables shared across UI submodules within the feature
  viewing/         # Read-only display composables (cards, detail sheets, alert sections)
  update/          # Add/edit screens, ViewModels, Koin ViewModel module
```

### Dependency rules (strictly enforced)

```
sharedassets  →  Compose resources only (zero feature dependencies)
model         →  core:model, kotlinx only
datamanager   →  :model, core:storage, core:model, Firebase, Koin, Coroutines
viewing       →  :model, :sharedassets, core:ui, core:model
update        →  :model, :datamanager, :viewing, :sharedassets, core:*
```

**Hard rule:** A module must never be added as a dependency of another module solely because it contains a string or drawable. Shared assets belong in `sharedassets/`. This keeps `datamanager` and `model` free of UI/resource dependencies, and keeps UI modules from pulling in each other's business logic.

### What lives where

| Layer | Module | Contents |
|-------|--------|----------|
| Domain | `model/` | Feature-specific data classes, enums (e.g. `DueStatus`, `DueMetadata`, `MaintenanceTaskWithStatus`) |
| Data | `datamanager/` | Manager interface, `impl/` package with Firestore/storage logic, Koin `*Module.kt` |
| Resources | `sharedassets/` | `strings.xml` (and drawables) referenced by both `viewing/` and `update/` |
| Display | `viewing/` | Stateless composables — cards, list items, detail sheets, alert sections |
| Edit | `update/` | Screens, routes, `viewmodel/` package with ViewModel + `UiState` sealed class, Koin ViewModel module, `compose/` package for form field components |

### Non-canonical exceptions

- **`feature/technician/manage/`** — uses `manage/` instead of `viewing/` + `update/` split; both list and edit screens coexist in one submodule. New features should prefer canonical unless CRUD-only with no standalone view.
- **`feature/settings/`** — flat module; single screen with no submodule structure.
- **`feature/userprofile/`** — legacy structure; being unified with Technician (see `docs/userprofile_as_technician.md`).
- **`feature/aircraft/dashboard/`** — single submodule (no canonical split); owns its own ViewModel and DI module.
- **`feature/fleet/`** — ViewModel lives inside `viewing/` (no separate update layer); fleet dashboard is read-only.
- **`feature/export/`** — `datamanager` + `sharedassets` + `update` only (no `model` or `viewing`); export is a single user-driven flow with no standalone read surface.

### Koin modules

Each submodule that provides injectable objects declares its own `*Module.kt`. All modules are aggregated in `composeApp/src/commonMain/kotlin/dev/fanfly/wingslog/di/initKoin.kt`. Add new modules there when creating a new feature.

## Architecture

**Stack:** MVVM + StateFlow | Koin DI | Kotlin Coroutines/Flow | SQLDelight (local-first store, R1) | Firebase Firestore (background sync only) | Protocol Buffers (Wire 6) | Compose Multiplatform

### Layering pattern (each feature follows this)

1. **UI** — `@Composable` screen collects `StateFlow<UiState>` from ViewModel via `koinViewModel()`
2. **ViewModel** — holds `MutableStateFlow<UiState>`, combines data from one or more managers using `combine()` / `flatMapLatest()`
3. **Manager (interface + impl)** — `datamanager/` module; interface defines the contract, `impl/` reads/writes the local `EntityStore` (never Firestore). Injected via Koin.

### Data flow example
```
EntityStore<Aircraft>.observeAll (SQLDelight Flow, FleetManagerImpl)
  → flatMapLatest → combine(tasks, logs, squawks per aircraft)
  → FleetDashboardViewModel._uiState (StateFlow)
  → DashboardScreen (collectAsStateWithLifecycle)
```

### Firestore + Protobuf serialization
Proto definitions live in `core/model/src/commonMain/proto/`. Feature managers **never touch Firestore** — they read/write the local `EntityStore` only. The sync engine is the sole Firestore client: each synced entity is one `SyncDocWire` document — `payload` (Base64-encoded proto bytes), `deleted`, `schema` (proto FQN), and `lastUpdateTimestamp` (Firestore server timestamp). The old `core/database` module and its `AIRCRAFT_INFO_BLOB` / `getBlobAsBytes` pattern have been **removed**.

### Local-first storage (R1 — shipped, default path)
`core/storage` provides `EntityStore` (SQLDelight-backed), `CollectionKind` (8 kinds: Aircraft, MaintenanceTask, MaintenanceLog, MaintenanceOverview, Technician, UserInfo, FeatureLab, Squawk), `EntityCodecRegistry`, and Koin modules. Schema tables: `entity`, `sync_cursor`, `sync_config`, `blob_object`. `feature/sync/data` implements the sync engine: `SyncEngine` (gated on signed-in AND non-anonymous AND cloud-sync-enabled) orchestrates `HydrationRunner` (initial pull), `PullListener` / `FirestorePullSubscription` (real-time updates), and `PushWorker` (drains `dirty=1` rows via `FirestoreSyncWriter`). Top-level kinds (Aircraft, Technician, UserInfo) hydrate on sign-in; per-aircraft kinds (logs, tasks, overview, squawks) hydrate per aircraft. Conflict resolution is last-writer-wins on Firestore server timestamp; dirty rows are immune from remote overwrite (no local clock in the ordering logic). Anonymous users are fully offline (engine idle). Binary blob transfers (R2) use WorkManager (Android) and background URLSession (iOS). See `docs/storage_r1_design.md`.

### Dependency Injection
- Central aggregation: `composeApp/src/commonMain/kotlin/dev/fanfly/wingslog/di/initKoin.kt`
- Each module has its own `di/*Module.kt`
- Platform-specific bindings: `androidMain` / `iosMain` provide Firebase SDK instances and storage implementations

### Multiplatform split
- `commonMain` — all shared Kotlin + Compose code
- `androidMain` / `iosMain` — Firebase SDK selection, HTTP client (OkHttp vs Darwin), auth, blob workers

## Key Dependencies (libs.versions.toml)

| Library | Version |
|---------|---------|
| Kotlin | 2.3.21 |
| Compose Multiplatform | 1.10.3 |
| Firebase KMP (GitLive) | 2.4.0 |
| Koin | 4.2.1 |
| Wire (protobuf) | 6.2.0 |
| Kotlinx Coroutines | 1.10.2 |
| Kotlinx Datetime | 0.7.1 |
| Ktor | 3.4.3 |
| Coil | 3.4.0 |
| SQLDelight | 2.3.2 |
| MockK | 1.14.9 |
| Google Truth | 1.4.5 |

## Design System

Defined in `core:ui`. Follows **Refined Minimalism**: Material 3 color scheme, intentional typography hierarchy, consistent spacing tokens. Prioritize clarity and readability over information density.

**Read `PRODUCT.md`, `DESIGN.md`, and `.impeccable/design.json` before any UI work.** Together they define the required aviation palette (Aviation Blue primary, Instrument Amber accent ≤10% of color moments, semantic forest/amber status colors), required typography (Space Grotesk titles, JetBrains Mono for technical data, system sans for body), and brand principles (Dependability First, Clarity over Density, Progressive Disclosure). Dynamic color is disabled; the aviation palette is the brand.

## Design Docs

Feature PRDs and architecture design docs live in `docs/` — including `PRD.md` (product overview), `storage_mode_PRD.md` / `storage_r1_design.md` / `storage_r2_design.md` (local-first), `attachments_PRD.md` / `attachments_design.md`, `squawk_design.md` / `user_squawking_prd.md`, `export_logs_PRD.md` / `export_logs_design.md` / `export_email_automation_design.html`, `technician_design.md`, `userprofile_as_technician.md`, `aircraft_overview_tabs.md`, and `intelligentsearch.md`. Consult these before making non-trivial changes to a feature area. Each design/PRD doc carries an **Implementation Status** note near the top reflecting what has actually shipped vs. the original plan. For export work, also inspect `docs/export_logs_sample/`.

## Dogfood Builds

The dogfood/debug tooling includes the **Fake Data Generator** (`feature/stresstest`) — a screen
that populates fake aircraft, logs, squawks, and tasks for UI stress testing. Android and iOS gate
it behind the `DogfoodFeatureExtensions` plugin interface defined in `composeApp/src/commonMain/`;
the web host exposes the same reusable plugin directly from Feature Lab.

### Architecture

```
feature/stresstest/config/StressTestPlugin.kt   ← shared composable UI + route registration (KMP commonMain)
        │
        ├── app/src/dogfood/DogfoodConfig.kt     ← Android thin wiring (dogfood flavor only)
        └── composeApp/src/iosMain/
              StressTestDogfoodExtensions.kt      ← iOS thin wiring (iosMain, all iOS builds)
```

Both wiring files are structurally identical — they implement `DogfoodFeatureExtensions` by delegating to the three functions in `StressTestPlugin.kt`. The split exists because Android uses product flavor source sets (`app/src/dogfood/`) while iOS has no equivalent; `composeApp/src/iosMain/` is the iOS entry-point layer.

### Android

- Product flavor `dogfood` in `app/build.gradle.kts` (dimension `environment`; the other is `prod`)
- `app/src/dogfood/` source set compiled only for dogfood variants; `app/src/prod/` returns `NoOpDogfoodExtensions`
- `feature:stresstest:config` is `dogfoodImplementation` — excluded from prod binaries entirely
- Build: `./gradlew assembleDogfoodDebug`

### iOS

- `feature:stresstest:config` is in `composeApp`'s `iosMain` dependencies — compiled into all iOS builds but only activated via the dogfood entry point
- `MainEntry.doInitKoinDogfood()` (`composeApp/src/iosMain/MainViewController.kt`) wires in `StressTestDogfoodExtensions`
- `iosApp.swift` uses `#if DOGFOOD` to call `doInitKoinDogfood()` vs `doInitKoin()`
- The **Dogfood** Xcode build configuration sets `SWIFT_ACTIVE_COMPILATION_CONDITIONS = "DEBUG DOGFOOD"` and hardcodes `FRAMEWORK_SEARCH_PATHS` to the `Debug` KMP framework variant
- The Compile Kotlin build phase remaps `CONFIGURATION=Dogfood → Debug` before invoking Gradle
- Build: open `iosApp/iosApp.xcodeproj`, select the **iosAppDogfood** scheme, and run

### Web

- `webApp` depends on `feature:stresstest:config` and registers the plugin route and Koin module
  directly.
- The Fake Data Generator is reachable through **Settings → Feature Lab → Debug Tools**.
- Build: `./gradlew :webApp:jsBrowserDevelopmentWebpack`

## Coding Conventions

- **Instants**: Always use `kotlin.time.Instant`, never `kotlinx.datetime.Instant`.
- **ViewModels in `fleet/`**: The ViewModel lives inside the `viewing/` submodule (no separate layer), since fleet has no editable screen at the feature level.
- **`technician/manage`**: This feature uses `manage/` instead of the canonical `viewing/` + `update/` split — both read and write screens coexist in one submodule. New features should prefer the canonical pattern unless the feature is inherently CRUD-only with no standalone viewing screen.
- **Feature flags**: Controlled by `FeatureLabManager` (Firestore-backed; `attachmentUploadEnabled` gates the attachment UI). Check `FeatureFlags` before shipping experimental code paths.
- **Transitive deps**: `core:storage` and `core:ui` api-export most shared deps; don't redeclare them in downstream modules.
