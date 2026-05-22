# Aircraft Overview: 3-Tab Refactor Plan

**Status: IMPLEMENTED — now 4 tabs.** Code lives in `feature/aircraft/dashboard/`. This doc describes the
original 3-tab split (Overview / Maintenance Tasks / Logs). A **Squawks** tab was later inserted at index 1, so
the shipped order is **Overview → Squawks → Tasks → Logs** (see `squawk_design.md` §6). Tab indices are now an
`AircraftTab` enum and the FAB is context-sensitive to the active tab.

## Goal

Split `AircraftOverviewContent` into three tabs — **Overview**, **Maintenance Tasks**, and **Logs** — to reduce vertical scroll depth and give each concern its own focused screen area.

---

## Tab Definitions

### Tab 1 — Overview

Current content kept as-is:

- Hero header (make/model + tail number in `FlowRow`)
- `ConfigurationCard` (aircraft data accordion)
- `CriticalAlertsSection` (overdue inspections, shown only when present)
- `LogOnboardingCard` **or** `LogStatsSection` (conditional on `logStats.total`)

The floating `LogDetailsBottomBar` is **removed** from this tab. With the Logs tab in place, the "Log Details" navigation destination no longer needs a dedicated button. An "Add Log" FAB or action can live on the Logs tab instead (see below).

### Tab 2 — Maintenance Tasks

Extracted from the current `ComplianceSection` block:

- The due/history segmented toggle
- Active inspections list (`InspectionCardItem`)
- Complied inspections list
- "Add Inspection" button
- Empty states (no active, no complied)

The `showComplied` toggle state moves here (stays `rememberSaveable` so it survives tab switches).

### Tab 3 — Logs

Embeds `MaintenanceLogListScreen` content **without its own `TopAppBar`**. The screen already has:

- Search bar
- Component filter (All / Airframe / Engine / Propeller)
- Result count badge
- `LazyColumn` of `MaintenanceLogCard`
- `MaintenanceLogDetailSheet` bottom sheet
- Empty/error/loading states

Since `MaintenanceLogListScreen` currently owns its own `Scaffold` + `TopAppBar` + FAB, the simplest integration strategy is to **extract its body into a stateless `MaintenanceLogListContent` composable** (analogous to the existing `AircraftOverviewContent` pattern) and call that from the Logs tab. The existing `MaintenanceLogListScreen` wrapper keeps its own `Scaffold` for the standalone navigation route — no routes change.

The Logs tab hosts an **"Add Log" FAB** (using the existing `onAddLogClick` action) so the action is still reachable without the bottom bar.

---

## Overlay State (Dialogs / Bottom Sheets)

`InspectionDetailSheet` and `DeleteInspectionConfirmDialog` are driven by `state.selectedInspection` and `state.deletingInspectionId`. These are Scaffold-level overlays — they must remain **outside** the tab pager, at the `Scaffold` content level, so they can appear regardless of which tab is active. No change to their logic.

---

## Component Changes

### New: `AircraftOverviewTabRow` (`compose/tabs/AircraftOverviewTabRow.kt`)

A simple `TabRow` composable taking `selectedTabIndex` and `onTabSelected`. Three tabs: Overview, Maintenance Tasks, Logs. Strings go in `sharedassets/strings.xml`.

### New: `OverviewTab` (`compose/tabs/OverviewTab.kt`)

Extracts the current Overview content (header + ConfigurationCard + CriticalAlertsSection + LogStats/Onboarding). Stateless — takes the same slice of `AircraftOverviewUiState.Success` it needs.

### New: `MaintenanceTasksTab` (`compose/tabs/MaintenanceTasksTab.kt`)

Wraps `ComplianceSection` with its `showComplied` local state. Receives `activeInspections`, `compliedInspections`, and the relevant `onAction` lambdas.

### New: `LogsTab` (`compose/tabs/LogsTab.kt`)

Calls the new `MaintenanceLogListContent` composable (see below) plus an "Add Log" FAB. Takes `aircraftId` and `onAddLogClick`.

### Refactor: `MaintenanceLogListScreen`

Extract inner body into `MaintenanceLogListContent(state, onAction, modifier)` — a stateless composable with no `Scaffold`. The existing `MaintenanceLogListScreen` calls `MaintenanceLogListContent` inside its own `Scaffold`. The `LogsTab` calls `MaintenanceLogListContent` directly. ViewModel wiring (`koinViewModel`) stays in `MaintenanceLogListScreen`; the Logs tab receives the same ViewModel reference passed down or obtained via `koinViewModel()` with the scoped aircraft ID.

### Changed: `AircraftOverviewContent`

- Replace the scrollable `Column` body + floating `LogDetailsBottomBar` with a `Column` containing:
  1. `AircraftOverviewTabRow`
  2. `HorizontalPager` (or manual tab switching with `AnimatedContent`) keyed to selected tab index
- Remove `scrollBehavior` from `TopAppBar` — each tab manages its own scroll independently.
- Remove `LogDetailsBottomBar`.
- Keep `showComplied` removed from this level (owned by `MaintenanceTasksTab`).

---

## Scroll Behavior

Each tab scrolls independently. The `TopAppBar` `enterAlwaysScrollBehavior` is removed at the overview level — with tabs, the bar should stay fixed so the tab strip is always reachable. Each tab composable owns its own `rememberScrollState` / `LazyListState`.

---

## State & Navigation

- No new routes — `MaintenanceLogListScreen` standalone route remains unchanged.
- `AircraftOverviewAction` gains no new entries.
- Tab selection is **local UI state** (`rememberSaveable { mutableStateOf(0) }`) — not promoted to ViewModel.
- The `LogDetailsClick` action in `AircraftOverviewAction` becomes unused and can be removed (or kept for now and removed in cleanup).

---

## Files Touched

| File | Change |
|------|--------|
| `AircraftOverviewContent.kt` | Replace body with `TabRow` + pager; remove bottom bar and scroll behavior |
| `LogDetailsBottomBar.kt` | Delete (no longer used) |
| `MaintenanceLogListScreen.kt` | Extract body into `MaintenanceLogListContent`; keep `Scaffold` wrapper |
| `compose/tabs/AircraftOverviewTabRow.kt` | New |
| `compose/tabs/OverviewTab.kt` | New |
| `compose/tabs/MaintenanceTasksTab.kt` | New |
| `compose/tabs/LogsTab.kt` | New |
| `sharedassets/strings.xml` | Add tab label strings |
| `AircraftOverviewAction.kt` | Remove `LogDetailsClick` (optional cleanup) |

---

## Open Questions for Review

1. **Pager vs AnimatedContent** — `HorizontalPager` (from `androidx.compose.foundation.pager`) gives swipe gestures between tabs. `AnimatedContent` is simpler but no swipe. Preference?
    * Answer: pager - gesture swipe is strongly preferred.
2. **ViewModel scope for Logs tab** — `MaintenanceLogListScreen`'s ViewModel is currently scoped by navigation route. Inside a tab there's no route — do we want a single shared ViewModel, or instantiate `MaintenanceLogViewModel` with the aircraft ID via a Koin parameter?
    * Answer: Instantiate MaintenanceLogViewModel with the aircraft ID via a Koin parameter?
3. **"Add Log" placement** — FAB on the Logs tab, or retain a smaller action in the Overview tab's `LogStatsSection` row?
    * Answer: FAB on the Logs tab.
4. **Tab persistence** — Should selected tab survive back-stack (saved in ViewModel) or reset to Overview on re-entry?
    * Reset on re-entry.