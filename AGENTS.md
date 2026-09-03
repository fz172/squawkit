# AGENTS.md

The canonical engineering reference for this repository, and the source of truth for every coding
agent working in it (Claude Code, Codex, and any other). [CLAUDE.md](CLAUDE.md) is a short pointer to
this file; where the two ever disagree, **this file wins** — fix CLAUDE.md rather than forking the
guidance.

Doc "Implementation Status" notes under `docs/` are maintained by hand and occasionally lag the code.
When a rollout gate, module name, or flag matters, check the source.

## What This Is

SquawkIt is a **Kotlin Multiplatform** app for aviation logbook and fleet management — aircraft CRUD,
maintenance logs, inspection compliance tracking, due-status computation, squawks, technicians,
attachments, logbook export, multi-user aircraft sharing, subscriptions, and free-tier display ads.
Targets Android (minSdk 33, target/compileSdk 37), iOS, and web, sharing one Compose Multiplatform
codebase.

The user-facing app is branded **SquawkIt**; codebase identifiers still use the original WingsLog
name (Kotlin package `dev.fanfly.wingslog`, Gradle root project `wingslog`, Firebase project
`wingslog-9ca4e`). This is deliberate — renaming the published identity would break Play Store /
App Store / Firebase registration.

The app is **local-first** (R1 — shipped, the only path): a SQLDelight entity store is the single
source of truth for every read and write, and a Firestore sync engine pushes local changes and pulls
remote ones in the background. There is **no Firestore in the UI read path** and **no rollout flag**.
Local-first **attachments** (R2) are built — local blob store plus background upload/download — with
file/photo upload gated by the Pro subscription (links are always free). See
`docs/storage/storage_r1_design.md` and `docs/storage/storage_r2_design.md`.

## Build & Test Commands

```bash
./gradlew assembleDebug                          # Android debug APK (developer tooling on)
./gradlew assembleRelease                        # Release APK (developer tooling off)
./gradlew assembleRelease -PdeveloperBuild=true  # "Dogfood-style" release APK (tooling on)
./gradlew lint                                   # Lint checks
./gradlew testDebugUnitTest testAndroidHostTest  # All Android unit tests (app + migrated KMP modules)
./gradlew :feature:fleet:datamanager:testAndroidHostTest   # One module's tests
./gradlew :composeApp:iosSimulatorArm64Test      # iOS simulator unit tests (local only)
./gradlew :webApp:jsBrowserDevelopmentWebpack    # Web development bundle
./gradlew :webApp:jsBrowserDistribution          # Web production bundle (what deploy-web ships)
```

For iOS, open `iosApp/iosApp.xcodeproj` and select the **iosAppDebug** scheme (**iosAppRelease** for
a tooling-off build). See [Developer Builds & Capabilities](#developer-builds--capabilities).

Notes that bite:

- **`assembleRelease` mutates `version.properties`** — it bumps `versionCode` and stamps
  `buildDate` (see `app/build.gradle.kts`). Don't run it casually; don't commit an accidental bump.
- Android builds need `app/google-services.json` (CI writes it from the `GOOGLE_SERVICES_JSON`
  secret).
- JDK 21 toolchain. `gradle.properties` sets `-Xmx16g` because Kotlin/Native compiles in-process
  inside the Gradle daemon and a release iOS framework link OOMs at the 4g default; scale down if
  your machine has less than the 64GB it was tuned on.

### Backend (`backend/firebase/functions/`)

```bash
npm ci
npm run build     # generate:proto (ts-proto from core/model/**/proto) + tsc
npm test          # vitest against the auth/firestore/storage emulators (needs firebase-tools on PATH)
npm run serve     # firebase emulators:start --only functions
```

## CI / CD (`.github/workflows/`)

| Workflow | Trigger | What it does |
|---|---|---|
| `build.yml` | **manual only** (`workflow_dispatch`) | lint → `assembleDebug` → `testDebugUnitTest` + `testAndroidHostTest` |
| `deploy-functions.yml` | PR + push to `main` under `functions/**` | emulator test suite as the gate; deploys functions on merge |
| `deploy-firestore-rules.yml` | PR + push to `main` on `firestore.rules` | emulator rules suite; deploys rules on merge |
| `deploy-storage-rules.yml` | PR + push to `main` on `storage.rules` | emulator rules suite; deploys rules on merge |
| `deploy-web.yml` | manual | `:webApp:jsBrowserDistribution` → Firebase Hosting `live` (production), or the `alpha` / `debug` preview channel |

**The Kotlin build does not run automatically on PRs or pushes.** Run `./gradlew lint
testDebugUnitTest testAndroidHostTest` locally before pushing anything non-trivial. iOS is never
built on CI.

Firestore and Storage security rules are source-controlled in `backend/firebase/` — the Firebase
console is read-only and these workflows are the only publisher.

## Module Structure

```
app/                    # Android entry point (MainActivity, WingsLogApplication, BuildConfig.DEVELOPER_BUILD)
composeApp/             # Android/iOS host — DB-integrity gate, theme wrapper, auth/shell graph wrappers
                        #   around the shared nav graph (feature/shell) + Koin init (initKoin.kt)
webApp/                 # Kotlin/JS web host — browser delta around the shared graph: history binding,
                        #   OPFS SQLite worker, single-tab gate, email-link completion tab, Firebase JS +
                        #   App Check bootstrap, share deep-link parking, SEO login landing
iosApp/                 # Xcode project (schemes: iosAppDebug / iosAppRelease) + Swift bridges (ads, consent)
core/
  model/                # Wire-generated protobuf models (Aircraft, MaintenanceLog, Squawk, Technician,
                        #   UserInfo, Subscription, DeveloperSettings, SharedAircraftRef…);
                        #   .proto sources in src/commonMain/proto/ — also consumed by the Cloud Functions
  nav/                  # Screen route definitions (Screen sealed class, route args)
  sharedassets/         # App-wide strings/drawables (brand name, generic actions, shell tab labels)
  analytics/            # AnalyticsManager, LocalAnalytics, AnalyticsPreference, screen-view feeder
  di/                   # CommonAppModules.kt — the single list of Koin modules shared by all hosts
  ui/                   # Material 3 theme, color tokens, shared Compose components
    theme/              #   WingslogTheme, palette, Spacing, StatusColors, AppearanceController
    adaptive/           #   AdaptiveAppShell, layout tiers, AdaptiveFormDialogFrame, ConstrainedTopBar
    widget/avataricon/  #   AvatarIcon composable
  lifecycle/            # AppForegroundObserver (+ compose/ AppForegroundEffect) — foreground/session signal
  auth/                 # Firebase Auth: AuthManager, AuthProvider, email-link sign-in, account
                        #   upgrade/link/merge, AccountDeleter; platform actuals for Google/Apple
  firebase/             # Shared Firebase utilities — FirebaseDataExt (ByteArray↔Storage Data),
                        #   functionsModule (the one Cloud Functions client every callable injects)
  storage/              # Local-first foundation: SQLDelight Schema.sq (entity, sync_cursor, sync_config,
                        #   blob_object), EntityStore, EntityScope, CollectionKind, EntityCodecRegistry,
                        #   CloudSyncSetting, ThingScopeResolver (interface), DatabaseIntegrityChecker,
                        #   DatabaseWriteLock, LocalAccountMigrator, TombstoneGc;
                        #   blob/ — LocalBlobStore + SqlDelightLocalBlobStore, BlobRef, BlobFilesystem,
                        #   UploadScheduler, AttachmentRefs, sha256Hex, BlobId, RemoteState
  datetime/             # Date/time utilities — WireInstantFactory, platform-specific formatters
  appinfo/              # App version/build info + AppCapability & createAppCapability (expect/actual),
                        #   logging configuration
feature/
  shell/                # Shared app nav graph — the composable counterpart to core:di's Koin aggregator:
                        #   formDialogs, sharingRoutes, settingsDetailRoutes, AdaptiveShellRoute (+ nested
                        #   sidebar-settings NavHost), NavigateToLoginOnSignOut / TrackRootScreenViews,
                        #   AdaptiveShellViewModel + shellModule
  login/                # AuthFlow (sign-in + onboarding), email-link sign-in, deep links, LoginViewModel
    onboarding/         #   WelcomeScreen, NameEntryScreen, AdsConsentExplainerScreen (CMP priming step)
    upgrade/            #   AccountUpgradeFlow + ViewModel (anonymous → linked account, clean & merge paths)
  fleet/                # Fleet data + empty state (no update — fleet has no editable screen)
    datamanager/        #   FleetManager: observes aircraft via EntityStore<Aircraft> Flow, CRUD
    picker/data/        #   SelectedAircraftStore — the shell's current-aircraft selection
    sharedassets/       #   Strings, drawables shared across fleet UI
    viewing/            #   FleetEmptyState (rendered by the shell)
  aircraft/             # Aircraft detail view + aircraft CRUD (not part of fleet/)
    dashboard/          #   AircraftOverviewScreen, 4 tabs (Overview → Squawks → Tasks → Logs),
                        #   AircraftOverviewViewModel, AircraftTab enum
    update/             #   EditAircraftScreen (add/edit), EditAircraftViewModel, Engine/Airframe sections
  logs/                 # Maintenance logs
    datamanager/        #   MaintenanceLogManager: CRUD for logs and maintenance overview
    sharedassets/       #   Strings, LogPickerSheet, MaintenanceDisplayExtensions
    viewing/            #   MaintenanceLogCard, MaintenanceLogDetailSheet, list ViewModel
    update/             #   MaintenanceLogFormScreen, form ViewModels
  tasks/                # Inspection compliance (canonical layout — the reference implementation)
    model/              #   DueMetadata, MaintenanceTaskWithStatus, domain enums
    datamanager/        #   TaskDataManager + TaskDueManager, Koin module
    sharedassets/       #   Strings, drawables
    viewing/            #   TaskCard, TaskDetailSheet
    update/             #   AddTaskScreen, EditTaskScreen, ViewModels, form sections
  squawk/               # Defect/discrepancy tracking — Aircraft Overview tab 2
    model/              #   SquawkWithStatus, SquawkStatus (OPEN / ADDRESSED / DISMISSED)
    datamanager/        #   SquawkManager (CRUD + markAddressed + dismiss/reopen) over EntityStore<Squawk>
    sharedassets/       #   Strings, priority colors, dismiss-reason labels
    viewing/            #   SquawkCard, SquawkDetailSheet, SquawkPickerSheet, AogAlertSection
    update/             #   SquawkFormScreen (2-tab add/edit), DismissSquawkDialog, SquawkFormViewModel
  technician/           # Technician management
    datamanager/        #   TechnicianManager
    manage/             #   Combined list + edit screens and ViewModels
    sharedassets/       #   CertificateInputFields, TechnicianPickerSheet, strings
  attachment/           # File/image/link attachments (R2). Upload gated by SubscriptionManager; links free
    model/              #   AttachmentStatus, AttachmentWithState, BlobSyncState, PendingAttachment
    datamanager/        #   AttachmentManager, AttachmentFormController, AttachmentOpener, QuotaChecker,
                        #   platform BlobFilesystem impls (the blob store itself is core:storage/blob)
    sharedassets/       #   Strings, type icons
    viewing/            #   AttachmentRow, AttachmentSection, AttachmentFormSection
  export/               # Logbook export (datamanager + sharedassets + update; no model/viewing)
    datamanager/        #   ExportManager (exportLogs Flow, listExports, delete, retry/resend delivery);
                        #   PDF/CSV/XLSX writers + ZipFileWriter; ExportHistoryRemoteRepository
                        #   (Firebase Storage + Firestore manifest); ExportDeliveryBackend →
                        #   requestExportDelivery Cloud Function (email delivery)
    sharedassets/       #   Strings for selection / progress / history / delivery
    update/             #   ExportSelectionScreen, ExportHistoryScreen, ViewModels, ExportFileSharer
  sharing/              # Multi-user aircraft access (GA). See docs/sharing/
    model/              #   ShareRole, AircraftShareState, InviteLink, InvitePreview, RedeemOutcome, InviteCode
    datamanager/        #   SharingManager (+Impl), AircraftScopeResolverImpl, ThingShareDeepLinks
    sharedassets/       #   Shared strings
    viewing/            #   ManageAccessScreen, AccessPanelViews, EnterInviteCodeScreen, RedeemConfirmationSheet
    update/             #   ManageAccessRoute/ViewModel, RedeemHost/ViewModel, LinkSharer (per-platform)
  subscription/         # SquawkIt Pro (GA on Android + iOS; gating unconditional, no build flag)
    model/              #   BillingManager interface, SubscriptionResolution (entitlement → effective tier)
    datamanager/        #   SubscriptionManager (the feature gate), SubscriptionManagerImpl,
                        #   EntitlementReconciler + FirebaseEntitlementReconciler, BillingIdentityCoordinator
    viewing/            #   SubscriptionScreen, ProPaywallContent, ProMembershipContent, ProUpsellSheet,
                        #   PaywallHost (per-platform), SubscriptionViewModel
    billing/            #   RevenueCat wrapper — Android + iOS ONLY (no Kotlin/JS variant is published);
                        #   web binds a no-purchase implementation via platformBillingModule
  ads/                  # Free-tier display ads (GA on Android + iOS; web has no ad product)
    model/              #   AdSlotKey, AdSlots, AdSlotFormat, AdSurface, AdUnitSize, AdConsentState, ListRow
    datamanager/        #   AdsManager + Impl, AdSessionCounter (session cap), AdConsentManager with
                        #   Android UMP / iOS bridge / web no-op actuals
    sharedassets/       #   Strings
    viewing/            #   AdSlot, AdView (AdMob on Android, Swift bridge on iOS, nothing on web)
  sync/                 # Local-first sync engine — the only entity-path Firestore client
    data/               #   SyncEngine, HydrationRunner, PullListener, PushWorker, PushFailureClassifier,
                        #   SyncCursorStore, SyncPreferences (implements core:storage's CloudSyncSetting),
                        #   SharedScopeJanitor, SubscriptionSyncListener, SyncDocWire, TombstoneGc,
                        #   impl/ — FirestorePullSubscription, FirestoreRemoteFetcher, FirestoreSyncWriter,
                        #   blob/ — BlobUploadDriver, BlobDownloadDriver, BlobDeleteDriver, AttachmentBroker,
                        #   AppCheckTokenProvider, Android WorkManager workers, iOS URLSessionUploadScheduler
    logging/            #   SyncTelemetry
    settings/           #   SyncSettingsScreen, SyncSettingsViewModel (Cloud Sync + Sync-on-Cellular)
    sharedassets/       #   Sync-related shared strings/drawables
  developeroptions/     # Runtime developer overrides (replaced the old FeatureLab)
    datamanager/        #   DeveloperOptionsManager, DeveloperFlags, Koin module
                        #   (synced as CollectionKind.DeveloperOptions; UI lives in feature/settings)
  settings/             # App settings (flat module, no submodule split)
                        #   SettingsScreen, appearance/logging rows, DeleteAccountDialog,
                        #   developeroptions/ — DeveloperOptionsScreen, SubscriptionDeveloperSettings,
                        #   DisplayAdsDeveloperSettings
  userprofile/          # Legacy profile remnant (sharedassets only) — being unified with Technician
  stresstest/           # Fake data generator, compiled into every build, runtime-gated by
                        #   AppCapability.isStressTestSupported
    config/             #   StressTestPlugin — shared composable UI + route registration
backend/firebase/       # NOT a Gradle module
  functions/            #   TypeScript Cloud Functions (v2, Node 22) + vitest emulator suite:
                        #   account/ (deleteMyAccount), export/ (requestExportDelivery + mailer + manifest),
                        #   sharing/ (create/preview/redeem/cancel invite, revoke, updateRole,
                        #   onAircraftDeleted, invite codes, rate limit), storage/ (blob broker, streamBlob,
                        #   getBlobUploadSession, onRecordDeleted, scheduled storage sweep),
                        #   subscription/ (RevenueCat webhook + API, entitlement apply/reconcile,
                        #   grantPromoEntitlement, projectAttachmentEntitlement), generated/proto/
  firestore.rules       #   Source-controlled; deployed only by CI
  storage.rules
```

Adding a module means editing `settings.gradle.kts` **and** `core/di/CommonAppModules.kt`.

## Canonical Feature Module Pattern

`feature/tasks` is the reference implementation. Every new feature module should follow this
submodule layout:

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
datamanager   →  :model, core:storage, core:model, Koin, Coroutines (Firebase only where justified)
viewing       →  :model, :sharedassets, core:ui, core:model
update        →  :model, :datamanager, :viewing, :sharedassets, core:*
```

**Hard rule:** a module must never be added as a dependency of another module solely because it
contains a string or drawable. Shared assets belong in `sharedassets/`. This keeps `datamanager` and
`model` free of UI/resource dependencies, and keeps UI modules from pulling in each other's business
logic.

### What lives where

| Layer | Module | Contents |
|-------|--------|----------|
| Domain | `model/` | Feature-specific data classes, enums (e.g. `DueStatus`, `DueMetadata`, `ShareRole`) |
| Data | `datamanager/` | Manager interface, `impl/` package, Koin `*Module.kt` |
| Resources | `sharedassets/` | `strings.xml` and drawables used by both `viewing/` and `update/`; may hold small leaf presentation helpers (label mappers, shared input fields) that other features consume without pulling in this feature's UI modules — may depend on `core:ui`/`core:model`, never on another feature |
| Display | `viewing/` | Stateless composables — cards, list items, detail sheets, alert sections |
| Edit | `update/` | Screens, routes, `viewmodel/` package with ViewModel + `UiState`, Koin ViewModel module, `compose/` package for form field components |

### Non-canonical exceptions (do not copy these for new features)

- **`feature/technician/manage/`** — `manage/` instead of `viewing/` + `update/`; list and edit
  screens coexist. Prefer canonical unless the feature is CRUD-only with no standalone view.
- **`feature/settings/`** — flat module; also hosts the Developer Options screens.
- **`feature/userprofile/`** — legacy remnant, being unified with Technician
  (`docs/technician/userprofile_as_technician.md`).
- **`feature/thing/dashboard/`** — single submodule with its own ViewModel and DI module; its
  sibling `aircraft/update` is canonical.
- **`feature/fleet/`** — no `model` or `update`; `viewing/` holds only `FleetEmptyState`, and
  `picker/data` is a data-only leaf. When a feature has no `update` sibling, `viewing/` may host the
  list ViewModel (e.g. `logs:viewing`'s `MaintenanceLogListViewModel`).
- **`feature/shell/`** — an aggregator (shared nav graph + shell ViewModel), not a domain feature.
- **`feature/export/`** — `datamanager` + `sharedassets` + `update` only; a single user-driven flow
  with no standalone read surface.
- **`feature/subscription/`** — the ViewModel lives in `viewing/` (there is no `update`), plus an
  Android+iOS-only `billing/` submodule because RevenueCat publishes no Kotlin/JS variant.
- **`feature/developeroptions/`** — `datamanager` only; its one screen lives in `feature/settings`,
  the same reasoning that kept the old FeatureLab screen there.

### Koin modules

Each submodule that provides injectable objects declares its own `*Module.kt`. All modules shared by
the hosts are aggregated in **`core/di/CommonAppModules.kt`** — add new modules there when creating a
feature. `composeApp`'s `initKoin.kt` and `webApp`'s `main.kt` are thin wrappers that take that list
and add host bootstrap only (`createAppCapability`, `stressTestKoinModules()`, host-only singles like
the web SQLite worker). The list is kept in one place because it drifted between hosts once before —
a module registered in one host but not the other fails at *runtime*
(`NoDefinitionFoundException`), not at compile time.

### New feature checklist

1. Create the submodules following the canonical layout.
2. Register them in `settings.gradle.kts`.
3. Add the Koin module(s) to `core/di/CommonAppModules.kt`.
4. Add route constants to `core/nav`'s `Screen`, and register routes in `feature/shell`'s nav graph
   (`formDialogs` / `settingsDetailRoutes` / a sibling registrar).
5. Put user-facing strings in the module's `strings.xml` (see the resource rules below).

The `feature-module-scaffolder` agent (`.claude/agents/`) automates the skeleton; it does not
implement business logic.

## Architecture

**Stack:** MVVM + StateFlow | Koin DI | Coroutines/Flow | SQLDelight (local-first store) | Firebase
Firestore (background sync only) | Protocol Buffers (Wire) | Compose Multiplatform

### Layering pattern

1. **UI** — `@Composable` screen collects `StateFlow<UiState>` from a ViewModel via `koinViewModel()`
2. **ViewModel** — holds `MutableStateFlow<UiState>`, combines manager data with `combine()` /
   `flatMapLatest()`
3. **Manager (interface + impl)** — in `datamanager/`; the interface defines the contract, `impl/`
   reads/writes the local `EntityStore`. Injected via Koin.

### Data flow example

```
EntityStore<Aircraft>.observeAll (SQLDelight Flow, FleetManagerImpl)
  → flatMapLatest → combine(tasks, logs, squawks per aircraft)
  → FleetDashboardViewModel._uiState (StateFlow)
  → DashboardScreen (collectAsStateWithLifecycle)
```

### Local-first storage (R1 — shipped, the only path)

`core/storage` provides `EntityStore` (SQLDelight-backed), `EntityScope`, `EntityCodecRegistry`,
Koin modules, and `CollectionKind` — **10 kinds**: Aircraft, MaintenanceTask, MaintenanceLog,
MaintenanceOverview, Technician, UserInfo, DeveloperOptions, Subscription, Squawk, SharedAircraftRef.
`CollectionKind.ALL` is asserted complete against `sealedSubclasses` by a coverage test, so a
forgotten entry fails the build instead of corrupting data at runtime. The `collection` column is
`TEXT`, so adding a kind is a zero-migration change.

Schema tables: `entity`, `sync_cursor`, `sync_config`, `blob_object`. Entity rows carry `dirty`,
`deleted`, `writer_uid`, `updated_at` (device wall clock — display and push ordering only, **never**
sync ordering) and `remote_updated_at` (the Firestore server timestamp, which *is* the sync ordering
key).

### Sync engine (`feature/sync/data`) — the only entity-path Firestore client

`SyncEngine` is anchored to `FirebaseAuth.authStateChanged` and gated on signed-in **and**
non-anonymous **and** cloud-sync-enabled. On sign-in it hydrates top-level scopes (Aircraft,
Technician, UserInfo) under the user's root, attaches pull listeners at the cursor watermark, starts
`PushWorker`, and observes the local aircraft list to spin up per-aircraft listeners for nested kinds
(logs, tasks, overview, squawks). On sign-out it tears down the per-user scope; data on disk is left
alone (a different user gets their own `users/{uid}/…` scope, so there is no leakage).

Supporting pieces: `HydrationRunner`, `PullListener` / `FirestorePullSubscription`, `PushWorker` +
`PushFailureClassifier` (drains `dirty=1` via `FirestoreSyncWriter`), `SyncCursorStore`,
`SharedScopeJanitor` (prunes revoked shared scopes), `SubscriptionSyncListener` (mirrors the
server-authoritative entitlement), `TombstoneGc`, and the blob drivers (upload/download/delete, with
Android WorkManager workers and an iOS background `URLSession` scheduler).

Conflict resolution is last-writer-wins on the Firestore server timestamp; dirty rows are immune from
remote overwrite (no local clock in the ordering logic). Anonymous users are fully offline — the
engine stays idle.

### Firestore + protobuf serialization

Proto definitions live in `core/model/src/commonMain/proto/` and are also the input to the Cloud
Functions' `generate:proto` step, so client and server share one schema. Each synced entity is one
`SyncDocWire` document: `payload` (Base64-encoded proto bytes), `deleted`, `schema` (proto FQN), and
`lastUpdateTimestamp` (Firestore server timestamp).

**Feature managers never touch Firestore.** Two deliberate, documented exceptions exist, both
online-only remote repositories rather than entity-path clients:

- `SharingManager` — share ACL docs are plain-field, rules-inspected and function-written, so they
  cannot ride the entity-sync path (`docs/sharing` §6.2).
- `ExportHistoryRemoteRepository` — the export manifest in Firebase Storage + Firestore.

### Sharing and scope resolution

Shared aircraft data lives **in place under the host's tree** — refs are pointers, not copies.
`SharedAircraftRef` (`users/{uid}/shared_aircraft_ref/{aircraftId}`) is the member-side index and
drives the sync engine's foreign-scope fan-out.

Per-aircraft managers (logs, tasks, squawks, overview) **must not derive a scope from the signed-in
uid**. They inject `ThingScopeResolver` (interface in `core:storage` so they need no dependency on
the sharing feature; implementation in `feature/sharing/datamanager`, bound via Koin — the same
pattern as `CloudSyncSetting`):

- own aircraft → `aircraftChildUnsafe(myUid, aircraftId)`
- shared aircraft → `aircraftChildUnsafe(hostUid, aircraftId)` from the ref

Storage entitlement follows the host: on a foreign-hosted aircraft the member is never blocked by
their own subscription — the host's entitlement governs and the blob broker enforces it.

### Gating: three mechanisms, kept separate

| Question | Mechanism |
|---|---|
| Does this build/platform support it at all? | `AppCapability` (`core:appinfo`) — injected singleton |
| Is the account entitled to it? | `SubscriptionManager` flows |
| Is a developer overriding it locally? | `DeveloperOptionsManager` / `DeveloperFlags` |

`AppCapability` fields: `isDeveloperOptionsSupported`, `isStressTestSupported`,
`isCameraCaptureSupported`, `isAnonymousLoginSupported`, `isAdsSupported`. Constructed once per host
at Koin startup via `createAppCapability(isDeveloperBuild)`.

`SubscriptionManager` gates: `status()`, `entitlement()`, `canUploadAttachments()` (links stay free),
`canEmailExports()` (export-to-device stays free), `canHostShare()` (accepting an invite is never
gated), `aircraftLimit()`, `shouldShowAds()`. The entitlement is server-authoritative — written only
by Cloud Functions at `subscriptions/{uid}` and mirrored read-only into the local store.
`shouldShowAds()` is false whenever `isAdsSupported` is off: a build that cannot sell Pro gives a
pilot no way to remove ads, so "no Pro" can only ever mean "no ads".

`DeveloperFlags` (honored only in developer builds): `forceSubscriptionStatus`, `forceAds`,
`adConsentTestDeviceHashedId`.

**FeatureLab no longer exists.** `feature/featurelab`, `FeatureLabManager`, and `FeatureFlags` were
removed; `attachmentUploadEnabled` in ViewModels is now fed by
`SubscriptionManager.canUploadAttachments()`. Don't reintroduce a lab-flag layer — pick one of the
three mechanisms above.

### Dependency injection

- Central aggregation: `core/di/CommonAppModules.kt` (one list, all hosts); `initKoin.kt`
  (composeApp) and `main.kt` (webApp) add host-only bootstrap.
- Each module has its own `di/*Module.kt`.
- Platform bindings via `androidMain` / `iosMain` / `jsMain` actuals — e.g.
  `platformBillingModule` (RevenueCat vs. no-purchase on web), `platformAdConsentModule` (UMP vs.
  Swift bridge vs. no-op), `platformStorageModule` (SQLite driver per host).

### Multiplatform split

- `commonMain` — all shared Kotlin + Compose code
- `androidMain` / `iosMain` / `jsMain` — Firebase SDK selection, HTTP client (OkHttp / Darwin / JS),
  auth providers, blob workers, ad views, billing

## Backend

TypeScript Cloud Functions (Firebase Functions v2, Node 22) in `backend/firebase/functions/`, grouped
by domain: `account/`, `export/`, `sharing/`, `storage/`, `subscription/`, with `shared/auth.ts`
enforcing authenticated + App Check callers and `config/` holding env and admin bootstrap. Protos are
generated from `core/model` at build time into `src/generated/proto/`.

Notable jobs beyond the callables: `scheduledStorageSweep` (orphaned-blob GC, armed),
`scheduledEntitlementReconcile`, `revenueCatWebhook`, `onRecordDeleted` and `onAircraftDeleted`
(cascade cleanup).

Tests are vitest running against the auth/firestore/storage emulators and cover functions **and**
the Firestore/Storage rules (`test/firestore-rules.test.ts`, `test/storage-rules.test.ts`,
`test/sharing-rules.test.ts`).

## Key Dependencies (`gradle/libs.versions.toml`)

| Library | Version |
|---------|---------|
| Kotlin | 2.4.10 |
| Compose Multiplatform | 1.11.1 |
| Android Gradle Plugin | 9.3.1 |
| Firebase KMP (GitLive) | 2.5.0 |
| Koin | 4.2.2 |
| Wire (protobuf) | 6.4.5 |
| SQLDelight | 2.3.2 |
| Kotlinx Coroutines | 1.11.0 |
| Kotlinx Datetime | 0.8.0 |
| Ktor | 3.5.1 |
| Coil | 3.5.0 |
| RevenueCat KMP (Android/iOS only) | 3.3.1 |
| Play Services Ads / UMP | 24.6.0 / 4.0.0 |
| MockK | 1.14.11 |
| Google Truth | 1.4.5 |

## Design System

Defined in `core:ui`. Follows **Refined Minimalism**: Material 3 color scheme, intentional typography
hierarchy, consistent spacing tokens. Prioritize clarity and readability over information density.

**Read `PRODUCT.md`, `DESIGN.md`, and `.impeccable/design.json` before any UI work.** Together they
define the required aviation palette (Aviation Blue primary, Instrument Amber accent ≤10% of color
moments, semantic forest/amber status colors), required typography (Space Grotesk titles, JetBrains
Mono for technical data, system sans for body), and brand principles (Dependability First, Clarity
over Density, Progressive Disclosure). Dynamic color is disabled; the aviation palette is the brand.

## Design Docs

Feature PRDs and architecture design docs live in `docs/`, organized into per-topic subfolders:

- `docs/product/` — `PRD.md`, `multi_domain_maintenance_PRD.md` (the pivot from aircraft-only to any
  maintainable “Thing” via a template configuration system — **Phase 1 shipped 2026-08-29; Phases 2–5 are
  proposed**), `thing_migration_design.md` (**shipped** — the Phase 1 proto/Firestore/Storage migration, and the
  reference for how a stored-identity change is sequenced), `template_system_design.md` (proposed — how a
  template is defined, distributed, versioned, and resolved; Phase 2's foundation),
  `platform_feature_parity.html`, store assets
  and screenshots
- `docs/storage/` — `storage_mode_PRD.md`, `storage_r1_design.md`, `storage_r2_design.md`,
  `deletion_gc_design.html`
- `docs/attachments/` — `attachments_PRD.md`, `attachments_design.md`
- `docs/squawks/` — `user_squawking_prd.md`, `squawk_design.md`
- `docs/export/` — `export_logs_PRD.md`, `export_logs_design.md`,
  `export_email_automation_design.html`, plus the `export_logs_sample/` reference bundle
- `docs/technician/` — `technician_design.md`, `userprofile_as_technician.md`
- `docs/sharing/` — `aircraft_sharing_PRD.html`, `aircraft_sharing_design.html`
- `docs/subscription/` — `subscription_PRD.html`, `subscription_design.html`
- `docs/ads/` — `display_ads_PRD.md`, `ads_design.html`
- `docs/account/` — `account_upgrade_PRD.html`, `account_upgrade_design.html`,
  `email_link_signin_design.html`
- `docs/analytics/` — `analytics_design.html` (proposed, not implemented)
- `docs/notifications/` — `notifications_PRD.md`, `notifications_design.md` (proposed, not
  implemented)
- `docs/aircraft/` — `aircraft_overview_tabs.md`
- `docs/search/` — `intelligentsearch.md`
- `docs/web/` — `web_target_expansion_plan.md`, `web_attachments_design.md`,
  `web_adaptive_layout_design.html`, `promo_site_design.html`
- `docs/cleanup/` — `codebase_cleanup_plan.md` (the 2026-07 cleanup; phases 1–5 executed, kept as the
  record of what moved where and why)
- `docs/branding/` — brand assets

Consult the relevant doc before making non-trivial changes to a feature area, and update its
**Implementation Status** note when you change what has shipped.

**Doc format policy:** all *new* docs are authored in **Markdown**. Existing HTML docs stay as-is
until substantially rewritten; do not bulk-convert. Place a new doc in the matching subfolder
(create one if no topic fits) and link related docs with relative paths. A Markdown doc opens in
any editor, diffs cleanly in review, and needs no embedded stylesheet — which is why it, not
self-contained HTML, is now the default.

## Developer Builds & Capabilities

There is no compiled-out "dogfood" variant. The **Fake Data Generator** (`feature/stresstest`) is a
normal dependency compiled into every build; its routes and the Developer Options entry are
registered by the shared nav graph (`feature/shell`) and gated on a single runtime flag —
`AppCapability.isStressTestSupported` — identically on Android, iOS, and web. `AppCapability` also
carries `isDeveloperOptionsSupported` (the Developer Options settings row), the platform-capability
constants (camera capture, anonymous login), and `isAdsSupported`.

`isDeveloperBuild` is computed once per host and passed to `createAppCapability`:

### Android

- No product flavor — a single `app` variant dimension (`debug`/`release`).
- `isDeveloperBuild` comes from `BuildConfig.DEVELOPER_BUILD`: hardcoded `true` for `debug`, settable
  on `release` via `-PdeveloperBuild=true` (see `app/build.gradle.kts`).
- Build: `./gradlew assembleDebug` · `./gradlew assembleRelease` ·
  `./gradlew assembleRelease -PdeveloperBuild=true` (signed dogfood-style release, tooling on).

### iOS

- Two configurations — **Debug** and **Release** — with one shared scheme each (`iosAppDebug`,
  `iosAppRelease`).
- `MainEntry.doInitKoin(forceDeveloperBuild:)` (`composeApp/src/iosMain/MainViewController.kt`) is
  the single entry point; `isDeveloperBuild` is `forceDeveloperBuild || Platform.isDebugBinary`.
  `iosApp.swift` passes `false`, so Debug gets developer tooling via the debug-binary check and
  Release does not. `forceDeveloperBuild` stays on the API as the iOS equivalent of
  `-PdeveloperBuild=true`; there is no Swift compile flag, because Swift cannot see
  `Platform.isDebugBinary`.
- Build: open `iosApp/iosApp.xcodeproj`, select **iosAppDebug**, run.

### Web

- `webApp` depends on `feature:stresstest:config` and registers the plugin route and Koin module the
  same way as Android/iOS. `isDeveloperBuild` comes from the webpack-injected `__WINGSLOG_DEBUG__`.
- The Fake Data Generator is reachable through **Settings → Developer Options**.
- Build: `./gradlew :webApp:jsBrowserDevelopmentWebpack`.

## Coding Conventions

- **Thing, not aircraft**: the domain is **Things**, not aircraft (Milestone 1, `docs/product/thing_migration_design.md`). New types, properties, wire names, and schema names use Thing vocabulary. Use aviation vocabulary **only** when the subject is genuinely and permanently an airplane — `Engine`, `Propeller`, `PropellerHub`, `EngineHourRule` qualify; anything that will one day hold a boat, a house, or a 3D printer does not.

  What survives with aviation names is **grandfathered, not exemplary**: `CollectionKind`'s five `aircraft.*` `schemaName`s, the `shared_aircraft_ref` wireName, the `SharedAircraftRef` and `ExportRecordAircraft` proto messages (and the Kotlin/TS names that mirror them), the `aircraft_id` proto field, the push payload's `aircraftId` key and its `*_aircraft_updated` bodyKeys, and the `airframe_hours` / `airframe` template keys. Issue #638 records why they stay — renaming stored identity is a data migration, not a refactor. Do not copy them.

  Everything else was renamed in #637: types, parameters, test constants, log messages, nav routes and the notification tap-URI segment now all say Thing.

  The cost is asymmetric and that is the whole point. A wrong name in Kotlin is a compiler-verified rename; a wrong `wireName` or `schemaName` is a global batch, a grace window, and a coordinated client release across three platforms. Milestone 1 did that once, deliberately. Get the name right when it is free.
- **Instants**: always `kotlin.time.Instant`, never `kotlinx.datetime.Instant`.
- **Koin injection**: always `get<ClassType>()`, never bare `get()`. A `PostToolUse` hook
  (`.claude/hooks/no-bare-koin-get.sh`) rejects the latter — a bare `get()` resolves positionally and
  silently rebinds to the wrong dependency when a constructor is reordered, while the explicit type
  argument turns that into a compile error. Kotlin property accessors (`val x get() = …`) are exempt.
- **No backslash escapes in Kotlin strings or `strings.xml`**: use a typographic apostrophe `’`,
  never `\'` (`"it's"` is already legal Kotlin, and `’` is better typography anyway). Enforced by
  `.claude/hooks/no-escape-chars.sh`; Kotlin char literals are exempt.
- **Analytics events are typed**: emit `analytics.log(ThingCreated(…))` from the taxonomy in
  `core/analytics` (`AnalyticsEvent.kt` / `AnalyticsEvents.kt`), never
  `analytics.logEvent("name", mapOf(…))`. A `PostToolUse` hook
  (`.claude/hooks/no-untyped-analytics-event.sh`) rejects callers outside `core/analytics` itself.
  **GA4 event and parameter names are append-only** — a name cannot be changed once data has landed
  against it, because renaming orphans the history rather than migrating it, exactly as with a wire
  identity (#638). `AnalyticsTaxonomyTest` pins the shipped names. Thing-scoped events implement
  `ThingScopedEvent`, which requires `template_id` — the dimension PRD §13 splits every metric by.
- **Feature managers read/write `EntityStore` only** — the sync engine is the Firestore client, with
  the two documented online-only exceptions above.
- **Per-thing scopes** come from `ThingScopeResolver`, never from the signed-in uid.
- **Capabilities**: build-time/platform gates go through the injected `AppCapability` singleton, not
  ad-hoc `isDeveloperBuild` checks or `expect`/`actual` booleans scattered across feature modules.
- **Entitlement gates** go through `SubscriptionManager`, not a per-feature copy of the tier logic.
- **ViewModels in `viewing/`**: allowed when a feature has no `update` submodule (e.g. `logs:viewing`,
  `subscription:viewing`); the app-shell ViewModel lives in `feature/shell`.
- **Transitive deps**: `core:storage` and `core:ui` api-export most shared deps; don't redeclare them
  in downstream modules.

## Testing

- Unit tests live in each module's **`src/test/kotlin`** and run under `./gradlew testDebugUnitTest`
  — except modules migrated to the `com.android.kotlin.multiplatform.library` plugin, where they live
  in **`src/androidHostTest/kotlin`** and run under `./gradlew testAndroidHostTest`. `commonTest` /
  `jsTest` are used only where a test genuinely must be multiplatform.
- Stack: **JUnit 4 + MockK + Google Truth + kotlinx-coroutines-test**.
- The densest suites are the best patterns to copy: `feature/sync/data`, `core/storage`,
  `feature/attachment/datamanager`, `feature/export/datamanager`, `feature/ads/datamanager`.
- Backend tests are vitest against the Firebase emulators (`backend/firebase/functions/test/`) and
  cover both functions and security rules.
- The `kmm-test-writer` agent (`.claude/agents/`) knows these conventions.

## Engineering Best Practices

### Post-task cleanup pass (required)

After finishing a large job — a sizable feature implementation, a big refactor, or any multi-file
change — perform a cleanup pass over **all changed `.kt` files before the final commit**.

**Why:** inline fully-qualified class paths, stray blank lines, and formatting inconsistencies
accumulate during implementation. Catching them before commit keeps the diff clean and the history
reviewable.

**How to apply** — scan every changed file for:

1. **Fully-qualified class references used inline** instead of imported — e.g.
   `kotlinx.coroutines.flow.flowOf(...)` should become an `import` plus `flowOf(...)`.
2. **Trailing blank lines** at the end of files.
3. **Extra blank lines** — collapse double-or-more blank lines where one is expected.
4. **Import ordering** — `kotlin.*` before `kotlinx.*`, alphabetical within each group.
5. **Other formatting issues** — inconsistent indentation, long lines that should wrap.

### User-facing strings must live in `strings.xml` (required)

Every user-facing string must be defined in a `strings.xml` resource and referenced via the generated
`Res` / `stringResource` — never hardcoded inline in Compose or other UI code.

**Where the resource goes** — placement follows actual usage:

1. **Used by a single module** → that module's own
   `src/commonMain/composeResources/values/strings.xml`.
2. **Shared with a module that already depends on the owner** → keep it in the owning module; the
   consumer reads it through the existing dependency.
3. **Shared across modules with no existing dependency**, where adding one just for a resource makes
   no sense → put it in a `sharedassets/` target and depend on that from both sides. `sharedassets`
   carries no feature deps, so it is the right home for cross-feature resources.

**Reuse before adding.** Before creating a new string, search `core/sharedassets`, the feature's
`sharedassets`, and the relevant module, and reuse what exists.

## graphify

This project can carry a knowledge graph at `graphify-out/` (git-ignored, so a fresh clone won't have
one) with god nodes, community structure, and cross-file relationships.

Rules:

- For codebase questions, first run `graphify query "<question>"` when `graphify-out/graph.json`
  exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for
  focused concepts. These return a scoped subgraph, usually much smaller than `GRAPH_REPORT.md` or
  raw grep output.
- If `graphify-out/wiki/index.md` exists, use it for broad navigation instead of raw source browsing.
- Read `graphify-out/GRAPH_REPORT.md` only for broad architecture review, or when
  query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
