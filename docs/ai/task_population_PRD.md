# PRD: Suggested Tasks from the Thing and Its Documents

**Epic:** [#1182](https://github.com/fz172/squawkit/issues/1182)
**Design doc:** [`task_population_design.md`](task_population_design.md)
**Sibling epics:** [#1181](https://github.com/fz172/squawkit/issues/1181) photo / paper logbook →
log entries (shares the AI backend), [#1183](https://github.com/fz172/squawkit/issues/1183) data log
anomaly detection
**Status:** 📋 Proposed
**Last updated:** 2026-09-27

> **Naming.** Code, protos, and this document call the feature **suggested tasks**. The noun a user
> sees ("task", "inspection", "chore") comes from the Thing's lexicon, never from code. New types
> use Thing vocabulary (`TaskSuggestion`, `TaskOrigin`, `SourceDocument`), per
> [AGENTS.md § Coding Conventions](../../AGENTS.md#coding-conventions).

> **The provider is chosen by measurement.** This PRD sets *what* the feature must do and the
> quality bar it has to meet. The LLM provider and model, OCR pre-processing, and how the
> maintenance section of a manual is found are all decided in the design phase against the
> evaluation in §9. Nothing here assumes a provider.

---

## 1. Problem

A new Thing starts with the template's starter pack (`template.starter_tasks`, offered by
`StarterPackViewModel`). That pack is one list per preset. The motorcycle preset offers the same
tasks to a Triumph and a Honda, and the airplane preset cannot know that a Rotax 915 iS has a
different schedule from a Lycoming O-320. It also knows nothing about the Thing's meters, its
history, or the tasks the owner already tracks.

The real schedule is in the manufacturer's documents, and for many Things it is spread across
several:

| Thing                                         | Where the schedule is                                                                      | What the owner does today                                    |
|-----------------------------------------------|--------------------------------------------------------------------------------------------|--------------------------------------------------------------|
| 2025 Triumph Bonneville T100                  | One table in the owner's handbook: first service, then every 10,000 mi or 12 months, …     | Types 20–40 tasks by hand, or keeps the handbook in a drawer |
| Sling TSi, Rotax 915 iS, Airmaster propeller  | Three PDFs: airframe maintenance manual, Rotax maintenance manual, prop manual             | Builds a spreadsheet from three manuals, often wrongly       |
| 1978 C172N, O-320-H2AD, an applicable AD      | Cessna service manual, Lycoming SIs, the AD itself                                         | Relies on the A&P at the annual                              |
| House with a gas water heater and a septic    | Appliance manuals (often lost); the rest is common practice                                | Nothing, until something fails                               |

Reading a 300-page manual, finding the schedule and turning each line into a task with the right
interval is work users will not do. It is also work a language model does well when it is given
the document. For an S-LSA, the manufacturer's maintenance manual *is* the required maintenance
program, so accuracy matters for more than convenience.

## 2. Goals

- **G1.** One press turns a Thing into a reviewed list of suggested tasks, with intervals and
  first-due, in the existing starter-pack picker.
- **G2.** A user-supplied document (manual, SB, AD, appliance manual) becomes its recurring tasks,
  and every task keeps the document attached as its source.
- **G3.** Every suggestion says where it came from: document, revision and page for a document,
  or *Common practice* for model knowledge. The source stays on the task after it is accepted.
- **G4.** A Thing made of several documented parts (airframe, engine, propeller) gets one merged
  list, grouped by component, without duplicates, and never duplicating a task the Thing already
  has.
- **G5.** The model never invents regulation. An AD or SB task exists only because the user
  supplied the document that states it.
- **G6.** Nothing is written without the user's confirmation.
- **G7.** Works on Android, iOS and web, for all seven presets (airplane, car, motorcycle, bike,
  boat, home, custom), for signed-in users only.

## 3. Non-Goals

- **Regulatory lookup.** v1 does not fetch ADs or SBs from the FAA DRS or any other feed, and the
  model is never asked which ADs apply (decision 2).
- **Web-located documents.** The backend does not search for or download manuals in v1 (§13).
- **A user-visible library.** Results are cached server-side (§5.8), but no screen ever says
  "already in the library", and no user sees a document another user uploaded.
- **Automatic refresh.** Changing specs (engine swap, new water heater) re-suggests nothing. The
  user presses *Suggest tasks* again (decision 6).
- **Airworthiness determinations.** Suggestions are schedule proposals. Nothing says a Thing is or
  is not airworthy, and nothing is signed off.
- **Checklist sub-items.** The task model has no checklist field, and v1 does not add one
  (decision 3).
- **On-device models.** Generation runs on the backend.
- **Guest use.** Guests (anonymous or local-only accounts) never reach the AI backend (R47).
- **Offline extraction queue.** Generation needs a connection in v1 (§13).
- **Intake from the paper-logbook backfill (#1181).** The hand-off contract is defined here (§10.1)
  and built in a later phase.
- **Replacing the template starter pack.** The static pack stays as the offline and fallback path.

## 4. Users and Stories

- **Aircraft owner, new Thing.** Adds a 1978 C172N, O-320-H2AD, 3,400 TT, with logs imported. The
  starter pack offers *Suggest tasks*. The picker shows the annual, 100-hour, oil and filter, ELT
  battery, transponder and pitot-static checks. Each has its first due worked out from current
  tach and the last logged occurrence, and each carries a source chip.
- **Motorcycle owner.** Creates a 2025 Triumph Bonneville T100 at 1,200 mi and uploads the
  owner's handbook. Gets the first service, the 10,000 mi / 12-month service, valve clearances,
  brake fluid and coolant, each citing a page. Unticks two and taps *Add*.
- **LSA owner with three manuals.** The sources sheet asks for documents per component. The owner
  uploads the Sling MM, the Rotax 915 iS MM and the Airmaster manual, and gets a list grouped
  *Airframe / Engine / Propeller* with items like "Rotax 915 iS MM, rev 3, p. 5-12". The three PDFs
  are each stored once and shared by every task that cites them.
- **Owner with an AD.** Uploads the PDF of an AD that applies to the engine. Gets its recurring
  inspection as an AD-typed task carrying the AD number, with the PDF attached.
- **Homeowner.** Creates a home with a gas water heater and a septic tank, and has no manuals. Gets
  suggestions marked *Common practice*, each with a plain explanation.
- **Owner with tasks already.** Presses *Suggest tasks* on a Thing with 12 tasks. Matches are shown
  as *Already tracked*. A suggestion with a different interval shows a note, and the existing task
  is not edited.
- **Technician on a shared Thing.** Uploads a manual to a plane shared with them. The owner's Pro
  entitlement decides whether document extraction is available, and the technician's own plan does
  not.
- **Offline in a hangar.** Sees the static pack and a note that suggestions need a connection.

## 5. Requirements

Priorities: **P0** ships in v1 or the feature does not ship; **P1** ships in v1 unless it slips, in
which case it is the first follow-up; **P2** is designed for, not built.

### 5.1 Entry points

- **R1 (P0). Starter pack.** The starter-pack screen gains *Suggest tasks* above the static list.
  This covers both existing routes to it: after creating a Thing (`EditThingScreen`) and the task
  list's empty state (`ComplianceSection`). No separate AI prompt is added to the empty state.
- **R2 (P0). Task list, any time.** A *Suggest tasks* action on a non-empty task list opens the same
  flow, filtered against existing tasks (R24).
- **R3 (P0). Add task → from a document.** The add-task flow offers *Tasks from a document*, which
  opens the sources sheet with the upload step first.
- **R4 (P1). From an existing attachment.** A PDF or image already attached to any record of the
  Thing offers *Find tasks in this document*. It reuses the stored blob instead of uploading it
  again.
- **R5 (P0). Enough identity.** The action is offered only when the template's required spec fields
  (e.g. make and model) are filled. Otherwise it explains what is missing and links to the Thing's
  edit screen. The custom preset has no required specs, so it is always offered, and a thin
  description is handled by R21a.

### 5.2 Sources

- **R6 (P0). Sources sheet.** One row per component that the template's component tree defines and
  the Thing fills (Airframe; Engine: Rotax 915 iS; Propeller: Airmaster). A Thing with a single part
  shows one row. Each row offers *Upload* and *Skip*. The primary action is *Suggest*.
- **R7 (P0). Document forms.** A PDF (with or without a text layer), photos of pages (camera or
  library). There is no page limit. The design doc sets the document-count and file-size limits
  and the copy for exceeding them.
- **R8 (P0). Component assignment.** Each document is assigned to a component. The app proposes the
  assignment from the document's title, and the user can change it.
- **R9 (P0). No documents.** The user can proceed without any document. Generation then uses model
  knowledge only and is labelled per R17.

### 5.3 Inputs sent to the backend

- **R10 (P0). Thing context.** Template id and version, lexicon, the meters the template defines
  (keys, units, current readings), spec field values, and each component instance with its own
  specs.
- **R11 (P0). Existing tasks.** Title, component, rules and compliance fields of every live task.
- **R12 (P0). Log history for last-done matching.** Per log entry: date, meter readings, title, work
  description and component. **Never sent:** technician names, certificate numbers, costs,
  attachments or comments. The design doc sets the cap (the most recent N entries or N months), and
  the payload says when history was truncated.
- **R13 (P0). No other account data.** Nothing from other Things, the user's profile, or other
  members of a shared Thing.
- **R14 (P0).** Documents travel through the blob upload path or a signed upload, never inline in
  the callable payload. The design doc picks which.

### 5.4 Generation

- **R15 (P0).** Generation runs on the backend, behind an authenticated callable function. No model
  API key, prompt or provider name reaches the client.
- **R16 (P0). Order of trust.** User documents first, then model knowledge. A task found in a
  document is never overridden by model knowledge.
- **R17 (P0). Source kinds.** Each suggestion carries exactly one:
  - *From your document*: the document title, revision (when printed) and page(s).
  - *Manufacturer schedule*: model knowledge attributed to a named publication ("Lycoming SI
    1014M") that the user did not supply. Shown as *verify against your manual*.
  - *Common practice*: general practice for the type, not attributed to a publication.
  - *From your logs*: a recurring item found in the Thing's own history (later also from #1181).
- **R18 (P0). No invented regulation.** Enforced on the server after the model responds, not left
  to the prompt:
  - Without a document, `type` is always `ROUTINE_INSPECTION`, and `reference_number` and
    `compliance_authority` are empty.
  - With a document, `AIRWORTHINESS_DIRECTIVE` or `SERVICE_BULLETIN` is allowed only when the
    document itself is that directive or bulletin, and `reference_number` must appear verbatim in
    the document's text. Anything that fails is downgraded to routine and its reference dropped.
  - A document-sourced suggestion whose cited page does not contain the item is dropped.
  - Domain-inherent rules (the annual, 14 CFR 91.409) may be named in rationale text but are still
    typed routine.
- **R19 (P0). Progress.** A progress state appears within one second, naming the current step
  ("Reading Rotax 915 iS manual…"). A cache hit returns in under 5 seconds. An uncached run takes minutes and
  carries on after the user leaves the screen; with three documents it finishes in under 10
  minutes at p90. The result is held
  server-side for that caller and Thing for up to 24 hours and deleted when fetched or accepted.
- **R19a (P0). One run at a time, shown wherever it is opened.** While a run is in flight for a
  Thing, no entry point (R1–R4) opens the sources sheet, so documents cannot be added, removed or
  uploaded again until the run finishes or fails. The person who started it sees its working screen
  on any of their devices. Leaving and coming back, restarting the app or reloading the web page
  returns to that screen and never starts a second run. Another member sees that suggestions are
  already being prepared for this Thing and can try again when the run ends.
- **R20 (P0). Push when a run finishes.** When an uncached run ends, whether it succeeded, came back
  empty or failed, the person who started it gets a push notification that opens the result for that
  Thing. It is not shown while that person is already looking at the run's screen.
- **R21 (P0).** A failed run writes nothing and says what failed: document unreadable, no schedule
  found, limit reached, or service unavailable. The static starter pack stays available with an
  inline retry.
- **R21a (P0). Low confidence returns nothing.** On any preset, when the backend's confidence in the
  Thing's identity or in the schedule is too low, it returns no model suggestions rather than a
  guess. Examples are a custom Thing with only a name, or an obscure make and model. The review
  screen then says there wasn't enough to go on and offers *Add details* (the Thing's edit screen)
  and *Add a document*. The static pack still shows where the template has one. The custom preset
  has none, so the fallback is the whole screen there. The design doc defines the confidence
  signal. Weak items in an otherwise confident run are dropped individually, not shown as low
  confidence.

### 5.5 What a suggestion contains

- **R22 (P0).** Each suggestion maps onto `MaintenanceTask` with nothing lost (§7):
  - **Title** and **description/rationale**. For an inspection event, the description holds the
    checklist items as text with their page references (decision 3).
  - **Component**, matched against the Thing's component tree by slot key and, where the slot is
    filled, the specific component. If nothing matches, the task is filed at Thing level. A
    suggestion never creates a component.
  - **Schedule**: `TimeRule`, `MeterRule`, `SeasonalRule` or `OnConditionRule`. Two rules on one
    task mean whichever comes first. `LinkedRule` and `ImmediateRule` are not produced in v1.
  - **One-time items** (the first service at 500 mi, a non-recurring AD action) set `is_one_time`
    and a first-due.
  - **Compliance** fields per R18.
- **R23 (P0). Units and meters.** Intervals are converted to the Thing's meter units, and the
  source's own figure is kept in the description when it differs ("every 16,000 km
  (10,000 mi)"). Meter rules use only meter keys the template defines; any other key is dropped
  server-side. An interval in an untracked unit (cycles on a Thing with no cycle meter) goes into
  the description, and the task falls back to its calendar rule or to on-condition. A home (no
  meters) gets only calendar, seasonal or on-condition rules.

### 5.6 Review

- **R24 (P0). De-duplication.** A suggestion that matches an existing task (same intent on the same
  component, whatever the wording) is shown as *Already tracked* and cannot be selected. If its
  interval differs, it carries a note ("You track this every 12 months; the manual says 6"). It
  never edits the existing task.
- **R25 (P0). One merged list.** Suggestions open in the starter-pack picker, grouped by component,
  with the static template items merged in. A static item and a suggestion for the same thing
  become one card, showing the suggestion's source and interval.
- **R26 (P0). Each card shows** the title, the schedule in plain words and lexicon terms ("Every
  100 h or 12 months"), first-due (R29), and a source chip. Tapping the chip shows the full citation
  and the one-line rationale.
- **R27 (P0). Pre-selection.** Suggestions from a document or from the logs are pre-selected.
  *Manufacturer schedule* and *Common practice* suggestions are pre-selected except on the airplane
  template, where they never are.
- **R28 (P1). Update before accepting.** A suggestion's title, intervals and first-due can be
  changed before it is accepted, using the normal task form pre-filled. Saving there counts as
  accepted-with-edits.
- **R29 (P0). First due.** Computed on the **client** by the existing due engine, not by the model.
  The model returns the rule and, when it finds it, the last-done evidence (log entry id, date,
  meter reading). The new task is then seeded with a `ForceCompliedStatus`. With no evidence the
  card says *No record of this being done* and uses the default for a new task.
- **R30 (P1).** For a document the user can access, the citation opens the document at the cited
  page.
- **R31 (P0). Disclosure.** The screen states once, plainly, that suggestions are drafted by AI from
  the listed sources and that the manufacturer's documents govern. Rationale reads as advice
  ("Lycoming recommends…"), never as obligation. The one exception is a user-supplied AD or SB,
  which is quoted as the document's own requirement.
- **R32 (P1). Report a wrong suggestion.** The report goes to the team with the suggestion and its
  citation, but not the document, and feeds the evaluation set.

### 5.7 Accepting and provenance

- **R33 (P0).** Accepting writes the selected tasks through the existing starter-pack path
  (`TaskDataManager.addTask`), so they are local-first, sync, and share like any other task.
- **R34 (P0). Persisted origin.** `MaintenanceTask` gains a `TaskOrigin` message holding:
  - origin kind: manual, template starter, AI Thing-based, AI document, and later backfill;
  - the R17 source kind and citation text;
  - the source attachment id and page or section;
  - the generation version (prompt and model).

  New id fields use boxed id messages (`id/ids.proto`). Existing and hand-made tasks have no origin
  and render as they do today, with no backfill.
- **R35 (P0).** Task detail shows the origin as one quiet line ("From Rotax 915 iS MM · p. 5-12",
  "Suggested · Common practice"). Editing a task keeps its origin, and the edit is counted for
  analytics (R50).
- **R36 (P0).** Accepting on a shared Thing requires the role that can already add tasks.

### 5.8 Documents and shared attachments

- **R37 (P0). One document, one blob.** A document that yields several tasks is stored **once**.
  Every task accepted from it carries an `Attachment` referencing the same blob, and no bytes are
  copied. The document is uploaded to the Thing's blob scope (via `ThingScopeResolver`) when the
  run starts, and becomes referenced when its first task is accepted. If no task is accepted, it is
  released and the orphan sweep reclaims it (design §8.1–8.2).
- **R38 (P0). Reference-aware deletion.** Removing the document from one task, or deleting one
  task, must not tombstone the blob while another live record in the Thing still references it.
  - The server's `onRecordDeleted` (`blobsReferencedByLiveRecords`) and the device's
    `TombstoneGc.stillReferenced` already skip blobs a live record names.
  - **The client form path does not.** `AttachmentFormController.resolveForSave` (pending delete)
    and `deleteSavedFiles` (parent delete) call `AttachmentManager.delete`. That tombstones the blob
    at once and schedules the remote delete.

  v1 changes `AttachmentManager.delete` from "delete this blob" to "release this record's
  reference". The blob is tombstoned only when no other live payload in the Thing's scope names it.
  This applies to every attachment removal, not only on AI tasks. The design doc chooses between
  reusing the live-payload scan (preferred, one source of truth, per `deletion_gc_design.html` §4)
  and an explicit count.
- **R39 (P0). Tests.** The remote storage sweep, the delete trigger and the device GC each get a
  case where two live tasks share one blob and one of them is deleted or drops the attachment. The
  blob survives, and it is collected when the second reference goes.
- **R40 (P0).** `isDuplicateOnParent` stays per record. Attaching an existing blob to another
  record (R4, R37) is a reference, not an upload, and does not trip it.
- **R41 (P1).** Removing a shared document from one task says it stays on the N other tasks that use
  it.

### 5.9 Server-side cache

- **R42 (P0).** The Thing-independent step of generation (document → schedule items, and
  identity → common schedule) is cached server-side by **document fingerprint** (content hash plus
  revision) and by **normalized identity** (template, make, model, year, component models). A cache
  hit costs no model call for that step. Per-Thing tailoring (dedup, last-done matching, units)
  always runs fresh.
- **R43 (P0).** The cache holds derived schedule items and document metadata (title, manufacturer,
  revision, page count, fingerprint) only. It never stores a document's bytes or text, and holds no
  personal data (serials, tail numbers, meter readings, names, log content). No UI exposes it.
- **R44 (P0).** A cached entry is written only after a successful run and carries its generation
  version. Bumping the version invalidates entries, and a reported entry (R32) can be evicted by
  the team.

### 5.10 Sharing, gating and limits

Three mechanisms, kept separate, per
[AGENTS.md § Gating](../../AGENTS.md#gating-three-mechanisms-kept-separate).

- **R45 (P0). Who and whose.** Any member of the Thing can run it: the owner and technician
  members, both of whom can already write tasks. The **Thing owner's** entitlement and quota decide
  access, not the caller's. A member's paywall copy names the owner's plan.
- **R46 (P0). Entitlement.** Through `SubscriptionManager` (`SquawkIt Pro`), no new flag system:
  - **Suggestions without documents (R9): free** to every signed-in account, as an onboarding hook.
  - **Anything with a document (R3, R4, R6 uploads): Pro.** Free users see the entry points and a
    paywall sheet.
- **R47 (P0). Signed-in users only.** Every AI action (suggestions with or without documents, on
  every entry point) needs a signed-in, non-anonymous account. The free tier in R46 means free
  *for signed-in accounts*, not free for guests.
  - **Client.** A guest sees the static starter pack exactly as today. *Suggest tasks* and *Tasks
    from a document* are visible and open a sign-in / link-account prompt instead of the flow, the
    same account-gate pattern as the data-log upload (data log PRD R40). Web has no guest mode, so
    this state exists only on mobile.
  - **Server.** The callable function rejects unauthenticated and anonymous callers before any
    model call or quota check, whatever the client shows.
- **R48 (P0). Rollout.** An `AppCapability` flag, true on developer builds only until v1 is
  complete, then deleted. No `DeveloperFlags` entry.
- **R49 (P0). Limits and cost.**
  - **One successful run per Thing per day**, for free and Pro alike, with or without documents.
    Only a run that returns suggestions counts. Failed runs (R21), low-confidence empty runs (R21a)
    and cache hits do not, so a bad upload never locks the user out until tomorrow. When the
    day's run is used, the action says when it becomes available again.
  - Per-run limits: documents per run and file size. No page limit.
  - A monthly cost ceiling per tier and a project-wide spend ceiling. Past a ceiling, uncached runs
    are refused, the copy says when the limit resets, and cached results keep working.
  - A server-side kill switch that turns uncached generation off without an app release.
  - Each run logs tokens, cost, latency, cache hit or miss, provider and owner tier. Prompt and
    document content are never logged.

### 5.11 Offline, lexicon, analytics

- **R50 (P0). Analytics.** Thing-scoped events in the typed taxonomy of `core/analytics`, with no
  task titles, document text or specs:
  - `task_suggestions_requested`: entry point, document count, template id.
  - `task_suggestions_shown`: count per source kind, cache hit, latency bucket, truncated-history
    flag.
  - `task_suggestions_accepted`: accepted and accepted-with-edits counts per source kind.
  - `task_suggestions_failed`: reason (offline, daily limit, entitlement, low confidence, unreadable
    document, backend).
  - `task_origin_edited`: an AI-origin task was edited later.

  `StarterTasksOffered` / `StarterTasksAccepted` keep firing for the static items.
- **R51 (P0). Offline.** The action is visible, disabled, and labelled as needing a connection.
  Nothing about the feature blocks Thing creation, the static pack or adding a task by hand.
- **R52 (P0). Lexicon.** Every string naming a task, inspection or component resolves from the
  lexicon. The source labels are new `strings.xml` entries shared by all templates.

## 6. UX

Mocks come with the design doc. The flow in words:

1. **Starter pack or task list** → *Suggest tasks* (or add task → *Tasks from a document*).
2. **Sources sheet** (R6). One row per component with *Upload* and *Skip*, and the Pro gate on
   upload for a free owner. The primary action is *Suggest*.
3. **Working** (R19, R19a). The step being worked on is named, and the user can leave. Any entry
   point opens this screen while the run is in flight, and a push brings the user back when it
   ends (R20).
4. **Review.** The starter-pack picker, grouped by component, with static cards shown first, source
   chips, *Already tracked* rows, first-due lines and the disclosure (R31). The Accept button stays
   usable while cards load.
5. **Add.** Tasks are written, and the user lands on the task list.

The card reuses the starter-pack card. Colour and type follow `DESIGN.md`, and the source chip is
text, not a color code.

## 7. Output Schema (product level)

The design doc owns the wire schema.

| Group      | Fields                                                           | Maps to                                                                        |
|------------|------------------------------------------------------------------|--------------------------------------------------------------------------------|
| Identity   | title, description / rationale, component slot key, component id | `title`, `notes`, `component`                                                  |
| Schedule   | one or more rules, one-time flag, first-due for one-time items   | `rules`, `is_one_time`, `force_due_date` / `force_due_meter`                   |
| Compliance | type, reference number, authority, details                       | `type`, `reference_number`, `compliance_authority`, `compliance_details` (R18) |
| Evidence   | last-done log entry id, date, meter reading                      | `force_complied_status` (R29)                                                  |
| Origin     | source kind, citation, document + page, generation version       | `TaskOrigin` (R34), `attachments` (R37)                                        |
| Dedup      | existing task it matches, interval-difference note               | picker only (R24)                                                              |

## 8. Architecture Constraints

### 8.1 Shared AI backend

Whichever of #1181 and #1182 ships first builds it, and the other reuses it. It provides:

- An authenticated callable function in `backend/firebase/functions`, following the existing
  `onCall` / `defineSecret` pattern. Clients call it through the shared GitLive `FirebaseFunctions`
  client, as sharing and subscription already do.
- A **provider abstraction**, since the provider is chosen by §9 and #1181 may choose differently.
- Caller → Thing → owner resolution through the sharing ACL, so R45 is enforced server-side.
- Limits, ceilings, kill switch and cost logging (R49).
- **No retention of inputs.** Documents and prompts pass through. The only stored copy of a
  document is the user's attachment in their Thing's blob scope. The server keeps the derived cache
  (R42–R44) and held results (R19, 24 h max) only.

### 8.2 Local-first

Suggestions are transient view state (hoisted in the ViewModel so they survive the file picker)
until accepted. Accepted tasks go to the `EntityStore` through `TaskDataManager` only. The sync
engine stays the only entity-path Firestore client, and the backend never writes entities. Per-Thing
reads and blob writes resolve through `ThingScopeResolver`.

### 8.3 Cache storage

The cache (R42) is backend data outside the entity paths, read and written only by the backend.
Security rules deny client access.

### 8.4 Module

Suggestions get their own module, `feature/tasks/suggestions`. The starter-pack UI and ViewModel
move there from `feature/tasks/update/.../starter/`; the pack's content stays in the template
`.textproto` files. The callable client sits behind a manager interface in
`feature/tasks/suggestions/datamanager`, which calls a shared `feature/ai` module for common AI
logic if #1181 has created one. The design doc decides the submodule split. Nothing lands in `feature/thing` or `feature/dashboard/host`.

## 9. Model and Provider Evaluation (design phase)

The design doc fills this in with measured results before phase B starts.

### 9.1 Deferred to design

- Provider and model: one model, or a cheap-plus-strong pair.
- Document handling: sending text directly or pre-processing it (PDF text extraction, OCR for scans
  and photos).
- Locating the maintenance section: PDF outline, printed TOC, page scoring, the whole document, or
  a combination.
- Log-history summarization for large histories (R12).

### 9.2 Candidates

Two providers at the same tier, plus a document pre-processor (OpenAI was dropped on 2026-09-30).
The bake-off chose Gemini 3.8 Flash for both tiers (decision 18):

- Google Gemini (e.g. 3.5 Flash), on Vertex AI in the existing GCP project.
- Anthropic Claude (e.g. Sonnet 5.5, Haiku 4.5), on Vertex AI in the same project.
- For scans and photos: Google Document AI in front of a text model. Mistral OCR was dropped
  from the bake-off (2026-09-28).

### 9.3 Evaluation set

A fixed set with hand-written expected answers, kept out of the repo if licensing requires:

| Case                                 | Sources                                      | Tests                                     |
|--------------------------------------|----------------------------------------------|-------------------------------------------|
| Sling TSi + Rotax 915 iS + Airmaster | Three manufacturer PDFs                      | Multi-document merge, citations, hours    |
| 1978 C172N, O-320, with logs         | No document; then with a real AD PDF         | Last-done matching, first-due, R18 typing |
| 2025 Triumph Bonneville T100         | Owner's handbook PDF; also no document       | Miles/km, first service, whichever-first  |
| A common car                         | Owner's manual PDF; also no document         | Model knowledge vs document               |
| Home: gas water heater + septic      | No documents; then an appliance manual photo | *Common practice* labelling, no meters    |
| A scanned (image-only) manual        | One PDF with no text layer                   | R7                                        |
| A Thing with existing tasks          | Any of the above                             | De-duplication (R24)                      |
| One case per remaining preset        | Bike, boat, custom                           | Lexicon, meters, R5                       |

Aviation results are reviewed against real inspection programs by someone with A&P knowledge
before the airplane preset is enabled.

### 9.4 Bar to ship

**Hard gates** (any failure blocks release):

- Zero AD or SB-typed suggestions without a supplied AD or SB document.
- Zero reference numbers that do not appear verbatim in the document.
- Zero document-sourced suggestions that are not in the document.
- Zero meter keys outside the template.
- 100% of runs produce output that maps onto `MaintenanceTask` (after at most one retry).

**Document-sourced cases:**

- Recall of ≥ 90% of the manual's scheduled items.
- ≥ 95% of the items present have the correct interval(s) and units.
- ≥ 95% of citations point to the page containing the item.
- An uncached three-document run finishes in under 10 minutes at p90. This was two minutes, then
  three (2026-09-30), and became 10 on 2026-10-01: past a minute the user leaves the screen
  anyway, so a cheaper, slower model costs them nothing.

**Cost:** reported per document and per run, with a guide of under about $1 for an uncached
three-document airplane. This is a guide, not a gate, and quality wins a tie.

**No-document cases:** judged qualitatively. The items are plausible, every item carries a
non-document source kind, and none is phrased as a requirement.

## 10. Rollout

| Phase                          | Scope                                                                                   | Exit                                                              |
|--------------------------------|-----------------------------------------------------------------------------------------|-------------------------------------------------------------------|
| **0 — Evaluate**               | §9 run, provider chosen, design doc written                                             | §9.4 bar met on the evaluation set                                |
| **A — Backend**                | Shared backend (§8.1) if #1181 has not shipped it; provider abstraction, cache, limits  | Valid suggestions for every evaluation case from a test harness   |
| **B — Reference-aware delete** | R37–R40, across client and server, independent of AI                                    | Shared-blob tests green; in production before D                   |
| **C — Suggestions**            | R1, R2, R5, R9–R13, R15–R19a, R20, R21–R29, R31, R33–R36, R45–R52, `AppCapability` on developer builds | No-document flow end to end on all hosts, all seven presets       |
| **D — Documents**              | R3, R4, R6–R8b, R14, R30, R37 wiring, R41, Pro paywall                                   | T100, Sling TSi and C172N + AD flows end to end; flag deleted; v1 |
| **E — Follow-ups**             | R32, anything P1 that slipped                                                           | —                                                                 |
| **F — Backfill intake**        | §10.1 once #1181's backfill exists                                                      | #1181 recurring items open this picker                            |

Phase B comes before any document code. It fixes a latent risk for every shared blob, and the
document flow depends on it.

### 10.1 Hand-off from #1181 (phase F)

#1181's backfill finds recurring items across historical entries. It produces the §7 shape with
source kind *From your logs* and evidence pointing at the backfilled entries, then opens this
picker. No second picker is built.

## 11. Success Criteria

- Of Things created after launch, the share that accepts at least one starter task (multi-domain
  PRD §13, target ≥ 60%) rises by at least 10 points where suggestions ran, and the share starting
  with at least five tasks doubles.
- ≥ 70% of document-sourced and ≥ 50% of other suggestions shown are accepted. Fewer than 10% of
  accepted suggestions are edited or deleted within 30 days.
- Wrong-suggestion reports on fewer than 1% of accepted suggestions.
- Zero production reports of a fabricated AD or SB task.
- Zero attachments lost to deletion while another record still referenced them.
- Average model cost per run, cache hits included, below $0.10.

## 12. Decisions

Settled 2026-09-27.

1. **Both inputs in v1.** Thing-based suggestions and documents ship together (phases C and D).
2. **Regulatory items are user-supplied only.** The model never proposes an AD or SB on its own,
   and R18 enforces this server-side.
3. **One task per inspection event.** "100 h / annual check" is one task whose description holds
   the checklist and page references. An item with its own interval or life limit (spark plugs,
   coolant, hoses, TBO) is its own task.
4. **The user always confirms** (G6).
5. **All seven presets in v1**, with an A&P review for aviation.
6. **Refresh is manual.** Spec changes trigger nothing.
7. **Origin persists on the task** (R34).
8. **First due uses meters and logs**, and log summaries are sent without personal data (R12,
   R29).
9. **Gating: no-document suggestions free, documents Pro.**
10. **Shared Things: any member can run it, on the owner's entitlement.**
11. **Documents are kept only as the user's attachment.** One blob is shared by every task it
    produced, with reference-aware deletion (R37, R38). The backend keeps no inputs.
12. **Server-side cache, no library UI** (R42–R44).
13. **The provider is chosen by measurement** (§9), behind a provider abstraction.
14. **Web-located documents and #1181 intake are later phases.**
15. **Signed-in users only.** Guests see the static pack and a sign-in prompt, and the backend
    rejects anonymous callers (R47).
16. **One successful run per Thing per day** (R49). Failed runs, empty low-confidence runs and
    cache hits do not count.
17. **Low confidence returns nothing** on any preset, with a fallback that asks for details or a
    document (R21a).
18. **Gemini 3.8 Flash for both tiers, on Vertex AI** (2026-10-01). Flash + Sonnet 5.5 scored a
    little higher and ran in half the time, but cost twice as much, and both take minutes. Results
    are in the design doc §12.5.

### Still open

- **Limit values** (R49): the per-run document cap and the cost ceilings, set from phase 0's
  cost data. The daily run limit is settled (decision 16).
- **Provider data terms.** Confirm Vertex AI's retention and training terms for Gemini in this use,
  and state them in the privacy policy before phase C ships.
- **Log history cap** (R12): the size and recency cut.

## 13. Later

- **Web-located documents.** The backend finds a manufacturer PDF, from an allowed list of
  manufacturer domains, pending a licensing decision.
- **Offline document queue**, shared with #1181's capture queue.
- **Curated regulatory source.** An AD feed by make/model/engine as grounding, once R18 has run in
  production.
- **Checklist sub-items** on a task, filled from the same extraction and signed off line by line.
- **Revision watch.** Notice a newer revision of a cached document and offer to review changed
  tasks.
- **Component swap / spec-change nudge.** Offer to re-run suggestions after an engine or appliance
  swap.
- **Suggest from a new attachment.** When an SB-like document is attached to any record, offer to
  look for tasks in it.
