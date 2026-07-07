# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## What This Is

SquawkIt is a **Kotlin Multiplatform** app for aviation logbook and fleet management — aircraft
CRUD, maintenance logs, inspection compliance tracking, and due-status computation. Targets
Android (minSdk 33), iOS, and web sharing Compose Multiplatform UI.

The user-facing app is branded **SquawkIt**; codebase identifiers (`wingslog`, package `dev.fanfly.wingslog`, Gradle module names) still use the original WingsLog name.

The app uses a **local-first architecture** (R1 — shipped, default path): a SQLDelight entity store is the single source of truth for every read and write, and a Firestore sync engine pushes local changes and pulls remote ones in the background. There is **no Firestore in the UI read path** and **no rollout flag** — local-first is the only code path. Local-first **attachments** (R2) infrastructure has largely landed (local blob store + background upload/download), but the attachment UI is gated behind the `attachmentUploadEnabled` feature-lab flag. See `docs/storage/storage_r1_design.md` and `docs/storage/storage_r2_design.md`.

## Build & CI Commands

```bash
./gradlew assembleDebug                          # Build debug APK (developer tooling on)
./gradlew assembleRelease                        # Build release APK (developer tooling off)
./gradlew assembleRelease -PdeveloperBuild=true   # Build a "dogfood-style" release APK (developer tooling on)
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
composeApp/             # Android/iOS host — DB-integrity gate, theme wrapper, auth/shell graph wrappers
                        #   around the shared nav graph (feature/shell) + Koin init (initKoin.kt)
webApp/                 # Kotlin/JS web host — browser delta (history binding, resource warm-up, SEO
                        #   login landing, OPFS SQLite storage, Firebase JS startup) around the shared graph
core/
  model/                # Wire-generated protobuf models (Aircraft, MaintenanceLog, Squawk, InspectionCard…)
  nav/                  # Screen route definitions (Screen sealed class, route args)
  sharedassets/         # App-wide strings/drawables (brand name, generic actions, shell tab labels, file sizes)
  analytics/            # AnalyticsManager, LocalAnalytics, trackScreenViews page-view feeder
  di/                   # CommonAppModules.kt — the single list of Koin modules shared by all hosts
  ui/                   # Material 3 theme, color tokens, shared Compose components (incl. DualSegmentedFilter, StatusChip, formatFileSize)
    theme/              #   WingslogTheme, palette, Spacing, StatusColors, AppearanceController
    adaptive/           #   AdaptiveAppShell, layout tiers, AdaptiveFormDialogFrame, ConstrainedTopBar
    widget/avataricon/  #   AvatarIcon composable
  auth/                 # Firebase Auth with platform-specific implementations
  firebase/             # Centralized Firebase utilities — FirebaseDataExt (ByteArray↔Firebase Storage Data, expect/actual)
  storage/              # R1 local-first foundation — SQLDelight schema (entity, sync_cursor, sync_config, blob_object),
                        #   EntityStore, CollectionKind (8 kinds), EntityCodecRegistry, CloudSyncSetting;
                        #   blob/ — LocalBlobStore + SqlDelightLocalBlobStore, BlobRef, BlobFilesystem,
                        #   UploadScheduler, sha256Hex, BlobId, RemoteState
  datetime/             # Date/time utilities — WireInstantFactory, platform-specific formatters
  appinfo/              # Cross-platform app version/build info + AppCapability (expect/actual)
feature/
  shell/                # Shared app nav graph — the composable/nav counterpart to core:di's Koin aggregator:
                        #   formDialogs (8 add/edit dialogs), settingsDetailRoutes, AdaptiveShellRoute (+ nested
                        #   sidebar-settings NavHost), NavigateToLoginOnSignOut / TrackRootScreenViews helpers,
                        #   AdaptiveShellViewModel + shellModule
  login/                # AuthFlow (sign-in + onboarding), email-link sign-in, LoginViewModel, deep links
  fleet/                # Fleet data + empty state (no update — fleet has no editable screen)
    datamanager/        #   FleetManager: observes aircraft via EntityStore<Aircraft> Flow, CRUD
    sharedassets/       #   Strings, drawables shared across fleet UI
    viewing/            #   FleetEmptyState (empty-fleet state rendered by the shell)
  aircraft/             # Aircraft detail view + aircraft CRUD (not part of fleet/ module)
    dashboard/          #   AircraftOverviewScreen, 4 tab composables (Overview → Squawks → Tasks → Logs),
                        #   AircraftOverviewViewModel, AircraftDashboardModule, AircraftTab enum
    update/             #   EditAircraftScreen (add/edit aircraft), EditAircraftViewModel, Engine/Airframe sections
  logs/                 # Maintenance log UI (canonical layout)
    datamanager/        #   MaintenanceLogManager: CRUD for logs and maintenance overview
    sharedassets/       #   Strings, shared helpers (LogPickerSheet, MaintenanceDisplayExtensions)
    viewing/            #   MaintenanceLogCard, MaintenanceLogDetailSheet, list ViewModel
    update/             #   MaintenanceLogFormScreen, form ViewModels
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
    update/             #   SquawkFormScreen (2-tab add/edit), DismissSquawkDialog, SquawkFormViewModel
                        #   (LogPickerSheet it uses lives in logs/sharedassets)
  technician/           # Technician management
    datamanager/        #   TechnicianManager
    manage/             #   Combined list + edit screens + ViewModels (TechnicianListScreen, EditTechnicianScreen)
    sharedassets/       #   CertificateInputFields, TechnicianPickerSheet, strings
  attachment/           # File/image attachment feature (R2 — substantially implemented; UI behind attachmentUploadEnabled flag)
    model/              #   AttachmentStatus, AttachmentWithState, BlobSyncState, PendingAttachment
    datamanager/        #   AttachmentManager, AttachmentFormController (shared form-side attachment state),
                        #   AttachmentOpener, QuotaChecker, platform BlobFilesystem impls
                        #   (the blob store itself lives in core:storage/blob)
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
    data/               #   SyncEngine, HydrationRunner, PullListener, PushWorker, SyncCursorStore,
                        #   SyncPreferences (implements core:storage's CloudSyncSetting),
                        #   FirestorePullSubscription, FirestoreRemoteFetcher, FirestoreSyncWriter, SyncDocWire, TombstoneGc,
                        #   blob/ — BlobUploadDriver, BlobDownloadDriver, BlobDeleteDriver,
                        #   Android WorkManager workers, iOS URLSessionUploadScheduler
    settings/           #   SyncSettingsScreen, SyncSettingsViewModel (Cloud Sync + Sync-on-Cellular toggles)
    sharedassets/       #   Sync-related shared strings/drawables
  featurelab/           # Firestore-backed feature flags (FeatureLab CollectionKind, synced like other entities)
    datamanager/        #   FeatureLabManager, FeatureFlags, Koin module
  settings/             # App settings screen (flat module, no submodule structure); also hosts FeatureLab screen + Export logs row
  userprofile/          # Remnants of the legacy profile feature (unification with Technician in progress)
    sharedassets/       #   Strings, anonymous user icon
    userprofilecard/    #   Profile card composable
  stresstest/           # Fake data generator for UI stress testing (compiled into every build,
                        # gated at runtime by AppCapability.isStressTestSupported)
                        #   FakeDataGenerator, StressTestScreen, StressTestViewModel, StressTestModule
    config/             #   StressTestPlugin — shared composable UI + route registration; routes are
                        #   registered by feature/shell's settingsDetailRoutes, gated on
                        #   isStressTestSupported on every host (see Dogfood Builds below)
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
sharedassets  →  Compose resources + leaf presentation helpers; may use core:ui / core:model,
                 never another feature module
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
| Resources | `sharedassets/` | `strings.xml` (and drawables) referenced by both `viewing/` and `update/`; may also hold small leaf presentation helpers (e.g. label mappers, shared input fields) that other features consume without pulling in this feature's UI modules — such helpers may depend on `core:ui`/`core:model` but never on another feature |
| Display | `viewing/` | Stateless composables — cards, list items, detail sheets, alert sections |
| Edit | `update/` | Screens, routes, `viewmodel/` package with ViewModel + `UiState` sealed class, Koin ViewModel module, `compose/` package for form field components |

### Non-canonical exceptions

- **`feature/technician/manage/`** — uses `manage/` instead of `viewing/` + `update/` split; both list and edit screens coexist in one submodule. New features should prefer canonical unless CRUD-only with no standalone view.
- **`feature/settings/`** — flat module; single screen with no submodule structure.
- **`feature/userprofile/`** — legacy structure; being unified with Technician (see `docs/technician/userprofile_as_technician.md`).
- **`feature/aircraft/dashboard/`** — single submodule (no canonical split); owns its own ViewModel and DI module. Its sibling `aircraft/update` follows the canonical `update/` shape.
- **`feature/fleet/`** — no `model` or `update`; `viewing/` holds only `FleetEmptyState`. When a feature has no `update` sibling, `viewing/` may host the list ViewModel (e.g. `logs:viewing`'s `MaintenanceLogListViewModel`).
- **`feature/shell/`** — single module, no canonical split; it's an aggregator (shared nav graph + shell ViewModel), not a domain feature.
- **`feature/export/`** — `datamanager` + `sharedassets` + `update` only (no `model` or `viewing`); export is a single user-driven flow with no standalone read surface.
- **`feature/settings/featurelab/`** — `FeatureLabScreen` lives inside `feature/settings` while `feature/featurelab` has only a `datamanager`; a dedicated UI module for one screen isn't worth it.

### Koin modules

Each submodule that provides injectable objects declares its own `*Module.kt`. All modules shared by the hosts are aggregated in **`core/di/CommonAppModules.kt`** — add new modules there when creating a new feature. `composeApp`'s `initKoin.kt` and `webApp`'s `main.kt` are thin wrappers that take that list and add host bootstrap only (`AppCapability` construction, stress-test Koin modules, host-only singles like the web SQLite worker). The list is kept in one place because it drifted between hosts once before — a module registered in one host but not the other fails at runtime (`NoDefinitionFoundException`), not at compile time.

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
`core/storage` provides `EntityStore` (SQLDelight-backed), `CollectionKind` (8 kinds: Aircraft, MaintenanceTask, MaintenanceLog, MaintenanceOverview, Technician, UserInfo, FeatureLab, Squawk), `EntityCodecRegistry`, and Koin modules. Schema tables: `entity`, `sync_cursor`, `sync_config`, `blob_object`. `feature/sync/data` implements the sync engine: `SyncEngine` (gated on signed-in AND non-anonymous AND cloud-sync-enabled) orchestrates `HydrationRunner` (initial pull), `PullListener` / `FirestorePullSubscription` (real-time updates), and `PushWorker` (drains `dirty=1` rows via `FirestoreSyncWriter`). Top-level kinds (Aircraft, Technician, UserInfo) hydrate on sign-in; per-aircraft kinds (logs, tasks, overview, squawks) hydrate per aircraft. Conflict resolution is last-writer-wins on Firestore server timestamp; dirty rows are immune from remote overwrite (no local clock in the ordering logic). Anonymous users are fully offline (engine idle). Binary blob transfers (R2) use WorkManager (Android) and background URLSession (iOS). See `docs/storage/storage_r1_design.md`.

### Dependency Injection
- Central aggregation: `core/di/CommonAppModules.kt` (one list shared by all hosts); `initKoin.kt` (composeApp) and `main.kt` (webApp) add host-only bootstrap on top
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

Feature PRDs and architecture design docs live in `docs/`, organized into **per-topic subfolders**:

- `docs/product/` — `PRD.md` (product overview), `platform_feature_parity.html` (Android/iOS/web feature matrix)
- `docs/storage/` — `storage_mode_PRD.md`, `storage_r1_design.md`, `storage_r2_design.md` (local-first)
- `docs/attachments/` — `attachments_PRD.md`, `attachments_design.md`
- `docs/squawks/` — `user_squawking_prd.md`, `squawk_design.md`
- `docs/export/` — `export_logs_PRD.md`, `export_logs_design.md`, `export_email_automation_design.html`, plus the `export_logs_sample/` reference bundle
- `docs/technician/` — `technician_design.md`, `userprofile_as_technician.md`
- `docs/sharing/` — `aircraft_sharing_PRD.html`, `aircraft_sharing_design.html` (multi-user aircraft access)
- `docs/aircraft/` — `aircraft_overview_tabs.md`
- `docs/search/` — `intelligentsearch.md`
- `docs/account/` — `account_upgrade_PRD.html`, `account_upgrade_design.html`
- `docs/web/` — `web_target_expansion_plan.md`, `web_attachments_design.md`, `web_adaptive_layout_design.html`
- `docs/cleanup/` — `codebase_cleanup_plan.md` (the 2026-07 structure/dedup/layering cleanup; phases 1–5 executed, kept as the record of what moved where and why)

Consult the relevant doc before making non-trivial changes to a feature area. Each design/PRD doc
carries an **Implementation Status** note near the top reflecting what has actually shipped vs. the
original plan.

**Doc format policy:** all *new* docs are authored in **HTML** (self-contained, styled). Existing
Markdown docs stay as-is until they're substantially rewritten; do not bulk-convert. When adding a
doc, place it in the matching subfolder (create a new one if no topic fits) and link related docs
with relative paths.

## Dogfood Builds

There is no compiled-out "dogfood" variant anymore — the **Fake Data Generator**
(`feature/stresstest`) is a normal, always-present dependency compiled into every build; its
routes and the Feature Lab "Debug Tools" entry are registered by the shared nav graph
(`feature/shell`), gated on a single runtime flag: `AppCapability.isStressTestSupported`
(see `core/appinfo/AppCapability.kt`) — the same gate on Android, iOS, and web.

`AppCapability` also folds in the "developer build" gate for the Feature Lab settings row itself
(`isFeatureLabSupported`) and three platform-capability constants (camera capture, anonymous
login, Apple sign-in) — one injectable singleton, constructed once per host via
`createAppCapability(isDeveloperBuild)` at Koin startup, instead of several unrelated flags.

### Android

- No product flavor — a single `app` variant dimension (`debug`/`release`).
- `isDeveloperBuild` comes from the `BuildConfig.DEVELOPER_BUILD` field: hardcoded `true` for the
  `debug` build type, and settable on `release` via the `-PdeveloperBuild=true` Gradle property
  (see `app/build.gradle.kts`).
- Build: `./gradlew assembleDebug` (developer tooling on) · `./gradlew assembleRelease` (tooling
  off) · `./gradlew assembleRelease -PdeveloperBuild=true` (signed "dogfood-style" release, tooling on)

### iOS

- `MainEntry.doInitKoin(forceDeveloperBuild:)` (`composeApp/src/iosMain/MainViewController.kt`) is
  the single entry point; `isDeveloperBuild` is `forceDeveloperBuild || Platform.isDebugBinary`.
- `iosApp.swift`'s `#if DOGFOOD` only decides the `forceDeveloperBuild` argument (Swift can't see
  `Platform.isDebugBinary` itself) — there's no more separate dogfood Kotlin entry point or wiring class.
- The **Dogfood** Xcode build configuration still exists for signing/distribution purposes (sets
  `SWIFT_ACTIVE_COMPILATION_CONDITIONS = "DEBUG DOGFOOD"`, remaps `CONFIGURATION=Dogfood → Debug`
  for the Compile Kotlin phase).
- Build: open `iosApp/iosApp.xcodeproj`, select the **iosAppDogfood** scheme, and run.

### Web

- `webApp` depends on `feature:stresstest:config` and registers the plugin route and Koin module
  directly, same as Android/iOS.
- The Fake Data Generator is reachable through **Settings → Feature Lab → Debug Tools**.
- Build: `./gradlew :webApp:jsBrowserDevelopmentWebpack`

## Coding Conventions

- **Instants**: Always use `kotlin.time.Instant`, never `kotlinx.datetime.Instant`.
- **ViewModels in `viewing/`**: When a feature has no `update` submodule, its list ViewModel may live inside `viewing/` (e.g. `logs:viewing`); the app-shell ViewModel lives in `feature/shell`.
- **`technician/manage`**: This feature uses `manage/` instead of the canonical `viewing/` + `update/` split — both read and write screens coexist in one submodule. New features should prefer the canonical pattern unless the feature is inherently CRUD-only with no standalone viewing screen.
- **Feature flags**: Controlled by `FeatureLabManager` (Firestore-backed; `attachmentUploadEnabled` gates the attachment UI). Check `FeatureFlags` before shipping experimental code paths.
- **Capabilities**: Build-time/platform gates (developer-only surfaces, per-platform support) go through the injected `AppCapability` singleton (`core:appinfo`), not ad-hoc `isDeveloperBuild` checks or `expect`/`actual` booleans scattered across feature modules.
- **Transitive deps**: `core:storage` and `core:ui` api-export most shared deps; don't redeclare them in downstream modules.

## Engineering Best Practices

### Post-task cleanup pass (required)

After finishing a large job — a sizable feature implementation, a big refactor, or any multi-file
change — perform a cleanup pass over **all changed `.kt` files before the final commit**.

**Why:** inline fully-qualified class paths, stray blank lines, and formatting inconsistencies
accumulate during implementation. Catching them before commit keeps the diff clean and the history
reviewable.

**How to apply** — scan every changed file for:

1. **Fully-qualified class references used inline** instead of being imported — e.g.
   `kotlinx.coroutines.flow.flowOf(...)` should become an `import` plus the short name `flowOf(...)`.
2. **Trailing blank lines** at the end of files.
3. **Extra blank lines** — collapse double-or-more blank lines where a single blank line is expected.
4. **Import ordering** — `kotlin.*` before `kotlinx.*`, alphabetical within each group.
5. **Other formatting issues** — inconsistent indentation, and long lines that should wrap.

### User-facing strings must live in `strings.xml` (required)

Every user-facing string must be defined in a `strings.xml` resource and referenced via the
generated `Res`/`stringResource` — never hardcoded inline in Compose or other UI code.

**Where the resource goes** — placement follows actual usage:

1. **Used by a single module** → put it in that module's own resource folder
   (`src/commonMain/composeResources/values/strings.xml`).
2. **Shared by another module that already depends on the owner** → keep it in the owning module;
   the consumer reads it through the existing dependency.
3. **Shared across modules with no existing dependency**, where adding one just for a resource
   doesn't make sense → put it in a `sharedassets/` target (e.g. `feature/foobar/sharedassets`) and
   depend on that from both sides. `sharedassets` carries no feature deps (see the dependency rules
   above), so it's the right home for cross-feature resources.

**Reuse before adding.** Share strings and other resources as much as possible. Before creating a
new string, search for an existing one (`core/sharedassets`, the feature's `sharedassets`, and the
relevant module) and reuse it rather than duplicating.
