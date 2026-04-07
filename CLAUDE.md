# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

WingsLog is a **Kotlin Multiplatform Mobile (KMM)** app for aviation logbook and fleet management — aircraft CRUD, maintenance logs, inspection compliance tracking, and due-status computation. Targets Android (minSdk 33) and iOS sharing Compose Multiplatform UI.

## Build & CI Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew lint                   # Lint checks
./gradlew testDebugUnitTest      # Run all unit tests
./gradlew :feature:fleet:testDebugUnitTest  # Run tests for a single module
```

CI (`.github/workflows/ci.yml`) runs lint → assembleDebug → testDebugUnitTest on every push. It requires `GOOGLE_SERVICES_JSON` secret to write `google-services.json` before building.

## Module Structure

```
app/                    # Android entry point (MainActivity, WingsLogApplication)
composeApp/             # Shared Compose UI — navigation graph (AppEntry.kt) + central Koin init
core/
  model/                # Wire-generated protobuf models (Aircraft, MaintenanceLog, InspectionCard…)
  ui/                   # Material 3 theme, color tokens, shared Compose components
  auth/                 # Firebase Auth with platform-specific implementations
  database/             # Firestore helpers — getFleetCollectionRef, DocumentReferences
feature/
  fleet/                # Fleet dashboard ViewModel + UI
  fleet/datamanager/    # FleetManager: observes aircraft via Firestore Flow, CRUD for aircraft
  maintenance/          # Maintenance log UI (list/create/edit)
  maintenance/datamanager/ # MaintenanceLogManager: CRUD for logs and maintenance overview
  inspection/           # (see canonical pattern below)
  userprofile/          # Profile edit UI + database
  settings/             # App settings screen
```

## Canonical Feature Module Pattern

`feature/inspection` is the reference implementation. Every feature module should follow this submodule layout:

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
datamanager   →  :model, core:database, core:model, Firebase, Koin, Coroutines
viewing       →  :model, :sharedassets, core:ui, core:model
update        →  :model, :datamanager, :viewing, :sharedassets, core:*
```

**Hard rule:** A module must never be added as a dependency of another module solely because it contains a string or drawable. Shared assets belong in `sharedassets/`. This keeps `datamanager` and `model` free of UI/resource dependencies, and keeps UI modules from pulling in each other's business logic.

### What lives where

| Layer | Module | Contents |
|-------|--------|----------|
| Domain | `model/` | Feature-specific data classes, enums (e.g. `DueStatus`, `DueMetadata`, `InspectionCardWithStatus`) |
| Data | `datamanager/` | Manager interface, `impl/` package with Firestore logic, Koin `*Module.kt` |
| Resources | `sharedassets/` | `strings.xml` (and drawables) referenced by both `viewing/` and `update/` |
| Display | `viewing/` | Stateless composables — cards, list items, detail sheets, alert sections |
| Edit | `update/` | Screens, routes, `viewmodel/` package with ViewModel + `UiState` sealed class, Koin ViewModel module, `compose/` package for form field components |

### Koin modules

Each submodule that provides injectable objects declares its own `*Module.kt` (e.g. `InspectionModule`, `InspectionUiModule`). These are aggregated in `composeApp/di/initKoin.kt` — add new modules there when creating a new feature.

## Architecture

**Stack:** MVVM + StateFlow | Koin DI | Kotlin Coroutines/Flow | Firebase Firestore (real-time) | Protocol Buffers (Wire 6) | Compose Multiplatform

### Layering pattern (each feature follows this)

1. **UI** — `@Composable` screen collects `StateFlow<UiState>` from ViewModel via `koinViewModel()`
2. **ViewModel** — holds `MutableStateFlow<UiState>`, combines data from one or more managers using `combine()` / `flatMapLatest()`
3. **Manager (interface + impl)** — `datamanager/` module; interface defines the contract, `impl/` handles Firestore. Injected via Koin.

### Data flow example
```
Firestore snapshot → Flow<List<Aircraft>> (FleetManagerImpl)
  → flatMapLatest → combine(inspections, logs per aircraft)
  → FleetDashboardViewModel._uiState (StateFlow)
  → DashboardScreen (collectAsStateWithLifecycle)
```

### Firestore + Protobuf serialization
Documents store binary blobs (e.g., field `AIRCRAFT_INFO_BLOB`). Decode: `Aircraft.ADAPTER.decode(doc.getBlobAsBytes(AIRCRAFT_INFO_BLOB))`. Proto definitions live in `core/model/src/commonMain/proto/`.

### Dependency Injection
- Central aggregation: `composeApp/src/commonMain/kotlin/di/initKoin.kt`
- Each module has its own `di/*Module.kt`
- Platform-specific bindings: `androidMain` / `iosMain` provide Firebase SDK instances

### Multiplatform split
- `commonMain` — all shared Kotlin + Compose code
- `androidMain` / `iosMain` — Firebase SDK selection, HTTP client (OkHttp vs Darwin), auth

## Key Dependencies (libs.versions.toml)

| Library | Version |
|---------|---------|
| Compose Multiplatform | 1.10.3 |
| Firebase KMP (GitLive) | 2.4.0 |
| Koin | 4.2.0 |
| Wire (protobuf) | 6.0.0 |
| Kotlinx Coroutines | 1.10.2 |
| Kotlinx Datetime | 0.7.1 |
| Ktor | 3.4.1 |
| Coil | 3.4.0 |
| MockK | 1.13.10 |
| Google Truth | 1.4.2 |

## Design System

Defined in `core:ui`. Follows **Refined Minimalism**: Material 3 color scheme, intentional typography hierarchy, consistent spacing tokens. Prioritize clarity and readability over information density. The `.impeccable.md` file in the repo root has brand/aesthetic detail.
