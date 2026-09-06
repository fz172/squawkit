# PRD: Card Swipe Actions and Squawk Deletion

**Design doc:** [`card_swipe_actions_design.md`](card_swipe_actions_design.md)
**Status:** 📝 Proposed
**Last updated:** 2026-09-05

> **Implementation status.** Not started. `SquawkManager.deleteSquawk()` already exists in
> `feature/squawk/datamanager` (it deletes the record and its comment thread) but nothing in the UI
> calls it. Task and log deletion ship today, reachable only from inside the edit forms.

---

## 1. Problem

Every record list in the app — the Squawks tab, the Tasks tab, and the Logs tab — is a column of
cards whose only affordance is *tap to open the detail sheet*. The actions a user actually takes on
a record day to day are two or three taps deeper:

| Wanted action | Today's path | Taps |
|---|---|---|
| Resolve a squawk (fixed / dismissed) | Card → detail sheet → Update → Resolve → option | 4 |
| Resolve a task (log it / skip this cycle) | Card → detail sheet → Update → Resolve → option | 4 |
| Delete a task | Card → detail sheet → Update → Adjustments tab → Delete task → confirm | 6 |
| Delete a log | Card → detail sheet → Update → Delete → confirm | 5 |
| Delete a squawk | **Not possible.** | — |

The last row is a real gap, not just friction. A squawk entered by mistake, a duplicate, or a test
entry can only be *dismissed*, which keeps it forever in the Closed list. Users on a shared Thing
have asked why a wrong entry cannot simply be removed the way a task can.

Swipe-to-reveal on a list card is the standard mobile answer to both problems: it puts the one or
two most common actions one gesture away without adding chrome to the card.

## 2. Goals

- **G1.** A horizontal swipe on any squawk, task, or log card reveals quick actions; tapping one
  performs it without leaving the tab.
- **G2.** The same swipe direction always means the same kind of thing across the three tabs:
  one side is the *constructive* action, the other is *Delete*.
- **G3.** Squawks can be deleted, from the swipe reveal and from the squawk edit form.
- **G4.** Nothing destructive happens on a gesture alone. Delete always confirms.
- **G5.** Every quick action stays reachable without the gesture, so keyboard, screen reader, and
  mouse users lose nothing.

## 3. Non-Goals

- **Undo.** Deletion is confirmed, not undone. The local-first store syncs deletes promptly and a
  record's comment thread goes with it; a snackbar Undo would have to resurrect both. Out of scope
  for V1; see design doc §9.
- **Reordering, archiving, pinning, or multi-select.** One card, one gesture.
- **Swipe on the wide-layout Logs table.** On tablet and web widths the Logs tab renders a table,
  not cards; table rows get no gesture. A row-level overflow menu is a possible follow-up.
- **Swipe on cards embedded outside a list**, such as the Overview tab's "Next due" rail card.
- **A first-run coach mark or peek animation.** The design system forbids decorative motion, and
  G5 guarantees a non-gesture path to every action.
- **Cascade rewrites on delete.** Deleting a squawk or task does not edit the logs that reference
  it, matching today's task-delete behaviour.

## 4. Users and Stories

- **Owner-operator, phone in hand at the hangar.** "I wrote the squawk twice. Let me swipe the
  duplicate away." → swipe, Delete, confirm.
- **Same person, after a fix.** "That brake squawk is done — I logged it yesterday." → swipe,
  Resolve, Fixed, pick the log.
- **Technician on a shared Thing.** "The owner skipped this cycle on their own; I'll mark it." →
  swipe the task, Resolve, Skip this cycle, confirm.
- **Anyone who fat-fingered a log entry.** → swipe, Delete, confirm. Or swipe the other way to
  jump straight into Update.
- **Screen-reader user.** Focuses a card, hears "Resolve" and "Delete" as custom actions, activates
  one. Same dialogs, same outcome.

## 5. Requirements

### 5.1 The gesture

- **R1.** Cards in the Squawks, Tasks, and Logs tabs support a horizontal drag. Dragging past a
  small threshold reveals an action panel behind the card on that side; releasing snaps the card to
  the fully open or fully closed position, whichever is nearer.
- **R2.** The reveal is *reveal-and-tap*, not fling-to-commit. Every action either opens a menu,
  opens a confirmation, or navigates, so a long swipe has nothing to save the user. This also makes
  an accidental swipe harmless.
- **R3.** At most one card is open at a time. Opening another, tapping the open card, tapping
  anywhere outside it, scrolling the list, or switching the Open/Closed filter closes it.
- **R4.** The gesture is available on every platform that renders the card list (phone, tablet
  grid, web). Vertical scrolling must not be captured; the drag only claims the pointer once
  horizontal intent is clear.
- **R5.** The card's tap-to-open behaviour is unchanged when the card is closed.

### 5.2 Direction and actions

Leading = swipe **start-to-end** (left-to-right in LTR), trailing = swipe **end-to-start**. The
trailing side is always Delete. The leading side is the record's constructive next step and depends
on its state.

| Tab | Record state | Leading (constructive) | Trailing |
|---|---|---|---|
| Squawks | Open | **Resolve** → Fixed / Dismiss | **Delete** |
| Squawks | Dismissed | **Reopen** | **Delete** |
| Squawks | Addressed | *(none)* | **Delete** |
| Tasks | Due / due soon / overdue / on condition | **Resolve** → Create work log / Skip this cycle | **Delete** |
| Tasks | Complied (History filter) | *(none)* | **Delete** |
| Logs | any | **Update** | **Delete** |

- **R6.** "Resolve" on a squawk opens the same two-option bubble the edit form shows today
  (*Fixed* / *Dismiss — no work planned*). *Fixed* navigates to Create Log with the squawk
  pre-linked; *Dismiss* opens the existing dismiss-reason dialog.
- **R7.** "Resolve" on a task opens the same two-option bubble the edit form shows today
  (*Create work log* / *Skip this cycle*). *Create work log* navigates to Create Log with the task
  pre-linked; *Skip this cycle* opens the existing skip confirmation and applies the same write.
- **R8.** "Update" on a log navigates to the log edit form, exactly as the detail sheet's Update.
- **R9.** A side with no action does not reveal. The card simply does not move in that direction.

### 5.3 Delete

- **R10.** Delete on any card opens a confirmation dialog naming the record. Confirm is red and
  labelled *Delete*; cancel closes the reveal too.
- **R11.** Squawk delete confirmation copy explains what is *not* removed: the addressing log (if
  any) stays, and comments on the squawk are removed. It also nudges toward Dismiss for a defect
  that was real but is no longer relevant: deletion is for entries made in error.
- **R12.** Task and log delete reuse today's dialogs and copy.
- **R13.** After a successful delete the card leaves the list and a short snackbar confirms
  ("Squawk deleted" / "Task deleted" / "Log deleted", noun taken from the Thing's lexicon). On
  failure the existing "Delete failed" snackbar shows and the card stays.
- **R14.** Deleting a squawk removes the squawk record and its comment thread, and the existing
  record-deletion garbage collection reclaims its attachments — the same lifecycle tasks and logs
  already have.

### 5.4 Squawk delete in the edit form

- **R15.** The squawk edit form gains a **Delete squawk** section at the bottom of the Details tab,
  visually identical to the task form's *Delete task* card (destructive-tinted card with a trash
  icon). It is present only when editing, never when adding.
- **R16.** Tapping it opens the same confirmation as R10/R11. On confirm the form closes to the
  Squawks tab with the R13 snackbar.
- **R17.** The bottom button bar's danger slot keeps its current job (Resolve / Reopen). Delete does
  not compete with it.

### 5.5 Who may do this

- **R18.** Quick actions follow the same rule as today's edit and delete entry points: anyone who
  can open the edit form can swipe. There is no new client-side role check; Firestore rules remain
  the enforcement, as for every other write on a shared Thing.

### 5.6 Accessibility and input

- **R19.** Each card exposes its available quick actions as accessibility custom actions, so
  TalkBack and VoiceOver users can trigger them from the card without a gesture.
- **R20.** Every quick action has a non-gesture route: Resolve, Reopen, and Delete in the edit form;
  Update in the detail sheet.
- **R21.** With a mouse or trackpad the same drag works. Nothing hover-only is introduced.

### 5.7 Analytics

- **R22.** One event, `record_quick_action`, with the surface (`squawks` / `tasks` / `logs`), the
  action (`resolve`, `reopen`, `update`, `delete`), and the source (`swipe`, `a11y`, `form`), fired
  when the user *commits* an action — after the confirmation for Delete and Skip, on selection for
  the rest.

## 6. UX

### 6.1 The reveal

```
Closed                                Trailing revealed (swiped left)
┌────────────────────────────────┐    ┌──────────────────────┐┌────────┐
│ [MEDIUM]                    →  │    │ [MEDIUM]             ││        │
│ Left brake dragging            │    │ Left brake dragging  ││ Delete │
│ 05/10/2026                     │    │ 05/10/2026           ││  🗑    │
└────────────────────────────────┘    └──────────────────────┘└────────┘

Leading revealed (swiped right)
┌─────────┐┌───────────────────────┐
│         ││ [MEDIUM]              │
│ Resolve ││ Left brake dragging   │
│   ✓     ││ 05/10/2026            │
└─────────┘└───────────────────────┘
```

- The action panel is a flat, full-height block behind the card, same corner radius, no shadow.
  Trailing/Delete uses the error container; leading uses the positive container (Resolve, Reopen) or
  the primary container (Update). Icon above a short label, both in the container's on-colour.
- The card itself does not tint or shrink; it slides. Motion is the drag itself plus a
  150–250 ms ease-out settle, per the design system's "state feedback only" rule.
- Tapping Resolve pops the existing resolve bubble anchored to the revealed button; choosing an
  option closes the reveal.

### 6.2 Squawk delete confirmation

> **Delete squawk?**
> "Left brake dragging" is removed for everyone with access, along with its comments. The work log
> that addressed it, if any, is kept. Deletion is for entries made in error — if the problem was
> real but no longer applies, Dismiss it instead. This cannot be undone.
>
> [Cancel]  [**Delete**]

"squawk" comes from the Thing's lexicon; on a car preset the title reads "Delete issue?".

### 6.3 Edit form

The Details tab of the squawk form ends with the destructive card:

```
┌──────────────────────────────────────┐
│ (🗑)  Delete this squawk             │
│       Permanently removes it and its │
│       comments. Logs are kept.       │
└──────────────────────────────────────┘
```

## 7. Success Criteria

- Resolve and Delete on a card each take ≤ 2 taps after the swipe (including confirmation).
- No accidental deletions: every delete passes a dialog; a swipe with no follow-up tap changes
  nothing.
- Vertical scroll performance and behaviour in the three lists are unchanged (no jank from the
  gesture handler, no stolen scrolls).
- `record_quick_action` shows up in GA4 within a week of release; the split between `swipe` and
  `form` sources tells us whether the gesture is being discovered.

## 8. Open Questions

1. **Discoverability without a hint.** We rely on the swipe being a learned mobile convention. If
   the `swipe` share of `record_quick_action` stays negligible after a release, revisit a one-time
   subtle hint under the filter row (text, not motion).
2. **Dangling references after a log delete.** A squawk whose addressing log is deleted still reads
   *Addressed* because status is derived from a non-empty `addressed_by_log_id`. This predates the
   feature (the log form can delete today) and is out of scope, but swipe makes log deletion easier
   and therefore the gap more visible. Tracked as a follow-up.
