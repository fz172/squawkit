# Design Doc: Card Swipe Actions and Squawk Deletion

**PRD:** [`card_swipe_actions_PRD.md`](card_swipe_actions_PRD.md)
**Status:** 📝 Proposed
**Last updated:** 2026-09-06

---

## Implementation status — not started

Nothing below is built. The one piece that already exists is `SquawkManager.deleteSquawk()`
(`feature/squawk/datamanager/.../SquawkManagerImpl.kt`), which deletes the record from the
`EntityStore` and takes its comment thread with it, exactly as `TaskDataManagerImpl.deleteTask()`
does. It has a unit test and no caller.

---

## 1. Overview

Three changes, in dependency order:

1. **A reusable `SwipeActionCard` wrapper in `core/ui`** that slides any card sideways — either
   direction — to reveal one row of actions. Built on Foundation's `anchoredDraggable`, so it is one
   implementation for Android, iOS, and web. Alongside it, `ResolveBubbleMenu` learns to point its
   tail at an arbitrary anchor and to flip below it.
2. **Quick actions wired into the three tabs.** The Squawks and Tasks tabs dispatch through
   `ThingOverviewViewModel`; the Logs tab through `MaintenanceLogListViewModel`. The resolve
   bubbles and confirmation dialogs the edit forms already own move down one layer so the tabs can
   render them.
3. **Squawk deletion**: a new `ThingOverviewAction`, a confirmation dialog in
   `feature/squawk/viewing`, and a destructive card at the end of the squawk form's Details tab.

No proto, schema, sync, or backend change. No new gating mechanism. No new notification code
(§8).

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
| Shared bubble | `core/ui/common/compose/ResolveBubbleMenu.kt` | A `Popup` whose `ResolveMenuPositionProvider` always places it *above* the anchor and clamps x to the window margin; `SpeechBubbleShape` always draws the tail centred on the bottom edge. So when x is clamped the tail no longer points at the anchor — fine for a centred bottom-bar button, wrong for a button at a card's edge |
| Delete → collaborator notification | `backend/firebase/functions/src/notifications/onRecordWritten.ts`: `activityKindOf()` returns `"deleted"` when `after.deleted === true`; `EntityStore.delete` writes exactly that tombstone (`deleted=1, dirty=1`) and the sync engine pushes it | Already covered by `notification-fanout.test.ts` ("names the record it deletes …") |
| Module deps | `feature/thing/dashboard/build.gradle.kts` depends on each feature's `model`, `datamanager`, `sharedassets`, `viewing` — **never `update`** | This is the constraint that shapes §4 |
| Snackbars | `AdaptiveShellRoute.kt` owns the `SnackbarHostState`; sections cannot reach it. `ThingOverviewEvent.ShowError` exists but `ThingSectionContent` does not consume it | See §7 |
| Gesture code | None. No `anchoredDraggable`, `SwipeToDismissBox`, or drag detector anywhere in `core/` or `feature/` | Greenfield |

## 3. `SwipeActionCard` (`core/ui`)

### 3.1 Why `anchoredDraggable` and not `SwipeToDismissBox`

Material 3's `SwipeToDismissBox` is built to *remove* the row: its terminal states are off-screen,
its background is a full-width scrim, and it fights any attempt to park the card partly open. What
we want is a drawer: three resting positions (open-left, closed, open-right), reveal-and-tap, no
fling-commit (PRD R2). `AnchoredDraggableState` in `androidx.compose.foundation.gestures` is the
primitive under `SwipeToDismissBox` and gives exactly that with ~150 lines, on every Compose
Multiplatform target we ship (CMP 1.11.1).

### 3.2 API

```kotlin
package dev.fanfly.wingslog.core.ui.common.compose

/** One revealable action of a [SwipeActionCard]. */
data class SwipeAction(
  val icon: ImageVector,
  val label: String,                 // one or two words, lexicon-resolved by the caller
  val tone: SwipeActionTone,         // POSITIVE or DESTRUCTIVE
  val onClick: () -> Unit,
  /**
   * Optional popup composed *inside* this action's button, so a Popup placed here anchors to the
   * button (the same trick BottomButtons.dangerMenuContent uses). Resolve puts its bubble here.
   */
  val menuContent: (@Composable () -> Unit)? = null,
)

/**
 * Slides [content] sideways in either direction to reveal [actions], laid out side by side in the
 * given order on whichever side the user dragged toward (PRD R3). The open distance is the row's
 * measured width (PRD R4). An empty list disables the drag. Reveal-and-tap only; the drag never
 * commits an action. Exposes every action as an accessibility custom action (PRD R21).
 */
@Composable
fun SwipeActionCard(
  actions: List<SwipeAction>,
  controller: SwipeRevealController,
  key: Any,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
)

/** One open card per list. Remembered by the list, passed to every card. */
@Composable
fun rememberSwipeRevealController(): SwipeRevealController
```

- **Panel width.** The action row is composed once, off to the side, with
  `Modifier.width(IntrinsicSize.Max)` and `onSizeChanged`; the measured width becomes the open
  distance. Each button is `widthIn(min = 72.dp)` with `Spacing.medium` horizontal padding around
  an icon-over-`labelMedium` column, so "Resolve" and "Delete" come out around 80 dp each, and the
  two-action card opens about 160 dp. A label the caller passes that wraps to two lines is a
  caller bug; labels are one or two short words (PRD R4).
- **Anchors.** `Closed = 0f`, `OpenEnd = -panelWidth` (dragged toward start), `OpenStart =
  +panelWidth` (dragged toward end). Both open anchors exist whenever `actions` is non-empty, since
  both sides reveal the same row. Anchors are rebuilt when the measured width changes.
- **Snap.** `positionalThreshold = { distance -> distance * 0.4f }`,
  `velocityThreshold = { 125.dp.toPx() }`, `snapAnimationSpec = tween(200, easing = EaseOut)`.
  Matches DESIGN.md §6 "150–250 ms, ease-out"; there is no bounce.
- **Layout.** A `Box`. The action row is drawn twice, once aligned to each edge, each clipped to
  `RoundedCornerShape(Spacing.cardCornerRadius)` and each only visible while the card's offset
  uncovers it; the card is offset by `state.requireOffset()`. Nothing peeks when closed.
- **Scroll conflict.** `Modifier.anchoredDraggable(state, Orientation.Horizontal)` participates in
  the standard nested-scroll/touch-slop negotiation: the `LazyColumn` claims a vertical drag, the
  card claims a horizontal one. Verify on iOS specifically, where the web and UIKit scroll containers
  differ from Android; this is the one place a device pass is mandatory before merge.
- **RTL.** Because both sides carry the same row, RTL needs no semantic flip. The row's own order
  follows `LocalLayoutDirection` like any `Row`.
- **Tap on an open card** closes it (`controller.close()`) instead of firing the card's `onClick`.
  Tap on a closed card passes through untouched (PRD R7).
- **`SwipeRevealController`** holds `openKey: Any?`. Opening a card sets it; a card whose `key` is
  no longer `openKey` animates itself closed in a `LaunchedEffect`. The list also calls `close()`
  from a `nestedScroll` connection on the first vertical scroll delta and when the filter toggle
  changes (PRD R5). It is `remember`ed, not saved: a revealed card is not state worth surviving
  process death.
- **Accessibility.** `Modifier.semantics { customActions = actions.map {
  CustomAccessibilityAction(it.label) { it.onClick(); true } } }` on the content. TalkBack and
  VoiceOver present these in the actions menu; the analytics `source` for these is `a11y`.
- **Tones.** `DESTRUCTIVE` → `colorScheme.errorContainer` / `onErrorContainer`. `POSITIVE` →
  `statusColors.positive.container` / `.accent`. Same tokens `ResolveBubbleMenu` already uses for
  its icon chips. Only these two exist; PRD §3 rules out Update and Reopen, which were the reasons
  a primary tone might have been wanted.

### 3.3 `ResolveBubbleMenu`: point at the button (PRD R8, R9, §6.2)

Two changes to the shared component, both backward compatible for the two edit forms:

1. **Tail follows the anchor.** `ResolveMenuPositionProvider.calculatePosition` already knows
   `anchorBounds` and the clamped `x`. It records `tailCenterX = anchorBounds.center.x - x` into a
   `MutableState` the bubble reads, and `SpeechBubbleShape` takes `tailCenterX: Float` (px, clamped
   to `[cornerRadius + tailWidth/2, width - cornerRadius - tailWidth/2]`) instead of assuming
   `size.width / 2`. On the bottom bar the anchor is centred and nothing changes visually.
2. **Flip below when there is no room above.** If `anchorBounds.top - popupHeight - gap < margin`,
   place the bubble at `anchorBounds.bottom + gap` and set `tailSide = Top`; the shape draws the
   tail on its top edge and the content padding moves from `bottom` to `top`. The forms never hit
   this branch (the danger button is at the bottom of the screen); a card at the top of a list does.

Because a `Popup` anchors to the composable that composes it, the tab passes the bubble as the
Resolve action's `menuContent`, and `SwipeActionCard` composes that lambda inside the Resolve
button's `Box`. That is the whole anchoring mechanism; no coordinates are passed by hand.

### 3.4 Where it is applied

| List | Wrap | Not wrapped |
|---|---|---|
| `SquawkTab.kt` `AdaptiveCardList` items | `SquawkCard` | ad rows |
| `ComplianceSection.kt` `AdaptiveCardList` items | `TaskCardItem` | ad rows; the `OverviewTab` "Next due" rail card and `CriticalAlertSection` |
| `MaintenanceLogListContent.kt` `LazyColumn` items | `MaintenanceLogCard` | ad rows; `MaintenanceLogTable` on wide tiers |

`AdaptiveCardList` lays a multi-column grid on wider tiers; each cell wraps independently, and one
controller per list still enforces one open card.

## 4. Moving the shared pieces down to `viewing`

`feature/thing/dashboard` may depend on a feature's `viewing` but not its `update` (AGENTS.md
§ Dependency rules). Three stateless composables currently live in `update/compose` only because
the form was their first caller. They move, unchanged in behaviour:

| Composable | From | To |
|---|---|---|
| `ResolveOptionsMenu` | `feature/squawk/update/compose` | `feature/squawk/viewing` |
| `DismissSquawkDialog` | `feature/squawk/update/compose` | `feature/squawk/viewing` |
| `ResolveTaskOptionsMenu` | `feature/tasks/update/compose` | `feature/tasks/viewing` |
| `DeleteSquawkConfirmDialog` | *(new)* | `feature/squawk/viewing`, modelled on `DeleteTaskConfirmDialog` |

Their strings (`dismiss_no_work_planned`, `fixed_option_label`, `create_work_log*`,
`skip_this_cycle_option*`) move from each feature's `update` `strings.xml` to its `sharedassets`
`strings.xml`, per the "never depend on a module for a string" rule. The `update` modules already
depend on `viewing` and `sharedassets`, so the forms keep compiling with only import changes.

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
`squawkManager.deleteSquawk`, `taskDataManager.deleteTask`. Two things are new:

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

- `SquawkTab.kt`: the Resolve `SwipeAction` carries `ResolveOptionsMenu(expanded =
  resolvingSquawkId == item.squawk.id, …)` as its `menuContent`; `DismissSquawkDialog` and
  `DeleteSquawkConfirmDialog` render at tab level.
- `ComplianceSection.kt` (via `MaintenanceTasksTab.kt`): likewise `ResolveTaskOptionsMenu` as
  `menuContent`, `SkipTaskConfirmDialog` at section level, and the `DeleteTaskConfirmDialog` that
  `ThingSectionContent` already renders when `deletingTaskId != null`.

All of these are `AlertDialog` / popup imports from `core.ui.common.compose`, per the enforced
popup rule; nothing new for `checkPopupSelectionScopes` to reject.

Per-state action tables (PRD §5.2) live in one place each, as pure functions the tests can hit and
with every label resolved through the lexicon (PRD R24):

```kotlin
// feature/squawk/viewing
@Composable fun SquawkWithStatus.quickActions(callbacks: SquawkQuickActionCallbacks): List<SwipeAction>
// feature/tasks/viewing
@Composable fun MaintenanceTaskWithStatus.quickActions(callbacks: TaskQuickActionCallbacks): List<SwipeAction>
```

(`@Composable` only for `stringResource` / `LocalThingLexicon`; the branching is on
`status` / `dueStatus.status` and is unit-tested through a non-composable core that takes the
resolved labels.)

### 5.2 Logs — `MaintenanceLogListViewModel`

The Logs tab has its own ViewModel and already has `logManager`. Add:

```kotlin
fun onDeleteLogClick(log: MaintenanceLog)   // sets uiState.deletingLog
fun cancelDeleteLog()
fun confirmDeleteLog()                      // logManager.deleteLog(thingId, id); ShowMessage
```

and a `deletingLog: MaintenanceLog?` on `MaintenanceLogListUiState`. `MaintenanceLogListContent`
gains `onDeleteLog: ((MaintenanceLog) -> Unit)?` mirroring `onEditLog`, and renders a
`DeleteLogConfirmDialog` (extracted from the inline dialog in `MaintenanceLogFormScreen.kt` into
`feature/logs/viewing`, so the form and the list share one). The card's single action is Delete;
a null callback yields an empty list, which is how a read-only caller opts out.

`MaintenanceLogTable` (wide tiers) is untouched (PRD non-goal).

## 6. Squawk delete in the edit form

- `SquawkFormViewModel` gains `showDeleteDialog` / `hideDeleteDialog` on its state and
  `delete(onSuccessMessage: String)`, shaped like `reopen()`: calls `squawkManager.deleteSquawk`,
  then `SquawkFormEvent.NavigateBackWithMessage`. Comment drafts are discarded with the record.
- `SquawkDetailsTab` (in `SquawkFormTabs.kt`) appends `DestructiveActionCard(Icons.Default.Delete,
  stringResource(delete_this_squawk_title, lexicon.squawkNoun.singular),
  stringResource(delete_this_squawk_subtitle, lexicon.logNoun.plural))` when `isEdit`, exactly
  where the task form puts its card. `SquawkFormScreen` renders `DeleteSquawkConfirmDialog` on
  `state.showDeleteDialog`.
- `BottomButtons`' danger slot is left alone (PRD R19).

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

## 8. Collaborator notifications on delete (PRD R16)

Nothing to build, one thing to protect. The path today is:

1. `EntityStore.delete(id, scope)` writes a tombstone row (`deleted=1, dirty=1`); observers drop
   the record immediately.
2. `SyncEngine` pushes the dirty row; the Firestore document ends up with `deleted: true`.
3. `onNotifiableThingRecordWritten` (`onRecordWritten.ts`) sees `after.deleted === true`,
   `activityKindOf()` returns `"deleted"`, and the N1 fan-out sends "*Name* removed the … " to every
   other member. `notification-fanout.test.ts` pins this ("names the record it deletes, and taps to
   the aircraft/tab instead of the gone record").

Every delete in this feature therefore calls the manager (`deleteSquawk` / `deleteTask` /
`deleteLog`) and nothing lower. The design-level guard is the existing rule that feature code never
touches the store's tables or Firestore directly; the test-level guard is one ViewModel test per
surface asserting the manager was called (§11). A future "hard delete" or "purge" path would
bypass step 3 and must not be introduced here.

## 9. Why no Undo

An Undo snackbar is the usual companion to swipe-delete, and it was considered. Three reasons it is
out for V1:

1. `EntityStore.delete` is a real delete that the sync engine pushes on its next tick; undo would be
   a re-`put`, which is a new revision with a new `writer_uid`, not a restoration. On a shared Thing
   that changes the authorship record (`LogAuthorship`) and fires a second, confusing notification
   (§8).
2. `deleteSquawk` / `deleteTask` also delete the comment thread; those cannot be re-put from the
   client.
3. Every existing delete in the app confirms and says "cannot be undone". Introducing a second
   model for the same verb on one surface is worse than keeping one.

If undo is wanted later, the right shape is a soft-delete grace period honoured by the store and the
sync engine, which is a storage design change, not a UI one.

## 10. Strings

New keys, all in `sharedassets` `strings.xml` files, noun-substituted via the lexicon format-string
convention from #656. Nothing hard-codes the record noun (PRD R24).

| Module | Key | Text |
|---|---|---|
| `feature/squawk/sharedassets` | `delete_squawk_title` | Delete %1$s? |
| | `delete_squawk_confirmation` | "%1$s" is removed for everyone with access, along with its comments. The %2$s that addressed it, if any, is kept. Deletion is for entries made in error — if the problem was real but no longer applies, dismiss it instead. This cannot be undone. |
| | `delete_this_squawk_title` | Delete this %1$s |
| | `delete_this_squawk_subtitle` | Permanently removes it and its comments. %1$s are kept. |
| | `squawk_deleted` | %1$s deleted |
| `feature/logs/sharedassets` | `log_deleted` | %1$s deleted |
| `core/sharedassets` | `quick_action_resolve` | Resolve |

`delete`, `cancel`, `delete_failed`, `task_deleted`, `delete_task*`, `skip_task_confirm*` already
exist and are reused. Apostrophes are written as `’`, never `\'`.

## 11. Analytics

One new `AnalyticsEvent.Name`: `RECORD_QUICK_ACTION("record_quick_action")`. Params: existing
`SURFACE` (`squawks` / `tasks` / `logs`) and `SOURCE` (`swipe` / `a11y` / `form`), plus a new
`ACTION("action")` (`resolve` / `skip` / `delete`). Fired from the ViewModel at commit time, so a
cancelled confirmation logs nothing. The analytics design's typed-taxonomy rule means the values are
enum-backed, not free strings.

## 12. Tests

- `core/ui` — `SwipeActionCardTest` (Compose UI test, `androidHostTest`): drag past threshold opens
  in each direction and both reveal the same labels; drag under threshold closes; an empty action
  list does not move; open distance equals the measured row width; tapping an open card closes it;
  opening a second card closes the first; custom accessibility actions match the list.
- `core/ui` — `ResolveBubbleMenuPositionTest`: tail x tracks the anchor centre when x is clamped
  at either window edge; the bubble flips below and the tail side becomes `Top` when there is no
  room above; the centred bottom-bar case is unchanged.
- `feature/squawk/viewing` — `SquawkQuickActionsTest`: the state table from PRD §5.2 (open →
  Resolve+Delete, dismissed or addressed → Delete only).
- `feature/tasks/viewing` — `TaskQuickActionsTest`: due/overdue/on-condition → Resolve+Delete,
  complied → Delete only.
- `feature/thing/dashboard` — `ThingOverviewViewModelTest` (new file; none exists today):
  confirm-delete-squawk calls `squawkManager.deleteSquawk` (the §8 guard), clears ids and emits
  the message; failure keeps state and emits `delete_failed`; confirm dismiss calls
  `dismissSquawk` with the chosen reason; skip calls `TaskDataManager.skipCycle` with the
  dashboard's current reading.
- `feature/tasks/datamanager` — `TaskDataManagerImplTest`: `skipCycle` clears overrides and stamps
  the complied meter (moved from `TaskViewModelTest`).
- `feature/logs/viewing` — `MaintenanceLogListViewModelTest`: `confirmDeleteLog` calls
  `logManager.deleteLog` and emits the message.
- `feature/squawk/update` — `SquawkFormViewModelTest`: `delete()` navigates back with the message
  on success and surfaces the error on failure.

MockK + Truth + JUnit 4 + coroutines-test, under `src/test/kotlin` as the repo convention requires.

## 13. Sequencing

Four PRs, each shippable on its own:

1. **Move-only.** §4 relocations and the `DestructiveActionCard` / `DeleteLogConfirmDialog`
   extractions, plus `TaskDataManager.skipCycle`. Zero behaviour change; the form tests prove it.
2. **Squawk delete.** §6 form card and dialog, `deleteSquawk` wiring in `SquawkFormViewModel`,
   strings. Ships user value (PRD G3) before any gesture exists.
3. **`SwipeActionCard`, the `ResolveBubbleMenu` anchoring change, `LocalSnackbarHostState`.** §3 and
   §7, with the component tests. No caller yet.
4. **Wire the three tabs.** §5, the quick-action tables and their tests, analytics. Device pass on
   iOS for scroll-vs-drag before merge.

After PR 4, update this doc's status block, `docs/squawks/squawk_design.md` (delete is now a
squawk verb), and the AGENTS.md design-doc map entry for `docs/cards/`. #815 (stale *Addressed*
after a log delete) is independent and can land before or after.

## 14. Risks

| Risk | Mitigation |
|---|---|
| Horizontal drag steals vertical scroll on iOS or web | `anchoredDraggable` uses touch-slop orientation locking; PR 4 requires a device pass on both. Fallback: raise the horizontal slop for the card via a custom `PointerInputScope` filter before falling back to disabling the gesture on that host behind `AppCapability`. |
| Users delete instead of dismissing | Confirmation copy (PRD R12) points to Dismiss; Resolve sits first in the row, Delete second. |
| A mid-drag recomposition (sync updates the list) resets the offset | `key` the `AnchoredDraggableState` on the record id and hoist it in `remember(key)`; the list items are already keyed by id. |
| Grid tiers: two cards in one row both half-open | Single `SwipeRevealController` per list; the second open closes the first regardless of column. |
| Bubble tail drifts off its button on very narrow windows | The tail clamp in §3.3 keeps it inside the bubble body; the bubble's 300 dp width is already narrower than any supported window minus margins. |
