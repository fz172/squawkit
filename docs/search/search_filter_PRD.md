# PRD: Search and Filter

**Status:** 📋 Proposed
**Last updated:** 2026-09-05
**Design doc:** `docs/search/search_filter_design.md`
**Related:** `docs/search/intelligentsearch.md` (engine research; superseded for v1 by the design doc, kept as the escalation path)

---

## 1. Overview

Every per-thing section of the app is a list: Squawks, Maintenance Tasks, Work Logs. Today only the
Logs tab can be searched or filtered, and its search is an exact substring match on the work
description alone. A mechanic who types `xpdr` does not find the transponder install, a typo finds
nothing, and there is no way on any tab to ask "what happened to the engine in the last twelve
months".

This PRD gives all three tabs the same search-and-filter bar, adds a time filter, widens what search
looks at, and makes matching tolerant of typos and domain acronyms. The shape is **per-tab search
and filter** (option A of the exploration below); global search across kinds is deliberately out of
scope for this release.

### 1.1 Shapes considered

Three experiences were mocked and compared before this PRD was written:

| Shape | Summary | Outcome |
|---|---|---|
| **A. Per-tab filter bars** | Extend the Logs tab bar to Squawks and Tasks; tab-specific choices inside one shared sheet | **Chosen.** Smallest step, builds on a pattern users already know, every tab gets better at browsing |
| B. One search over everything | A top-bar search icon opening grouped cross-kind results; tabs unchanged | Deferred. Strong for recall, no home for time and component filters |
| C. Global search plus a per-thing lens | B plus a component-and-period lens shared across tabs | Deferred. Best fit for annual-style reviews but two concepts to learn; A's sheet becomes C's lens later without rework |

---

## 2. Goals and Non-Goals

### Goals

- **Same bar on every tab.** Squawks, Tasks and Logs get an identical search field, filter button,
  active-filter chips, result count and clear action.
- **Filter by component and by time.** Component where the template supports it; time everywhere,
  with presets and a custom range.
- **Search that forgives.** Typos, plurals and the acronyms mechanics actually type (`xpdr`, `mags`,
  `ELT`, `AD`) find the right record, and the result says why it matched.
- **Search that looks at more than one field.** Titles, descriptions, notes, reference numbers,
  serials and technician names.
- **One implementation.** A single filter model, matcher and set of composables shared by all three
  tabs, so the tabs cannot drift apart again.

### Non-Goals (this release)

- A single search bar across kinds, or across things in the fleet (shapes B and C).
- Full-text indexes, vector embeddings or on-device ML models (`intelligentsearch.md`). The corpora
  are small and local; an in-memory matcher is enough and runs on web too.
- Filtering by a specific component *instance* (Engine 1 vs Engine 2). The component filter uses
  the frozen `ComponentType` enum the records already carry.
- Saved or named filters, and filter persistence across app launches.
- Changing what the Overview tab shows.

---

## 3. User Stories

| ID | Role | Story |
|:---|:---|:---|
| US.1 | Owner | As an owner preparing for an annual, I want to see every log on the engine in the last twelve months so I can brief the IA. |
| US.2 | Owner | As an owner, I want to type `xpdr` and find the transponder squawk and the GTX install log, because that is how I write it. |
| US.3 | Technician | As a technician who misspells `trasnponder`, I still want the right log to come up without retyping. |
| US.4 | Owner | As an owner, I want to type an AD number or a serial and get an exact hit ranked first. |
| US.5 | Owner | As an owner, I want to narrow the Tasks tab to what is due in the next three months so I can plan a shop visit. |
| US.6 | Owner | As an owner, I want to see only high-priority open squawks before a flight. |
| US.7 | Any | As a user who left a filter on, I want to see at a glance that the list is filtered and clear it in one tap. |
| US.8 | Any | As a user tapping a notification, I want to land on the record even if a filter would have hidden it. |
| US.9 | Car or home owner | As a user whose thing has no airframe, engine or propeller, I do not want to be offered a component filter at all. |

---

## 4. Functional Requirements

### 4.1 The filter bar (all three tabs)

| ID | Requirement |
|---|---|
| FR.1 | Each of Squawks, Tasks and Logs shows, above its list: a search field, a filter button, a row of active-filter chips (only when something is applied), and a result count in the form "N of M ‹noun›". |
| FR.2 | The filter button shows an indicator when any non-search filter is active. Tapping it opens the filter sheet (§4.4). |
| FR.3 | Each active filter renders as a dismissible chip. Dismissing a chip removes only that filter. |
| FR.4 | A **Clear** action next to the count removes every filter and the query on that tab. |
| FR.5 | The existing Open / Closed and Active / Complied toggles stay. Filters apply *within* the selected sub-view, and the toggle counts reflect the filtered counts so the user can see what the other sub-view holds under the same filter. |
| FR.6 | When nothing matches, the list shows an empty state naming the situation ("No ‹noun› match these filters") with a **Clear filters** button. |
| FR.7 | Filter and query state is held per tab and per thing. It survives switching tabs and rotating the device, resets when the selected thing changes, and is not persisted across launches. |
| FR.8 | Jump-to-record paths (notification taps, log ↔ squawk and log ↔ task links) clear that tab's filters first so the target is always reachable. This is the existing Logs behaviour, kept and extended. |

### 4.2 Search

| ID | Requirement |
|---|---|
| FR.9 | Search matches per kind against: **Squawks** title, description, component serial. **Tasks** title, notes, reference number, compliance authority and details. **Logs** work description, technician name, component serial. |
| FR.10 | Matching is tolerant: case- and accent-insensitive; plural and common suffix variants match (`mags` / `magneto`, `leaking` / `leak`); a query token that is a known acronym or synonym matches its expansion and vice versa (`xpdr` ↔ `transponder`, `ELT` ↔ `emergency locator transmitter`); a token of four letters or more matches a corpus word within one edit, two edits from eight letters. |
| FR.11 | Tokens that are numbers, serials or references (`91.413`, `AD 2011-10-09`, `3AB012345`) match exactly or by prefix only. Never fuzzily. |
| FR.12 | Every query token must match somewhere for a record to be a result. Results are ranked: exact before stem before prefix before synonym before fuzzy; title and reference fields outrank body fields; ties break on recency. |
| FR.13 | A result that matched only through a synonym, prefix or fuzzy step shows a one-line explanation on its card ("Matched transponder for xpdr", "Close to transponder"). Exact hits show nothing. |
| FR.14 | Search updates as the user types, debounced, and never blocks the UI. |
| FR.15 | The synonym set has a generic pack (applies to every thing) and a per-template pack (aviation for the airplane preset). Packs are bundled in the app; no network. |

### 4.3 Filters

| ID | Requirement |
|---|---|
| FR.16 | **Component.** Multi-select over Airframe, Engine, Propeller and Unspecified. Shown only when the thing's template uses component types (the same rule that shows the component picker on the log form). Absent otherwise, on every tab. |
| FR.17 | **Time.** Single-select: All time, Last 3 months, Last 12 months, Custom range. Custom opens start and end date pickers. Presets are relative to today at the time of filtering. |
| FR.18 | Time means something different per tab, and the sheet labels it accordingly. **Logs:** the work date. **Squawks:** Open sub-view by created date; Closed sub-view by the date it was addressed or dismissed. **Tasks:** Active sub-view reads the presets as "due within" against the next due date; Complied sub-view by the date of compliance. |
| FR.19 | An active task whose only rule is meter-based has no due date and is never excluded by a time filter. The sheet says so in one line. |
| FR.20 | **Squawk priority** (Squawks only). Single-select: AOG, High, Medium, Low. |
| FR.21 | **Compliance type** (Tasks only). Single-select over the template's compliance terms (Routine, mandatory, advisory: for aviation, Inspection / AD / SB). |
| FR.22 | **Technician** (Logs only). Single-select over the technician names present in this thing's logs. |
| FR.23 | Filters combine with AND across kinds of filter and OR within a multi-select. |

### 4.4 The filter sheet

| ID | Requirement |
|---|---|
| FR.24 | One bottom sheet, shared by all tabs, titled "Filter ‹noun›" with a subtitle "Applies to this tab only". Sections, in order: Component (when applicable), Time, the tab-specific section (§4.3). |
| FR.25 | Every section is a row of choice chips. Selections apply immediately behind the sheet. The sheet has **Clear** and **Done**. |
| FR.26 | The sheet is one of the popups covered by the text-selection rule (AGENTS.md § Popups): it is built from `core.ui.common.compose` and starts its own selection scope. |

### 4.5 Words and platforms

| ID | Requirement |
|---|---|
| FR.27 | Every label comes from `strings.xml`; the nouns in titles, placeholders and counts come from the thing's lexicon ("Search squawks", "Search issues"). |
| FR.28 | Behaviour is identical on Android, iOS and web. On web, `/` focuses the search field of the visible tab and `Esc` clears it. |
| FR.29 | Wide layouts (tablet, desktop web) use the same bar and sheet in v1. A persistent side panel is a later refinement. |

### 4.6 Analytics

| ID | Requirement |
|---|---|
| FR.30 | Filter application and search use are logged as typed events with the tab, filter kind and value class (preset name, not dates; query length bucket and match kinds, never query text). |

---

## 5. Experience

The mockup for shape A is the reference for layout and copy. In prose:

1. **At rest** a tab shows the search field and an outlined filter button in one row, then the
   status toggle, then the count "12 of 12 logs", then the cards.
2. **Typing** narrows the list on each keystroke. Cards that matched through a synonym or a typo
   carry an amber explanation line under their metadata.
3. **Tapping the filter button** raises the sheet. Choosing "Engine" and "Last 12 months" filters
   the list immediately; the sheet's Done just closes it.
4. **Back on the tab** the filter button is filled with an indicator, the chips row reads
   "Engine ×  Last 12 months ×", and the count reads "5 of 12 logs".
5. **Clear** in the count row returns everything to rest.

Copy rules: the noun is the lexicon's, the period labels are the same words on every tab, and the
Tasks sheet retitles its time section "Due within" so the different meaning is visible before the
user picks.

---

## 6. Success Metrics

- Search used on Squawks and Tasks, not only Logs, by at least a third of active things within
  30 days of release.
- At least a quarter of searches that return results include a synonym or fuzzy match, showing the
  tolerance is doing work.
- Fewer than 5% of searches end with the user clearing the query with no result tapped and no
  filter changed (a proxy for "found nothing useful").
- Time filter applied on the Logs tab in at least 15% of Logs sessions.

---

## 7. Phasing

| Phase | Scope | Ships alone? |
|---|---|---|
| **P1 – One bar, three tabs** | Shared filter model and composables; component and time filters (with custom range) on all three tabs; search widened to the fields in FR.9 with the existing substring matching; jump-target clearing on all tabs | Yes. Replaces the Logs-only sheet |
| **P2 – Tolerant search** | Tokenizer, stemmer, synonym packs, edit-distance matching, ranking, explanations on cards, ground-truth test suite | Yes |
| **P3 – Tab-specific filters and polish** | Priority, compliance type, technician; web keyboard shortcuts; analytics | Yes |

---

## 8. Open Questions

1. **"Since last annual."** An anchor preset is the most aviation-native period. It needs a reliable
   date for the last compliance of the anchor task; deferred until `DueMetadata` carries compliance
   dates (design §5.3).
2. **Closed-squawk date for addressed squawks.** The addressing log's timestamp is the right date;
   the Squawks tab does not currently load logs. Design §5.2 proposes the cheapest way to get it.
3. **Component instance filtering** for twins and multi-engine things: wait for demand.
4. **Persisting filters across launches.** Not in v1; revisit if the metrics show users re-applying
   the same filter every session.
