# PRD: Card Swipe Actions and Squawk Deletion

**Design doc:** [`card_swipe_actions_design.md`](card_swipe_actions_design.md)
**Status:** 📝 Proposed
**Last updated:** 2026-09-06

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

- **G1.** A horizontal swipe on any squawk, task, or log card reveals its quick actions; tapping one
  performs it without leaving the tab.
- **G2.** Direction does not matter. Swiping either way reveals the same set of actions, so there is
  nothing to memorise per tab or per side.
- **G3.** Squawks can be deleted, from the swipe reveal and from the squawk edit form.
- **G4.** Nothing destructive happens on a gesture alone. Delete always confirms.
- **G5.** Every quick action stays reachable without the gesture, so keyboard, screen reader, and
  mouse users lose nothing.
- **G6.** A delete from a card is a delete like any other: collaborators on a shared Thing are
  notified exactly as they are today when a record is removed from the edit form.

## 3. Non-Goals

- **Undo.** Deletion is confirmed, not undone. The local-first store syncs deletes promptly and a
  record's comment thread goes with it; a snackbar Undo would have to resurrect both. Out of scope
  for V1; see design doc §9.
- **Update / Edit as a quick action.** Opening the record already leads to Update in one tap; a
  swipe would not shorten it enough to earn a slot.
- **Reopen as a quick action.** Reopening a dismissed squawk is rare and deliberate; it stays in
  the edit form.
- **Reordering, archiving, pinning, or multi-select.** One card, one gesture.
- **Swipe on the wide-layout Logs table.** On tablet and web widths the Logs tab renders a table,
  not cards; table rows get no gesture. A row-level overflow menu is a possible follow-up.
- **Swipe on cards embedded outside a list**, such as the Overview tab's "Next due" rail card.
- **A first-run coach mark or peek animation.** The design system forbids decorative motion, and
  G5 guarantees a non-gesture path to every action.
- **Cascade rewrites on delete.** Deleting a squawk or task does not edit the logs that reference
  it, matching today's task-delete behaviour. (Log delete leaving a squawk stale is a separate,
  pre-existing gap: [#815](https://github.com/fz172/squawkit/issues/815).)

## 4. Users and Stories

- **Owner-operator, phone in hand at the hangar.** "I wrote the squawk twice. Let me swipe the
  duplicate away." → swipe, Delete, confirm.
- **Same person, after a fix.** "That brake squawk is done — I logged it yesterday." → swipe,
  Resolve, Fixed, pick the log.
- **Technician on a shared Thing.** "The owner skipped this cycle on their own; I'll mark it." →
  swipe the task, Resolve, Skip this cycle, confirm.
- **Anyone who fat-fingered a log entry.** → swipe, Delete, confirm.
- **Co-owner elsewhere.** Gets the same "removed the log entry …" notification they would get if
  the deletion had come from the edit form.
- **Screen-reader user.** Focuses a card, hears "Resolve" and "Delete" as custom actions, activates
  one. Same dialogs, same outcome.

## 5. Requirements

### 5.1 The gesture

- **R1.** Cards in the Squawks, Tasks, and Logs tabs support a horizontal drag in either direction.
  Dragging past a small threshold reveals the card's action panel behind it on that side;
  releasing snaps the card to fully open or fully closed, whichever is nearer.
- **R2.** The reveal is *reveal-and-tap*, not fling-to-commit. Every action either opens a menu or
  a confirmation, so a long swipe has nothing to save the user. This also makes an accidental
  swipe harmless.
- **R3.** Both directions reveal the **same** actions. The panel on the left and the panel on the
  right are the same buttons in the same order.
- **R4.** The card stops after sliding just far enough to show every action. Each action is an icon
  with a one- or two-word label; actions sit side by side, so a card with one action opens about
  one button's width and a card with two opens about two.
- **R5.** At most one card is open at a time. Opening another, tapping the open card, tapping
  anywhere outside it, scrolling the list, or switching the filter toggle closes it.
- **R6.** The gesture is available on every platform that renders the card list (phone, tablet
  grid, web). Vertical scrolling must not be captured; the drag only claims the pointer once
  horizontal intent is clear.
- **R7.** The card's tap-to-open behaviour is unchanged when the card is closed.

### 5.2 Actions per record state

| Tab | Record state | Actions revealed (either direction) |
|---|---|---|
| Squawks | Open | **Resolve** · **Delete** |
| Squawks | Dismissed or Addressed | **Delete** |
| Tasks | Due / due soon / overdue / on condition | **Resolve** · **Delete** |
| Tasks | Complied (History filter) | **Delete** |
| Logs | any | **Delete** |

- **R8.** *Resolve* on a squawk opens a two-option speech bubble — *Fixed* / *Dismiss, no work
  planned* — with the same options the edit form offers. The bubble is **anchored to the Resolve
  button itself**: its tail points at that button, above it when there is room and below it when
  the card is near the top of the screen. It must not float up from the bottom bar the way the
  form's bubble does, because there is no bottom bar here and the pointer would be meaningless.
  *Fixed* navigates to Create Log with the squawk pre-linked; *Dismiss* opens the existing
  dismiss-reason dialog.
- **R9.** *Resolve* on a task opens the equivalent bubble — *Create work log* / *Skip this cycle* —
  anchored to its Resolve button the same way. *Create work log* navigates to Create Log with the
  task pre-linked; *Skip this cycle* opens the existing skip confirmation and applies the same
  write.
- **R10.** Choosing any bubble option, or dismissing the bubble, closes the card's reveal.

### 5.3 Delete

- **R11.** Delete on any card opens a confirmation dialog naming the record. Confirm is red and
  labelled *Delete*; cancel closes the reveal too.
- **R12.** Squawk delete confirmation copy explains what is *not* removed: the addressing log (if
  any) stays, and comments on the squawk are removed. It also nudges toward Dismiss for a defect
  that was real but is no longer relevant: deletion is for entries made in error.
- **R13.** Task and log delete reuse today's dialogs and copy.
- **R14.** After a successful delete the card leaves the list and a short snackbar confirms
  ("Squawk deleted" / "Task deleted" / "Log deleted", noun taken from the Thing's lexicon). On
  failure the existing "Delete failed" snackbar shows and the card stays.
- **R15.** Deleting a squawk removes the squawk record and its comment thread, and the existing
  record-deletion garbage collection reclaims its attachments — the same lifecycle tasks and logs
  already have.
- **R16.** Every delete, from any surface, produces the collaboration notification other members
  already receive for a deletion ("*Name* removed the log entry …"). Concretely: a card delete
  must go through the same manager method as the form delete, which writes a synced tombstone; the
  server treats that tombstone as a `deleted` activity and fans it out. No new notification code,
  but no shortcut around the managers either.

### 5.4 Squawk delete in the edit form

- **R17.** The squawk edit form gains a **Delete** section at the bottom of the Details tab,
  visually identical to the task form's *Delete task* card (destructive-tinted card with a trash
  icon). Its title and body use the Thing's lexicon noun, so it reads "Delete this squawk" on an
  airplane and "Delete this issue" on a car. It is present only when editing, never when adding.
- **R18.** Tapping it opens the same confirmation as R11/R12. On confirm the form closes to the
  Squawks tab with the R14 snackbar.
- **R19.** The bottom button bar's danger slot keeps its current job (Resolve / Reopen). Delete does
  not compete with it.

### 5.5 Who may do this

- **R20.** Quick actions follow the same rule as today's edit and delete entry points: anyone who
  can open the edit form can swipe. There is no new client-side role check; Firestore rules remain
  the enforcement, as for every other write on a shared Thing.

### 5.6 Accessibility and input

- **R21.** Each card exposes its available quick actions as accessibility custom actions, so
  TalkBack and VoiceOver users can trigger them from the card without a gesture.
- **R22.** Every quick action has a non-gesture route: Resolve and Delete in the edit form.
- **R23.** With a mouse or trackpad the same drag works. Nothing hover-only is introduced.

### 5.7 Lexicon

- **R24.** Every user-visible word that names the record — button labels, dialog titles, dialog
  bodies, snackbars, the form's delete card — takes its noun from `LocalThingLexicon`. No string
  in this feature hard-codes "squawk", "task", or "log".

### 5.8 Analytics

- **R25.** One event, `record_quick_action`, with the surface (`squawks` / `tasks` / `logs`), the
  action (`resolve`, `skip`, `delete`), and the source (`swipe`, `a11y`, `form`), fired when the
  user *commits* an action — after the confirmation for Delete and Skip, on selection for Fixed /
  Dismiss / Create work log.

## 6. UX

### 6.1 The reveal

Two-action card (open squawk), swiped left and swiped right. Same buttons, same order:

```
Closed
┌──────────────────────────────────────────┐
│ [MEDIUM]                              →  │
│ Left brake dragging                      │
│ 05/10/2026                               │
└──────────────────────────────────────────┘

Swiped left                                 Swiped right
┌──────────────────────┐┌────────┐┌───────┐ ┌────────┐┌───────┐┌──────────────────────┐
│ [MEDIUM]             ││   ✓    ││  🗑   │ │   ✓    ││  🗑   ││ [MEDIUM]             │
│ Left brake dragging  ││Resolve ││Delete │ │Resolve ││Delete ││ Left brake dragging  │
│ 05/10/2026           ││        ││       │ │        ││       ││ 05/10/2026           │
└──────────────────────┘└────────┘└───────┘ └────────┘└───────┘└──────────────────────┘

One-action card (log), swiped left
┌────────────────────────────────┐┌───────┐
│ [ENGINE]     1428.1 hrs        ││  🗑   │
│ Replaced left magneto          ││Delete │
│ 05/10/2026     J. Rivera       ││       │
└────────────────────────────────┘└───────┘
```

- Each action is a flat, full-height block behind the card, sharing the card's corner radius on
  the outer edge, no shadow. Delete uses the error container; Resolve uses the positive container.
  Icon above a one- or two-word label, both in the container's on-colour.
- The card itself does not tint or shrink; it slides. Motion is the drag itself plus a
  150–250 ms ease-out settle, per the design system's "state feedback only" rule.

### 6.2 The resolve bubble, anchored to its button

```
              ┌─────────────────────────────────┐
              │ (✓)  Fixed                      │
              │ ─────────────────────────────── │
              │ (×)  Dismiss — no work planned  │
              └───────────────▼─────────────────┘
┌──────────────────────┐┌────────┐┌───────┐
│ [MEDIUM]             ││   ✓    ││  🗑   │
│ Left brake dragging  ││Resolve ││Delete │
└──────────────────────┘└────────┘└───────┘
```

The tail sits under the bubble at the horizontal centre of the Resolve button, even when the
bubble has been shifted sideways to stay on screen. If there is no room above the card the bubble
opens below with the tail on its top edge. This is a change to the shared bubble component; the
edit form keeps its bottom-bar placement.

### 6.3 Squawk delete confirmation

> **Delete squawk?**
> "Left brake dragging" is removed for everyone with access, along with its comments. The work log
> that addressed it, if any, is kept. Deletion is for entries made in error — if the problem was
> real but no longer applies, Dismiss it instead. This cannot be undone.
>
> [Cancel]  [**Delete**]

"squawk" is the lexicon noun. On a car preset the title reads "Delete issue?".

### 6.4 Edit form

The Details tab of the squawk form ends with the destructive card. `{squawk}` is the lexicon noun:

```
┌──────────────────────────────────────┐
│ (🗑)  Delete this {squawk}           │
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
- A card delete on a shared Thing produces the same collaborator notification as a form delete
  (asserted by the existing fan-out test, which already covers the tombstone path).
- `record_quick_action` shows up in GA4 within a week of release; the split between `swipe` and
  `form` sources tells us whether the gesture is being discovered.

## 8. Open Questions

1. **Discoverability without a hint.** We rely on the swipe being a learned mobile convention. If
   the `swipe` share of `record_quick_action` stays negligible after a release, revisit a one-time
   subtle hint under the filter row (text, not motion).
2. **Dangling references after a log delete.** A squawk whose addressing log is deleted still reads
   *Addressed* because status is derived from a non-empty `addressed_by_log_id`. This predates the
   feature (the log form can delete today) and is out of scope here, but swipe makes log deletion
   easier and therefore the gap more visible. Tracked as
   [#815](https://github.com/fz172/squawkit/issues/815).
