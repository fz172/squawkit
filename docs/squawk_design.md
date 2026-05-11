# Design Doc: User Squawking

**PRD:** `docs/user_squawking_prd.md`
**Status:** Draft
**Last updated:** 2026-05-11

---

## 1. Overview

Squawks are ad-hoc defect reports tied to an aircraft. A squawk is `Open` until a log entry is linked to it, at which point it becomes `Addressed`. AOG-priority squawks surface prominently on the Aircraft Overview screen above the existing `CriticalAlertsSection`. The feature uses the canonical module layout with a new `feature/squawk/` tree and minimal changes to existing modules.

---

## 2. Proto Definition

### 2.1 New `squawk.proto` (`core/model/src/commonMain/proto/aircraft/squawk.proto`)

```proto
syntax = "proto3";

import "google/protobuf/timestamp.proto";
import "aircraft/attachment.proto";
import "aircraft/component_type.proto";

option java_package = "dev.fanfly.wingslog.aircraft";
option java_multiple_files = true;

enum SquawkPriority {
  SQUAWK_PRIORITY_UNKNOWN = 0;
  SQUAWK_PRIORITY_LOW     = 1;
  SQUAWK_PRIORITY_MEDIUM  = 2;
  SQUAWK_PRIORITY_HIGH    = 3;
  SQUAWK_PRIORITY_AOG     = 4;
}

message Squawk {
  string id                    = 1;
  string title                 = 2;  // Required
  string description           = 3;  // Optional
  SquawkPriority priority      = 4;
  google.protobuf.Timestamp created_at = 5;
  ComponentType component_type = 6;  // Optional
  string component_serial      = 7;  // Optional; mirrors log serial field
  repeated Attachment attachments = 8;
  // Populated when addressed; empty means Open.
  string addressed_by_log_id   = 9;
}
```

`addressed_by_log_id` being empty is the canonical "Open" state. No separate status enum is stored — `Open` vs `Addressed` is derived at read time. This avoids the risk of the status field getting out of sync with the actual log linkage.

### 2.2 Update `maintenance_log.proto`

Add one field (field 14 is next available):

```proto
// Squawks addressed by this log entry.
repeated string squawk_ids = 14;
```

---

## 3. Firestore & Storage

### 3.1 Collection path

Squawks live under the aircraft scope, matching tasks and logs:

```
users/{uid}/aircraft/{aircraftId}/squawk/{squawkId}
  squawk_info_blob: bytes   ← Squawk proto
```

### 3.2 CollectionKind

Add to `core/storage/src/commonMain/kotlin/.../CollectionKind.kt`:

```kotlin
data object Squawk : CollectionKind {
  override val wireName = "squawk"
  override val schemaName = "aircraft.Squawk"
}
```

Also add `Squawk` to `CollectionKind.ALL`. The `CollectionKindCoverageTest` will enforce this.

### 3.3 Sync

No special sync machinery needed. `SyncEngine` already handles all `CollectionKind` entries generically through `HydrationRunner` and `PullListener`. Adding `CollectionKind.Squawk` to `ALL` is sufficient for it to participate in real-time sync.

---

## 4. Module Layout

```
feature/squawk/
  model/           ← SquawkWithStatus, SquawkStatus enum (Open/Addressed)
  datamanager/     ← SquawkManager interface + impl + Koin module
  sharedassets/    ← strings.xml, priority color mapping util
  viewing/         ← SquawkCard, SquawkDetailSheet, AogAlertSection, SquawkPickerSheet
  update/          ← AddSquawkScreen, EditSquawkScreen, ViewModels, Koin module
```

### 4.1 `model/`

```kotlin
enum class SquawkStatus { OPEN, ADDRESSED }

data class SquawkWithStatus(
  val squawk: Squawk,
  val status: SquawkStatus,
)
```

`SquawkStatus` is derived: `if (squawk.addressed_by_log_id.isEmpty()) OPEN else ADDRESSED`.

### 4.2 `datamanager/`

**Interface (`SquawkManager`):**

```kotlin
interface SquawkManager {
  fun observeSquawks(aircraftId: String): Flow<List<Squawk>>
  suspend fun addSquawk(aircraftId: String, squawk: Squawk): Result<Boolean>
  suspend fun updateSquawk(aircraftId: String, squawk: Squawk): Result<Boolean>
  suspend fun deleteSquawk(aircraftId: String, squawkId: String): Result<Boolean>
  /** Sets addressed_by_log_id on each squawk in [squawkIds]. Called by the log form on save. */
  suspend fun markAddressed(
    aircraftId: String,
    squawkIds: List<String>,
    logId: String,
  ): Result<Unit>
}
```

**Implementation (`SquawkManagerImpl`):** Uses `EntityStore<Squawk>` with `CollectionKind.Squawk`, identical pattern to `TaskDataManagerImpl`. `markAddressed` fetches each squawk, sets `addressed_by_log_id = logId`, and calls `store.put`.

**Koin module:** `SquawkModule.kt` in `datamanager/`. Register in `initKoin.kt`.

### 4.3 `sharedassets/`

`strings.xml` for all user-visible squawk strings.

Priority color utility (used by both `viewing/` and `update/`):

```kotlin
// Returns a Material3 color role for a SquawkPriority chip.
fun SquawkPriority.chipColor(scheme: ColorScheme): Color = when (this) {
  SquawkPriority.SQUAWK_PRIORITY_AOG    -> scheme.error
  SquawkPriority.SQUAWK_PRIORITY_HIGH   -> scheme.errorContainer
  SquawkPriority.SQUAWK_PRIORITY_MEDIUM -> scheme.tertiary
  SquawkPriority.SQUAWK_PRIORITY_LOW    -> scheme.surfaceVariant
  else                                  -> scheme.surfaceVariant
}
```

### 4.4 `viewing/`

**`SquawkCard`** — compact row for the squawk list: priority chip, title, `Addressed` badge when applicable, "View Log" chip.

**`SquawkDetailSheet`** — modal bottom sheet: title, description, component, priority chip, attachments list, "View Resolving Log" action if addressed.

**`AogAlertSection`** — card displayed on the Aircraft Overview tab when one or more AOG squawks are open. Mirrors `CriticalAlertsSection` visually but uses `MaterialTheme.colorScheme.error` tint and a `FlightLandOutlined`-style icon. Each row shows the squawk title and a "View" action. Collapses (not rendered) when `aogSquawks` is empty.

**`SquawkPickerSheet`** — modal bottom sheet presented from the log form. Shows only `Open` squawks for the current aircraft. Searchable by title. Returns a `Set<String>` of selected squawk IDs to the caller.

### 4.5 `update/`

**Screens:**
- `AddSquawkScreen` — full-screen form for creating a squawk.
- `EditSquawkScreen` — same form pre-populated for editing. Addressed squawks are read-only (title, description, component grayed out; attachments still editable).

**Form fields:**
1. Title (required, single-line)
2. Priority picker — segmented control or dropdown: Low / Medium / High / AOG
3. Description (optional, multi-line)
4. Component section — reuse `ComponentSection` from `feature/logs/update` if extractable; otherwise a simple `ComponentType` dropdown + serial auto-fill
5. Attachments — same `AttachmentSection` already used by logs

**ViewModels:**
- `AddSquawkViewModel` / `EditSquawkViewModel` — follow the same `UiState` + `Channel<Event>` pattern as `TaskViewModel`. Saving delegates to `SquawkManager`.

---

## 5. `core/ui` Refactor — `DualSegmentedFilter`

The Tasks tab already has a two-option segmented filter ("Due (N) / History (N)") implemented inline in `ComplianceSection`. The Squawk tab needs an identical control ("Open (N) / Addressed (N)"). Rather than duplicate the `SingleChoiceSegmentedButtonRow` boilerplate, extract a shared primitive to `core/ui`.

**New composable: `DualSegmentedFilter`**
(`core/ui/src/commonMain/kotlin/dev/fanfly/wingslog/core/ui/component/DualSegmentedFilter.kt`)

```kotlin
@Composable
fun DualSegmentedFilter(
  option1: String,   // already-formatted label, e.g. "Due (3)"
  option2: String,   // e.g. "History (12)"
  selectedIndex: Int, // 0 or 1
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
    SegmentedButton(
      selected = selectedIndex == 0,
      onClick = { onSelect(0) },
      shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
    ) { Text(option1) }
    SegmentedButton(
      selected = selectedIndex == 1,
      onClick = { onSelect(1) },
      shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
    ) { Text(option2) }
  }
}
```

Labels are passed pre-formatted so `DualSegmentedFilter` stays free of string resources and feature knowledge.

**Refactor `ComplianceSection`:** Replace the inline `SingleChoiceSegmentedButtonRow` block with `DualSegmentedFilter`, formatting the labels at the call site using the existing `due_with_count` / `history_with_count` string resources. No behaviour change.

---

## 6. `SquawkTab` Composable (`feature/aircraft/dashboard`)

The Squawks tab lives in `feature/aircraft/dashboard` alongside the other tab composables, since it is driven by the same `AircraftOverviewViewModel` and shares the same aircraft scope.

**`compose/tabs/SquawkTab.kt`** — stateless; receives `squawks: List<SquawkWithStatus>` and callbacks. Layout mirrors `MaintenanceTasksTab`:

```
title "Squawks"
DualSegmentedFilter("Open (N)" / "Addressed (N)")
[list or empty state]
```

**Filter state:** `var showAddressed by rememberSaveable { mutableStateOf(false) }` — local to the composable, reset on recomposition, survives tab switches via `rememberSaveable`.

**List content:** When a segment is selected, display the matching subset. Each row uses `SquawkCard` from `feature/squawk/viewing`. Open squawks are sorted by priority descending (AOG → High → Medium → Low), then by `created_at` ascending. Addressed squawks are sorted by `created_at` descending (most recently resolved first).

**Empty states:**

| Filter | Condition | UI |
|--------|-----------|-----|
| Open (selected) | No open squawks | Bordered card: "No open squawks" body text + "New Squawk" `OutlinedButton` (mirrors the Tasks empty state card) |
| Addressed (selected) | No addressed squawks | Inline text: "No addressed squawks yet" in `onSurfaceVariant` (mirrors `no_complied_yet` pattern) |

**FAB context-sensitivity:** The FAB in `AircraftOverviewContent` becomes context-sensitive based on the current pager page:

```kotlin
val fabConfig = when (AircraftTab.entries[pagerState.currentPage]) {
  AircraftTab.SQUAWKS -> FabConfig(label = addSquawk, onClick = { onAction(AddSquawkClick(..)) })
  AircraftTab.TASKS   -> FabConfig(label = addTask,   onClick = { onAction(AddTaskClick(..)) })
  AircraftTab.LOGS    -> FabConfig(label = addLog,    onClick = { onAction(AddLogClick(..)) })
  AircraftTab.OVERVIEW -> null  // no FAB on Overview tab
}
```

**`AircraftDashboardTabRow`** gains a fourth tab entry between Overview and Tasks:

```
Overview (0) → Squawks (1) → Tasks (2) → Logs (3)
```

Tab index constants move to an `AircraftTab` enum to avoid scattered magic numbers:

```kotlin
enum class AircraftTab { OVERVIEW, SQUAWKS, TASKS, LOGS }
```

The `HorizontalPager` page count updates to 4; `SquawkTab` is inserted at page index 1.

**`AogAlertSection` "View Squawks" action** calls `onNavigateToSquawksTab()` — a callback that animates the pager to `AircraftTab.SQUAWKS.ordinal` — rather than navigating to a separate screen.

---

## 7. Changes to Existing Modules

### 7.1 `feature/logs/update` — Log Form Integration

**`MaintenanceLogFormUiState`:** Add:
```kotlin
val openSquawks: List<Squawk> = emptyList()
val selectedSquawkIds: Set<String> = emptySet()
val squawkPickerVisible: Boolean = false
```

**`MaintenanceLogFormViewModel`:**
- Inject `SquawkManager`.
- In `init`, observe `squawkManager.observeSquawks(aircraftId)`, filter to `Open`, expose as `openSquawks`.
- On save: after writing the log, call `squawkManager.markAddressed(aircraftId, selectedSquawkIds.toList(), savedLogId)`.
- Actions: `ToggleSquawkPickerVisible`, `SquawkSelectionChanged(id: String, selected: Boolean)`.

**`MaintenanceLogFormScreen`:** Insert `SquawkSection` composable between the Technician section and the Attachments section. `SquawkSection` shows:
- Empty state: "Address squawk(s)" tap target.
- Non-empty: list of selected squawk titles with remove affordance.
- Tapping opens `SquawkPickerSheet`.

### 7.2 `feature/aircraft/dashboard`

**`AircraftOverviewUiState.Success`:** Add:
```kotlin
val squawks: List<SquawkWithStatus> = emptyList()
val aogSquawks: List<Squawk> = emptyList()  // derived: squawks filtered to AOG + Open
```

**`AircraftOverviewViewModel`:**
- Inject `SquawkManager`.
- The current `combine` already has 5 flows (at the arity limit). Wrap squawk observation in a nested combine, the same pattern already used for `blobStatesFlow()`:

```kotlin
combine(
  fleetManager.loadAircraft(aircraftId),
  logManager.observeLogs(aircraftId),
  taskDataManager.observeTasks(aircraftId),
  logManager.observeMaintenanceOverview(aircraftId),
  combine(
    blobStatesFlow(),
    attachmentOpener.downloadingIds,
    squawkManager.observeSquawks(aircraftId),   // ← added here
  ) { blobStates, downloadingIds, squawks -> Triple(blobStates, downloadingIds, squawks) }
) { aircraft, logs, taskCards, overview, (blobStates, downloadingIds, squawkList) -> ... }
```

- Derive `squawksWithStatus` and `aogSquawks` inside the lambda; populate both fields on `Success`.

**`AircraftOverviewAction`:** Add `AddSquawkClick(aircraftId: String)`, `EditSquawkClick(aircraftId: String, squawkId: String)`.

**`AircraftOverviewEvent`:** Add `NavigateToAddSquawk(aircraftId: String)`, `NavigateToEditSquawk(aircraftId: String, squawkId: String)`.

**`AircraftOverviewContent` (Overview tab):** Render `AogAlertSection(aogSquawks, onViewSquawksTab)` above `CriticalAlertsSection` when `aogSquawks` is non-empty. `onViewSquawksTab` animates the pager to index 1.

### 7.3 `feature/fleet` — Fleet Dashboard AOG Badge

**`FleetDashboardViewModel`:** Inject `SquawkManager`. For each aircraft in the fleet, observe squawks and derive `hasAog: Boolean`. Surface as a flag on the per-aircraft card state.

**`AircraftDashboardCard`:** Show a small `AOG` chip (using `error` color) when `hasAog` is true, positioned alongside the existing due-status indicator.

---

## 8. Navigation

Add/edit squawk are full-screen routes in the app navigation graph (`composeApp/`):

```
Screen.AddSquawk(aircraftId)            → AddSquawkScreen
Screen.EditSquawk(aircraftId, squawkId) → EditSquawkScreen
```

There is no `SquawkListScreen` route — the list lives entirely within the Squawks tab of `AircraftOverviewContent`, driven by `AircraftOverviewViewModel`.

**Entry points:**
- Squawks tab FAB → `Screen.AddSquawk`
- `SquawkCard` tap → `Screen.EditSquawk`
- `AogAlertSection` "View Squawks" → animates pager to Squawks tab (pager state in `AircraftOverviewContent`, no navigation event)
- `SquawkDetailSheet` "View Log" chip → `Screen.EditLog(aircraftId, logId)`

---

## 9. Cascade Behavior on Deletion (Deferred)

Per PRD §5.2, cascade behavior when a resolving log is deleted is out of scope for V1. The `addressed_by_log_id` field will remain set even if the log is deleted. A future release will add a cleanup pass via `SquawkManager.reconcileAddressedState()`.

---

## 10. Addressing Logic — Atomicity

`markAddressed` issues N individual `store.put` calls (one per squawk). If the app crashes mid-flight, some squawks may be addressed and others not. This is acceptable for V1 — the user can re-save the log or manually re-link. A future design could batch-write all squawk updates as a single Firestore transaction, but the current `EntityStore` abstraction does not expose transactions.

---

## 11. Implementation Order

1. `core/ui` — `DualSegmentedFilter` composable
2. `feature/aircraft/dashboard` — refactor `ComplianceSection` to use `DualSegmentedFilter`
3. `squawk.proto` + Wire codegen + `maintenance_log.proto` field 14
4. `CollectionKind.Squawk` + `CollectionKindCoverageTest` update
5. `feature/squawk/model/` + `feature/squawk/datamanager/` + Koin wiring
6. `feature/squawk/sharedassets/` — strings + priority color util
7. `feature/squawk/viewing/` — `SquawkCard`, `SquawkDetailSheet`, `AogAlertSection`, `SquawkPickerSheet`
8. `feature/squawk/update/` — Add/Edit screens + ViewModels
9. `AircraftTab` enum + `AircraftDashboardTabRow` (4 tabs) + `SquawkTab` (with `DualSegmentedFilter`, empty states, sorted lists) + context-sensitive FAB in `AircraftOverviewContent`
10. Navigation routes (`Screen.AddSquawk`, `Screen.EditSquawk`)
11. `feature/aircraft/dashboard` — ViewModel squawk observation + `AogAlertSection` on Overview tab
12. `feature/logs/update` — squawk section in log form + addressing on save
13. `feature/fleet` — AOG badge on fleet card
