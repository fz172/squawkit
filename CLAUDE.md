# CLAUDE.md

Guidance for Claude Code (claude.ai/code) working in this repository.

## How this file relates to AGENTS.md

[AGENTS.md](AGENTS.md) is the long-form reference: module-by-module descriptions, the full
dependency-rule rationale, engineering best practices, and design-doc map. Read it for depth.

This file is the **current-state orientation** and is kept in sync with the code. AGENTS.md still
describes a few things that have since changed (see "Recently changed" below); where the two
disagree, the code is authoritative and this file follows it.

## What this is

**SquawkIt** — a Kotlin Multiplatform aviation logbook / fleet-management app (aircraft CRUD,
maintenance logs, inspection compliance and due-status, squawks, technicians, attachments, export,
aircraft sharing, subscriptions, ads). One Compose Multiplatform codebase ships to **Android**
(minSdk 33, targetSdk/compileSdk 37), **iOS**, and **web** (Kotlin/JS).

> **Naming:** the user-facing brand is **SquawkIt**; every identifier still uses the original
> **WingsLog** name — package `dev.fanfly.wingslog`, Gradle root project `wingslog`, Firebase
> project `wingslog-9ca4e`. This is deliberate; do not "fix" it.

**Architecture in one line:** MVVM + StateFlow, Koin DI, a local-first SQLDelight `EntityStore` as
the single source of truth for every read and write, with a background Firestore sync engine
(`feature/sync/data`) as the *only* entity-path Firestore client.

## Recently changed (things stale docs still get wrong)

- **FeatureLab is gone.** `feature/featurelab` and `FeatureLabManager` / `FeatureFlags` no longer
  exist. Runtime developer overrides now live in `feature/developeroptions/datamanager`
  (`DeveloperOptionsManager` + `DeveloperFlags`, synced as `CollectionKind.DeveloperOptions`), with
  the UI under `feature/settings/developeroptions/`. Reachable only when
  `AppCapability.isDeveloperOptionsSupported`.
- **Attachment upload is a subscription gate, not a feature flag.** `attachmentUploadEnabled` in
  ViewModels is now fed by `SubscriptionManager.canUploadAttachments()` (Pro), not a lab flag.
  Links remain free for everyone.
- **Subscriptions (SquawkIt Pro) shipped, GA on Android + iOS**, gating unconditional (no
  `isSubscriptionSupported` build gate any more).
- **Aircraft sharing shipped and GA'd** — the staged-rollout `isAircraftSharingSupported` gate was
  removed.
- **Display ads GA'd** on Android and iOS (`AppCapability.isAdsSupported = true` on both; web has no
  ad product). `docs/ads/display_ads_PRD.md` still says "nothing shipped" — that note is stale.
- **`CollectionKind` has 10 kinds**, not 8 (added `DeveloperOptions`, `Subscription`,
  `SharedAircraftRef`; `FeatureLab` removed).
- **CI `build.yml` is `workflow_dispatch` only** — lint/build/test do **not** run automatically on
  push. Run them locally before committing.

Doc "Implementation Status" notes are maintained by hand and can lag the code; when a gate or a
module name matters, check the source.

## Repository layout

```
app/                      Android entry point (MainActivity, WingsLogApplication, BuildConfig.DEVELOPER_BUILD)
composeApp/               Android/iOS host — DB-integrity gate, theme wrapper, auth/shell wrappers, initKoin.kt
webApp/                   Kotlin/JS host — OPFS SQLite worker, single-tab gate, email-link completion,
                          Firebase JS + App Check bootstrap, share deep-link parking
iosApp/                   Xcode project (schemes: iosAppDebug / iosAppRelease)
core/
  model/                  Wire-generated protobuf models (+ .proto sources in src/commonMain/proto/)
  nav/                    Screen route definitions
  sharedassets/           App-wide strings/drawables
  analytics/              AnalyticsManager, page-view feeder
  di/                     CommonAppModules.kt — the single Koin module list shared by all hosts
  ui/                     Material 3 theme + shared composables   (theme/, adaptive/, widget/avataricon/)
  lifecycle/              AppForegroundObserver (+ compose/ AppForegroundEffect)
  auth/                   Firebase Auth, platform actuals, account upgrade/link/merge
  firebase/               Shared Firebase utilities + the one Cloud Functions client (functionsModule)
  storage/                EntityStore, CollectionKind, EntityScope, AircraftScopeResolver (interface),
                          EntityCodecRegistry, CloudSyncSetting, SQLDelight Schema.sq, blob/ (LocalBlobStore…)
  datetime/               WireInstantFactory, platform formatters
  appinfo/                AppCapability + createAppCapability (expect/actual), logging config
feature/
  shell/                  Shared nav graph (ShellNavGraph, AdaptiveShellRoute, form dialogs) + shell VM
  login/                  AuthFlow (sign-in + onboarding, incl. ad-consent priming step), deep links
  fleet/                  datamanager · picker/data (SelectedAircraftStore) · sharedassets · viewing
  aircraft/               dashboard (overview + 4 tabs) · update (add/edit aircraft)
  logs/                   datamanager · sharedassets · viewing · update
  tasks/                  model · datamanager · sharedassets · viewing · update   ← canonical reference
  squawk/                 model · datamanager · sharedassets · viewing · update
  technician/             datamanager · manage · sharedassets
  attachment/             model · datamanager · sharedassets · viewing
  export/                 datamanager · sharedassets · update
  sharing/                model · datamanager · sharedassets · viewing · update
  subscription/           model · datamanager · viewing · billing (RevenueCat; Android+iOS only)
  ads/                    model · datamanager · sharedassets · viewing
  sync/                   data (engine + blob drivers) · logging · settings · sharedassets
  developeroptions/       datamanager (DeveloperOptionsManager, DeveloperFlags)
  settings/               Flat module: Settings screen, Developer Options screens, delete-account dialog
  userprofile/            sharedassets only — legacy remnant, being folded into Technician
  stresstest/             Fake data generator (+ config/ plugin), runtime-gated by isStressTestSupported
backend/firebase/         NOT a Gradle module
  functions/              TypeScript Cloud Functions (v2, Node 22) + vitest emulator suite
  firestore.rules         Source-controlled security rules (console is read-only; CI is the only publisher)
  storage.rules
```

Every module is listed in `settings.gradle.kts`; adding one means editing that file **and**
`core/di/CommonAppModules.kt`.

## Build, test, run

```bash
./gradlew assembleDebug                              # Android debug APK (developer tooling on)
./gradlew assembleRelease                            # Release APK (developer tooling off)
./gradlew assembleRelease -PdeveloperBuild=true      # "Dogfood-style" release APK (tooling on)
./gradlew lint
./gradlew testDebugUnitTest                          # All Android unit tests
./gradlew :feature:fleet:datamanager:testDebugUnitTest   # One module
./gradlew :composeApp:iosSimulatorArm64Test          # iOS simulator tests (local only)
./gradlew :webApp:jsBrowserDevelopmentWebpack        # Web dev bundle
./gradlew :webApp:jsBrowserDistribution              # Web production bundle (what deploy-web ships)
```

- **iOS:** open `iosApp/iosApp.xcodeproj`, pick the **iosAppDebug** scheme (Release build →
  **iosAppRelease**). iOS is never built on CI.
- **`assembleRelease` mutates `version.properties`** (bumps `patch` + `versionCode`, stamps
  `buildDate`). Don't run it casually, and don't commit an unintended bump.
- JDK 21 toolchain. `gradle.properties` sets `-Xmx16g` because the Kotlin/Native release link runs
  in-process; lower it if your machine has less RAM.
- Android builds need `app/google-services.json` (CI writes it from the `GOOGLE_SERVICES_JSON`
  secret).

Backend (from `backend/firebase/functions/`):

```bash
npm ci
npm run build          # generate:proto (ts-proto from core/model protos) + tsc
npm test               # vitest against the auth/firestore/storage emulators (needs firebase-tools)
```

## CI / CD (`.github/workflows/`)

| Workflow | Trigger | Does |
|---|---|---|
| `build.yml` | **manual only** (`workflow_dispatch`) | lint → assembleDebug → testDebugUnitTest |
| `deploy-functions.yml` | PR / push to `main` on `functions/**` | emulator test suite; deploys functions on merge |
| `deploy-firestore-rules.yml` | PR / push to `main` on `firestore.rules` | rules test suite; deploys rules on merge |
| `deploy-storage-rules.yml` | PR / push to `main` on `storage.rules` | rules test suite; deploys rules on merge |
| `deploy-web.yml` | manual | builds `:webApp:jsBrowserDistribution`, deploys to the `alpha` (release) or `debug` hosting channel |
| `promote-web.yml` | manual | promotes the alpha channel to live |

Because the Kotlin build is not on PR automation, **run `./gradlew lint testDebugUnitTest` yourself**
before pushing anything non-trivial.

## Architecture details

### Local-first storage (R1 — shipped, the only path)

`core/storage` owns the SQLDelight schema (`entity`, `sync_cursor`, `sync_config`, `blob_object`),
`EntityStore`, `EntityScope`, and `CollectionKind` — 10 kinds: Aircraft, MaintenanceTask,
MaintenanceLog, MaintenanceOverview, Technician, UserInfo, DeveloperOptions, Subscription, Squawk,
SharedAircraftRef. `CollectionKind.ALL` is asserted complete by a coverage test, so adding a subtype
without registering it fails the build.

Entities are proto bytes (Wire). Rows carry `dirty`, `deleted`, `updated_at` (device clock, display
and push ordering only) and `remote_updated_at` (Firestore server timestamp — **the** sync ordering
key), plus `writer_uid`.

### Sync engine (`feature/sync/data`) — the only entity-path Firestore client

`SyncEngine` is anchored to `FirebaseAuth.authStateChanged` and gated on signed-in **and**
non-anonymous **and** cloud-sync-enabled. It orchestrates `HydrationRunner` (initial pull),
`PullListener` / `FirestorePullSubscription` (live updates), `PushWorker` (drains `dirty=1` via
`FirestoreSyncWriter`), `SharedScopeJanitor`, `SubscriptionSyncListener`, and the blob drivers
(`BlobUploadDriver` / `BlobDownloadDriver` / `BlobDeleteDriver`, Android WorkManager + iOS background
`URLSession`). Conflict resolution is last-writer-wins on the server timestamp; dirty rows are immune
from remote overwrite. Anonymous users are fully offline (engine idle).

Each synced entity is one `SyncDocWire` doc: Base64 proto `payload`, `deleted`, `schema`,
`lastUpdateTimestamp`.

### Sharing and scopes

Shared aircraft data lives **in place under the host's tree** — refs are pointers, not copies.
Per-aircraft managers must resolve their scope through `AircraftScopeResolver` (interface in
`core:storage`, implemented in `feature/sharing/datamanager`), never by deriving a scope from the
signed-in uid. `SharedAircraftRef` (`users/{uid}/shared_aircraft_ref/{aircraftId}`) drives the sync
engine's foreign-scope fan-out.

`SharingManager` is a **deliberate exception** to "managers never touch Firestore": share ACL docs
are plain-field, rules-inspected and function-written, so they can't ride the entity-sync path. It is
an online-only surface (precedent: `ExportHistoryRemoteRepository`). Shared *content* stays
local-first.

### Gating: three different mechanisms, don't mix them

| Question | Mechanism |
|---|---|
| Does this build/platform support it at all? | `AppCapability` (`core:appinfo`), injected singleton — `isDeveloperOptionsSupported`, `isStressTestSupported`, `isCameraCaptureSupported`, `isAnonymousLoginSupported`, `isAdsSupported` |
| Is the account entitled to it? | `SubscriptionManager` flows — `canUploadAttachments`, `canEmailExports`, `canHostShare`, `aircraftLimit`, `shouldShowAds` |
| Is a developer overriding it locally? | `DeveloperOptionsManager` / `DeveloperFlags` (honored only in developer builds) |

Never add an ad-hoc `isDeveloperBuild` check or a scattered `expect`/`actual` boolean; extend
`AppCapability` instead. `isDeveloperBuild` comes from `BuildConfig.DEVELOPER_BUILD` (Android),
`forceDeveloperBuild || Platform.isDebugBinary` (iOS), and the webpack-injected `__WINGSLOG_DEBUG__`
(web).

### Dependency injection

Each submodule declares its own `di/*Module.kt`. **All** shared modules are aggregated in
`core/di/CommonAppModules.kt` — one list, both hosts. `composeApp/initKoin.kt` and `webApp/main.kt`
add host bootstrap only (`createAppCapability`, `stressTestKoinModules()`, web SQLite worker). A
module registered in one host but not the other fails at *runtime* with
`NoDefinitionFoundException`, which is why the list is centralized.

## Canonical feature module pattern

`feature/tasks` is the reference:

```
feature/foobar/
  model/           Domain data classes / enums only
  datamanager/     Manager interface + impl/ + Koin module (repository + business logic)
  sharedassets/    Strings, drawables, leaf presentation helpers shared inside the feature
  viewing/         Read-only composables (cards, detail sheets)
  update/          Add/edit screens, ViewModels (+ UiState), Koin VM module
```

Dependency rules (strictly enforced):

```
sharedassets  →  Compose resources + leaf helpers; core:ui / core:model; NEVER another feature
model         →  core:model, kotlinx only
datamanager   →  :model, core:storage, core:model, Koin, Coroutines (+ Firebase only where justified)
viewing       →  :model, :sharedassets, core:ui, core:model
update        →  :model, :datamanager, :viewing, :sharedassets, core:*
```

**Hard rule:** never add a module dependency just to reach a string or drawable — move the resource
to a `sharedassets/` target instead.

Known non-canonical modules (don't copy these for new features): `technician/manage` (combined
list+edit), `settings` (flat), `userprofile` (legacy), `aircraft/dashboard` (single submodule),
`fleet` (no model/update), `shell` (aggregator), `export` (no model/viewing), `subscription` (VM in
`viewing/`, plus an Android+iOS-only `billing/`), `developeroptions` (datamanager only — its UI lives
in `feature/settings`).

New feature checklist: create submodules → `settings.gradle.kts` → Koin module in
`core/di/CommonAppModules.kt` → routes in `feature/shell`'s nav graph → route constants in
`core/nav`. The `feature-module-scaffolder` agent (`.claude/agents/`) automates the skeleton.

## Coding conventions

- **Instants:** `kotlin.time.Instant` only — never `kotlinx.datetime.Instant`.
- **Koin injection:** always `get<ClassType>()`, never bare `get()`. A `PostToolUse` hook
  (`.claude/hooks/no-bare-koin-get.sh`) rejects the latter — positional resolution silently rebinds
  when a constructor is reordered.
- **No backslash escapes in Kotlin strings or `strings.xml`.** Use a typographic apostrophe `’`, not
  `\'`. Enforced by `.claude/hooks/no-escape-chars.sh`.
- **User-facing strings live in `strings.xml`** and are read via the generated `Res` /
  `stringResource` — never hardcoded in Compose. Placement follows usage: owning module first, its
  `sharedassets/` when two modules need it with no existing dependency. Search for an existing
  string before adding one.
- **Feature managers read/write `EntityStore` only** — the sync engine is the Firestore client. The
  documented exceptions (sharing ACL, export history) are online-only remote repositories.
- **Transitive deps:** `core:storage` and `core:ui` api-export most shared deps; don't redeclare them
  downstream.
- **Per-aircraft data:** resolve scopes via `AircraftScopeResolver`, never from the signed-in uid.
- **Post-task cleanup pass (required)** before the final commit of any multi-file change: import
  fully-qualified inline references, drop trailing/extra blank lines, order imports (`kotlin.*`
  before `kotlinx.*`, alphabetical within a group), fix indentation and long lines.

## Testing

- Tests live in each module's **`src/test/kotlin`** (or `src/test/java`) and run under
  `testDebugUnitTest`; `commonTest` / `jsTest` are used only where a test must be multiplatform.
- Stack: **JUnit 4 + MockK + Google Truth + kotlinx-coroutines-test**.
- Densest suites (good patterns to copy): `feature/sync/data`, `core/storage`,
  `feature/attachment/datamanager`, `feature/export/datamanager`.
- Backend rules/function tests are vitest against the Firebase emulators
  (`backend/firebase/functions/test/`).
- The `kmm-test-writer` agent (`.claude/agents/`) knows these conventions.

## Design system and product docs

**Before any UI work**, read `PRODUCT.md`, `DESIGN.md`, and `.impeccable/design.json`. They define
the required aviation palette (Aviation Blue primary, Instrument Amber accent ≤10% of color moments,
semantic forest/amber status colors), typography (Space Grotesk titles, JetBrains Mono for technical
data, system sans body) and brand principles (Dependability First, Clarity over Density, Progressive
Disclosure). Dynamic color is disabled — the aviation palette *is* the brand. Tokens live in
`core:ui/theme`.

**Before non-trivial feature work**, read the matching doc in `docs/`:

`docs/product/` (PRD, platform parity) · `docs/storage/` (R1/R2, deletion GC) · `docs/attachments/` ·
`docs/squawks/` · `docs/export/` · `docs/technician/` · `docs/sharing/` · `docs/subscription/` ·
`docs/ads/` · `docs/account/` (upgrade, email-link sign-in) · `docs/analytics/` (proposed) ·
`docs/aircraft/` · `docs/search/` · `docs/web/` · `docs/cleanup/` · `docs/branding/`.

New docs are authored as self-contained **HTML** in the matching subfolder; existing Markdown docs
stay as-is until substantially rewritten. Keep each doc's "Implementation Status" note honest when
you change what shipped.

## Key dependency versions (`gradle/libs.versions.toml`)

Kotlin 2.4.10 · Compose Multiplatform 1.11.1 · AGP 9.3.1 · Koin 4.2.2 · Wire 6.4.5 · SQLDelight
2.3.2 · Coroutines 1.11.0 · kotlinx-datetime 0.8.0 · Ktor 3.5.1 · Coil 3.5.0 · GitLive Firebase 2.5.0
· RevenueCat KMP 3.3.1 (Android/iOS only) · Play Services Ads 24.6.0 + UMP 4.0.0 · MockK 1.14.11 ·
Truth 1.4.5.

## graphify

If a knowledge graph exists at `graphify-out/` (git-ignored, so a fresh clone won't have one):

- Prefer `graphify query "<question>"` for codebase questions, `graphify path "<A>" "<B>"` for
  relationships, `graphify explain "<concept>"` for focused concepts — they return a scoped subgraph
  far smaller than `GRAPH_REPORT.md` or raw grep output.
- Use `graphify-out/wiki/index.md` for broad navigation when present; read `GRAPH_REPORT.md` only for
  whole-architecture review.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
