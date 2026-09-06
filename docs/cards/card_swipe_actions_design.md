# Design Doc: Card Swipe Actions and Squawk Deletion

**PRD:** [`card_swipe_actions_PRD.md`](card_swipe_actions_PRD.md)
**Status:** 📝 Proposed
**Last updated:** 2026-09-05

---

## Implementation status — not started

Nothing below is built. The one piece that already exists is `SquawkManager.deleteSquawk()`
(`feature/squawk/datamanager/.../SquawkManagerImpl.kt`), which deletes the record from the
`EntityStore` and takes its comment thread with it, exactly as `TaskDataManagerImpl.deleteTask()`
does. It has a unit test and no caller.

---

## 1. Overview

Three changes, in dependency order:

1. **A reusable `SwipeActionCard` wrapper in `core/ui`** that slides any card sideways to reveal up
   to one action per side. Built on Foundation's `anchoredDraggable`, so it is one implementation
   for Android, iOS, and web.
2. **Quick actions wired into the three tabs.** The Squawks and Tasks tabs dispatch through
   `ThingOverviewViewModel`; the Logs tab through `MaintenanceLogListViewModel`. The resolve
   bubbles and confirmation dialogs the edit forms already own move down one layer so the tabs can
   render them.
3. **Squawk deletion**: a new `ThingOverviewAction`, a confirmation dialog in
   `feature/squawk/viewing`, and a destructive card at the end of the squawk form's Details tab.

No proto, schema, sync, or backend change. No new gating mechanism.

## 2. What exists today, verified

| Piece | Where | Notes |
|---|---|---|
| Squawk cards | `feature/squawk/viewing/SquawkCard.kt`, rendered by `feature/thing/dashboard/compose/tabs/SquawkTab.kt` inside `AdaptiveCardList` | Tap → `ThingOverviewAction.ShowSquawkDetail` |
| Task cards | `feature/tasks/viewing/TaskCardItem.kt`, rendered by `feature/thing/dashboard/compose/ComplianceSection.kt` | Tap → `ThingOverviewAction.TaskCardClick` |
| Log cards | `feature/logs/viewing/log/compose/MaintenanceLogCard.kt`, rendered by `MaintenanceLogListContent.kt` in a `LazyColumn` on compact tiers and a `MaintenanceLogTable` when `LocalLayoutTier.current.hasSideNav` | Own ViewModel: `MaintenanceLogListViewModel` |
| Task delete from the dashboard | `ThingOverviewViewModel.deleteTask()`, `deletingTaskId` in `ThingOverviewUiState.Success`, `DeleteTaskConfirmDialog` in `feature/tasks/viewing` | The action pair `CancelDeleteTask` / `ConfirmDeleteTask` exists but nothing in the tabs currently sets `deletingTaskId`; the state is dormant |
| Task delete from the form | `EditTaskScreen.kt` → `TaskAdjustmentsTab.DeleteTaskCard` → `DeleteTaskConfirmDialog` → `TaskViewModel.deleteTask()` | Success message travels via `CROSS_SCREEN_SUCCESS_MESSAGE` on the shell back-stack entry |
| Log delete from the form | `MaintenanceLogFormScreen.kt`, `BottomButtons.onDangerClick` → inline `AlertDialog` → `MaintenanceLogFormViewModel.deleteLog()` | |
| Squawk resolve | `SquawkFormScreen.kt`: `BottomButtons` danger slot hosts `ResolveOptionsMenu` (Fixed / Dismiss) and Reopen; `DismissSquawkDialog` picks a `SquawkDismissReason` | All in `feature/squawk/update/compose` |
| Task resolve | `EditTaskScreen.kt`: `ResolveTaskOptionsMenu` (Create work log / Skip this cycle); `SkipTaskConfirmDialog` is already in `feature/tasks/viewing`; the skip write is `TaskViewModel.skipThisCycle()` | Menu in `feature/tasks/update/compose` |
| Shared bubble | `core/ui/common/compose/ResolveBubbleMenu.kt` | Already in `core:ui`, so both menus can move freely |
| Module deps | `feature/thing/dashboard/build.gradle.kts` depends on each feature's `model`, `datamanager`, `sharedassets`, `viewing` — **never `update`** | This is the constraint that shapes §4 |
| Snackbars | `AdaptiveShellRoute.kt` owns the `SnackbarHostState`; sections cannot reach it. `ThingOverviewEvent.ShowError` exists but `ThingSectionContent` does not consume it | See §7 |
| Gesture code | None. No `anchoredDraggable`, `SwipeToDismissBox`, or drag detector anywhere in `core/` or `feature/` | Greenfield |

## 3. `SwipeActionCard` (`core/ui`)

### 3.1 Why `anchoredDraggable` and not `SwipeToDismissBox`

Material 3's `SwipeToDismissBox` is built to *remove* the row: its terminal states are off-screen,
its background is a full-width scrim, and it fights any attempt to park the card partly open. What
we want is a drawer: three resting positions (leading open, closed, trailing open), reveal-and-tap,
no fling-commit (PRD R2). `AnchoredDraggableState` in `androidx.compose.foundation.gestures` is the
primitive under `SwipeToDismissBox` and gives exactly that with ~120 lines, on every Compose
Multiplatform target we ship (CMP 1.11.1).

### 3.2 API

```kotlin
package dev.fanfly.wingslog.core.ui.common.compose

/** One revealable action on one side of a [SwipeActionCard]. */
data class SwipeAction(
  val icon: ImageVector,
  val label: String,
  val tone: SwipeActionTone,      // POSITIVE, PRIMARY, DESTRUCTIVE → statusColors / colorScheme
  val onClick: () -> Unit,
)

/**
 * Slides [content] sideways to reveal [leading] on start-to-end drag and [trailing] on
 * end-to-start drag. A null side does not move (PRD R9). Reveal-and-tap only; the drag never
 * commits an action. Exposes both actions as accessibility custom actions (PRD R19).
 */
@Composable
fun SwipeActionCard(
  leading: SwipeAction?,
  trailing: SwipeAction?,
  controller: SwipeRevealController,
  key: Any,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
)

/** One open card per list. Remembered by the list, passed to every card. */
@Composable
fun rememberSwipeRevealController(): SwipeRevealController
```

- **Anchors.** `Closed = 0f`, `LeadingOpen = +panelWidth`, `TrailingOpen = -panelWidth`, with the
  open anchor omitted when that side's action is null. `panelWidth` is a fixed 88 dp: wide enough
  for a 24 dp icon over a `labelMedium` word, and comfortably above the 48 dp touch minimum.
- **Snap.** `positionalThreshold = { distance -> distance * 0.4f }`,
  `velocityThreshold = { 125.dp.toPx() }`, `snapAnimationSpec = tween(200, easing = EaseOut)`.
  Matches DESIGN.md §6 "150–250 ms, ease-out"; there is no bounce.
- **Layout.** A `Box`; the action panels are laid out full-height at the start and end edges,
  clipped to `RoundedCornerShape(Spacing.cardCornerRadius)`; the card is offset by
  `state.requireOffset()`. The panel's visible width is exactly the offset, so nothing peeks.
- **Scroll conflict.** `Modifier.anchoredDraggable(state, Orientation.Horizontal)` participates in
  the standard nested-scroll/touch-slop negotiation: the `LazyColumn` claims a vertical drag, the
  card claims a horizontal one. Verify on iOS specifically, where the web and UIKit scroll containers
  differ from Android; this is the one place a device pass is mandatory before merge.
- **RTL.** `anchoredDraggable` takes a `reverseDirection` flag; pass `LocalLayoutDirection.current
  == Rtl` so "leading" stays the constructive side.
- **Tap on an open card** closes it (`controller.close()`) instead of firing the card's `onClick`.
  Tap on a closed card passes through untouched (PRD R5).
- **`SwipeRevealController`** holds `openKey: Any?`. Opening a card sets it; a card whose `key` is
  no longer `openKey` animates itself closed in a `LaunchedEffect`. The list also calls `close()`
  from a `nestedScroll` connection on the first vertical scroll delta and when the filter toggle
  changes (PRD R3). It is `remember`ed, not saved: a revealed card is not state worth surviving
  process death.
- **Accessibility.** `Modifier.semantics { customActions = listOfNotNull(leading, trailing).map {
  CustomAccessibilityAction(it.label, { it.onClick(); true }) } }` on the content. TalkBack and
  VoiceOver present these in the actions menu; the analytics `source` for these is `a11y`.
- **Tones.** `DESTRUCTIVE` → `colorScheme.errorContainer` / `onErrorContainer`. `POSITIVE` →
  `statusColors.positive.container` / `.accent`. `PRIMARY` → `colorScheme.primaryContainer` /
  `primary`. Same tokens `ResolveBubbleMenu` already uses for its icon chips.

### 3.3 Where it is applied

| List | Wrap | Not wrapped |
|---|---|---|
| `SquawkTab.kt` `AdaptiveCardList` items | `SquawkCard` | ad rows |
| `ComplianceSection.kt` `AdaptiveCardList` items | `TaskCardItem` | ad rows; the `OverviewTab` "Next due" rail card and `CriticalAlertSection` |
| `MaintenanceLogListContent.kt` `LazyColumn` items | `MaintenanceLogCard` | ad rows; `MaintenanceLogTable` on wide tiers |

`AdaptiveCardList` lays a multi-column grid on wider tiers; each cell wraps independently, and one
controller per list still enforces one open card.

## 4. Moving the shared pieces down to `viewing`

`feature/thing/dashboard` may depend on a feature's `viewing` but not its `update` (AGENTS.md
§ Dependency rules). Four stateless composables currently live in `update/compose` only because
the form was their first caller. They move, unchanged in behaviour:

| Composable | From | To |
|---|---|---|
| `ResolveOptionsMenu` | `feature/squawk/update/compose` | `feature/squawk/viewing` |
| `DismissSquawkDialog` | `feature/squawk/update/compose` | `feature/squawk/viewing` |
| `ResolveTaskOptionsMenu` | `feature/tasks/update/compose` | `feature/tasks/viewing` |
| `DeleteSquawkConfirmDialog` | *(new)* | `feature/squawk/viewing`, modelled on `DeleteTaskConfirmDialog` |

Their strings (`dismiss_no_work_planned`, `fixed_option_label`, `create_work_log*`,
`skip_this_cycle_option*`, `reopen_issue`, `resolve_issue`) move from each feature's `update`
`strings.xml` to its `sharedassets` `strings.xml`, per the "never depend on a module for a string"
rule. The `update` modules already depend on `viewing` and `sharedassets`, so the forms keep
compiling with only import changes.

`DeleteTaskCard` in `TaskAdjustmentsTab.kt` is `private`. It becomes `DestructiveActionCard(icon,
title, subtitle, onClick)` in `core/ui/common/compose` so the squawk form can reuse it (§6).

## 5. Wiring the tabs

### 5.1 Squawks and Tasks — `ThingOverviewViewModel`

New actions on `ThingOverviewAction`:

```kotlin
// Squawks
data class SquawkResolveClick(val squawk: SquawkWithStatus) : ThingOverviewAction   // opens bubble
data object DismissSquawkResolveMenu : ThingOverviewAction
data class SquawkFixedClick(val squawkId: String) : ThingOverviewAction             // → Create Log
data class SquawkDismissClick(val squawkId: String) : ThingOverviewAction           // opens reason dialog
data class ConfirmDismissSquawk(val reason: SquawkDismissReason) : ThingOverviewAction
data object CancelDismissSquawk : ThingOverviewAction
data class ReopenSquawkClick(val squawkId: String) : ThingOverviewAction
data class DeleteSquawkClick(val squawk: SquawkWithStatus) : ThingOverviewAction    // opens confirm
data object ConfirmDeleteSquawk : ThingOverviewAction
data object CancelDeleteSquawk : ThingOverviewAction

// Tasks
data class TaskResolveClick(val card: MaintenanceTaskWithStatus) : ThingOverviewAction
data object DismissTaskResolveMenu : ThingOverviewAction
data class TaskCreateLogClick(val cardId: String) : ThingOverviewAction             // → Create Log
data class TaskSkipClick(val card: MaintenanceTaskWithStatus) : ThingOverviewAction // opens confirm
data object ConfirmSkipTask : ThingOverviewAction
data object CancelSkipTask : ThingOverviewAction
data class DeleteTaskClick(val card: MaintenanceTaskWithStatus) : ThingOverviewAction // sets deletingTaskId
```

New fields on `ThingOverviewUiState.Success`, alongside the existing `deletingTaskId`:

```kotlin
val resolvingSquawkId: String? = null,   // bubble open for this squawk
val dismissingSquawkId: String? = null,  // reason dialog open
val deletingSquawkId: String? = null,    // confirm open
val resolvingTaskId: String? = null,
val skippingTaskId: String? = null,
```

Navigation actions (`SquawkFixedClick`, `TaskCreateLogClick`) are handled in the `onAction`
wrapper in `ThingSectionContent.kt` next to `AddLogClick`, navigating to
`Screen.AddMaintenanceLog.createRoute(thingId, squawkId = …)` / `(thingId, cardId = …)` — the route
already accepts both. Everything else falls through to `viewModel.onAction`.

The ViewModel's handlers call what already exists: `squawkManager.dismissSquawk`,
`squawkManager.reopenSquawk`, `squawkManager.deleteSquawk`, `taskDataManager.deleteTask`. Two
things are new:

- **`deleteSquawk` result handling.** On success clear `deletingSquawkId` and `selectedSquawk`, emit
  `ThingOverviewEvent.ShowMessage(squawk_deleted)`; on failure keep the state and emit
  `ShowMessage(delete_failed)`.
- **Skip this cycle** is currently `TaskViewModel.skipThisCycle()` in `feature/tasks/update`, which
  the dashboard cannot reach. The computation (`withForcedDueMeter(defaultMeterKey, null)`, clear
  `force_due_date`, set `force_complied_status` with the current reading) moves to
  `TaskDataManager.skipCycle(thingId, card, currentReading): Result<Boolean>`; `TaskViewModel`
  delegates to it. The dashboard already holds the current reading it needs in
  `logStats.readings` (the same value `TaskViewModel.currentReading()` derives).

The tabs render, keyed on those ids:

- `SquawkTab.kt`: `ResolveOptionsMenu` anchored to the revealed Resolve button (the bubble is a
  `Popup` and positions itself from its anchor slot), `DismissSquawkDialog`,
  `DeleteSquawkConfirmDialog`.
- `ComplianceSection.kt` (via `MaintenanceTasksTab.kt`): `ResolveTaskOptionsMenu`,
  `SkipTaskConfirmDialog`, and the `DeleteTaskConfirmDialog` that `ThingSectionContent` already
  renders when `deletingTaskId != null`.

All of these are `AlertDialog` / popup imports from `core.ui.common.compose`, per the enforced
popup rule; nothing new for `checkPopupSelectionScopes` to reject.

Per-state action tables (PRD §5.2) live in one place each, as pure functions the tests can hit:

```kotlin
// feature/squawk/viewing
fun SquawkWithStatus.quickActions(callbacks: SquawkQuickActionCallbacks): Pair<SwipeAction?, SwipeAction?>
// feature/tasks/viewing
fun MaintenanceTaskWithStatus.quickActions(callbacks: TaskQuickActionCallbacks): Pair<SwipeAction?, SwipeAction?>
```

### 5.2 Logs — `MaintenanceLogListViewModel`

The Logs tab has its own ViewModel and already has `onEditLog(logId)` (navigates via
`MaintenanceLogListEvent.NavigateToEditLog`) and `logManager`. Add:

```kotlin
fun onDeleteLogClick(log: MaintenanceLog)   // sets uiState.deletingLog
fun cancelDeleteLog()
fun confirmDeleteLog()                      // logManager.deleteLog(thingId, id); ShowMessage
```

and a `deletingLog: MaintenanceLog?` on `MaintenanceLogListUiState`. `MaintenanceLogListContent`
gains `onDeleteLog: ((MaintenanceLog) -> Unit)?` mirroring `onEditLog`, and renders a
`DeleteLogConfirmDialog` (extracted from the inline dialog in `MaintenanceLogFormScreen.kt` into
`feature/logs/viewing`, so the form and the list share one). Leading action = `onEditLog(log.id)`,
trailing = `onDeleteLog(log)`; both are null when the callbacks are null, which is how a read-only
caller opts out.

`MaintenanceLogTable` (wide tiers) is untouched (PRD non-goal).

## 6. Squawk delete in the edit form

- `SquawkFormViewModel` gains `showDeleteDialog` / `hideDeleteDialog` on its state and
  `delete(onSuccessMessage: String)`, shaped like `reopen()`: calls `squawkManager.deleteSquawk`,
  then `SquawkFormEvent.NavigateBackWithMessage`. Comment drafts are discarded with the record.
- `SquawkDetailsTab` (in `SquawkFormTabs.kt`) appends `DestructiveActionCard(Icons.Default.Delete,
  delete_this_squawk_title, delete_this_squawk_subtitle)` when `isEdit`, exactly where the task
  form puts its card. `SquawkFormScreen` renders `DeleteSquawkConfirmDialog` on
  `state.showDeleteDialog`.
- `BottomButtons`' danger slot is left alone (PRD R17).

## 7. Snackbars from inside a shell section

Today only dialog destinations can post a snackbar, by writing `CROSS_SCREEN_SUCCESS_MESSAGE` to
the shell's back-stack entry before popping. A quick action runs *inside* the shell entry, so that
channel is the wrong shape. Add to `core/ui/adaptive`:

```kotlin
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState?> { null }
```

provided by `AdaptiveShellRoute` around `sectionContent`. `ThingSectionContent` and `LogsTab`
collect `ThingOverviewEvent.ShowMessage` / a new `MaintenanceLogListEvent.ShowMessage` and call
`showSnackbar` on it. `ThingOverviewEvent.ShowError`, which nothing consumes today, is folded into
`ShowMessage`. A null local (a host that has not provided one, such as a preview) is a silent no-op.

The squawk form's delete goes through the existing cross-screen channel, like task delete.

## 8. Strings

New keys, all in `sharedassets` `strings.xml` files, noun-substituted via the lexicon format-string
convention from #656:

| Module | Key | Text |
|---|---|---|
| `feature/squawk/sharedassets` | `delete_squawk_title` | Delete %1$s? |
| | `delete_squawk_confirmation` | "%1$s" is removed for everyone with access, along with its comments. The work log that addressed it, if any, is kept. Deletion is for entries made in error — if the problem was real but no longer applies, dismiss it instead. This cannot be undone. |
| | `delete_this_squawk_title` | Delete this %1$s |
| | `delete_this_squawk_subtitle` | Permanently removes it and its comments. Logs are kept. |
| | `squawk_deleted` | %1$s deleted |
| `feature/logs/sharedassets` | `log_deleted` | %1$s deleted |
| `core/sharedassets` | `quick_action_resolve` | Resolve |
| | `quick_action_reopen` | Reopen |
| | `quick_action_update` | Update |

`delete`, `cancel`, `delete_failed`, `dismiss`, `task_deleted`, `delete_task*`, `skip_task_confirm*`
already exist and are reused. Apostrophes are written as `’`, never `\'`.

## 9. Why no Undo

An Undo snackbar is the usual companion to swipe-delete, and it was considered. Three reasons it is
out for V1:

1. `EntityStore.delete` is a real delete that the sync engine pushes on its next tick; undo would be
   a re-`put`, which is a new revision with a new `writer_uid`, not a restoration. On a shared Thing
   that changes the authorship record (`LogAuthorship`).
2. `deleteSquawk` / `deleteTask` also delete the comment thread; those cannot be re-put from the
   client.
3. Every existing delete in the app confirms and says "cannot be undone". Introducing a second
   model for the same verb on one surface is worse than keeping one.

If undo is wanted later, the right shape is a soft-delete tombstone honoured by the store and the
sync engine, which is a storage design change, not a UI one.

## 10. Analytics

One new `AnalyticsEvent.Name`: `RECORD_QUICK_ACTION("record_quick_action")`. Params: existing
`SURFACE` (`squawks` / `tasks` / `logs`) and `SOURCE` (`swipe` / `a11y` / `form`), plus a new
`ACTION("action")` (`resolve` / `reopen` / `update` / `delete` / `skip`). Fired from the ViewModel at
commit time, so a cancelled confirmation logs nothing. The analytics design's typed-taxonomy rule
means the values are enum-backed, not free strings.

## 11. Tests

- `core/ui` — `SwipeActionCardTest` (Compose UI test, `androidHostTest`): drag past threshold opens;
  drag under threshold closes; a null side does not move; tapping an open card closes it; opening a
  second card closes the first; custom accessibility actions match the non-null sides.
- `feature/squawk/viewing` — `SquawkQuickActionsTest`: the state table from PRD §5.2 (open →
  Resolve+Delete, dismissed → Reopen+Delete, addressed → Delete only).
- `feature/tasks/viewing` — `TaskQuickActionsTest`: due/overdue/on-condition → Resolve+Delete,
  complied → Delete only.
- `feature/thing/dashboard` — `ThingOverviewViewModelTest` (new file; none exists today): delete
  squawk success clears ids and emits the message; failure keeps state and emits `delete_failed`;
  confirm dismiss calls `dismissSquawk` with the chosen reason; skip calls
  `TaskDataManager.skipCycle` with the dashboard's current reading.
- `feature/tasks/datamanager` — `TaskDataManagerImplTest`: `skipCycle` clears overrides and stamps
  the complied meter (moved from `TaskViewModelTest`).
- `feature/logs/viewing` — `MaintenanceLogListViewModelTest`: `confirmDeleteLog` calls
  `deleteLog` and emits the message.
- `feature/squawk/update` — `SquawkFormViewModelTest`: `delete()` navigates back with the message
  on success and surfaces the error on failure.

MockK + Truth + JUnit 4 + coroutines-test, under `src/test/kotlin` as the repo convention requires.

## 12. Sequencing

Four PRs, each shippable on its own:

1. **Move-only.** §4 relocations and the `DestructiveActionCard` / `DeleteLogConfirmDialog`
   extractions, plus `TaskDataManager.skipCycle`. Zero behaviour change; the form tests prove it.
2. **Squawk delete.** §6 form card and dialog, `deleteSquawk` wiring in `SquawkFormViewModel`,
   strings. Ships user value (PRD G3) before any gesture exists.
3. **`SwipeActionCard` + `LocalSnackbarHostState`.** §3 and §7, with the component test. No caller
   yet.
4. **Wire the three tabs.** §5, the quick-action tables and their tests, analytics. Device pass on
   iOS for scroll-vs-drag before merge.

After PR 4, update this doc's status block, `docs/squawks/squawk_design.md` (delete is now a
squawk verb), and the AGENTS.md design-doc map entry for `docs/cards/`.

## 13. Risks

| Risk | Mitigation |
|---|---|
| Horizontal drag steals vertical scroll on iOS or web | `anchoredDraggable` uses touch-slop orientation locking; PR 4 requires a device pass on both. Fallback: raise the horizontal slop for the card via a custom `PointerInputScope` filter before falling back to disabling the gesture on that host behind `AppCapability`. |
| Users delete instead of dismissing | Confirmation copy (PRD R11) points to Dismiss; Dismiss is the *leading* swipe's default sub-option, Delete is the trailing side. |
| A mid-drag recomposition (sync updates the list) resets the offset | `key` the `AnchoredDraggableState` on the record id and hoist it in `rememberSaveable`-free `remember(key)`; the list items are already keyed by id. |
| Grid tiers: two cards in one row both half-open | Single `SwipeRevealController` per list; the second open closes the first regardless of column. |
