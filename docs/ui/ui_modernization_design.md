# Design Doc: UI Modernization

**Status:** 📋 Proposed — tracked in project #15, issues #1071–#1101
**Last updated:** 2026-09-19
**Mocks:** before/after canvas — https://claude.ai/artifact/C2MnVWeHCmMR9af1euAD1f

---

## 1. Overview

A survey of the shipped UI against `DESIGN.md` and `PRODUCT.md` found that most of what makes the
app feel dated is not taste, it is drift: the design system says one thing and the code does
another. The neutral palette is not the one the doc describes, uppercase has spread well past
buttons, nine files re-implement the same card, and several documented affordances
(`hasDashboardRail`, a MEDIUM nav rail) do not exist at all.

This doc breaks the work into **21 units that each ship on their own**. There is no big-bang
redesign branch, no long-lived feature flag, and no PR that only makes sense once a later one
lands.

### What "ships alone" means here

Each unit must satisfy all four:

1. **Merges to `main` and goes to users as-is.** No half-migrated state that looks broken.
2. **Is coherent to a user.** Either a visible improvement or invisible; never a visible regression
   waiting on a follow-up.
3. **Does not require another unit to land first**, except where §7 records a hard dependency
   (there are three).
4. **Carries its own `DESIGN.md` change**, so the doc is never behind the code.

Units are sized for one PR each. Where a change touches every feature (the row component, the form
chrome), the unit lands the `core/ui` piece *and* migrates every call site in the same PR, rather
than leaving two idioms alive across releases.

### Not a goal

Re-theming. The instrument palette, Space Grotesk, JetBrains Mono and the `StatusTier` semantics all
stay exactly as `DESIGN.md` §2–3 define them. UI-1 makes the neutrals match the doc; it does not
change the brand.

---

## 2. What exists today, verified

| Piece           | Where                                                                                                                                                                                                                     | Finding                                                                                                                                                                                                                                                                                                                                                                               |
|-----------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Colour scheme   | `core/ui/theme/Theme.kt:11-45`                                                                                                                                                                                            | Overrides `primary`/`secondary`/`tertiary` and their containers only. Every `surface`, `surfaceContainer*`, `surfaceVariant`, `outline`, `background` and `error` falls through to the **M3 baseline**, which is violet-tinted. `DESIGN.md` §4 says each surface step warms toward the primary blue; it does not. `.impeccable/design.json` states card `#EEF1F6`; `#F3EDF7` renders. |
| Card recipe     | `TaskCard.kt:72`, `SquawkCard.kt:62`, `MaintenanceLogCard.kt:79`, `DataLogCard.kt:57`, `LogStatsSection.kt:46`, `CriticalAlertSection.kt:55`, `AogAlertSection.kt:48`, `ThingDataCard.kt:70`, `MaintenanceLogTable.kt:76` | Nine copies of `Card(surfaceContainer, BorderStroke(hairline, outlineVariant), 12dp, elevation = 0)`. The hairline exists because the tonal ramp does not separate.                                                                                                                                                                                                                   |
| List rows       | repo-wide                                                                                                                                                                                                                 | `androidx.compose.material3.ListItem` is used **zero times**.                                                                                                                                                                                                                                                                                                                         |
| Loading         | `MaintenanceLogListContent.kt:211`, `DataLogSectionContent.kt:116`, `ThingSectionContent.kt:412`                                                                                                                          | Centred `CircularProgressIndicator` replaces the whole list including the filter bar. `grep -i "shimmer\|skeleton"` → nothing. `SquawkTab` and `MaintenanceTasksTab` have no loading branch at all, so a cold open flashes the empty state.                                                                                                                                           |
| Sort / grouping | `SquawkTab.kt:108`, `MaintenanceLogListViewModel.kt:160`, `ComplianceSection.kt:112`                                                                                                                                      | No sort UI anywhere; order is fixed in code. No grouping — the comment at `ComplianceSection.kt:112` says so explicitly.                                                                                                                                                                                                                                                              |
| Laziness        | `SquawkTab.kt:190`, `MaintenanceTasksTab.kt:142`, `AdaptiveCardList.kt:23`                                                                                                                                                | Squawks and tasks compose every card eagerly inside `Column(verticalScroll)`. Only logs and data logs use `LazyColumn`.                                                                                                                                                                                                                                                               |
| Wide tiers      | repo-wide                                                                                                                                                                                                                 | No `ListDetailPaneScaffold` / `SupportingPaneScaffold` / `androidx.compose.material3.adaptive` usage. `material3-adaptive-navigation-suite` is an `api` dependency (`core/ui/build.gradle.kts:48`, `core/ui/adaptive/build.gradle.kts:47`) and `NavigationSuiteScaffold` appears nowhere.                                                                                             |
| Dead code       | `LayoutTier.kt:38`, `AdaptiveAppShell.kt:263-267`                                                                                                                                                                         | `hasDashboardRail` is declared and unit-tested but referenced by zero production code. The KDoc promises a MEDIUM `NavigationSuiteScaffold` rail; `hasFullSidebar` includes MEDIUM, so it never renders.                                                                                                                                                                              |
| Detail on wide  | `DetailSheet.kt:131-170`                                                                                                                                                                                                  | A fixed 460dp `Dialog(usePlatformDefaultWidth = false)` floating over the empty half of the screen, with no enter/exit animation.                                                                                                                                                                                                                                                     |
| Motion          | repo-wide                                                                                                                                                                                                                 | Zero `enterTransition`/`exitTransition` on any nav destination. Zero `Crossfade`, `AnimatedContent`, `animateContentSize`, `animateItem`, `SharedTransitionLayout`. Section switching is a bare state swap (`AdaptiveAppShell.kt:318`). `DESIGN.md` §6 requires continuity motion — a screen that teleports makes the user re-read it.                                                                                 |
| Form fields     | `FormField.kt:86`, `:232`, `:113-114`                                                                                                                                                                                     | `FormTextField` uppercases its label. `FormValueField` draws a tappable picker and a genuinely read-only value the same way — label + plain text, no border or chevron — so `LogWorkTab.kt:58` (tappable, opens the date picker) is indistinguishable from the component block above it (locked by `EditTaskScreen.kt:323`). One shared `required` string is the error message for every field in the app, and `supportingText` and error text share a slot.                                                                                                          |
| Form actions    | `BottomButtons.kt:38`                                                                                                                                                                                                     | Up to three 56dp buttons in one row. The middle slot is Delete on a work log and Resolve on a task — same position, opposite consequence. Tasks and squawks additionally carry a Delete in a danger zone, so deletion has two homes.                                                                                                                                                  |
| Nav bar         | `FloatingPillNavigationBar.kt:75-108`, `ThingSectionContent.kt:127-180`                                                                                                                                                   | Five destinations, unselected icon-only, selected expands to a labelled pill. `LocalNavPillClearance` (`:37`) exists; the section FAB does not respect it. DASHBOARD and SETTINGS get no FAB.                                                                                                                                                                                         |
| Status          | `StatusColors.kt:19-56`, `StatusChip.kt:22`                                                                                                                                                                               | `StatusTier` → `StatusTone` is centralised and consistent. This is the strongest part of the system and does not change.                                                                                                                                                                                                                                                              |
| Preview banner  | `PreviewBanner.kt:45`, `TaskViewModel.previewDue():328`                                                                                                                                                                   | Recomputes next-due as you edit the schedule. Keep.                                                                                                                                                                                                                                                                                                                                   |
| Swipe actions   | `SwipeActionCard.kt:163`                                                                                                                                                                                                  | The most considered component in the codebase. Carries Resolve and Delete on squawk and task rows already.                                                                                                                                                                                                                                                                            |

Screenshots in `docs/product/screenshots/` are **stale** — they show a four-destination nav bar with
text labels. The current build has five destinations with icon-only unselected items, and a Data
Logs Archive section. Regenerate them as part of UI-21.

---

## 3. Foundations

### UI-0 · Amend `DESIGN.md` to match what we intend

`AGENTS.md` says `DESIGN.md` is the source of truth and to update it rather than patch around it.
Six statements in it are either untrue today or block work below, so the doc changes first.

- **§4 elevation** — state the neutral ramp explicitly (UI-1 implements it) instead of "warmer
  toward the primary blue", which is aspirational.
- **§3 uppercase** — scope the Uppercase Commitment Rule to **button labels and status badges**.
  Today it is read as licence for screen titles, section labels, field labels and values.
- **§6 motion** — replace "no decorative motion" with "no decorative motion; continuity motion is
  required". Shared-axis, container transform and list-item animation explain where things went;
  they are not decoration. `SwipeActionCard.kt:128-137` already argues this in a code comment.
- **§6 hero metrics** — the ban on hero-metric grids is violated by `LogStatsSection`. Either the
  rule narrows to *gradient-accented marketing* grids, or the section goes. UI-11 assumes the
  former plus a rework.
- **§8 dashboard order** — record hero → attention → data card → work logs, and retire the
  collapse-when-overdue rule (UI-11 explains why it becomes unnecessary).
- **§9 component reference** — add `ListRow`, `SectionHeader`, `DangerZone`, `SkeletonList`.

**Ships alone:** documentation only. **Depends on:** nothing. **Unblocks:** UI-1, UI-4, UI-11, UI-18.

---

### UI-1 · Author the neutral ramp

Fill in every M3 neutral role in `lightColorScheme`/`darkColorScheme` on the aviation hue (≈251) so
`background`, `surface`, `surfaceContainerLow/​High/Highest`, `surfaceVariant`, `outline`,
`outlineVariant` and their `on*` pairs are cool blue-grey rather than M3's violet baseline.

Proposed dark values are on the canvas **Foundations** board: `#0A0E14` background, `#10151D`
containerLow, `#141A24` container, `#1D2532` containerHigh, `#2A3442` outlineVariant, `#9BA8BC`
onSurfaceVariant. Light gets the mirrored ramp.

- **Files:** `core/ui/theme/Theme.kt`, `core/ui/theme/Color.kt`, `.impeccable/design.json`.
- **Ships alone:** every existing border and surface still renders; cards simply stop needing the
  border to be visible. Nothing else has to change in the same release.
- **Verify:** every screen in light and dark; check `StatusTone` contrast still passes 4.5:1 against
  the new containers (`StatusColors.kt` values are unchanged but their backgrounds move).
- **Depends on:** UI-0 (§4 wording).

### UI-2 · One `ListRow`, and delete the hairline

Add `ListRow` to `core/ui/common/compose`: leading slot, title, metadata line, trailing slot, 72dp,
one-line truncation. Migrate all nine card sites to it or to a plain `Surface`, and drop the
`BorderStroke(hairline, outlineVariant)` from every card that no longer needs it. Containment stays
only where an item is genuinely set apart — the down-state squawk, the Thing data card.

- **Files:** new `core/ui/common/compose/ListRow.kt`; the nine files in §2.
- **Ships alone:** one PR replaces every call site, so no release has two row idioms.
- **Depends on:** **UI-1 (hard).** Removing borders before the ramp lands makes cards invisible in
  dark mode.

### UI-3 · Motion tokens, and the first two uses

Add `core/ui/theme/Motion.kt` with durations and easings mirroring `Spacing.kt`. Apply immediately to
(a) section switching in `AdaptiveAppShell.kt:318` via `AnimatedContent` with a shared-axis
transition, and (b) `animateItem()` on the log and data-log `LazyColumn`s.

- **Ships alone:** a tokens-only PR has no user value; pairing it with two uses gives the release
  something to show and proves the tokens.
- **Depends on:** UI-0 (§6 motion rule).

---

## 4. Cross-cutting components

### UI-4 · Roll uppercase back to buttons and badges

Screen titles, section labels, field labels and values become sentence case. `FormTextField` stops
calling `.uppercase()` on its label (`FormField.kt:86`); `FormSectionLabel` (`:315`) drops the 1.2sp
tracking and the caps. Button labels and `StatusChip` keep uppercase.

- **Touches `strings.xml` values**, so `StringSnapshotTest` rows need rewriting — per the existing
  rule, rewrite the TSV rows, never regenerate.
- **Watch:** apostrophes are written literally in `strings.xml`; `\'` renders as a backslash.
- **Depends on:** UI-0 (§3 scope).

### UI-5 · Tell editable fields apart from locked ones

The problem is not that pickers look unselectable. It is that **a locked value and a tappable picker
are drawn identically**, and a locked one never says why it is locked.

Some fields are chosen at creation and immutable afterwards, by design. `EditTaskScreen.kt:323`
passes `onComponentChange = null` where `AddTaskScreen.kt:281` passes a real callback, and
`TaskIdentityTab.kt:59` documents it: *"Pass null for `onComponentChange` to render that section
read-only."* `IdentityRadioItem` (`TaskIdentityTab.kt:270-291`) then drops the radio entirely and
prints the label. So the `COMPONENT / AIRFRAME` block on the task edit form is a correct read-out of
a value that genuinely cannot change — not a broken picker. Same for `COMPLIANCE TYPE / ROUTINE`.

The real casualty is the field next to it. The work log's date (`LogWorkTab.kt:58`) **is** tappable —
`onClick = onDateClick` opens the date picker — and renders as a calendar icon plus plain text with
no border and no chevron, indistinguishable from the locked component above it. Its placeholder
string is literally `tap_to_change_date`, which is copy compensating for a missing affordance.

Two halves, one PR:

- **Editable** — `FormValueField` with an `onClick` (`FormField.kt:232`) gains a border, a trailing
  chevron and a 48dp minimum height. Six call sites: `LogWorkTab.kt:58`, `LogRecordsTab.kt:73`,
  `SubComponentDropdown.kt:37`, `SquawkBasicSection.kt:67`, `SpecFieldInput.kt:58`,
  `CertificationInputFields.kt:250`.
- **Locked** — keep the plain read-out, which is deliberate and right, but make it legibly a
  read-out rather than an unstyled twin of the editable one, and give it a reason line: *"Set when
  the task was created."* A user who cannot change a field deserves to know that is the intent
  rather than assume the tap missed.

`IdentityRadioItem`'s read-only branch also calls `label.uppercase()` (`:276`); UI-4 removes that.

- **Ships alone:** a `core/ui` change plus the one read-only branch, improving every form at once.
- **Note:** the two halves must land together. Restyling only the editable one leaves the locked
  value looking like a picker that stopped working.

### UI-6 · Two-button action bar, one home for delete

`BottomButtons` (`BottomButtons.kt:38`) narrows to Cancel + primary. Add `DangerZone` to `core/ui`
and move Delete into it at the end of the form for all four entities — tasks and squawks already have
one, work log and Thing gain it. Reserve bottom padding equal to the bar height so the bar never
overlays fields (today `BLADE 1/2/3` sit behind it on the Thing form).

Resolve leaves the bar for the detail sheet (§9 D1), in the entity-specific shape D2 sets out:
*Resolve* with Fixed / Dismiss on a squawk, *Log work* with *Skip this cycle* on a task. The two are
not the same action and must not share a design.

- **Files:** `BottomButtons.kt`, new `DangerZone.kt`, the four form screens.
- **Ships alone:** all four forms move together, so the bar means one thing in every release.

### UI-7 · Labelled, scrollable tabs

`IconLabelTabRow` always renders each tab's label and scrolls horizontally when the row overflows.
The task form has five tabs; four of them are currently an unlabelled pencil, info, calendar, sliders
and speech glyph.

### UI-8 · Skeletons instead of full-screen spinners

Add `SkeletonList` to `core/ui` and use it in `MaintenanceLogListContent.kt:211`,
`DataLogSectionContent.kt:116` and `ThingSectionContent.kt:412`. Give `SquawkTab` and
`MaintenanceTasksTab` a loading branch — they currently render the empty state while loading.

### UI-9 · Sticky section headers

Add `SectionHeader` and make it sticky inside a `LazyColumn`. Required by UI-10, UI-12 and UI-13,
each of which groups a list.

---

## 5. Per-section work

Ordering is fixed per list and there is **no sort control anywhere**: squawks are always by
priority, work logs and data logs always newest first. Search and filters remove rows; they never
reorder. This is what makes grouping and the log spine safe to rely on.

### UI-10 · Squawks: group by priority

Group into Grounded / High / Medium / Low using the lexicon's `down_status` for the top tier. The
header carries the tier name and count, so rows drop their priority badge and their status dot. Only
the down-state group keeps a container. Rows become two-line with a truncated description.

- **Also:** move `SquawkTab` to a `LazyColumn` so headers can be sticky and `animateItem` works.

### UI-11 · Dashboard: reorder, and tell the truth about meters

- **Order:** hero → *Needs attention* (3 rows + count + "All tasks") → Thing data card → *Work logs ·
  N*. Attention above the card removes the need for the amber summary strip, which duplicated the
  list below it, and retires the collapse-when-overdue rule.
- **Data card** stays expanded, absorbing the meters and the component tree
  (Engine → Propeller → Blades, serials in mono) and keeping *Manage access* / *Update*.
- **Meters are plain numbers plus an as-of date** from the latest log. No progress bars: the app has
  no overhaul interval to count down to, and inventing one is a lie. Per-task due information already
  lives in *Needs attention*.
- **`LogStatsSection` is deleted**; its log count becomes the *Work logs · N* section header, which is
  also a link.

**Depends on:** UI-0 (§8 order, §6 hero-metric rule).

### UI-12 · Work logs: month headers and the meter spine

Month group headers, a left gutter carrying the meter reading, a connector between adjacent entries,
a one-line summary as the row title and the full description left to the detail sheet. Attachments
are a `N files` count in the metadata line, **not** thumbnails. Raises visible entries from ~3.5 to 6.

### UI-13 · Work-log search and filters keep the order

A query or facet filter removes rows and leaves the order and the month headers intact. The connector
goes dashed across omitted entries with a count (`4 entries without a match`), so a line spanning six
months never implies nothing happened. The dashed run is optional; without it the spine simply spans
the gap.

**Depends on:** UI-12.

### UI-14 · Data logs archive

Month grouping and flat rows matching UI-12. `TAIL MISMATCH` keeps a badge — it is a data-integrity
warning — in the single badge treatment, with a line saying what was actually recorded
(`Recorded as N533SL`). **One upload action**: the desktop screen currently shows a header pill and a
FAB simultaneously.

### UI-15 · Thing form: consistent fields, removable blades

Every field outlined and identically labelled — today Make, Model and Serial render as blue-labelled
text while Tail number is an outlined field in the same card. Each blade gets its own remove control
and there is an *Add a blade* row.

### UI-16 · Task schedule tab

Tracking mode, due mode and interval become segmented controls instead of six tall cards. Drop the
`Step 1 · / Step 2 · / Step 3 ·` numbering: all three are on screen simultaneously, so they are
labelled choices, not steps. Keep `PreviewBanner` — it is the best thing in the form — and restyle its
4dp left stripe, which `DESIGN.md` §6 forbids. The whole schedule then fits above the fold.

---

## 6. Adaptive shell

### UI-17 · Sidebar: the selected Thing is not a row

The selected Thing becomes a distinct block at the top — filled, bordered, name plus identifier, with
a switch affordance — and the section rows sit directly beneath it because they belong to it. Below a
divider, *Switch to* lists four by recency, then *All N things* opens the picker. Nothing scrolls;
the structure is identical at 3 Things and at 17.

Which sections appear is a template capability: the airplane preset declares Data logs, the car does
not, and the shell does not change between them.

### UI-18 · List-detail on MEDIUM and wider

Adopt `ListDetailPaneScaffold` for tasks first: sidebar, list pane, detail pane. Today the detail
opens as a 460dp modal drawer floating over an empty lower half. Also cap the segmented filter at its
natural width — it currently stretches across 1500px.

Logs (UI-19) and data logs (UI-20) follow the same shape in their own PRs, each shipping on its own.

**Also in this PR:** delete `LayoutTier.hasDashboardRail` and its test, and fix the
`AdaptiveAppShell` KDoc at `:263-267` which describes a MEDIUM nav rail that does not exist.

### UI-19 · List-detail for work logs
### UI-20 · List-detail for data logs, with a preview pane

The data-log detail pane previews what opening the chart tool would give: duration, series and sample
counts, a two-series sketch of the recording, and the recorded series. *Open chart* becomes a
deliberate step rather than the only way to learn anything about the file.

---

## 7. Bugs

These are defects, not design debt. Each is a small PR and none depends on anything above.

| #     | Bug                                                                                                                                           | Where                                                               |
|-------|-----------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------|
| UI-B1 | `FlightLand` icon renders unconditionally — the home dashboard shows an aeroplane landing on a house                                          | `AogAlertSection.kt`                                                |
| UI-B2 | The section FAB overlaps the nav pill and the last list row on every compact list screen; `LocalNavPillClearance` exists and is not respected | `ThingSectionContent.kt:127-180`, `FloatingPillNavigationBar.kt:37` |
| UI-B3 | Two *Upload Data Log* controls visible at once on desktop                                                                                     | data-log section content                                            |
| UI-B4 | New squawk's primary button reads `SAVE CHANGES` on a record that does not exist yet                                                          | `feature/squawk/update`                                             |

### UI-21 · Regenerate store screenshots

`docs/product/screenshots/` predates the five-destination nav bar and the data-log section. Run
`docs/product/screenshot_generator/` after the visual units land.

---

## 8. Dependencies and sequencing

Only three hard dependencies exist:

```
UI-0 ──> UI-1 ──> UI-2
                UI-12 ──> UI-13
```

Everything else can land in any order, including in parallel. Suggested sequence, by leverage:

1. **UI-0, UI-1** — the ramp changes every screen from one file.
2. **UI-B1…UI-B4** — cheap, and they are bugs.
3. **UI-2, UI-4, UI-5, UI-6, UI-9** — the component layer; each improves every feature at once.
4. **UI-10 … UI-16** — per-section, fully parallel.
5. **UI-17, UI-18, UI-19, UI-20** — the shell; largest effort, biggest payoff for web and iPad.
6. **UI-3** — motion, once the surfaces it animates have settled.
7. **UI-21** — screenshots last.

---

## 9. Decisions

All three settled 2026-09-19.

### D1 — Resolve moves to the detail sheet ✅

Resolve leaves the action bar (UI-6) and becomes the detail sheet's primary action, alongside the
swipe action `SwipeActionCard` already carries on squawk and task rows. It changes a record's state,
not its fields, and edit forms are for fields. The decisive argument is that its current position is
undefined rather than merely inconsistent: tapping Resolve beside Save with unsaved edits has no
defined behaviour.

### D2 — Resolve is **not** one action ✅

It is two different things wearing one label, and the detail sheets must not share a design.

| | Squawk | Task |
|---|---|---|
| Menu | `ResolveOptionsMenu` (`feature/squawk/viewing`) | `ResolveTaskOptionsMenu` (`feature/tasks/viewing`) |
| Options | `fixed_option_label` → ADDRESSED, links `addressed_by_log_id` through `LogPickerSheet`; `dismiss_no_work_planned` → DISMISSED | `create_work_log_option` → comply by logging work; `skip_this_cycle_option` → skip |
| Shape | A state machine with a terminal state | A recurring schedule being advanced |
| Reverse | Yes — the button becomes **Reopen** when dismissed (`SquawkFormScreen.kt:308-315`) | None. A task has no end state; resolving produces the next due date |

Consequences:

- **Squawk detail sheet** — primary action *Resolve*, offering *Fixed* (pick the log that addressed
  it) and *Dismiss — no work planned*. The primary action is **state-dependent**: it reads *Reopen*
  on a dismissed squawk.
- **Task detail sheet** — the primary action is **Log work**, not Resolve, with *Skip this cycle*
  beside it. `LOG WORK` on the tablet detail pane mock is therefore right, but Skip has to sit with
  it or the schedule cannot be advanced without logging work that never happened.

This lands with UI-18 and UI-6.

### D3 — Attachment filenames stay as they are ✅

No previews and no thumbnails, in the list *or* the detail sheet. The sheet keeps filename and file
type, as today. UI-12 already puts an `N files` count in the log row metadata; nothing else changes.

---

## 10. Deliberately out of scope

Recorded so they are choices rather than oversights:

- **Undo and optimistic delete.** Every delete is confirm-then-block; there is no undo anywhere in
  the app. Worth doing, but it is a behaviour change across every entity, not a UI unit.
- **Accessibility.** 159 `contentDescription = null` against 61 set (including the only exit from a
  form), exactly one `semantics {}` block in all of `core` + `feature` (`FormField.kt:251-254`), no
  focus management, no `liveRegion` for validation errors, sub-48dp targets in `MonthGrid`. This
  needs its own PRD; it was explicitly dropped once before (#871).
- **Per-field validation.** One shared `required` string, fired on submit only, on forms with up to
  five tabs and no indication of which tab holds the error.
- **Global search / command palette.** A full search engine exists in `feature/search/datamanager`
  with three inline entry points and no shell-level access.
- **M3 Expressive.** The app is on Material3 1.9.0. Nothing here needs the 1.4-alpha API set.

---

## 11. Verification

CI's Kotlin build is manual-dispatch, so every PR runs locally before pushing:

```bash
./gradlew lint                                    # includes checkPopupSelectionScopes
./gradlew testDebugUnitTest testAndroidHostTest
```

Repo rules that bite in this work specifically:

- Popups come from `core.ui.common.compose`, never Material directly; nav dialogs use
  `selectionDialog`. The web `SelectionContainer` crashes otherwise.
- No `\'` in `strings.xml` or Kotlin strings — use `’`.
- User-facing strings come from `strings.xml`; reuse before adding. `StringSnapshotTest` rows are
  rewritten by hand, never regenerated.
- Nouns come from `LocalThingLexicon` / `LocalThingTemplate`. A screen that reads correctly for a
  home and an airplane at once is the test — UI-10's group headers and UI-11's section titles both
  have to pass it.
- Run the post-task cleanup pass over changed `.kt` files before the final commit.
