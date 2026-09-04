# PRD: Things & Templates — Multi-Domain Maintenance

> **Implementation status.** **Phases 1, 2 and 3 have shipped; Phase 5's cleanup shipped inside Phase 3.**
>
> | Phase | State |
> |---|---|
> | **1 — Migration** | **Shipped 2026-08-28/29.** The `Thing` proto, its Firestore and Cloud Storage paths, the `thing_shares` ACL tree, and a completed data migration across every account — see [`thing_migration_design.md`](thing_migration_design.md). |
> | **2 — Lexicon plumbing** | **Shipped 2026-08-29/30.** `core:template`, `TemplateRegistry`, `CurrentThingTemplate`, the lexicon and its formatter, ~230 strings converted with a byte-identical snapshot test, capability flags wired, notification copy, and the analytics taxonomy — see [`template_system_design.md`](template_system_design.md). |
> | **3 — The pivot ships** | **Shipped 2026-08-31 → 09-04**, per [`pivot_rollout_design.md`](pivot_rollout_design.md): inflate-on-write and the backfill (the hard gate), presets as `.textproto` assets, all six presets, the type picker and template-driven create form, template-driven rendering (spec, component tree, meters, export), the degraded state, `MeterRule`, technician certifications with derived roles, starter packs with both §13 events, the subscription rename, this document's companions (`PRODUCT.md`, `DESIGN.md`, store and web copy), and the snapshot narrowed into the aviation cohort's copy guardrail. **Still open from Phase 3:** the publishing script, the `fetch_templates` RPC and the canonical cache (#725–#727) — every template is baked in, none is fetched. |
> | **4 — Depth** | Proposed. |
> | **5 — Cleanup** | **Shipped with Phase 3** (#668): the transitional fields 2–6 are reserved and every reader is on `spec` / `components`. |
>
> What still does **not** exist: a template that arrives any way other than baked into the build; the custom
> template editor; per-preset export layouts beyond the logbook/generic split; a calendar-anchored ("seasonal")
> task rule, which the home starter pack approximates with 6- and 12-month intervals. §5, §7, §8 and §10–§14 are
> now a description of what shipped, with the open decisions in §16 still open.

**Owner:** Product · **Status:** Phases 1–3 and 5 shipped; Phase 4 proposed · **Date:** 2026-08-12 · **Refreshed:** 2026-09-04
**Related:** [Product overview](PRD.md) · [Storage R1 design](../storage/storage_r1_design.md) · [Squawk design](../squawks/squawk_design.md) · [Subscription PRD](../subscription/subscription_PRD.html) · [Export PRD](../export/export_logs_PRD.md) · [Sharing PRD](../sharing/aircraft_sharing_PRD.html) · [Notifications PRD](../notifications/notifications_PRD.md) · [Display ads PRD](../ads/display_ads_PRD.md) · [Aircraft overview tabs](../aircraft/aircraft_overview_tabs.md) *(historical — predates the shell sections)*

> **What changed under this document since it was drafted.** Every code claim below was re-verified against
> `main` on 2026-08-26. Five things moved, and each is reflected in place:
>
> 1. **Notifications shipped** (`feature/notifications`, 10 submodules). The urgency ladder is the first
>    machinery outside the aircraft feature modules to encode an aviation assumption — `UrgencyTier.GROUNDED`
>    and `NotificationChannel.GROUNDED` are keyed on `SquawkPriority.AOG`. It is now a row in §1 and §3.3,
>    and a surface in §8.5.
> 2. **The four tabs became four shell sections.** The `AircraftTab` enum and its tab host are gone; the
>    adaptive shell owns `ShellSection.DASHBOARD / SQUAWKS / TASKS / LOGS` and `AircraftSectionContent` renders
>    them. §8.3 is rewritten around that.
> 3. **There is no fleet list screen.** `feature/fleet/viewing` holds only `FleetEmptyState`; the fleet is a
>    **switcher** in the shell backed by `SelectedAircraftStore`. §8.2 is rewritten around that.
> 4. **The tiers are Basic and Pro**, not Core and Heavy (renamed in `c00312d5`), and the free limit is
>    **`FREE_AIRCRAFT_LIMIT = 2`**, raised from 1 in #375. §12 and §16's recommendation are restated against
>    the real numbers — half of what §12 recommended has already happened.
> 5. **The string corpus grew** from 820 entries in 25 files to **982 in 31**. §1 and §10 carry the recount.
>
> Unchanged and re-verified: the `Aircraft` / `MaintenanceLog` / `MaintenanceTask` / `Squawk` proto shapes and
> field numbers, `ComponentType`'s three values, `TaskDueManagerImpl`'s 1-month and `+10f` due-soon constants
> and its end-of-month snapping, and the five `InspectionRule` rule types.

---

SquawkIt is a maintenance system that happens to know a lot about airplanes. This PRD generalizes it into a
maintenance system for **anything worth maintaining** — a boat, a car, a motorcycle, a bike, a house — without
diluting what an aircraft owner gets today. The mechanism is a **template configuration system**: one declarative
config per kind of thing that drives the strings, the due calculations, the available actions, and the starter
service schedule. Adding a domain becomes authoring a config, not writing a feature.

> **The thesis in one paragraph.** The squawk / task / log triad is domain-independent — *something is wrong*,
> *something is due*, *something was done*. That is the whole product, and it transfers unchanged. What does
> **not** transfer is the aircraft's shape: a fixed `Airframe / Engine / Propeller` component tree, three
> hardcoded hour counters, `tail_number` on the root message, aviation-tuned due thresholds, and ~230
> user-facing strings that say "aircraft," "tail number," "engine hours," or "AOG." All of it moves into a
> **template config**. Everything else stays exactly as it is.

> **Settled by product direction.** The thing being maintained is called a **Thing**; the collection of them is
> **Stuff**. "Add a new thing" is the create action. The v1 preset catalog is **airplane, car, motorcycle, bike,
> boat, home, custom**. See §3.2 for why "Thing" is a better fit than "Asset" and how an aircraft owner never
> actually reads the word.

---

## 1. Problem & Background

SquawkIt today is a general-aviation logbook: an account holds aircraft, one of them selected at a time in the
adaptive shell's switcher, and each has a maintenance log, compliance tasks with due-status computation, and
ad-hoc squawks. The architecture is local-first (SQLDelight `EntityStore` as the single source of truth,
Firestore sync in the background), it runs on Android, iOS, and web from one Compose Multiplatform codebase, and
the feature set — attachments, export, sharing, technicians, subscription, display ads, and now urgency
notifications — is mature.

The constraint is the market, not the software. General aviation in the US is on the order of 200k active
aircraft; the population that will pay a subscription to track annuals is small and already served by incumbents.
Meanwhile the *same person* who logs a 100-hour inspection also has a boat whose impeller is overdue, a car with a
brake-fluid interval, a gravel bike whose chain is at 0.7% stretch, and a house whose gutters need doing before
the first freeze. They are tracking all of it in a notes app, a spreadsheet, or nothing.

Every one of those is the same job: **a thing, a service schedule, a record of what was done, and a list of what's
wrong with it right now.** The app already does this well. It just insists the thing is an airplane.

### Where the aircraft assumption is actually baked in

| Assumption | Where it lives | Why it blocks other domains |
|---|---|---|
| The thing has make / model / serial / **tail number**, and its powerplant is engines that each carry a propeller | `aircraft.proto`, `engine.proto`, `propeller.proto`, `EditAircraftScreen` + `AirframeSection` / `EngineSection` | A race car has an engine but no propeller; a bike has neither; **a house has no make, model, or serial at all**. Tail number is meaningless outside aviation. |
| Components are one of three fixed kinds | `ComponentType` enum (`AIRFRAME`/`ENGINE`/`PROPELLER`); referenced by `MaintenanceLog`, `MaintenanceTask`, and `Squawk` | A boat needs hull / outdrive / generator; a home needs HVAC / water heater / roof / appliances. The enum can't grow per user, and every consumer switches on it. |
| Usage is measured in exactly three hour counters | `MaintenanceLog.engine_hour`, `.airframe_time`, `.prop_time`; `MaintenanceOverview.current_*_time`; `EngineHourRule` | Cars run on odometer miles, bikes on distance *and* ride hours, boats on engine hours *and* nautical miles — and **a house has no usage meter whatsoever**. |
| Due-soon thresholds are aviation-tuned constants | `TaskDueManagerImpl`: due-soon = within 1 month, or within `+10f` of the tracked hour metric | "Within 10" is right for tach hours and absurd for odometer miles. One month of warning is right for an annual and too long for a 3-month furnace filter. |
| Month/year intervals snap to end-of-month | `TaskDueManagerImpl`: `baseDate.plus(months).endOfMonth()` | Correct and required for an annual inspection (due the last day of the month). Wrong for "flush the water heater 12 months after I last did it." |
| The metric a task tracks is inferred from component type | `TaskDueManagerImpl`: `component == AIRFRAME ? airframe_time : engine_hour` | With arbitrary components and arbitrary meters, a task must name its meter explicitly. |
| Scheduling is interval-from-last-service only | `InspectionRule` oneof: time / engine-hour / on-condition / linked / immediate | Home upkeep is **seasonal**, not interval-based: gutters in April and October, sprinkler blowout before the first freeze. No existing rule can express it. |
| Compliance means ADs and Service Bulletins; technicians have certificates | `ComplianceType` enum, `compliance_authority`, `reference_number`, `CertificateInputFields` | The *shape* is right in some domains (a recall on a car) and pure noise in others. A plumber has no A&P number. |
| Notification urgency is ranked on an aviation ladder | `feature/notifications`: `UrgencyRank`, `UrgencyTier.GROUNDED`, `NotificationChannel.GROUNDED`, `SquawkWithStatus.reportableTier()` | The ladder's *shape* is domain-independent — a defect got worse, a task crossed a threshold — but its top rung is named for an aircraft on the ground, and it is an OS-level channel id, so the name a homeowner sees in Android's notification settings is "Grounded". |
| The shared app chrome names its subject "aircraft" | `core:ui:adaptive`: `ShellAircraft`, `PER_AIRCRAFT_SECTIONS`, the `add_thing` string, and the section labels **Dashboard · Squawks · Maint. · Logs** | This is the one surface a mixed-Stuff account can never navigate away from. "Squawks" as a permanent bottom-bar label is aviation vocabulary in the most persistent place in the app. |
| ~230 user-facing strings name the domain | 31 `strings.xml` files, 982 entries: 87 say "aircraft", 18 say "tail", 42 say "engine hour" or "tach", 83 say "squawk", 8 say "AOG" | Terminology is the whole felt experience. A homeowner reading "Add aircraft" churns on the first screen. |

Notably absent: the **storage engine, sync engine, attachments, export pipeline, sharing/ACL, technician records,
subscription/entitlement, display ads, the notification scan/schedule/permission/token/delivery machinery, and the
entire squawk lifecycle**. None of them know what an aircraft is. That is what makes this pivot tractable.

Two entries in the table above are worth separating from the rest, because they are *newer* than the aircraft
feature modules and show which way the codebase drifts when nobody is watching for this. The notification
ladders and the shell's `ShellAircraft` API both encode the aviation noun in shared, non-feature code that did
not exist when the aircraft assumption was first made — which is the argument for landing the config seam
(Phase 1) before the next such surface is built, rather than after.

---

## 2. Goals & Non-Goals

### Goals

- Let a user add **any maintainable thing** and get a first-class squawk / task / log experience for it.
- Introduce a **template configuration system** — one declarative config per kind of thing — that drives
  terminology, spec fields, component structure, usage meters, **due calculation**, and **which actions and
  fields the UI offers**.
- Ship **seven presets**: airplane, car, motorcycle, bike, boat, home, custom.
- Support **meterless and seasonal domains** (home) as first-class, not as a degraded aircraft.
- Make terminology **data, not code**, so an aviation user still reads "squawk," "tail number," and "AOG" while a
  homeowner reads "issue," "address," and "emergency."
- Ship **starter task packs** per preset so a new thing arrives with a credible schedule instead of an empty list
  — the single biggest onboarding lever in the pivot.
- Guarantee an existing aviation account a **bit-identical experience** after migration: same fields, same words,
  same due dates, same export layout.
- Keep **old and new clients interoperable** through the sync engine for the entire mixed-version window.

### Non-Goals

- **Parts inventory, purchasing, or cost tracking.** Adjacent and tempting; a separate product decision.
- **Work orders, customer billing, or multi-tenant shop management.** This is a maintenance record for an owner,
  not a CMMS for a business.
- **Telemetry / IoT ingestion** (OBD-II, NMEA, engine monitors, smart-home hubs). Meters are entered by a human in
  v1; the meter model leaves room for automatic readings later without a schema change.
- **A template marketplace or template sharing between users.** Built-in presets plus user-authored custom
  templates only.
- **Per-thing config overrides.** Customization happens by forking a preset into a custom template, not by
  drifting one thing away from its template — see §4.7.
- **Localization of the lexicon.** v1 lexicons are English. See §10 for the ceiling this creates.

---

## 3. Things & Templates

### 3.1 The two concepts

**A Thing** is what's being maintained — the row in the Stuff switcher, the owner of squawks, tasks, and logs. It is
today's `Aircraft`, generalized: a name, a template reference, spec values, a component tree, and meter readings.

**A Template** is the configuration that shapes a Thing. It is *not* copied into the Thing; the Thing holds a
`template_id` and the config is resolved at read time.

The critical property: **a template is a lens over a stable schema, never a schema of its own.** Two Things with
different templates are the same proto message and the same `CollectionKind`, in the same table, synced by the
same engine. A template can be edited, versioned, or deleted without any stored Thing becoming unreadable — an
unresolvable field key renders as a plain labeled value rather than disappearing. This is what keeps the
local-first store and the sync engine untouched, and it is the non-negotiable constraint on everything in §4.

### 3.2 Why "Thing"

"Asset" is what a finance system calls your stuff. "Thing" is what *you* call it — "I've got a bunch of things to
take care of" is how people actually describe this problem. It makes the create action read naturally (**"Add a
new thing"**), it has an honest plural for the home surface (**Stuff**), and it stays graceful for the Custom
preset, where the app genuinely does not know what the user is tracking.

The obvious objection is register: `PRODUCT.md` commits the brand to **Dependable, Precise, Calm**, and "thing"
sounds loose next to a 100-hour inspection. The resolution is that **"Thing" is the fallback noun, not a
replacement noun.** Every preset's lexicon supplies its own word, so the noun is only ever generic where the
context genuinely is:

| Surface | What the user reads |
|---|---|
| Aircraft detail screen | "Add aircraft" · "2 open squawks" · "AOG" |
| Home detail screen | "Add home" · "2 need attention" · "Emergency" |
| Bike detail screen | "Add bike" · "2 open issues" · "Unrideable" |
| Mixed Stuff switcher / template picker | "Add a new thing" · "What is it?" |
| Custom template, unnamed | "Add a thing" · "2 need attention" · "Down" |

An aircraft owner reads the word "thing" in exactly one place: the create button on a mixed-Stuff account. On an
all-aircraft account even that is overridden to "Add aircraft" (§8.2). The playful word buys warmth at the entry
point and costs nothing at the places where precision is the product.

> **Settled.** Proto message `Aircraft` → `Thing`; Kotlin identifiers follow. `CollectionKind.Aircraft.wireName`
> moves to `"thing"` — a one-time migration, not a permanent mismatch; see §6 and §9. Subscription's
> `aircraftLimit` → `thingLimit`. The home surface is **Stuff**.

### 3.3 What transfers unchanged

| Capability | Change required |
|---|---|
| **Squawk lifecycle** — Open → Addressed / Dismissed, reopen, log↔squawk linkage, dismiss reasons | None. Priority and dismiss-reason labels come from the lexicon; enum values and every state transition are untouched. |
| **Task compliance** — force-due, force-complied, linked rules, one-time tasks, due-status computation | `EngineHourRule` generalizes to `MeterRule`; a `SeasonalRule` is added; thresholds and end-of-month snapping move into config. The algorithm's structure is unchanged. |
| **Maintenance logs** — CRUD, technician, work description, inspection linkage | Three hour fields become a meter-reading list. Component reference becomes an ID. |
| **Attachments** (R2) — local blob store, background upload, lazy download | None to the broker, upload/download, or local store logic. The Cloud Storage path each blob lives at is derived from the same `aircraft` scope segment as the Firestore path, so it moves in the same migration (§6, §9) — a data copy, not a feature change. |
| **Export** — PDF / CSV / XLSX / ZIP, email delivery, history | Headers and sheet names come from the lexicon and meter set; layout selected by config. Pipeline unchanged. |
| **Sharing** — invite codes, ACL, `SharedAircraftRef`, foreign-scope sync fan-out | None functionally; the `TECHNICIAN` role label comes from the lexicon. |
| **Technicians** — records, certificates, picker | None to the model; certificate fields are shown or hidden by config. |
| **Notifications** — urgency scan, watermarks, background scheduling, permission, push tokens, tap routing | None to the scanner, the watermark model, or delivery. `UrgencyTier.GROUNDED` takes its copy from `Lexicon.down_status`. OS channel names and descriptions stay neutral fixed text, and the channel **ids** are pinned by test (§8.5, #663). The four settings toggles are structural and unchanged. |
| **Display ads** — slots, consent, session cap | None. |
| **Storage (R1) + Sync** — `EntityStore`, `CollectionKind`, hydration, pull/push, tombstones | **No new `CollectionKind`.** `template_system_design.md` §5.1 supersedes an earlier plan for a twelfth: a Thing's template is inflated into the `Thing` document itself, so nothing new syncs and no new `wireName` / `schemaName` is committed to (#638). The `Thing` payload simply got bigger. No engine changes. |
| **Subscription / entitlement** | Rename only; the limit *value* is a product decision — §12. |
| **Adaptive layout, theme, navigation shell** | Structure, window tiers, and routing unchanged. Section labels and the switcher's noun come from the lexicon; `ShellAircraft` → `ShellThing`, `PER_AIRCRAFT_SECTIONS` → `PER_THING_SECTIONS`. |

---

## 4. The Template Configuration System

This is the heart of the PRD. A template config is a single declarative object with **seven blocks**. Everything
the app does differently for a home versus an airplane is expressed here, and nowhere else.

### 4.1 What a template configures

| # | Block | Controls |
|---|---|---|
| 1 | **Identity** | Template id, version, display name, icon, sort order in the picker, and the form's declared section labels (`groups`, §4.2). |
| 2 | **Lexicon** (§4.5) | Every domain noun and status word: what a Thing is called, what a defect is called, what "down" is called. |
| 3 | **Spec fields** (§4.2) | Which identity/detail fields the Thing has, their labels, input types, validation, and grouping on the form. |
| 4 | **Component slots** (§4.3) | The default component tree, cardinality, and which meters accrue against which slot. |
| 5 | **Meters** (§4.4) | Named usage counters with units, precision, monotonicity, and due-soon thresholds. May be empty. |
| 6 | **Scheduling** (§4.6) | Which rule types are offered, the due-soon window, end-of-month snapping, seasonal anchors. |
| 7 | **Capabilities** (§4.8) | Which actions, fields, tabs, and buttons exist at all for this kind of Thing. |

Plus one payload that is content rather than configuration: the **starter task pack** (§4.9).

### 4.2 Spec fields

An earlier draft of this design kept **make / model / serial** hardcoded on the Thing as a "universal core." The
**home** preset kills that idea outright: a house has no make, no model, and no serial number. So *every* field
except the user-chosen `name` is template-declared. Make/model/serial survive as three *conventional keys* that
most presets declare — and that map onto existing proto fields for storage — not as a privileged core.

```proto
message SpecField {
  string key = 1;              // "make", "tail_number", "vin", "address", "year_built"
  string label = 2;            // "Make", "Tail number", "VIN", "Address" — the field's caption
  InputType input = 3;         // TEXT | NUMBER | DATE | YEAR | ENUM | MULTILINE
  bool required = 4;
  bool is_identifier = 5;      // VIN, tail number, serial, hull ID: renders in JetBrains Mono
                                // (the Mono Rule, DESIGN.md) and is matched exactly, never fuzzy
  string hint = 6;             // "N12345"
  repeated string enum_values = 7;
  string group = 8;            // one of the template's declared `groups` (Identity block, §4.1) —
                                // a section header on the edit form, validated like spec_key (§4.7)
  bool title_candidate = 9;    // may be offered as the Thing's display name when the user hasn't
                                // set one — distinct from `label`, which is just the field's caption
}
```

Values are stored on the Thing as string key/value pairs with typed parsing at read. String storage is
deliberate: an unknown-to-this-client field round-trips losslessly through an older or newer app version, which
matters because two devices on one account can run different releases.

### 4.3 Component slots

The fixed `Aircraft → Engine → Propeller → (hub, blades)` shape becomes a recursive tree, declared by the template
and instantiated per Thing:

```proto
message ComponentSlot {
  string key = 1;                  // "airframe" | "engine" | "hvac" | "water_heater"
  string label = 2;                // "Engine", "Furnace / HVAC", "Water heater"
  Cardinality cardinality = 3;     // EXACTLY_ONE | ZERO_OR_ONE | ZERO_OR_MORE — also governs
                                    // whether the user can add one: ZERO_OR_MORE always allows it,
                                    // ZERO_OR_ONE allows it only while absent, EXACTLY_ONE never does
  bool serial_required = 4;
  repeated string spec_keys = 5;   // which of make/model/serial/etc. this slot shows
  repeated string meter_keys = 6;  // meters that accrue against this slot
  repeated ComponentSlot children = 7;
}
```

> **No separate `user_addable` flag.** An earlier draft had one; it was redundant with `cardinality` in every
> case that matters, and a field that can only ever restate its neighbor is a bug waiting for the two to drift.

Note that make / model / serial are genuinely universal at the *component* level even where they are meaningless
at the Thing level — a boat's outboard has all three, the Thing itself may not (a bike's Thing-level spec is
frame number, not make/model/serial). And the tree itself is optional: **Home declares zero component slots**
(§5.1) — capability, not compromise, and the strongest proof that the tree isn't a hidden second "universal core"
after §4.2 already killed the first one.

> **Why component IDs, not serials.** Today a log, task, or squawk points at a component with `ComponentType` +
> `component_serial`. That breaks the moment two components share a blank serial (universal on bikes, common on
> home fixtures) and it re-points silently when a component is replaced with a new serial. A stable
> `component_id` fixes both: replacing a water heater creates a new component and the old records stay attached to
> the old one, which is exactly the history an owner wants. Legacy fields stay dual-written for the airplane
> template through the compatibility window (§9).

### 4.4 Meters

The three hardcoded hour doubles become a declared set of counters. This is the highest-leverage generalization:
it makes "every 5,000 miles," "every 100 tach hours," and "every 200 ride hours" the same feature.

```proto
message Meter {
  string key = 1;                // "airframe_hours", "odometer", "ride_hours"
  string label = 2;              // "Airframe time", "Odometer", "Ride hours"
  string unit = 3;               // "hrs", "mi", "km", "nm"
  int32 decimals = 4;            // 1 for tach, 0 for odometer
  double due_soon_threshold = 5; // 10 hrs · 500 mi · 25 ride hrs — replaces the +10f constant
  bool monotonic = 6;            // odometers and tach may not decrease
  bool per_component = 7;        // engine hours are per-engine; airframe time is per-Thing
  bool primary = 8;              // shown on the Stuff switcher row
}
```

> **Why `key` and `unit` stay strings, not enums.** A custom template can declare a meter no built-in preset has
> — "cycles" for a 3D printer, "flights" for a sailplane — and an enum can't grow to fit a user-authored template
> without a code release, which is exactly the `ComponentType` trap called out in §6 ("frozen, not extended").
> Built-in presets are Kotlin declarations (§4.7) and get an enum's typo-safety for free at compile time; only
> user-authored values are genuinely free text, and that is the case an enum can't cover.

A log carries `repeated MeterReading { meter_key, component_id, value }`. `MaintenanceOverview`'s three
`current_*_time` fields become `repeated MeterReading current`, computed the same way (max over logs).

> **The meter list may be empty — and the home preset makes it so.** A house accumulates no usage a homeowner
> would ever record. **Home declares zero meters**, which must mean: no meter fields on the log form, no meter
> column in export, no meter rule type in the task editor, and a due-status computation that runs purely on
> dates. If any of those degrade to a blank "0.0 hrs" field, the config system has failed its first real test.
> This is the strongest argument for shipping home in v1 rather than as a follow-on: it is the preset that keeps
> the abstraction honest.

### 4.5 Lexicon

One lexicon per template, resolved into a `CompositionLocal` at the Thing scope.

```proto
message Noun { string singular = 1; string plural = 2; string article = 3; }  // "a" / "an"

message Lexicon {
  Noun thing = 1;        // aircraft · car · bike · boat · home · thing
  Noun defect = 2;       // squawk · issue · fault · attention
  Noun task = 3;         // inspection · service · chore
  Noun log = 4;          // logbook entry · service record · work record
  Noun component = 5;    // component · part · system
  Noun technician = 6;   // mechanic · shop · person
  string ready_status = 7;       // "Airworthy" · "Ready" · "Good"
  string down_status = 8;        // "AOG" · "Dead in the water" · "Emergency"
  string down_status_long = 9;   // "Aircraft on ground" · "Out of service"
  string collection_label = 10;  // "Fleet" · "Garage" · "Stuff"
  string compliance_mandatory = 11;  // "Airworthiness directive" · "Safety recall"
  string compliance_advisory = 12;   // "Service bulletin" · "TSB"
  string authority_label = 13;       // "FAA" · "NHTSA" · "Manufacturer"
}
```

`down_status` and `down_status_long` carry more weight than the rest of the block: besides the alert section on
the Dashboard, they name the **notification channel** a user sees in their OS settings and the title of every
grounded-tier notification (§8.5). That is the one place a lexicon string escapes the app's own surfaces, which
is why it is a whole string rather than a substitution.

> **"Squawk" isn't always a defect.** In aviation the line between a one-off compliance task (an AD or SB) and a
> squawk (something's wrong right now) is sharp. Outside aviation it often isn't — a homeowner using this app to
> track "clean the gutters" and "the fence gate is broken" wants one inbox, not a judgment call about which
> lifecycle a random TODO belongs to. Rather than build a second concept, ambiguous domains get the existing
> squawk lifecycle under a word that doesn't presuppose failure: `defect` resolves to **"attention"** for Home and
> Custom, where "something needs attention" covers both a broken gate and a chore nobody scheduled. Aviation and
> the other vehicle presets keep a defect-shaped word (issue, squawk) because for them the one-off-task-vs-defect
> boundary genuinely is clean.

**And the noun alone is not enough — `empty_states` carries the sentence.** Home resolving `squawk` to "attention
item" fixes every heading and still leaves the Attention tab reading *"Tap + to report a defect or anomaly"*,
because that line has no slot to substitute: what makes it useful is the examples. So the lexicon also carries
nine whole sentences — the copy for the Squawks, Tasks and Logs tabs and the three Dashboard rails — authored per
template rather than assembled from a noun and an English frame. It is `down_status`'s reasoning applied to copy,
and the narrow version of the per-string override design §10a sketches for localisation: only the strings that
actually need replacing, named as fields rather than keyed by resource name.

### 4.6 Scheduling & due calculation

Three aviation conventions currently live as constants in `TaskDueManagerImpl` and become config:

```proto
message Scheduling {
  repeated RuleType enabled_rules = 1;   // TIME | METER | SEASONAL | ON_CONDITION | LINKED | IMMEDIATE
  int32 due_soon_days = 2;               // 30 for aircraft · 14 for home · 30 for car
  bool month_intervals_snap_to_end_of_month = 3;  // true for aircraft, false elsewhere
  bool overdue_blocks_ready_status = 4;  // an overdue item makes the Thing "not airworthy"
  string due_engine = 5;                 // which decision engine evaluates this template — see §7.1
}
```

**End-of-month snapping** is the subtlest of the three. Today a month- or year-based interval snaps to the last
day of the month it lands in — logged 14 Dec 2025 + 12 months is due 31 Dec 2026. That is correct and legally
meaningful for an annual inspection, and simply wrong for "flush the water heater 12 months after I last did it,"
which should be due on the anniversary. Making it a per-template flag preserves aviation behavior exactly while
letting every other domain behave the way a normal person expects.

#### The new rule type: SeasonalRule

Home upkeep is not interval-from-last-service; it is **calendar-anchored**. Gutters get cleaned in April and
October regardless of when they were last done. Sprinklers get blown out before the first freeze. No existing rule
expresses this, so one is added to the `InspectionRule` oneof:

```proto
message SeasonalRule {
  repeated int32 months = 1;   // 1-12; e.g. [4, 10] for April and October
  int32 day_of_month = 2;      // 0 = end of the named month
}
```

Semantics: the next due date is the next occurrence of any listed month strictly after the last compliance date
(or after the task's creation date if never complied). It slots into the existing rule loop as one more branch
producing a candidate date, and the loop's existing "earliest candidate wins" behavior handles a task carrying
both a seasonal and a time rule with no new machinery. Seasonal rules are date-only and are unaffected by meters,
which is exactly why home works without them.

### 4.7 Resolution, precedence, versioning, validation

#### Two layers, deliberately not three

Config resolves as **base defaults → template**. A template omitting a block inherits the base default (generic
lexicon, no components, no meters, all rule types, 30-day due-soon window), so a minimal custom template is a few
lines rather than a full document.

There is no third per-Thing override layer. It is tempting — "let this one bike show a different meter label" —
and it is a trap: it makes every Thing a potential schema of one, breaks the promise that a template's config
describes its Things, and turns export and search into per-row special cases. Users who need something different
**fork a preset into a custom template**, which is a comprehensible mental model and reuses machinery already
needed.

#### Versioning

A Thing records `template_id` and `template_version`. Config resolution always uses the **current** template, not
the version recorded at creation — a preset improvement (a better label, a corrected threshold) should reach
existing Things. The recorded version exists for two narrower purposes: telling the user a preset has been updated
since their Thing was created, and letting a migration target only Things created before a specific revision.

Preset changes are governed by one rule: **additive and label changes are free; removing a spec field, meter, or
slot key is not.** Removal orphans stored values. The retirement path is to mark a key `deprecated`, which hides it
from the create form while continuing to render any Thing that already has a value for it.

#### Lenient resolution

If `template_id` resolves to nothing — a custom template deleted, or an id written by a newer app version — the
Thing renders with the **custom** preset's generic lexicon, and every stored spec value appears under its raw key
as a labeled row. Data is never hidden and never dropped. This is the same failure posture as an unknown proto
field, and it is what makes templates safe to evolve.

#### Validation

A config is invalid if it enables `METER` rules while declaring no meters, references a `meter_key` or `spec_key`
that does not exist, declares a `SpecField.group` not present in Identity's `groups` list, declares a duplicate
key, has a starter task referencing an undeclared meter, or ships an empty lexicon noun. Built-in presets are validated by a unit test that loads all of them; custom templates are
validated at save time with inline errors.

> **Where the config lives.** **Built-in presets are Kotlin declarations** in a new `core:template` module —
> type-safe, compile-checked, no parsing, and identical across Android, iOS, and web without a resource-loading
> path per host. **Custom templates are proto** (`ThingTemplate`), stored as a new `CollectionKind.ThingTemplate`
> (`wireName "thing_template"`) and synced like any other entity. Both resolve through one `TemplateRegistry`
> interface, so no consumer knows or cares which kind it got.
>
> `core:template` depends only on `core:model` and kotlinx — no Compose, no Firebase — so `datamanager` layers can
> depend on it without violating the module rules in `AGENTS.md`. The Compose side (`LocalThingLexicon` and the
> noun formatter) lives in `core:ui`.

### 4.8 Capabilities — which buttons exist

The block that answers "what actions are available." Everything here is a hard on/off that removes UI rather than
disabling it — a homeowner should never see a greyed-out "Engine hours" field.

```proto
message Capabilities {
  bool components = 1;                 // component tree UI, picker, config card
  bool meters = 2;                     // meter fields on logs; METER rules
  bool compliance = 3;                 // type chip, reference number, authority
  bool technicians = 4;                // technician picker on the log form
  reserved 5;                          // was technician_certificates — retired by §8.6
  bool component_serial_prompt = 6;    // nag for serials at creation
  repeated SquawkPriority priorities = 7;  // which priority values are offered
  repeated Section sections = 8;       // which of Dashboard/Defects/Tasks/Logs appear, and order
  ExportLayout export_layout = 9;      // LOGBOOK | GENERIC
  bool weight_balance = 10;            // future, aviation-only
}
```

| Capability | Airplane | Car | Moto | Bike | Boat | Home | Custom |
|---|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| Components | ✓ | ✓ | ✓ | ✓ | ✓ | off | off |
| Meters | ✓ | ✓ | ✓ | ✓ | ✓ | — | ✓ |
| Compliance (recalls / ADs) | ✓ | ✓ | ✓ | — | — | — | off |
| Technician picker | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Serial prompt at creation | ✓ | — | — | — | — | off | off |
| Seasonal rules | — | — | ✓ | — | ✓ | ✓ | ✓ |
| Export layout | logbook | generic | generic | generic | generic | generic | generic |
| Due-soon window | 30 d | 30 d | 30 d | 14 d | 30 d | 14 d | 30 d |
| End-of-month snap | ✓ | — | — | — | — | — | — |

> **`technician_certificates` is gone from this table**, retired by §8.6 rather than reworked. It gated fields on
> a record the technician roster aggregates *across the account*, so no single Thing's template could answer it.
> What replaced it is not another flag: a template declares the `certifications` it recognises, and a template
> declaring none offers none — the same statement, made where the scope is right.

> **Why Car and Motorcycle stay separate presets.** They look alike at this table's altitude — engine, brakes,
> suspension — but the component and spec vocabulary genuinely differs (final drive vs. transmission, no
> doors/interior, different registration and insurance classes), and motorcyclists reliably bristle at being
> folded into "automobile." Merging them would save one row in a seven-item picker at the cost of the
> terminology precision this pivot is built on. If the picker ever needs to shrink, Bike (pedal) is the more
> defensible merge candidate with Custom, not Car with Moto.
>
> **A preset worth adding later:** drone / RC aircraft. Flight hours and battery cycles instead of tach time,
> frame/motor/battery/gimbal component slots, and an audience already primed by this app's aviation register —
> a strong next candidate once the v1 picker has proven itself (§5.2).

Concretely, on the log form: the home template hides the meter fields entirely, drops the serial autofill, and
labels the person field "Person" rather than assuming a paid contractor — a homeowner splitting chores with a
spouse or doing the work themselves is at least as common as hiring one out. No vehicle preset (car, motorcycle,
boat) prompts for component serials at creation either — most owners don't know their engine or chassis serial
off-hand, and accepting one later costs nothing. The airplane template shows three meter fields, the
certificate-bearing technician picker, and the compliance chip. Same screen, same ViewModel, same proto — one
config.

> **No notification capability flag.** Every domain wants to be told that something got worse or came due, so
> the urgency ladder is unconditional and the four notification toggles stay a user preference rather than a
> template capability. This is the flag-justification rule from §14 working as intended: a flag no preset would
> set differently is not config.

### 4.9 Starter task packs

A template may ship a recommended schedule. At creation the user is offered it as one opt-in step ("Add 8
recommended chores?") with per-item checkboxes, and can add it later from an empty Tasks tab. Items are created as
ordinary `MaintenanceTask` rows — editable, deletable, in no way special afterwards.

This is where the pivot earns its keep. An aircraft owner already knows their annual is due every 12 months. A
person who just bought a house does **not** know the water heater wants an annual flush, the dryer vent is a fire
risk, and the sprinklers need blowing out before the first freeze. An app that tells them is worth paying for —
and starter packs are the cheapest possible content moat, because they are data.

> **Liability posture.** Packs are labeled **recommendations, not authority**, with a one-line disclaimer pointing
> to the manufacturer's service manual. The airplane pack contains only universal intervals — the 12-month
> condition inspection (framed for the experimental fleet, which is who builds a logbook from scratch), the
> 100-hour (off by default), ELT, transponder, pitot-static, and a 50-hour oil change — never model-specific
> intervals, and never an AD or SB, which must remain user-entered. Pinned by content in `CanonicalTemplatesTest`.

---

## 5. The Preset Catalog

| Preset | Spec fields | Component slots | Meters | Defect / down words |
|---|---|---|---|---|
| **Airplane** *(migration target)* | Make · Model · Serial · **Tail number** | Airframe ×1 · Engine ×N → Propeller ×0-1 → (Hub ×1, Blade ×N) | Airframe hours · Engine hours *(per engine)* · Prop hours | Squawk / AOG |
| **Car** | Make · Model · Year · VIN *(mono)* · Plate | Chassis ×1 · Engine ×1 · Transmission ×0-1 · Brakes ×0-1 · Suspension ×0-1 · Tires ×0-N | Odometer *(primary)* · Engine hours | Issue / Off the road |
| **Motorcycle** | Make · Model · Year · VIN *(mono)* · Plate | Frame ×1 · Engine ×1 · Final drive ×0-1 · Brakes ×0-1 · Suspension ×0-1 · Tires ×0-N | Odometer *(primary)* · Engine hours | Issue / Off the road |
| **Bike** | Make · Model · Frame number *(mono)* · Size · Year | Frame ×1 · Drivetrain ×0-1 · Fork ×0-1 · Rear shock ×0-1 · Brakes ×0-1 · Wheels ×0-N | Distance *(primary)* · Ride hours | Issue / Unrideable |
| **Boat** | Make · Model · Year · Hull ID (HIN) *(mono)* · Registration · Length | Hull ×1 · Engine ×N · Outdrive/Saildrive ×0-N · Generator ×0-N · Rigging ×0-1 | Engine hours *(primary, per engine)* · Generator hours · Nautical miles | Issue / Dead in the water |
| **Home** *(no meters, no components)* | None beyond the Thing's own name *(e.g. "Home", "655 Lincoln")* | **None** | **None** | Attention / Emergency |
| **Custom** | Name only; components and the one meter are added per-Thing, not template-edited until Phase 4 (§16 #3) | Off by default, user-addable | One generic "Usage", renameable | Attention / Down |

### 5.1 The home preset, in detail

Home is the preset that stresses every joint in the design, so it is specified rather than sketched.

| | |
|---|---|
| Lexicon | thing: home / homes · defect: **attention** · task: **chore** · log: work record · component: system · technician: **person** |
| Status words | ready: "Good" · down: "Emergency" |
| Spec fields | **None** — the Thing's own `name` (e.g. "Home", "655 Lincoln") is enough |
| Components | **None** |
| Meters | none — date-only scheduling |
| Rules enabled | TIME · **SEASONAL** · ON_CONDITION · LINKED · IMMEDIATE *(no METER)* |
| Due-soon window | 14 days · no end-of-month snapping |
| Capabilities off | components · compliance · technician certificates · serial prompt · weight & balance |

**No components, no extra spec fields.** An earlier draft gave Home a system tree (HVAC, water heater, roof,
gutters...) mirroring the vehicle presets, plus Address, Year built, Square footage, and Purchase date as spec
fields. Review feedback cut both: most homeowners can't name half of what's in their own mechanical room, a
component picker in front of "clean the gutters" is a step that buys nothing, and purchase date/square footage
are private details this app has no use for. The Thing's own `name` already carries what "Address" was for — the
user types "Home," "The lake house," or "655 Lincoln" and that is the whole identity. This makes Home structurally
identical to Custom with `capabilities.components` off; what still distinguishes it is the lexicon, the starter
pack below, and the seasonal-scheduling defaults — which is the point. Home was never a different *shape*, just
different words and a smarter default schedule. Every chore in the starter pack below is a Thing-level task, with
no `component_id`, the same as any Custom-template task.

**Starter pack** (the highest-value pack in the catalog):

| Chore | Rule |
|---|---|
| HVAC filter | Time, 3 months |
| HVAC service | Seasonal, April & October |
| Clean gutters | Seasonal, April & October |
| Smoke & CO detector batteries | Time, 12 months |
| Flush water heater | Time, 12 months |
| Clean dryer vent | Time, 12 months |
| Sprinkler blowout | Seasonal, October |
| Chimney sweep & inspection | Time, 12 months |
| Refrigerator water filter | Time, 6 months |
| Septic pump-out | Time, 36 months |

**What home proves about the design:** spec fields and the component tree are not universal — a Thing can have
zero of either; meters must be genuinely optional rather than zeroed; scheduling needs a calendar-anchored rule;
and the aviation end-of-month convention has to be config, not code. Every one of those is a correction the
preset forced, and each makes the abstraction more honest rather than more elaborate.

### 5.2 Presets deliberately not in v1

**3D printer** and **shop / garage equipment** were in the original framing and are covered in v1 by **Custom**.
They are pure data — a config plus a starter pack, no code — so they ship the moment the content is worth
shipping. Holding them back keeps the v1 picker to seven legible choices rather than nine, and lets the printer
pack be written once there is a user asking for it. Same logic for tractors, HVAC-only rentals, sailplanes,
**drone / RC aircraft** (§4.8), and firearms.

---

## 6. Data Model Changes

**This is a clean cutover, not a permanent fork.** An earlier draft of this section kept the aircraft-shaped
fields forever — dual-written on every write, never removed — to avoid ever breaking a client that hadn't
updated. Review feedback rejected that trade on two grounds. First, the Things feature has never shipped to a
single client, so every account's data is uniformly aircraft-shaped today — there is no installed base running
Things-aware code to be compatible with. Second, and more decisively: **SquawkIt is currently distributed
manually to a small dogfood group (under 50 accounts)**, not through app stores, so "every device is on the new
build" is a fact the developer can directly confirm rather than something that has to be inferred from a
self-reported version floor. Given that, Phase 1 (§15) is scoped as a **non-UI migration milestone**: swap the
aircraft protos for Thing/template protos and the Firestore/Storage paths that go with them, support the airplane
preset fully (the template language itself can still be partial), and ship **no new functionality — no template
picker, no non-airplane preset — until every dogfood account is confirmed migrated.** Fields 2–6 below exist only
for the span of that milestone and are deleted — not just deprecated — once it closes (§9, §15 Phase 5).

```proto
// aircraft.proto → thing.proto (message renamed; wireName moves to "thing" — see below)
message Thing {
  string id = 1;

  // --- transitional storage slots, dual-written only until Phase 1 migration is 100% complete (§9) ---
  // The storage location for the conventional spec keys make/model/serial, and for the
  // airplane preset's engine tree, until the one-time migration moves them into spec/components
  // and this block is deleted in a follow-up proto revision.
  string make = 2;
  string model = 3;
  string serial = 4;
  string tail_number = 5;
  repeated Engine engine = 6;      // transitional: mirrored from components

  // --- new, permanent ---
  string template_id = 7;          // "airplane" | "car" | "home" | ... | custom UUID
  int32 template_version = 8;      // preset revision at creation (informational — see §4.7)
  string name = 9;                 // user-chosen display name ("N12345", "The house")
  repeated SpecValue spec = 10;    // template-declared fields, incl. mirrors of 2-5
  repeated Component components = 11;
}

message SpecValue { string key = 1; string value = 2; }

message Component {
  string id = 1;                   // stable UUID — the join key for logs/tasks/squawks
  string slot_key = 2;             // "airframe" | "engine" | "hvac" | "water_heater"
  string label = 3;
  string make = 4;
  string model = 5;
  string serial = 6;
  repeated Component children = 7;
  repeated SpecValue spec = 8;
}
```

| Message | Change | Compatibility |
|---|---|---|
| `Aircraft` → `Thing` | Add `template_id`, `template_version`, `name`, `spec`, `components`. Fields 2–6 are transitional. | A device still on the pre-Phase-1 build can't reach `/thing` at all (its account hasn't cut over yet — §9); a Phase 1 device dual-writes 2–6 for airplane Things throughout Phase 1, and the fields are removed from the schema once every dogfood account has cut over, ahead of Phase 2. |
| `MaintenanceLog` | Add `component_id` and `repeated MeterReading meters`. Keep `component_type`, `component_serial`, `engine_hour`, `airframe_time`, `prop_time` for the same transitional window. | Dual-written for airplane-template Things for the duration of Phase 1; legacy fields removed from the schema alongside `Aircraft`'s. |
| `MaintenanceTask` | Add `component_id`; add `MeterRule` and `SeasonalRule` to the `InspectionRule` oneof; add `force_due_meter { key, value }`. Keep `component`, `engine_hour_rule`, `force_due_engine_hour` transitionally. | An `EngineHourRule` reads as a `MeterRule` on the engine-hours meter; writes emit both throughout Phase 1. `SeasonalRule` can't appear before Phase 3, which starts only after every dogfood account has cut over — see §9. |
| `Squawk` | Add `component_id`. Keep `component_type`, `component_serial` transitionally. | Same bounded dual-write rule. |
| `MaintenanceOverview` | Add `repeated MeterReading current` and `map<string,uint32> log_count_by_slot`. Legacy log counters and `current_*_time` doubles retired with the rest of Phase 1's transitional fields. | Recomputed locally from logs; no migration risk. |
| `ComponentType` | **Frozen, not extended.** Retained only for the transitional fields above; new code reads `slot_key`. | Adding enum values would be a trap — an enum can never cover user-defined slots. |
| `Technician` | Add `repeated Certification certifications`, each with a template-declared `type` key, number, and expiry. Fields 3–7 become one aviation `Certification` and are then transitional. Roles are **derived** from the certification types, never stored. | The record is account-scoped and so cannot read a per-Thing capability (§8.6). `Certification.type` is a string key, not an enum, for the reason `ComponentType` is frozen — an enum cannot be extended by a template. No stored backfill: every technician predating this was created when airplane was the only preset, so the value is derivable. |
| `ThingTemplate` *(new)* | The config from §4, as proto, for custom templates. | New `CollectionKind.ThingTemplate` — the twelfth kind, after `NotificationSettings` took the count to eleven. Zero-migration per the R1 design (§4.2.1): the `collection` column is `TEXT` and `CollectionKind.ALL` is coverage-tested against `sealedSubclasses`, so a forgotten entry fails the build. |

> **`CollectionKind.Aircraft.wireName` moves to `"thing"`, and every path built from it moves too — not just
> Firestore.** The wire name is the persisted `collection` column value and the Firestore subcollection path, and
> an earlier draft kept both as `"aircraft"` permanently to avoid a rewrite. That reasoning doesn't survive the
> point above: because nothing has ever synced Things-shaped data, a backend script can copy each account's
> `/aircraft` Firestore subcollection to `/thing` (§9.1 step 1) before the Phase 1 build — which ships
> `wireName = "thing"` as a compile-time constant — ever reaches that account's devices. No client-side rename is
> needed; the new build simply starts reading and writing at a path the backend has already prepared.
>
> **Attachment blobs live under the same "aircraft" segment and need the same treatment.** Per `EntityScope`
> (`core:storage`) and `storage.rules`, an attachment's Cloud Storage object sits at
> `users/{uid}/aircraft/{acId}/blobs/{blobId}` — the identical scope string that names the Firestore
> subcollection, since both are derived from `AircraftScopeResolver`/`EntityScope.aircraftChildUnsafe`. A
> migration that renamed the Firestore path and left every attachment's blob sitting under `.../aircraft/...`
> would recreate the exact mismatch this decision exists to avoid, just one layer down. The backend script
> therefore does two copies per account, not one: `/aircraft` → `/thing` in Firestore, and each
> `users/{uid}/aircraft/{acId}/blobs/**` object to `users/{uid}/thing/{thingId}/blobs/**` in Cloud Storage.
> `storage.rules` needs no rule change — its ownership check is `request.auth.uid == userId` on the path's first
> segment, blind to what comes after — so this is a data-copy operation, not a security-rules deploy. The local
> `scope_path` each device's own blob store recorded is corrected afterward, per device, by the local backfill in
> §9.1 step 2 — a string rewrite against already-synced data, not a second network migration.
>
> **Run manually, once per account, before the build ships to it — not gated by an automated version floor.**
> With distribution manual to under 50 dogfood accounts, the developer already controls exactly when each
> account's backend cuts over and when its devices receive the new build; building and maintaining a
> version-floor flag to infer that automatically is machinery this scale doesn't need. The old `/aircraft`
> Firestore path and `.../aircraft/...` blob objects are deleted only after both copies are confirmed for that
> account; `schemaName` becomes `"thing.Thing"`, consistent with the new wireName rather than a permanently
> documented mismatch. `CollectionKind.kt` gets a comment noting the rename happened once, in this migration, so
> the next reader doesn't go looking for a reason it's still `"aircraft"`. **This is a decision for the current
> stage, not a permanent one:** once distribution moves to app stores at real scale, "the developer sequences it
> by hand" stops being true, and an automated floor (or a background per-account migration worker) becomes
> necessary again — revisit before then, not after.

---

## 7. Due-Status Generalization

`TaskDueManagerImpl` keeps its structure — a loop over rules producing candidate dates and meter values, earliest
wins — and loses five aviation assumptions:

1. **Metric selection.** Today `component == AIRFRAME ? airframe_time : engine_hour`. New: each `MeterRule` names
   its meter; the current value is the max reading for that `(meter_key, component_id)` pair across the Thing's
   logs.
2. **Due-soon thresholds.** Today a hardcoded 1 month and `+10f`. New: the date window comes from
   `Scheduling.due_soon_days`, the meter window from `Meter.due_soon_threshold`.
3. **End-of-month snapping.** Now `Scheduling.month_intervals_snap_to_end_of_month`, true only for airplanes.
4. **Force-due.** `force_due_engine_hour` becomes `force_due_meter { key, value }`, with the legacy float mapped
   onto the engine-hours meter on read.
5. **Seasonal scheduling.** One new branch in the rule loop producing a candidate date from the next matching
   month.

Force-complied, linked rules with cycle protection, one-time tasks moving to history, on-condition, and immediate
are all meter-agnostic already and are not touched.

> **Regression bar for this section.** The existing `TaskDueManager` test suite must pass **unmodified** against
> the generalized implementation, with the airplane preset supplying meters and scheduling flags. If a test needs
> editing to pass, the generalization changed aviation behavior and the change is wrong. This is the cheapest
> available proof that the pivot is non-destructive.
>
> The bar now extends one module further. `DueStatus` feeds `UrgencyRank` and `reportableTier()` in
> `feature/notifications`, whose `when` blocks are deliberately exhaustive with no `else` so that a new
> `DueStatus` value fails the build rather than silently ranking zero. Generalizing due status must therefore
> leave the `DueStatus` enum alone — the four values are the contract between the two features — and the
> notification ladder suites must pass unmodified alongside the due-status ones. A pivot that quietly stopped
> notifying an owner about an overdue annual would be the worst possible failure mode, and it is a build error,
> not a review catch, only as long as that enum is left intact.

### 7.1 Note for the engineering design doc: keep the decision engine pluggable

> **Carry this into the design doc, not into Phase 1.** The config system in §4 handles **parametric** variation —
> same algorithm, different numbers, labels, and flags. It does not handle **structural** variation, where the
> decision logic differs in kind. When a domain's logic forks hard from aviation's, the answer is **not** to keep
> growing `TaskDueManagerImpl` with template branches. It is to make the decision engine a plugin and let
> `TaskDueManager` route to the right one.

The shape: `TaskDueManager` stays exactly what it is today — one injected interface, one method, unchanged call
sites and ViewModels. Its implementation becomes a thin **router** that resolves the Thing's template to a
`TaskDueEngine` and delegates:

```kotlin
interface TaskDueEngine {
  fun computeNextDue(task: MaintenanceTask, logs: List<MaintenanceLog>,
                     allTasks: List<MaintenanceTask>, config: TemplateConfig): DueMetadata
}

// The public interface is untouched; only the impl changes shape.
class TaskDueManagerImpl(
  private val templates: TemplateRegistry,
  private val engines: Map<String, TaskDueEngine>,   // keyed by Scheduling.due_engine
) : TaskDueManager {
  override fun computeNextDue(...) =
    engines.getValue(templates.resolve(thing).scheduling.dueEngine)
           .computeNextDue(...)
}
```

Today's implementation becomes `IntervalDueEngine` — interval-from-last-service against dates and meters — which
serves airplane, boat, car, motorcycle, and bike, because those genuinely are the same algorithm with different
meters and thresholds. **Home** is the candidate for the first split: calendar-anchored scheduling, no meters,
seasonal windows, and a different notion of "overdue" (a chore missed in April is not 6 months overdue, it is due
again in October). If the seasonal branch starts pulling the shared loop out of shape, that is the signal to lift
it into a `SeasonalDueEngine` rather than to add a sixth flag.

#### When to split

Any one of these is sufficient cause; none of them is "a new domain was added":

- A scheduling flag exists to serve **exactly one** template, and cannot be justified by a second.
- The rule loop accumulates `if (config.x)` branches that **don't compose** — where two flags set together produce
  a combination nobody designed and no test covers.
- A domain needs a different **notion of due**, not a different interval: recurring-window instead of
  point-in-time, or a status that isn't on the `NORMAL / DUE_SOON / OVERDUE / COMPLIED` ladder.
- Adding a domain requires editing the shared implementation rather than adding a config — the direct violation of
  the system's premise.

#### What the split buys, and what it must not cost

- **The aviation engine gets frozen.** Once `IntervalDueEngine` is the only thing computing airworthiness, a
  change made for home cannot regress it. That is worth more than the deduplication it costs, and it is the real
  argument for the structure.
- **Engines are independently testable** against their own fixtures, instead of one suite that has to hold every
  domain's semantics at once.
- **The config stays declarative data.** The failure mode this avoids is config becoming a small programming
  language — conditionals, precedence, escape hatches — which is strictly worse than Kotlin at being Kotlin.
- **The engine is still template-selected** (`Scheduling.due_engine`), so the config remains the single place a
  domain's behavior is declared. Config *selects* an engine rather than *parameterizing* one.
- **Shared lifecycle stays shared.** Force-due, force-complied, linked rules with cycle protection,
  one-time-task-to-history, and `DueStatus` mapping belong in a shared base or in the router, not copy-pasted per
  engine. The split is on the *scheduling computation* only. Two engines that are 95% identical and drift apart
  would be a worse outcome than the flag matrix this avoids.

> **Do not start here.** Phase 1 ships **one** engine plus config, deliberately. Building the plugin structure
> before a second domain has forced it produces two nearly identical engines, a registry with one entry, and an
> abstraction shaped by a guess about the future. The `due_engine` key ships in the config from day one so the
> seam exists and the migration is a refactor rather than a redesign — but every preset points at `"interval"`
> until one of the split triggers above actually fires.

#### The same principle applies beyond due status

Three other surfaces will feel this pressure, and the design doc should apply the identical rule — config until
the fork is structural, then a plugin behind the interface that already exists:

| Surface | Config handles | Plugin when |
|---|---|---|
| **Export writers** | Column sets, headers, sheet names, which meters appear | A domain needs a fundamentally different document — the aviation paper-logbook tab layout vs. a generic service history is already two layouts, so this is the most likely second split. |
| **Create / edit form builder** | Field lists, labels, validation, grouping, slot cardinality | A domain needs a genuinely different flow rather than a different field list (e.g. a wizard step no other template has). |
| **Overview cards** | Which cards appear and their order | A domain needs a card whose *content* has no analogue elsewhere (a seasonal calendar strip for home; a W&B envelope for aircraft). |

---

## 8. UX, Surface by Surface

### 8.1 Creating a Thing

| Step | Content |
|---|---|
| 1 — What is it? | Icon grid: Airplane · Car · Motorcycle · Bike · Boat · Home · Custom |
| 2 — Identity | Name + the template's spec fields, template-labeled |
| 3 — Components | Pre-filled slots; skipped entirely when `capabilities.components` is off |
| 4 — Starter pack | Per-item checkboxes; "Skip" is a first-class button |

**What "Custom" gets at step 1.** Choosing Custom does not open a field/slot/meter editor — that's the template
*editor*, deferred to Phase 4 (§16 #3). What v1 gives a Custom Thing is per-Thing flexibility on top of a fixed,
minimal template: no pre-filled component slots (the user adds their own, ad hoc, at step 3), and the one generic
"Usage" meter can be renamed. It looks like customization because the starting point is nearly empty, not because
the template itself is editable yet.

Steps 3 and 4 are skippable; a Thing with a name and a template is valid. This matters, and the current form is
the proof: `EditAircraftUiState` refuses to save unless make, model, **and** serial are all non-blank, and
`AirframeSection` / `EngineSection` mark each blank one in error. That is exactly right for an aircraft and
hostile for a house, which has none of the three. Required-ness becomes `SpecField.required`, declared per
template.

**Homogeneous-account shortcut:** if every existing Thing shares one template, step 1 pre-selects it with a
"change" affordance. An aviation user adding their second aircraft sees today's flow with one extra tap.

### 8.2 The Stuff switcher

There is no fleet *list screen* to generalize — `feature/fleet/viewing` holds exactly one composable,
`FleetEmptyState`. The fleet is reached through the adaptive shell's **switcher**: `ShellThing` rows in the
sidebar on the wide tiers, the same switcher behind the top bar on compact ones, with `SelectedThingStore`
persisting the choice per host. Sections then render for whatever is selected.

That makes the mixed-type problem smaller than it looked when this section was first drafted against a list. Each
row already carries the two lines it needs: the Thing's name, and beneath it the make and model, falling back to
whatever spec field the template marks `is_identifier` — a VIN for a car, a serial for an airplane, a street
address for a home. That is the right second line and it stays.

**No meter value on the row, and no next-due chore.** An earlier draft of this section put a primary meter
reading there, with a home showing its next due chore instead. Both are dropped. The identifier is what
distinguishes one row from another when a user is choosing between them, which is the only job the switcher has;
an odometer reading does not help you tell two cars apart, and it costs a `primary` flag on `MeterDef` plus a
per-Thing reading lookup in the shell projection to display something less useful than what is already there.

What the row still gains is the template's `icon`, so a mixed account can be read at a glance, and a colour on
the selected row — a checkmark alone in the trailing corner is easy to miss while scanning labels.

Rows of the same type sort together, but **grouping is a sort, not a section**: no heading per group and no rule
between them. On a menu this short a label per group was more furniture than the grouping was worth, and the
icon already says which type a row is.

The switcher is titled **Your Stuff**, and stays that way. An earlier draft had `Lexicon.collection_label`
override it on a homogeneous account — **Fleet** for all-aircraft, **Garage** for all-car. That is dropped: the
switcher spans the account rather than any one template, so a title that tracks today's rows renames the whole
surface the moment a second type is added. `collection_label` is still authored on every preset and still renders
in `no_fleet_title`; it just does not name this.

The **create action is neutral** for the same reason ("Add a new thing", `switcher_add_thing`). It names what the
user is about to add, which nothing yet describes — offering "Add aircraft" to an all-aircraft owner whose next
Thing is a house is the one place the switcher would actively mislead. It opens the type picker (§8.2a).

`FleetEmptyState` is the one surface with no Thing to resolve a lexicon from, so it defaults to the generic
noun — which is correct: a brand-new account genuinely does not yet know what it is for.

### 8.2a The type picker

"Add a new thing" opens a picker before the form, because the template decides which spec fields the form asks
for — a picker inside the form would rebuild it on every change. Until it existed, creating a Thing always
produced an airplane whatever the account held.

Cards are the template's `display_name` and `icon`, two per row, drawn from `TemplateRegistry.canonical()` — which
is already ordered by `sort_order` and already filtered to what the build can render, so a template naming a
`min_app_version` above the client never appears. An icon key the build does not recognise falls back to a
generic vector rather than failing: a type is never unpickable because we could not draw it.

**Always a bottom sheet**, at every window size. `DetailSheet` switches to an end drawer above COMPACT, which is
right for reading a record beside the list it came from; the picker is a short interruption on the way to the
form, and the sheet reads better wide than a drawer does.

The chosen template rides to the form on the route (`add_thing?templateId=`), and the form provides its own
template locals for its subtree — `LocalThingLexicon` and friends are provided app-wide from the *selected*
Thing, so without that the form titles itself after whatever the switcher points at. **The type cannot be changed
from inside the form.** Cancel and pick again.

### 8.3 Thing overview

The four per-Thing surfaces are preserved. They are no longer tabs inside an aircraft screen: since this document
was drafted they became **shell sections** — `ShellSection.DASHBOARD / SQUAWKS / TASKS / LOGS` in
`core:ui:adaptive`, rendered by `AircraftSectionContent` and reached from a bottom bar, rail, or sidebar
depending on window tier, with `SETTINGS` alongside them as the one global section.

The generalization is unchanged in substance and cheaper in practice than the tab version would have been: labels
come from the lexicon, section presence from `capabilities.sections`, and **one enum in one shared module** is
the single place the set is declared, instead of a tab enum owned by the aircraft feature. The aviation labels living in
`core:sharedassets` (`shell_tab_squawks`, `shell_title_tasks` = "Maintenance Tasks", `shell_title_logs` = "Work
Logs") become lexicon-resolved at render.

The Dashboard section's `AircraftDataCard` — the configuration accordion — renders the component tree generically
instead of the hardcoded airframe/engine/prop sections in `EngineDetails` and `BladeChipsOverview`, and
`AogAlertSection` becomes a down-status alert section keyed on `Lexicon.down_status`, above the unchanged
`CriticalAlertSection`.

### 8.4 Log entry

The smart component picker already loads real components and auto-fills serials — preserved and improved, since it
now walks the component tree rather than three special cases. The three hour fields become one field per
applicable meter, labeled and unit-suffixed from the meter definition, shown only for meters relevant to the
selected component — and absent entirely when the template declares none.

### 8.5 Export, notifications, settings, sharing, technicians

The paper-logbook tab layout becomes the **airplane** preset's layout, selected by `capabilities.export_layout`;
other presets get a generic layout whose columns derive from the lexicon and meter set. Sheet names, file names,
and the README are lexicon-driven. Pipeline, ZIP structure, delivery, and history are untouched. Technician
certificate fields are shown or hidden by capability so a homeowner never sees "A&P / IA" — but that gate
alone stops working the moment an account holds two templates, for reasons that are structural rather than
verbal. See §8.6.

**Notifications** need one thing beyond copy substitution, and it is the opposite of what an earlier draft of
this section said.

**OS channel names and descriptions are neutral fixed text, never lexicon-driven.** A channel is a surface the
user configures once — importance, sound, whether it is blocked at all — and then expects to stay put. A
description that flipped between "aircraft", "home" and "car" as the account changed would be unsettling for no
benefit, and on a mixed account no template's word could be right for all of it. There is **one** set of OS
channels per install, not one per template, so there is nothing for a per-thing lexicon to attach to.

The **id** matters for a separate and harder reason: renaming one drops every user's per-channel settings, with
no error and no migration path, so the ids are pinned as literals by a regression test (#663).

Two corrections of fact, recorded because this section asserted both:

- `NotificationChannel.GROUNDED` **does not exist.** AOG stopped being its own tier on 2026-08-26 and reports
  through `URGENCY_UPDATE` like any other escalation. The channels are `COLLABORATION` and `URGENCY_UPDATE`.
- The N1 collaboration channel was described here as "domain-neutral already". It was not — it read "Someone
  changes a shared aircraft". It is now, in this section's own words: "Someone changes something you share".

Everything else on the notification surface is ordinary substitution: the tier titles, the notification bodies,
and the toggle labels in `NotificationSettingsScreen`.

### 8.6 Technicians: certifications carry the domain, roles are derived

**The technician record is aviation-shaped in the proto, not only in its words.** `Technician` carries
`cert_type`, `cert_number`, `cert_expiration`, `cert_expire_limit`, and a `CertificateType` enum whose values are
`REPAIRMAN` and `AMT` — FAA certificates, in the schema. §4.8 gates those fields behind
`capabilities.technician_certificates`, which is sufficient while every Thing is an airplane and insufficient
immediately afterwards.

**What breaks is scope, not vocabulary.** A capability is read from a Thing's template. The technician list is
**account-scoped** — it aggregates every technician the user has, including ones linked from shared Things — so
there is no Thing in context to read a capability from. On an account holding an airplane and a house, "show
certificate fields?" has no single answer, and neither the union nor the intersection is right: the A&P who signs
the annual has a certificate, the neighbour who clears the gutters does not, and they are both rows in one list.

#### The certification already says which domain it belongs to

An earlier draft of this section asked the user directly — a role picker on the add flow, "is this an aircraft
technician, home help, or a car mechanic?". That question is redundant. **An A&P certificate means aviation. An
electrician's licence means home. An ASE certificate means car.** The domain is implied by the credential, so
asking for it separately is asking the user to state something they are about to demonstrate.

So the model is: a technician carries **`repeated Certification`**, and the roles they hold are *derived* from the
certification types present. Nothing about roles appears in the add flow at all — the user adds a certification,
picking from the types the account's templates declare, and the tagging follows.

This is smaller than the role-picker design and strictly better on three counts:

- **One question instead of two.** Type-then-certificate collapses into certificate.
- **Multi-role falls out.** Two certifications means two roles, with no multi-select to design, explain, or get
  wrong. The A&P who also services the user's car is one contact with two certifications — which matters because
  the app already ships duplicate detection and a merge sheet, and any model that pushed users toward one record
  per domain would manufacture exactly the duplicates that feature exists to clean up.
- **One source of truth.** A stored role could disagree with the certifications beneath it. A derived one cannot.

**`Certification.type` is a template-declared string key, not an enum.** This is the same trap the PRD already
identified for `ComponentType` — "an enum can never cover user-defined slots" (§6) — and it bites identically
here: an enum of certificate types cannot be extended by a template, which is the entire point. Each template
declares the certifications it recognises, with their labels, whether they expire, and what a number looks like.
`CertificateType` is frozen alongside `ComponentType` and retained only for the transitional read path.

#### What this costs

**An uncertified person carries no role, and so no tag.** The neighbour who clears the gutters has no credential
to imply a domain. That is a real limitation of deriving rather than asking, and it is accepted rather than
worked around, because the resulting behaviour is the one you would have chosen anyway: **an uncertified helper
is offered on every Thing.** Nothing about them says otherwise, and inventing a role for them would be inventing
information the user never supplied.

A certification whose template is not installed — a shared technician from a preset this build lacks — is
likewise untagged rather than an error, matching `canonicalById` returning null as expected rather than
exceptional (§4.7).

#### Backfill and phase

A technician created before this change carries the single legacy certificate, which becomes one `Certification`
of the corresponding aviation type. **The backfill is derivable rather than stored**: every such record was
created when airplane was the only preset, so the set is closed — the same argument that let `Thing.template_id`
be dropped instead of kept as a hint (§6).

The screen's own chrome stays **neutral**. It spans every template, so its title and description are fixed text
rather than lexicon substitutions — a surface that belongs to no single Thing cannot borrow one Thing's words
(§10). The domain appears in the certification rows and their derived tags, each of which genuinely belongs to
exactly one template.

**Phase 3, with the second preset — not later.** Phase 3 is the first moment a mixed account can exist, and
therefore the first moment the current model is *wrong* rather than merely narrow. Deferring it would ship a
release whose technician screen has no correct behaviour available to it.

What Phase 4 takes is the general case: arbitrary template-declared spec fields on a certification, beyond
number and expiry, using the same machinery as `SpecField` on a Thing (§4.2). That belongs with the custom
template editor (§16 decision 3). Phase 3 needs only the fields the shipped presets declare.

---

## 9. Migration

**The migration is a hard, all-or-nothing gate on Phase 1 — not a soft version-floor feature built into the
app.** An earlier draft of this section designed for indefinite compatibility: an account-level version-floor
flag the client reads, a self-clearing gate on the create flow, and a matrix of what a pre-floor device sees when
it encounters non-airplane data. Review feedback cut all of that, for a reason specific to where the product is
right now rather than a general principle: **SquawkIt is distributed manually to under 50 dogfood accounts**, and
Phase 1 (§15) ships **zero new functionality** — no template picker, no non-airplane preset, nothing a pre-floor
device could even encounter that a post-floor device wrote. There is no automated gate to build because there is
nothing on the other side of it yet. The developer runs each account's backend cutover, then distributes the
Phase 1 build to that account's devices (§9.1) — and only once every dogfood account has been through both do
Phase 2 (lexicon plumbing, still aviation-only) and Phase 3 (the pivot itself) proceed. This is a decision for
the current distribution model, flagged in §6, and it should be revisited — toward something automated — before
the app moves to store distribution at real scale.

### 9.1 Migration steps

Two steps. They run in a fixed order — backend first, then client — which is precisely what makes a manual,
unautomated cutover safe: the developer controls both ends and can sequence them, rather than a client racing a
migration on the other side that it has no way to observe.

**1 — Backend cutover (per-account, one-time, run manually by the developer, before the build goes out).** This
step moves data the client doesn't own, so it has to happen first: distributing the Phase 1 build to a device
before its account's backend has cut over would point that device at a `/thing` path with nothing in it yet,
which looks exactly like data loss. Per account, the developer's script:

1. Copies the account's Firestore subcollection from `/aircraft` to `/thing`.
2. Copies every attachment blob the account owns from `users/{uid}/aircraft/{acId}/blobs/**` to
   `users/{uid}/thing/{thingId}/blobs/**` in Cloud Storage (§6) — binary data, so this step verifies size or
   checksum per object before touching the source.
3. Deletes the old Firestore subcollection and the old Storage objects, only after both copies above are
   confirmed for that account.

Run each account's cutover as a short maintenance window — a write landing on the old path mid-copy would be
lost — which is simple at under 50 accounts and stops being available at store-distribution scale, the other
half of why this section says "for now" (§6).

**2 — Local backfill (per-device, automatic, non-dirtying, after the build reaches that device).** Once an
account has cut over and its devices receive the Phase 1 build — which already ships `wireName = "thing"` as a
compile-time constant, so it reads and writes at `/thing` from first launch — a local migration runs over every
stored `Aircraft` and populates the new shape in memory and in the local store:

- `template_id = "airplane"`, `template_version` = shipped revision.
- `name` = `tail_number` if non-empty, else `"$make $model"`.
- `spec` gains `make`, `model`, `serial`, `tail_number` mirrors.
- Component tree built from legacy fields: one `airframe` component carrying the Thing's own make/model/serial;
  one `engine` per `Engine`; a `propeller` child per engine with `hub` and `blade` children. IDs are generated
  **deterministically** from `(thing_id, slot_key, index)`.
- Logs, tasks, and squawks get `component_id` resolved from `(component_type, component_serial)`; an unmatched
  serial leaves it empty and the row falls back to legacy display. It is never dropped.
- Log meters backfilled: `airframe_time` → `airframe_hours`, `engine_hour` → `engine_hours`, `prop_time` →
  `prop_hours`.
- Every locally-stored `BlobRef`'s `scope_path` is rewritten from `.../aircraft/{id}/...` to `.../thing/{id}/...`
  to match — a local string rewrite, not a network call, so it costs nothing extra alongside the rest.

This step is **idempotent and non-dirtying**: it does not mark rows dirty and does not push, so it is safe to run
independently on every device of an already-cut-over account, in whatever order those devices happen to update.

> **Deterministic IDs are load-bearing.** If component IDs were random per device, the same aircraft would migrate
> to different IDs on a phone and a tablet, and last-writer-wins would silently reassign every log's component.
> Deriving IDs from `(thing_id, slot_key, index)` makes migration a pure function of data that already synced.
> Covered by an explicit test that migrates the same payload twice and asserts identical output.

### 9.2 Why there is no mixed-version matrix here

An earlier draft carried a table of what a pre-floor device sees when it reads or edits data a post-floor device
wrote in a non-airplane shape. That table is gone because the scenario it described can't occur: Phase 1 code has
no template picker and offers no way to create anything but an airplane, so **no non-airplane-shaped Thing can
exist anywhere in the fleet of dogfood devices until Phase 1 has already finished on all of them.** The only
version-skew that is real during Phase 1 is one device on an already-cut-over account running step 2's local
backfill slightly before or after another device on that same account — which is exactly what step 2's
idempotent, non-dirtying design (above) already covers, since both devices converge on the same shape
independent of order. Once step 1's backend cutover has closed out every dogfood account, the transitional
fields in §6 are removed from the schema in one code change (§15 Phase 5), and this section stops applying for
good, not "until further notice."

---

## 10. How Terminology Actually Works

**982 strings across 31 `strings.xml` files**; roughly 230 name the domain, in three buckets:

| Bucket | Count (approx.) | Treatment |
|---|---|---|
| **Domain-neutral** — "Save", "Delete", "Due soon", "Open", "Dismissed" | ~755 | Untouched. |
| **Noun-substitutable** — "Add aircraft", "No aircraft yet", "Delete this squawk?" | ~150 | Become format strings: `"Add %1$s"` resolved against the lexicon at render. |
| **Structurally aviation** — "Airworthiness directive", "Tail number", "Aircraft on ground", "Tach time" | ~75 | Move into the lexicon or the template's field/meter labels as whole strings, not substitutions. |

The corpus grew by ~160 entries between this document's first draft and its refresh (notifications, sharing, and
subscription copy), and the domain-naming share held roughly steady at just under a quarter. That is the useful
signal: the work does not shrink by waiting, and it does not run away either — it scales with the app at a
predictable ratio, so the estimate above is stable enough to plan Phase 2 against.

Mechanically: a `LocalThingLexicon` `CompositionLocal` is provided at the Thing scope (with a sensible default for
account-level screens), plus a formatter handling sentence-case, title-case, plural, and indefinite article.
Strings stay in `strings.xml` — the repo's hard rule is unchanged; they gain a placeholder.

> **Honest limitation: noun substitution does not localize.** `"Add %1$s"` + a noun works in English and breaks in
> languages with grammatical case or gender agreement. The app is English-only today, so this is not a regression
> — but it is a **ceiling we are choosing**. When localization lands, the ~110 substitutable strings will need
> per-locale noun forms with case variants, or whole-sentence variants keyed by template. The `Noun` message is
> shaped to carry per-locale forms later; nothing here forecloses the fix, and nothing here does it.

**What the aviation user sees:** nothing. The airplane lexicon reproduces every current string exactly, and a
snapshot test asserts that rendering it produces byte-identical strings to today's resources. Terminology drift on
the existing product would be the most damaging outcome of this work, so it is tested, not reviewed.

---

## 11. Brand & Register

**The app name stays SquawkIt.** "Squawk" is aviation slang, but "squawk it" already reads as a verb — *report the
thing that's wrong* — to someone who has never flown. Renaming would cost the store listing, ASO history, the
domain, and a second identifier migration on a codebase still carrying `wingslog` from the first one.

> **Decision needed — is "squawk" universal or per-template?** **Recommendation: per-template, retained for
> aviation.** The lexicon already carries `defect`, so it costs nothing structurally. A homeowner reading "3 open
> squawks" learns a word they didn't ask to learn; the brand still says SquawkIt on the icon, which is where a
> brand belongs. The counter-argument — one universal verb is stronger branding — is real, and if it wins, the
> change is one lexicon field set identically across presets.
>
> The per-template word isn't just aviation-vs-everyone-else, though: Home and Custom resolve `defect` to
> **"attention"** rather than to a defect-shaped word like "issue," because those domains don't have aviation's
> clean line between a scheduled one-off task and something-is-wrong-right-now (§4.5). Car, motorcycle, boat, and
> bike keep an "issue"-style word because for them that line is as clean as it is for aviation.

**Visual identity is unchanged.** The aviation palette (Aviation Blue, Instrument Amber ≤10%, semantic
forest/amber status), Space Grotesk titles, and JetBrains Mono for identifiers read as *precision instrument*, not
*airplane* — exactly the right register for someone servicing a furnace or a race car. Dynamic color stays off.

`PRODUCT.md` needs one revision — the "Users" section broadens beyond aircraft owners and mechanics, and the
monospace line becomes "identifiers, serials, and meter readings" rather than "tail numbers, serials, tach
times". `DESIGN.md` needs a few more than one, all the same edit: **The Mono Rule**, the typography usage table
(`dataLarge` "Engine hours, tach times", `dataMedium` "Tail numbers in cards", `displaySmall` "Hero display (tail
numbers)"), and the `ComponentTypeBadge` spec each name aviation data where they mean *technical* data. None of
them is a design change; the rule they encode — mono means measurement or identifier, never copy — is already
domain-neutral and just happens to be written in examples. The **Refined Minimalism** direction and all five
design principles carry over unchanged — "Add a new thing" is plain-spoken, not unserious, and plain-spoken has
always been the brief.

**Store positioning** is a genuine tension: one listing selling to a pilot and a homeowner. Recommendation is to
lead with the universal promise ("Maintain anything with the rigor of an aircraft logbook") and use the screenshot
set to show breadth, keeping aviation as the credibility proof rather than the headline. Deserves its own
treatment before launch; out of scope here.

---

## 12. Subscription & Entitlement Impact

> **Shipped (Phase 3, #735).** `thingLimit()` / `FREE_THING_LIMIT` replaced the aircraft names, and the
> subscription and upsell copy is neutral fixed text — "Unlimited things", "Logs, tasks & issues" — under the
> account-scoped rule from #687 rather than lexicon substitution (§6). The free limit is still **2**; the
> decision below on raising it to 3 is open.

Mechanically trivial: `aircraftLimit()` → `thingLimit()`, `FREE_AIRCRAFT_LIMIT` → `FREE_THING_LIMIT`, and the
`upsell_body_*` strings drop "aircraft" for the neutral noun. The other four gates —
`canUploadAttachments()`, `canEmailExports()`, `canHostShare()`, `shouldShowAds()` — are unaffected; none of them
is aircraft-shaped. Storage entitlement following the host on a shared Thing (AGENTS.md, *Sharing and scope
resolution*) is likewise unchanged.

> **Decision needed — where does the free limit land?** Two corrections to this section's earlier framing, both
> of which the app has since supplied. The tiers are **Basic** (free) and **Pro** (paid) — `Subscription.Status`
> is `STATUS_FREE` / `STATUS_PRO`, and the Core / Heavy names this document used were renamed in `c00312d5`. And
> the free limit is no longer one aircraft: **`FREE_AIRCRAFT_LIMIT = 2`** since #375.
>
> So half of what this section recommended has already happened, for aviation reasons rather than multi-domain
> ones. The remaining question is narrower and better posed: **does 2 hold, or go to 3?** Two is still tight in a
> multi-domain world — a car and a house fills it before the app has proven anything, and the third Thing is
> exactly the one that establishes the multi-Thing habit. **Recommendation: raise Basic to 3 Things** and lean
> harder on attachments, email export, and sharing as the Pro differentiators. It trades a little immediate
> conversion pressure for a materially better first week. A revenue decision, not an engineering one — make it
> with whatever conversion data the move from 1 to 2 produced, which is real evidence this document did not have
> when it first asked the question.

---

## 13. Success Metrics

| Target | Metric |
|---|---|
| **≥ 40%** | of new accounts, 90 days post-launch, create a first Thing with a **non-airplane** preset. Below ~20% means the pivot did not reach a new audience. |
| **≥ 1.8** | median Things per active account (from ~1.0 today). The multi-domain thesis is that one person maintains several things; this is the direct test. |
| **≥ 60%** | of new Things accept at least one starter task. Starter packs are the onboarding bet; if they are skipped, the value proposition is not landing. |
| **0 pt** | **Guardrail:** no regression in D30 retention or conversion for the pre-launch aviation cohort. The metric that can stop the rollout. |

Instrumentation: `template_id` becomes a property on Thing-scoped analytics events (create, defect created, task
complied, log created, export). No new event *types* are needed — but note what exists today.
`AnalyticsManager` (Firebase Analytics → GA4) implements `logScreenView` plus an untyped `logEvent` escape
hatch, and the click/timing taxonomy in `docs/analytics/analytics_design.html` is still not modelled. Every
metric in this table therefore depends on events that have to be defined and emitted first. That is a small,
independent piece of work with no dependency on the pivot, and it should land **before** Phase 3 ships — a
rollout whose guardrail metric cannot be measured is not a guarded rollout.

---

## 14. Risks

| Risk | Severity | Mitigation |
|---|---|---|
| **Dilution.** An app for everything is an app for nothing; the aviation product's credibility comes from being specific. | High | Config-driven UI means an aviation user's app is *visually and verbally identical* to today — not "mostly the same." Enforced by the byte-identical lexicon snapshot test and the unmodified due-status suite. The aviation guardrail metric can halt the rollout. |
| **Mixed-version sync corruption.** A device running an older build edits a Thing shaped by newer code and drops fields it doesn't understand. | High | Structurally prevented, not just mitigated: Phase 1 ships no way to create a non-airplane Thing, and the backend cutover for an account runs *before* that account's devices ever receive the build that could write one (§9). An explicit round-trip test still proves Wire preserves unknown fields across old/new edits, as a backstop. Verify before Phase 3, not after. |
| **Config over-generalization.** A capability flag per UI element ends in an unreadable config nobody can reason about and a UI that is a matrix of special cases. | Medium | The capability list is **closed and small** (10 flags), and every flag must be justified by at least two presets differing on it. A flag needed by exactly one preset is a code branch, not config. Reviewed at the end of Phase 1. When a fork is structural rather than parametric, the escape hatch is a **pluggable decision engine** behind the existing interface, not another flag — see §7.1, which the engineering design doc should carry forward. |
| **Scope.** Generic components, meters, and capabilities touch aircraft, logs, tasks, squawks, export, overview, and dashboard — nearly every feature module. | Medium | Phase 1 lands the model and config with **zero UI change** and one preset reproducing today. If the phase is correct the app is byte-identical; if not, that is visible immediately and cheaply. |
| **Starter-pack accuracy.** Wrong intervals are worse than none, and carry liability weight in aviation. | Medium | Recommendations-not-authority framing with a manual disclaimer; airplane pack limited to regulatory-universal intervals; no ADs or SBs, ever, in a pack. |
| **Home is a different product.** Home upkeep buyers may want reminders and photos, not a logbook — and may not overlap with the vehicle audience at all. | Medium | Home ships in v1 precisely to find this out early, on a preset that costs a config and a starter pack rather than a feature branch. Track its D30 separately; if home retains poorly it is cheap to de-emphasize in the picker. |
| **Preset catalog sprawl** as users ask for tractors, RVs, sailplanes, firearms. | Low | Custom absorbs the long tail; built-ins added only on demonstrated demand (§5.2). |
| **Localization ceiling** from noun substitution. **This blocks shipping outside English-speaking markets**, and Phase 2 makes it worse rather than merely leaving it. | Low today · **Blocking on the first non-English locale** | Accepted deliberately, not solved — see `template_system_design.md` §13. Today all 982 strings are independently translatable with ordinary plural resources: a solved problem. Phase 2 converts ~230 of them into lexicon substitutions, which cannot express grammatical gender or case — so a quarter of the corpus stops being translatable by translating it. Revisit **before** committing to a non-English market, not after; the likely fix is **per-string overrides** — a template replaces whole strings where substitution's grammar fails, keeping the lexicon for the strings where it works — not a bigger `Noun`. |

---

## 15. Rollout

| Phase | Scope | User-visible? |
|---|---|---|
| **0 — Decisions** | Resolve the open decisions in §16. The free-tier limit and the squawk-word question are cheap now and expensive later. | No |
| **1 — Migration** *(hard gate)* | Non-UI only. `Thing` / `Component` / `Meter` / `ThingTemplate` protos replace the aircraft protos. **`core:template` and `TemplateRegistry` were *not* built here** — `thing_migration_design.md` §1 put them out of scope, and Phase 2 built them (#648). Phase 1 shipped the proto shapes and the data cutover, nothing that reads a template. The developer runs the manual backend cutover (§9.1 step 1) covering Firestore and attachment Storage paths per account, *then* distributes the build to that account's devices, where local backfill (§9.1 step 2) runs automatically. **No template picker, no non-airplane preset, no other new functionality ships until every account has cut over.** | **No — by design.** The app must be indistinguishable, and there is nothing else to see yet. |
| **2 — Lexicon plumbing** *(shipped)* | **Shipped 2026-08-29/30.** String parameterization across all 31 `strings.xml` files, `LocalThingLexicon`, formatter, byte-identical snapshot test. Capability flags wired into the UI, all still on. Notification tier titles resolve through the lexicon; OS channel names stay neutral and their ids are pinned (§8.5). Analytics event taxonomy defined and emitted (§13). Aviation-only. Starts only after Phase 1 has closed out on every account. | No |
| **3 — The pivot ships** *(shipped 2026-08-31 → 09-04 — [`pivot_rollout_design.md`](pivot_rollout_design.md))* | **Shipped:** inflate-on-write and the backfill (#717, #718), presets as assets (#675), the six presets (#721–#723), the type picker and template-driven create form (#738–#740, #781), template-driven rendering of spec, components, meters and export (#703, #729, #730, #770), the switcher (#731), the degraded state (#728), `MeterRule` replacing `EngineHourRule` (#759, #761), technician certifications with derived roles (#684), starter packs with both §13 events (#733, #707), the subscription rename (#735), `PRODUCT.md`/`DESIGN.md` and the store/web repositioning copy (#736), and the snapshot narrowed into the aviation cohort's guardrail (#734). **Still open:** the publishing script, `fetch_templates` RPC and canonical cache (#725–#727). | **Yes** |
| **4 — Depth** | Custom template editor, template-declared spec fields on technician certifications (§8.6), per-preset export layouts beyond logbook/generic, template-aware search, additional presets (3D printer, equipment), a calendar-anchored task rule for seasonal chores. | Yes |
| **5 — Cleanup** | **Shipped inside Phase 3** (#668): fields 2–6 (`make`, `model`, `serial`, `tail_number`, `engine`) are reserved and never reused, and every reader moved to `spec`/`components` first — a stored-data change sequenced with #638 discipline, exactly as this row once demanded. | No |

---

## 16. Open Decisions

| # | Decision | Recommendation |
|---|---|---|
| 1 | Is "squawk" universal or per-template? | **Per-template**, retained for aviation (§11), with the domain-specific word used wherever one fits (issue, defect, fault) — and **"attention"** for Home and Custom, where the one-off-task-vs-defect boundary isn't clean enough to warrant a defect-shaped word (§4.5). |
| 2 | Basic tier Thing limit — hold at the current 2, or raise? | **Raise to 3**, pending conversion data from the recent 1 → 2 move (§12). |
| 3 | Custom template *editor* in v1? | **No — Phase 4.** The Custom *preset* ships in v1 (generic lexicon, user-addable components), and the `ThingTemplate` proto and `CollectionKind` ship in v1 so storage is settled. Only the field/slot/meter builder UI is deferred — a user can already add ad-hoc components and rename the one meter *on their own Thing* in v1; what's deferred is editing the *template* itself so those changes carry forward to future Things. |
| 4 | Can a Thing's template change after creation? | **No.** A Thing's template is fixed at creation. Picking the wrong preset means deleting and re-adding the Thing — rare, since the picker is the very first screen — rather than carrying a narrow in-place-editing path (a "no logs yet" gate, legacy-row rendering for orphaned keys) for a need nobody has demonstrated yet. Revisit if custom-template users hit it in practice. |
| 5 | Does an overdue task block the "ready" status for every preset? | **Config it** (`overdue_blocks_ready_status`), default on. An overdue annual genuinely means not airworthy; an overdue gutter cleaning does not mean the house is unusable, and overstating it trains people to ignore the badge. |
| 6 | Ship 3D printer and shop-equipment presets in v1? | **No** — Custom covers them; they are data-only additions whenever the content is ready (§5.2). |

---

## 17. Appendix — One Task, Three Templates

The same feature, expressed under three configs, showing that the stored rows are identical:

| | Airplane | Car | Home |
|---|---|---|---|
| Task title | Oil change | Oil & filter | Clean gutters |
| Called a… | Inspection | Service | Chore |
| Component | Engine 1 (slot `engine`) | Engine (slot `engine`) | **None** — Thing-level task, no `component_id` |
| Rules | `MeterRule(engine_hours, 50)` + `TimeRule(4 mo)` | `MeterRule(odometer, 5000)` + `TimeRule(6 mo)` | `SeasonalRule([4, 10])` |
| Due-soon at | 10 hrs / 30 days | 500 mi / 30 days | 14 days |
| Month interval snaps | end of month | anniversary | n/a |
| Person who did it | Mechanic (with A&P) | Shop | Person |
| Defect if it fails | Squawk · AOG | Issue · Off the road | Attention · Emergency |

All three are stored as **one `MaintenanceTask` row** — same table, same sync path, same due algorithm.

> **Why this is the right shape.** Maintaining an airplane and maintaining a house differ in *vocabulary, detail,
> and which knobs exist* — not in *structure*. Encoding that as a template config — a lens over a stable schema
> rather than a schema per domain — means each new domain is a config file and a starter pack, not a feature
> branch. The test of the design is that **home**, the preset furthest from aviation, needed no new concepts
> beyond one rule type and one flag.
