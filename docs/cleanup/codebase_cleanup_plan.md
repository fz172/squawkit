# Codebase Cleanup Plan

> **Implementation Status:** Phases 1–2 complete (2026-07-06); Phases 3–5 not started. Each
> phase updates its checkbox table as work lands. Source: full-codebase audit (structure,
> dependencies, duplication, logic ownership), 2026-07-06.

This plan sequences the cleanup so that low-risk deletions land first (shrinking the surface
everything else has to touch), consolidation second, and the riskiest structural moves last.
Every phase leaves the build green and is independently shippable; no phase depends on a later
one.

**Verification baseline for every phase:**

```bash
./gradlew lint
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew :webApp:jsBrowserDevelopmentWebpack   # webApp is not covered by testDebugUnitTest
./gradlew :composeApp:iosSimulatorArm64Test     # local only — not on CI
```

---

## Phase 1 — Delete dead modules and unused dependency edges (completed)

Pure removals. No behavior change possible; every target was verified to have zero consumers.

### 1.1 Delete `core/attachments`

Orphaned remnant of the pre-`feature/attachment` implementation. Already de-registered from
`settings.gradle.kts`; only two stale build files remain, referenced by nothing (verified: no
`core.attachments` / `core:attachments` hits outside the directory itself).

- Delete `core/attachments/` entirely (`datamanager/build.gradle.kts`, `model/build.gradle.kts`).
- Remove the "ORPHANED" entry from the AGENTS.md module tree (see Phase 5).

### 1.2 Remove the empty `feature/fleet/model` module

Registered and depended upon, but has **no `src/` directory** and nothing anywhere imports
`dev.fanfly.wingslog.feature.fleet.model` (verified by grep across all source sets).

- Delete `feature/fleet/model/`.
- Remove `include(":feature:fleet:model")` from `settings.gradle.kts`.
- Remove `implementation(project(":feature:fleet:model"))` from
  `feature/fleet/datamanager/build.gradle.kts` and `feature/fleet/viewing/build.gradle.kts`.
- AGENTS.md currently describes this module as "Aircraft-related domain types" — fix in Phase 5.

### 1.3 Shrink the `feature/userprofile` tree to its two live modules

The only live code in the tree is `userprofilecard` (consumed by `feature/settings` →
`UserProfileCard` in `SettingsScreen.kt`) and `sharedassets` (drawable + strings consumed by
`userprofilecard`). The rest is shell:

| Module                                | Contents                                                                        | Consumers          | Action     |
|---------------------------------------|---------------------------------------------------------------------------------|--------------------|------------|
| `feature/userprofile` (root)          | one unreferenced `strings.xml` (`license_*` — no generated-Res import anywhere) | none               | **delete** |
| `feature/userprofile/database`        | no `src/` at all                                                                | none               | **delete** |
| `feature/userprofile/userprofilecard` | `UserProfileCard.kt`                                                            | `feature/settings` | keep       |
| `feature/userprofile/sharedassets`    | `ic_anonymous_user.xml`, `strings.xml`                                          | `userprofilecard`  | keep       |

- Delete `feature/userprofile/build.gradle.kts` + `feature/userprofile/src/` and
  `feature/userprofile/database/`.
- Remove `include(":feature:userprofile")` and `include(":feature:userprofile:database")` from
  `settings.gradle.kts`.
- Remove from `composeApp/build.gradle.kts`: `implementation(project(":feature:userprofile"))`
  and `implementation(project(":feature:userprofile:database"))`. (Also remove
  `implementation(project(":feature:userprofile:userprofilecard"))` — composeApp never
  references it; `feature/settings` carries its own dependency.)
- The `license_*` strings are aviation-license fields likely intended for the
  userprofile-as-technician unification (`docs/technician/userprofile_as_technician.md`). If
  they're wanted later they can be re-added where they're used; don't keep dead resources on
  spec.

**Coordination note:** the userprofile → technician unification is listed as in progress. This
phase only deletes *empty* modules, so it cannot conflict — but whoever picks up the
unification should know `userprofile:database` no longer exists as a landing spot.

### 1.4 Remove unused declared dependencies

Verified by import cross-check (zero imports from the declared module):

- `feature/export/update/build.gradle.kts` → drop `implementation(project(":feature:sync:data"))`.

---

## Phase 2 — Consolidate duplicated logic and resources (completed)

### 2.1 Extract a shared attachment-form controller (highest-value consolidation)

The pending-attachment workflow is implemented **three times**:

| File                                                                                                | Shape                                                              |
|-----------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| `feature/tasks/update/.../viewmodel/TaskViewModel.kt` (~79–290)                                     | `_pendingAttachments` StateFlow + helpers                          |
| `feature/squawk/update/.../viewmodel/SquawkFormViewModel.kt` (~96–330)                              | near line-for-line copy of the tasks version                       |
| `feature/logs/update/.../viewmodel/MaintenanceLogFormViewModel.kt` + `MaintenanceLogFormUiState.kt` | same behavior, third implementation, state held inside the UiState |

Each copy owns: pending-attachment state, `filesAtLimit`, `showAttachmentPicker` /
`hideAttachmentPicker`, add-local-file (with per-file limit check and error surfacing),
add-link, `removeAttachment` (with Saved → PendingDelete transition), and
`resolveAttachments` on save. `MAX_FILE_ATTACHMENTS = 3` is declared in all three modules.

**Plan:**

1. Create `AttachmentFormController` in `feature/attachment/datamanager` (all three update
   modules already depend on it — no new edges). It owns:
    - `pendingAttachments: StateFlow<List<PendingAttachment>>`
    - `showPicker: StateFlow<Boolean>` (or keep picker visibility in each ViewModel if it's
      entangled with other form state — decide during implementation)
    - `filesAtLimit`, `addLocalFiles(...)`, `addLink(...)`, `remove(id)`,
      `seedExisting(attachments)`, `resolve(entityId): List<Attachment>`
    - the single `MAX_FILE_ATTACHMENTS` constant
2. Convert `TaskViewModel` and `SquawkFormViewModel` first (they're structurally identical —
   mechanical change). Each ViewModel instantiates the controller with its own scope and
   delegates.
3. Convert `MaintenanceLogFormViewModel` second; its UiState-embedded variant needs a small
   adapter (`combine` the controller flow into the UiState) — do it as its own commit.
4. Migrate the existing ViewModel tests (`SquawkFormViewModelTest` etc.) and write direct
   controller tests; delete the per-ViewModel attachment test duplication where covered.

**Risk:** medium — touches three save paths. Mitigate by converting one feature per commit and
running that feature's module tests each time (`:feature:tasks:update:testDebugUnitTest`, …).

**Outcome (2026-07-06):** `AttachmentFormController` landed in `feature/attachment/datamanager`
with direct unit tests (`AttachmentFormControllerTest`); all three ViewModels now delegate.
Implementation notes: picker visibility moved into the controller for tasks/squawk but stays in
`MaintenanceLogFormUiState` for logs (the ViewModel mirrors the controller flow into the UiState,
seeding synchronously before the initial dirty-check snapshot); the unused `resolveAttachments(id)`
parameter was dropped (`resolveForSave()`); errors surface via a UI-free `AddFileError` sealed
type — tasks/squawk keep silently skipping oversized files, logs keeps surfacing `file_too_large`;
the controller rethrows `CancellationException`, fixing the logs copy's latent swallow.

### 2.2 Small code dedups

- **`StatusBadge`** — private composable duplicated in
  `feature/tasks/viewing/.../TaskDetailSheet.kt:235` (takes `DueMetadata`) and
  `feature/squawk/viewing/.../SquawkCard.kt:112` (takes `SquawkStatus`). Add one generic badge
  primitive in `core:ui` (`StatusBadge(text, containerColor, contentColor)`); keep the
  domain-enum → color/text mapping in each feature.
  **Resolved 2026-07-06 with no code change:** the generic primitive already exists —
  `core:ui`'s `StatusChip(label, tier)` owns all rendering, and both private `StatusBadge`
  functions are pure domain→(label, tier) mapping, which this plan keeps feature-local anyway.
- **`TestClock`** — duplicated test fixture in `core/storage` and
  `feature/attachment/datamanager` tests. Options: (a) accept the duplication (it's tiny), or
  (b) add a small `core:storage` test-fixtures source set and share it. Prefer (a) unless a
  third copy appears; note it here either way so the next duplicator finds this entry.
- **`NavigateToEditLog(aircraftId, logId)`** — identical nav event in
  `feature/aircraft/dashboard/.../AircraftOverviewEvent.kt` and
  `feature/logs/viewing/.../MaintenanceLogListViewModel.kt`. Leave as-is for now (nav events
  are per-screen contracts; sharing them couples the two ViewModels), but fold into the
  Phase 3 nav work if a shared nav-event type emerges there.

### 2.3 String resource consolidation

Per the "reuse before adding" rule in AGENTS.md. Verified duplicates and their resolutions:

| Value                 | Copies                                                                                                     | Resolution                                                                                                                                               |
|-----------------------|------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| "Back"                | `core/sharedassets` `back`, `feature/login` `email_back`                                                   | login uses `core/sharedassets` `back`                                                                                                                    |
| "%1$s KB" / "%1$s MB" | `attachment/sharedassets` `file_size_kb/mb`, `export/sharedassets` `export_size_kb/mb`                     | move to `core/sharedassets`; both features read it from there. Check whether the KB/MB *formatting logic* is also duplicated in code and unify alongside |
| "No open squawks"     | `aircraft/dashboard` `overview_no_squawks_title`, `squawk/sharedassets` `no_open_squawks`                  | dashboard reads `squawk/sharedassets` (it already depends on it)                                                                                         |
| "Settings"            | `core/sharedassets` `settings`, `feature/settings` `settings_title`                                        | settings screen reads the core string                                                                                                                    |
| "Logs" / "Squawks"    | `core/sharedassets` shell-tab strings vs `aircraft/dashboard` `tab_logs` / `squawk/sharedassets` `squawks` | **keep separate** — tab labels and shell labels can legitimately diverge; do not merge                                                                   |

Leave context-specific near-dupes (e.g. `%1$d / %2$d` progress counters) alone.

**Outcome (2026-07-06):** all four merges landed as tabled. The size strings
(`file_size_zero_kb` / `file_size_bytes` / `file_size_kb` / `file_size_mb`) now live in
`core/sharedassets`, and the KB/MB *code* duplication — export's identical `readableBytes()`
in two screens plus attachment's `formatFileSize()` — is unified as one
`Long.formatFileSize()` in `core:ui` (`core/ui/common/compose/FormatFileSize.kt`). Unified
semantics: decimal units, "0 KB" for empty, bytes below 1 KB, KB rounded up, MB to one
decimal — this switched attachment rows from binary (1024-based) to decimal units.
`feature/settings` gained a `core:sharedassets` dependency to read the core `settings` string.

---

## Phase 3 — Unify the host navigation graph

**Problem:** `webApp/src/jsMain/.../WebApp.kt` (~130–384) is a near line-for-line copy of
`composeApp/src/commonMain/.../AppEntry.kt` (~123–400): all eight form dialogs, the settings
detail routes, the nested sidebar-settings NavHost + `SettingsSection`, the shell wiring, the
auth-state redirect, and the three analytics page-view feeders. They have **already drifted**:
composeApp gates stress-test routes and the FeatureLab debug entry on
`AppCapability.isStressTestSupported`; webApp registers both unconditionally. This is the exact
failure mode that `core/di/CommonAppModules.kt` was created to stop for the DI list (its doc
comment records the incident).

**Plan:**

1. Create a shared app-graph module — either extend `core:nav` (it already owns `Screen`) or
   add a small aggregator (working name `feature:shell`). It must be able to depend on every
   feature UI module, so a dedicated module (like `core:di` does for DI) is the cleaner shape;
   putting it in `core:nav` would invert `core → feature` layering. **Recommendation: new
   `feature:shell` module**, mirroring `core:di`'s role but for composables/nav.
2. Move into it, verbatim where possible:
    - `formDialogs(...)` (8 dialog routes)
    - `secondaryRoutes(...)` / `settingsDetailRoutes(...)` (reconcile the two names — they are
      the same function; adopt the composeApp behavior of gating stress-test registration on
      `isStressTestSupported`, which fixes the live web drift)
    - `SettingsSection(...)` + `SETTINGS_ROOT_ROUTE`
    - the shell-route composable body (AdaptiveShell wiring + page-view feeder 2)
    - the auth-state-redirect `LaunchedEffect` and the root `trackScreenViews` feeder, as small
      reusable helpers
3. Reduce each host to its genuine delta:
    - **composeApp:** DB-integrity gate, theme wrapper, auth graph.
    - **webApp:** browser-history binding, resource warm-up workaround, `WebLoginLandingScreen`
      swap (already parameterized via `AuthFlow(loginContent = …)`), browser gutter color,
      `BrowserTitleAnalytics` wrapper.
4. Behavior decision to make explicit during review: webApp currently ships stress-test routes
   unconditionally. If that was *intentional* (web always dev-tools-on), express it through
   `AppCapability.isStressTestSupported` returning true on JS — not by bypassing the gate.
5. Also move `AdaptiveShellViewModel` out of `feature/fleet/viewing` into the new shell module
   (it drives all shell sections, not just fleet). This removes the reason `core:di`/hosts need
   `fleet:viewing` directly.

**Risk:** highest in the plan — touches every navigation path on three platforms. Mitigations:
land as a stack of small PRs (helpers first, hosts converted one at a time); manually smoke-test
Android + web (all form dialogs, settings sub-pages in both compact and sidebar tiers, login
round-trip); keep the iOS dogfood scheme build in the loop since iOS shares composeApp.

---

## Phase 4 — Fix layering and ownership violations

### 4.1 `feature:technician:datamanager` must stop touching Firestore and the sync engine

`TechnicianManagerImpl` violates the repo's core rule ("feature managers never touch
Firestore — the sync engine is the only Firestore client"):

- imports `dev.gitlive.firebase.firestore.FirebaseFirestore` and reads
  `UserInfo` directly (`readSelfIdFromFirestore`, TechnicianManagerImpl.kt:121);
- imports `SyncPreferences` from `feature.sync.data`, making technician:datamanager the only
  datamanager that depends on the sync feature — and since `login`, `fleet:viewing`,
  `settings`, `stresstest`, and `export:datamanager` all depend on technician:datamanager, the
  sync engine is transitively wired into nearly everything.

**Plan:**

1. Move the "self technician id" cloud-fallback read into the sync layer where remote reads
   belong: hydration already pulls `UserInfo` on sign-in; ensure the self-id lands in the
   local store as part of hydration, then `TechnicianManagerImpl` reads **local only**.
2. If the manager genuinely needs a "cloud sync enabled" signal, define a narrow interface in
   `core:storage` (next to `sync_config`, which it already owns) implemented by
   `SyncPreferences`, and inject that — datamanagers may depend on `core:storage`, not on
   `feature:sync:data`.
3. Remove `implementation(project(":feature:sync:data"))` and the Firestore dependency from
   `feature/technician/datamanager/build.gradle.kts`; update `TechnicianManagerImplTest`.

**Risk:** medium — sign-in/onboarding self-record flow. Test: fresh-install sign-in (new and
existing cloud accounts), anonymous flow, plus existing unit tests.

### 4.2 Move `LocalBlobStore` to `core:storage`; drop sync's api-export of a feature module

`feature:sync:data` api-exports `feature:attachment:datamanager` — infrastructure depending on
a feature — solely because `LocalBlobStore` lives there while its `blob_object` table already
lives in `core:storage`.

- Move `LocalBlobStore` (interface + SQLDelight impl) into `core:storage` beside its schema.
- `feature/attachment/datamanager` and `feature/sync/data` both consume it from core.
- Replace `api(project(":feature:attachment:datamanager"))` in sync with whatever narrow pieces
  it still needs; if sync also uses attachment *domain* types, move those specific types to
  `core:storage`'s blob namespace (blob `RemoteState` is already there per AGENTS.md).

### 4.3 Relocate aircraft editing out of `feature/logs`

`feature/logs/update/aircraft/` (`EditAircraftScreen`, `EditAircraftViewModel`,
`EditAircraftUiState`, `EngineSection`, `AirframeSection`) is aircraft CRUD UI living in the
maintenance-logs feature. Aircraft data ownership is `fleet:datamanager` (`FleetManager`).

- Create `feature/aircraft/update` (the `feature/aircraft` umbrella already exists with
  `dashboard/`) and move the five files + their strings (currently in logs resources) there.
- Update route registration (one call site per host today; after Phase 3, one call site in the
  shared graph) and `core:di` imports.
- This is a mechanical move; do it **after** Phase 3 so only the shared graph needs rewiring.

### 4.4 Reconcile the `sharedassets` rule with reality

The written rule ("`sharedassets` → Compose resources only") is contradicted by three modules
that ship code and UI deps: `technician/sharedassets` (`CertificateInputFields.kt`, 185 lines),
`logs/sharedassets` (`LogPickerSheet.kt`, `MaintenanceDisplayExtensions.kt`),
`squawk/sharedassets` (`SquawkDismissReasonLabel.kt`).

**Decision required (pick one, then enforce):**

- **Option A — move the code out** (restores the strict rule):
  `CertificateInputFields` → `technician:manage`; `LogPickerSheet` +
  `MaintenanceDisplayExtensions` → `logs:viewing`; `SquawkDismissReasonLabel` →
  `squawk:viewing`. Verify no `sharedassets`-only consumer needs them (that's what created
  this arrangement — LogPickerSheet is used by `squawk:update`, which already depends on
  `logs:sharedassets` but *not* on `logs:viewing`; Option A therefore adds a
  `squawk:update → logs:viewing` edge).
- **Option B — amend the rule**: "sharedassets may contain leaf presentation helpers shared
  across features, and may depend on `core:ui` / `core:model`, but never on another feature."
  Zero code motion; update AGENTS.md.

**Recommendation: Option B.** The current placements exist precisely to avoid cross-feature
UI-module edges (the thing the hard rule most cares about); the code in question is small,
leaf, and dependency-clean. Amend the doc, and add the constraint that `sharedassets` must
never depend on a feature module.

### 4.5 Route `SyncEngine` access in settings through a manager (optional, low priority)

`feature/settings` injects `SyncEngine` directly (`SettingsModule`,
`AccountUpgradeViewModel`) — UI driving infrastructure without a manager in between. Same
coupling class as 4.1 but lower stakes since settings is inherently sync-adjacent
(`sync:settings` does the same). Fold into any future sync-API refactor rather than doing it
standalone; recorded here so it isn't re-discovered.

### 4.6 FeatureLab screen ownership (docs-only)

`FeatureLabScreen` lives in `feature/settings/featurelab/` while `feature/featurelab` has only
a datamanager. Moving it would create a new UI module for one screen — not worth it. Instead,
document the exception in AGENTS.md's "Non-canonical exceptions" list (Phase 5).

---

## Phase 5 — Documentation refresh (AGENTS.md / CLAUDE.md)

Agents are told these files are authoritative; today they materially misdescribe the codebase.
Do this phase **last** so it captures the post-cleanup state, but if earlier phases stall, ship
the corrections below independently — they are wrong today regardless:

- **Module tree**: add `core:nav`, `core:sharedassets`, `core:analytics`, `core:di`,
  `core:ui:theme` / `core:ui:adaptive` / `core:ui:widget:avataricon`, and `feature:login`;
  remove `core/attachments`, `feature/fleet/model`, `feature/userprofile` root +
  `database` (after Phase 1).
- **DI aggregation**: central list is `core/di/CommonAppModules.kt`; `initKoin.kt` is a thin
  wrapper that adds `AppCapability` + stress-test modules. Fix both AGENTS.md ("All modules are
  aggregated in … initKoin.kt") and CLAUDE.md.
- **Stale module descriptions**: `userprofile/database` ("UserProfileManager (Firestore)" — was
  empty), `fleet/model` ("Aircraft-related domain types" — was empty), `LogPickerSheet` listed
  under `squawk/update` (actually `logs/sharedassets`).
- **Dependency rules**: encode the Phase 4.4 decision; document that `viewing` may host the
  list ViewModel when the feature has no `update` sibling (fleet) — and after cleanup, note
  `logs:viewing`'s ViewModel or move it per the canonical pattern.
- **Non-canonical exceptions**: add FeatureLab screen hosted in `feature/settings` (4.6).
- Add pointers to this plan from AGENTS.md if the cleanup is still in flight.

---

## Execution tracking

| #  | Item                                                                  | Phase | Risk | Status       |
|----|-----------------------------------------------------------------------|-------|------|--------------|
| 1  | Delete `core/attachments`                                             | 1.1   | none | ☑ 2026-07-06 |
| 2  | Delete `feature/fleet/model` + dep edges                              | 1.2   | none | ☑ 2026-07-06 |
| 3  | Delete `feature/userprofile` root + `database`; prune composeApp deps | 1.3   | none | ☑ 2026-07-06 |
| 4  | Drop unused `export:update → sync:data`                               | 1.4   | none | ☑ 2026-07-06 |
| 5  | `AttachmentFormController` — tasks + squawk                           | 2.1   | med  | ☑ 2026-07-06 |
| 6  | `AttachmentFormController` — logs form                                | 2.1   | med  | ☑ 2026-07-06 |
| 7  | `StatusBadge` primitive in `core:ui`                                  | 2.2   | low  | ☑ 2026-07-06 |
| 8  | String consolidation pass                                             | 2.3   | low  | ☑ 2026-07-06 |
| 9  | Shared shell/nav module; de-dup `AppEntry` / `WebApp`                 | 3     | high | ☐            |
| 10 | Move `AdaptiveShellViewModel` out of `fleet:viewing`                  | 3     | med  | ☐            |
| 11 | Technician manager: local-only reads, no sync dep                     | 4.1   | med  | ☐            |
| 12 | `LocalBlobStore` → `core:storage`; fix sync api-export                | 4.2   | med  | ☐            |
| 13 | `feature/logs/update/aircraft/` → `feature/aircraft/update`           | 4.3   | low  | ☐            |
| 14 | `sharedassets` rule decision (recommend Option B)                     | 4.4   | low  | ☐            |
| 15 | AGENTS.md / CLAUDE.md refresh                                         | 5     | none | ☐            |

Suggested landing order: 1–4 in one PR; 5–8 as one PR per item; 9–10 as a reviewed PR stack;
11–13 individually; 14–15 alongside whichever phase settles them.
