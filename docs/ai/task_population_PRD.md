# PRD: Suggested Tasks from the Thing and Its Manuals

**Design doc:** `task_population_design.md` (not yet written)
**Epic:** [#1182](https://github.com/fz172/squawkit/issues/1182)
**Status:** 📋 Proposed
**Last updated:** 2026-09-27

> **Naming.** Code, protos, and this document call the feature **suggested tasks**. The noun a user
> sees ("task", "inspection", "chore") comes from the Thing's lexicon, never from code. New types
> use Thing vocabulary (`TaskSuggestion`, `SourceDocument`), per
> [AGENTS.md § Coding Conventions](../../AGENTS.md#coding-conventions).

> **Model choice is deliberately open.** This PRD sets *what* the feature must do and the quality
> bar it must meet. Which LLM provider and model, whether documents are pre-processed with OCR, and
> how the maintenance section of a manual is located are decided in the design phase against the
> evaluation defined in §7. Nothing in this document assumes a provider.

---

## 1. Problem

A new Thing starts with an empty task list, and an empty task list is what makes a user miss a due
date. The template's starter pack fills some of the gap, but it is one list per preset: the
automotive preset offers the same eight car-shaped tasks to a Tacoma and to a Triumph, and the
airplane preset cannot know that a Rotax 915 iS has a different schedule from a Lycoming O-320.

The real schedule lives in the manufacturer's manuals, and for many Things it lives in more than
one:

| Thing                                         | Where the schedule is                                                                       | What the owner does today                                   |
|-----------------------------------------------|---------------------------------------------------------------------------------------------|-------------------------------------------------------------|
| 2025 Triumph Bonneville T100                  | One table in the owner's handbook: first service, then every 10,000 mi or 12 months, …      | Types 20–40 tasks by hand, or keeps the handbook in a drawer |
| Sling TSi, Rotax 915 iS, Airmaster propeller  | Three PDFs: the airframe maintenance manual, the Rotax maintenance manual, the prop manual  | Builds a spreadsheet from three manuals, often wrongly       |
| House with a gas water heater and a furnace   | Appliance manuals, often lost; the schedule is "common practice"                            | Nothing, until something fails                               |

Reading a 300-page manual, finding the schedule, and turning each line into a task with the right
interval is exactly the work a user will not do, and exactly the work a language model does well
when it is given the document. For an S-LSA, the manufacturer's maintenance manual *is* the
required maintenance program, so getting this right matters beyond convenience.

## 2. Goals

- **G1.** One press turns a Thing into a reviewed list of suggested tasks, with intervals, that
  the user accepts in the existing starter-pack picker.
- **G2.** Suggestions come from the manufacturer's documents whenever they can be had, whether
  uploaded by the user, found on the web, or already in the shared library. Model knowledge alone
  is the last resort and is labelled as such.
- **G3.** Every suggestion says where it came from. When the source is a document, it names the
  document, the revision, and the page, so the user (or their A&P) can check it.
- **G4.** A Thing made of several documented parts (airframe, engine, propeller) gets one merged
  list without duplicates, grouped by component.
- **G5.** Work is done once per document revision, not once per user: the second owner of a
  Sling TSi gets the list in seconds, at no model cost.
- **G6.** Nothing is written without the user's confirmation. The feature proposes; the user
  decides.
- **G7.** Works on Android, iOS, and web, and for every Thing type the templates define.

## 3. Non-Goals

- **Choosing the LLM in this document.** See §7.
- **Airworthiness directives and service bulletins as a source of truth.** V1 does not look up
  ADs or SBs from a regulatory database, and never presents a suggestion as an AD. A user can still
  upload an SB as a document; its tasks are labelled with that document like any other (§12).
- **Autonomous writes.** No task is created, edited, or deleted without a user tap.
- **Replacing the template starter pack.** The static pack stays as the offline and fallback path.
- **Checklist sub-items.** The task model has no checklist field; V1 does not add one (see
  decision 3, §11).
- **On-device models.** Generation needs large documents and web lookup; it runs on the backend.
- **Photo-to-log extraction and paper-logbook backfill.** That is the sibling epic,
  [#1181](https://github.com/fz172/squawkit/issues/1181). Its recurring-item detection feeds this
  feature later (§12).
- **Refreshing tasks when a manual is revised.** V1 records the revision; noticing a newer one is
  later (§12).
- **Redistributing manuals.** A document one user uploads is never shown to another user (R32).

## 4. Users and Stories

- **Motorcycle owner, new Thing.** Creates "2025 Triumph Bonneville T100", 1,200 mi. The starter
  pack offers *Suggest from manufacturer schedule*. Thirty seconds later the picker shows the
  first service, the 10,000 mi / 12 month service, valve clearances, brake fluid, coolant, each
  tagged with its source. Unticks two, taps *Add*.
- **LSA owner with three manuals.** Creates a Sling TSi with a Rotax 915 iS and an Airmaster
  propeller. Presses *Suggest*; the app asks for the maintenance documents per component and shows
  the ones it already has for the Rotax. Uploads the Sling MM and the Airmaster manual from the
  owner portal. Gets a list grouped *Airframe / Engine / Propeller*, each item citing
  "Rotax 915 iS MM, rev 3, p. 5-12". Opens one citation to check it against the page.
- **Second Sling TSi owner, a month later.** Presses *Suggest*. The Sling and Rotax documents are
  already in the library at the same revision; the list appears in seconds.
- **Homeowner.** Creates a home with a gas water heater and a furnace, no manuals. Gets
  suggestions marked *Common practice*, none pre-selected, each with a plain explanation.
- **Owner with tasks already.** Presses *Suggest* on a Thing with 12 tasks. Suggestions that
  match an existing task are shown as *Already tracked* and are not selectable.
- **Offline in a hangar.** Presses *Suggest* with no signal. Sees the template's static pack and a
  note that manufacturer suggestions need a connection.

## 5. Requirements

Priorities: **P0** ships in V1 or the feature does not ship; **P1** ships in V1 unless it slips, in
which case it is the first follow-up; **P2** is designed for, not built.

### 5.1 Entry points

- **R1 (P0).** The starter-pack screen gains a *Suggest from manufacturer schedule* action above
  the static list. This covers both existing routes to it: the one after creating a Thing
  (`EditThingScreen`) and the task list's empty state (`ComplianceSection`).
- **R2 (P1).** The task list gets a *Suggest tasks* action even when it is not empty, so an
  existing Thing can use the feature. Suggestions are then filtered against existing tasks (R22).
- **R3 (P0).** The action is offered only when the Thing has enough identity to act on: the
  template's required spec fields (for example make and model) are filled. Otherwise it explains
  what is missing and links to the Thing's edit screen.

### 5.2 Inputs

- **R4 (P0).** The Thing's identity: template id, spec fields (make, model, year, serial where
  present), current meter readings, and each component instance with its own spec fields (engine
  make and model, propeller make and model).
- **R5 (P0).** Source documents, one or more per component. A document may come from:
    - **Upload** (P0): a PDF from the device, or one already attached to the Thing.
    - **Library** (P0): a document already processed for another user at the same revision (§5.7).
      Offered by matching make and model, and confirmed by the user.
    - **Web** (P1): the backend locates a manufacturer PDF for the make and model and downloads it
      itself. The user sees the title and source domain before it is used.
    - **Photos of pages** (P2): a few photographed pages of a paper manual.
- **R6 (P0).** The user can assign each document to a component (airframe, engine #1,
  propeller, …), and the app proposes the assignment from the document title.
- **R7 (P0).** The user can proceed with no documents at all. Generation then uses model knowledge
  and web sources, labelled per R12.
- **R8 (P1).** Scanned PDFs with no text layer are accepted. Whether they need OCR first is a
  design-phase decision (§7).

### 5.3 Generation

- **R9 (P0).** Generation runs on the backend, behind an authenticated callable function. No model
  API key, prompt, or provider name is on the client.
- **R10 (P0).** Sources are used in this order of trust: user-provided documents, library
  documents, web-located documents, model knowledge. A task found in a higher source is never
  overridden by a lower one.
- **R11 (P0).** Every suggestion carries its source: the document title, revision (when printed),
  and page(s) for a document; the URL for a web page; or *Common practice* for model knowledge.
- **R12 (P0).** A suggestion whose only source is model knowledge is labelled *Common practice —
  verify against your manual*. On the airplane template it is never pre-selected.
- **R13 (P0).** A progress state is shown within one second of the press. A cached result appears
  in under 5 seconds. An uncached run with documents may take up to about two minutes. The user
  can leave the screen, and the result waits for them on return.
- **R14 (P1).** When an uncached run finishes after the user has left, a notification brings them
  back to the review screen.
- **R15 (P0).** A failed run leaves nothing written and says what failed (document unreadable, no
  schedule found, service unavailable). The static starter pack stays available.

### 5.4 What a suggestion contains

- **R16 (P0).** Each suggestion maps onto the existing `MaintenanceTask` with no loss:
    - **Title** and **description**, with what to do and, for an inspection event, the checklist
      items as text (decision 3, §11).
    - **Component**, resolved to a component slot of the Thing's template, or the Thing itself.
    - **Schedule**, as one or more rules the due engine already supports: a calendar interval, a
      meter interval (on a meter the template defines), seasonal months, or on-condition. Two
      rules on one task mean "whichever comes first".
    - **One-time items** (for example "first service at 500 mi") as a one-time task with its
      first-due set.
    - **Compliance type**: routine inspection. V1 never emits an airworthiness directive (§3).
    - **Source**, per R11.
- **R17 (P0).** Units are converted to the Thing's meter units, and the source's own figure is
  kept in the description when it differs (for example "every 16,000 km (10,000 mi)").
- **R18 (P0).** Meter intervals use only meters the Thing's template declares. An interval in a
  unit the Thing does not track (for example cycles on a Thing with no cycle meter) goes into the
  description, and the task falls back to its calendar rule or to on-condition.
- **R19 (P1).** First-due is computed from the Thing's current meters and, where the Thing's logs
  show the item was last done, from that log. It is shown and editable before accepting.

### 5.5 Review

- **R20 (P0).** Suggestions open in the existing starter-pack picker, grouped by component, with
  the static template items merged in and de-duplicated.
- **R21 (P0).** Each row shows the title, the interval in plain words ("Every 100 h or 12 months"),
  and a source chip. Tapping the chip shows the full citation.
- **R22 (P0).** A suggestion that matches an existing task on the Thing is shown as *Already
  tracked* and cannot be selected.
- **R23 (P0).** Pre-selection: document-sourced suggestions are pre-selected; *Common practice*
  follows R12.
- **R24 (P1).** A suggestion can be edited (title, intervals, first-due) before accepting.
- **R25 (P1).** For an uploaded or library document the user has access to, the citation opens
  the document at the cited page.
- **R26 (P0).** The screen states, once and plainly, that suggestions are drafted by AI from the
  listed sources and that the manufacturer's documents govern.

### 5.6 Accepting

- **R27 (P0).** Accepting writes the selected tasks through the existing starter-pack path
  (`TaskDataManager.addTask`), so tasks are local-first, sync, and share like every other task.
- **R28 (P0).** The source citation is stored on the task. Whether that uses existing fields
  (`reference_number`, `compliance_details`, `notes`) or a new field is a design decision.
- **R29 (P1).** Where the user uploaded the source document and holds the attachment
  entitlement, the document is attached to each task it produced. It is stored once, not once per
  task.
- **R30 (P0).** Accepting on a shared Thing requires the role that can already add tasks.

### 5.7 The shared library

- **R31 (P0).** Results are cached server-side by **document fingerprint** (content hash plus
  revision) and, for runs without documents, by **normalised identity** (template, make, model,
  year). A cache hit costs no model call.
- **R32 (P0).** The library stores the **derived suggestions and document metadata** (title,
  manufacturer, revision, page count, fingerprint). It never stores or serves another user's PDF.
  A library hit shows the document's title and revision, not its contents.
- **R33 (P0).** A user-uploaded document enters the library as derived data only, and only after
  a successful run. No personal data (serials, tail numbers, meter readings, names) is stored in
  the library.
- **R34 (P1).** The library can be seeded by the team with common documents (Rotax 912/914/915,
  popular propellers, popular kits and vehicles) through an offline job.
- **R35 (P1).** A way to report a wrong suggestion. A report marks that library entry for review
  and is visible to the team.

### 5.8 Offline and failure

- **R36 (P0).** The feature needs a connection. Offline, the action explains this and the static
  starter pack works as today.
- **R37 (P0).** Nothing about the feature blocks any other flow: Thing creation, the static pack,
  and adding a task by hand work whether or not the backend is reachable.

### 5.9 Gating

Three mechanisms, kept separate, per
[AGENTS.md § Gating](../../AGENTS.md#gating-three-mechanisms-kept-separate).

- **R38 (P0). Account.** A signed-in, non-anonymous account, so calls can be rate-limited and
  attributed. A guest sees the action and a prompt to link an account.
- **R39 (P0). Entitlement.** `SubscriptionManager` gains one method for the feature. The split
  between tiers is open (§11, *Still open*).
- **R40 (P0). Rollout.** An `AppCapability` flag, true on developer builds only until V1 is
  complete, then deleted.
- **R41 (P0).** No `DeveloperFlags` entry.

### 5.10 Cost and abuse controls

- **R42 (P0).** Per-user rate limits (runs per day, documents per run, pages per document) and a
  project-wide monthly spend ceiling. Past the ceiling, uncached runs are refused with a clear
  message; cached results keep working.
- **R43 (P0).** A server-side kill switch that turns uncached generation off without an app
  release.
- **R44 (P0).** Every run logs its token usage, cost, latency, cache hit or miss, and provider, so
  §10's cost criterion can be measured.

### 5.11 Lexicon

- **R45 (P0).** Every string that names a task, an inspection, or a component resolves from the
  lexicon. "Manufacturer schedule" and "Common practice" are new `strings.xml` entries shared by all
  templates.

### 5.12 Analytics

- **R46 (P0).** Thing-scoped events in the typed taxonomy of `core/analytics`:
    - `task_suggestions_requested` (source mix: documents / library / web / none; document count)
    - `task_suggestions_shown` (count by source class; cache hit; latency bucket)
    - `task_suggestions_failed` (reason)
    - `task_suggestions_accepted` (accepted and edited count by source class)

  The existing `StarterTasksOffered` and `StarterTasksAccepted` events keep firing for the static
  items.

## 6. UX

Mocks to follow. The flow in words:

1. **Starter pack** → *Suggest from manufacturer schedule*.
2. **Sources sheet.** One row per component (Airframe, Engine: Rotax 915 iS, Propeller:
   Airmaster). Each row shows a library match if there is one ("Rotax 915 iS Maintenance Manual,
   rev 3, in library"), plus *Upload PDF*, *Find on the web* (P1), and *Skip*. The primary action is
   *Suggest*. A Thing with a single component (a motorcycle, a water heater) shows one row.
3. **Working.** A progress state names the step it is on ("Reading Rotax 915 iS manual…"). The
   user can leave (R13).
4. **Review.** The starter-pack picker, grouped by component, with source chips, *Already tracked*
   rows, and the AI disclosure (R26).
5. **Add.** Tasks are written, and the user lands on the task list.

## 7. Model and Provider Evaluation (design phase)

**This section is intentionally left open.** The design doc fills it in with measured results
before implementation starts. The PRD fixes only the candidates, the evaluation set, and the bar.

### 7.1 Decisions deferred to design

- LLM provider and model for extraction and merging. One model or a cheap-plus-strong pair.
- Whether document text is sent directly or pre-processed (PDF text extraction, OCR for scans).
- How the maintenance section is located. Options are PDF outline, printed TOC, page scoring, or
  sending the whole document, possibly combined.
- How web search locates documents (R5, P1).
- The provider abstraction in the backend, and the fallback provider if any.

### 7.2 Candidates to evaluate

At least three providers of the same tier, plus a document pre-processor:

- Google Gemini (for example 3.5 Flash), on Vertex AI in the existing GCP project.
- Anthropic Claude (for example Sonnet 5, Haiku 4.5).
- OpenAI (a mini-class model).
- For scans: Mistral OCR or Google Document AI in front of a text model.

### 7.3 Evaluation set

A fixed set with a hand-written expected answer for each item, kept out of the repo if licensing
requires:

| Case                                   | Sources                                              | Tests                                   |
|----------------------------------------|------------------------------------------------------|-----------------------------------------|
| Sling TSi + Rotax 915 iS + Airmaster    | Three manufacturer PDFs                               | Multi-document merge, citations, hours  |
| 2025 Triumph Bonneville T100            | Owner's handbook PDF; also with no document           | Miles/km, first service, whichever-first |
| A common car                            | Owner's manual PDF; also with no document             | Model knowledge vs document              |
| Gas water heater + furnace              | No documents                                          | *Common practice* labelling              |
| A scanned (image-only) manual           | One PDF with no text layer                            | R8                                       |
| A Thing with existing tasks             | Any of the above                                      | De-duplication (R22)                     |

### 7.4 Bar to ship

Measured on the evaluation set, for document-sourced cases:

- **Recall:** at least 90% of the manual's scheduled items appear.
- **Interval accuracy:** at least 95% of the items that do appear have the correct interval(s) and
  units.
- **Citation accuracy:** at least 95% of citations point to the page that contains the item.
- **Invented items:** zero document-sourced suggestions that are not in the document.
- **Valid output:** 100% of runs produce output that maps onto `MaintenanceTask` (after at most one
  retry).
- **Latency:** an uncached three-document run finishes in under two minutes at p90.
- **Cost:** reported per document and per run. It should stay under about $1 for a
  three-document airplane, uncached. That is a guide, not a gate. Quality wins a tie.

For runs with no documents, the bar is qualitative: the items are plausible, every item is
labelled *Common practice*, and none is phrased as a requirement.

## 8. Architecture Constraints

- **Backend.** A new callable function in `backend/firebase/functions`, following the existing
  `onCall` and `defineSecret` pattern. The client calls it through the shared GitLive
  `FirebaseFunctions` client, as sharing and subscription already do.
- **Local-first.** Suggestions are transient UI state until accepted. Accepted tasks are written
  to the `EntityStore` through `TaskDataManager` only. The sync engine stays the only entity-path
  Firestore client.
- **Library storage.** The shared library is backend data, not entity data. It lives outside the
  entity paths and is read only by the backend. Security rules deny client access.
- **Documents.** Uploaded PDFs travel to the backend through the existing blob upload path or a
  signed upload, never inline in the callable payload. The design doc picks which.
- **Scope.** Per-Thing reads resolve through `ThingScopeResolver`, so a member of a shared Thing
  can run the feature against the host's Thing.
- **Template.** No template or proto change is required for V1 beyond what R28 decides.
- **Module.** The client side lives in `feature/tasks` (a new datamanager API and the
  starter-pack view model). The design doc decides whether a separate `feature/tasksuggest`
  module is warranted.

## 9. Rollout

| Phase                  | Scope                                                                                       | Exit                                                                  |
|------------------------|---------------------------------------------------------------------------------------------|-----------------------------------------------------------------------|
| **0 — Evaluate**       | §7 evaluation run, provider chosen, design doc written                                        | §7.4 bar met by the chosen setup on the evaluation set                 |
| **A — Backend**        | Callable function, provider abstraction, library cache, rate limits, kill switch, cost logging | Returns valid suggestions for every evaluation case from a test harness |
| **B — Client**         | R1, R3, sources sheet with upload, review in the picker, accept path, `AppCapability` flag     | T100 and Sling TSi flows end to end on developer builds, all hosts     |
| **C — Polish**         | R14, R19, R24, R25, R29, R35, analytics, gating, strings                                     | Flag deleted; V1 release                                               |
| **D — Sources**        | Web-located documents (R5), library seeding (R34), scanned manuals (R8)                     | Each behind its evaluation cases                                       |

## 10. Success Criteria

- Of Things created after launch, the share that starts with at least five tasks doubles against
  the static pack alone.
- At least 70% of suggestions shown are accepted, and fewer than 10% of accepted suggestions are
  edited or deleted within 30 days.
- Wrong-suggestion reports (R35) on fewer than 1% of accepted suggestions.
- Library hit rate above 50% within three months of launch.
- Average model cost per generation, cache hits included, below $0.10.

## 11. Decisions

Proposed. They need product sign-off before the design doc starts.

1. **The user always confirms.** Suggestions are never written without the picker (G6).
2. **Documents first, model knowledge last, and always labelled** (R10, R12).
3. **One task per inspection event.** A manual's "100 h / annual check" becomes one task whose
   description holds the checklist and its page references. An item with its own interval or life
   limit (spark plugs, coolant, hoses, gearbox inspection, TBO) becomes its own task. A structured
   checklist field is later (§12).
4. **Cache by document revision and by identity** (R31), and never share a user's document (R32).
5. **The provider is chosen by measurement** (§7), not in this PRD.

### Still open

- **Entitlement split.** Options: Pro only; one free run per Thing on Basic and unlimited on Pro;
  or free library hits and Pro-only uncached runs. The cost model from §7 informs this.
- **Library seeding scope.** Which documents the team seeds before launch, and the licensing
  position on each manufacturer's manuals.
- **Web-located documents.** Whether downloading a manufacturer PDF found by search is acceptable
  for every source, or only for an allowed list of manufacturer domains.

## 12. Later

- **Checklist sub-items** on a task, filled from the same extraction, and signed off line by line.
- **Revision watch.** Notice when a library document has a newer revision and offer to review the
  changed tasks.
- **Service bulletins and ADs** as tracked sources, once a trustworthy data source exists.
- **Backfill hand-off.** Recurring items found by the paper-logbook backfill
  ([#1181](https://github.com/fz172/squawkit/issues/1181)) arrive as suggestions here, with
  first-due taken from the last logged occurrence.
- **Component swap.** Re-run suggestions for one component when an engine or propeller is
  replaced.
