# Design Doc: Phase 3 — The Pivot Ships

**Status:** Proposed
**Supersedes nothing.** Builds on `template_system_design.md` (Phase 2) and `thing_migration_design.md` (Phase 1).
**Milestone:** Milestone 3 — The Pivot Ships (Phase 3)

---

## 1. Scope

Phase 1 changed the data. Phase 2 built the machinery with exactly one preset and **no user-visible change**.
Phase 3 is the first phase a user can see, and the first that writes durable data in the template shape.

This doc decides the things Phase 3 has to decide. It deliberately does **not** restate
`template_system_design.md`, which already settles distribution (§4), the DNA model (§5), compatibility gating
(§6), storage (§7), resolution (§8), and the localisation ceiling (§10a). Where this doc references those, it
cites them rather than paraphrasing.

**Out of scope**, and named so this doc does not sprawl: Phase 4's custom template editor, per-preset export
layouts, template-aware search, and the template marketplace PRD §2 rules out.

---

## 2. What Phase 2 actually left behind

Verified against the tree, not against the plan. Two of these are not what the PRD or the issue tracker
believed.

|                                                             | State                                                                              |
|-------------------------------------------------------------|------------------------------------------------------------------------------------|
| `ThingTemplate` / `Lexicon` / `Capabilities` protos         | Shipped                                                                            |
| `core:template`, `TemplateRegistry`, `CurrentThingTemplate` | Shipped                                                                            |
| Airplane preset                                             | Shipped, **as Kotlin** (`canonical/AirplaneTemplate.kt`), not as an asset — see §5 |
| Lexicon plumbing, formatter, byte-identical snapshot test   | Shipped                                                                            |
| Capability flags                                            | Wired, **all `true`**                                                              |
| Analytics taxonomy + `template_id` on Thing-scoped events   | Shipped                                                                            |
| `Thing.template` (field 12) — **read** path                 | Shipped: `forThingWithFallback(thing) = thing.template ?: fallback`                |
| `Thing.template` — **write** path                           | **Absent.** Nothing in the app ever writes it                                      |
| `Thing.spec` (10) / `Thing.components` (11)                 | **Written only by the backend cutover script.** Never by the client                |
| `Thing` transitional fields 2–6                             | Still present, still populated, still read by 12 files                             |

### 2.1 The gap that matters

§5.3 decided there is **no backfill** for `Thing.template`, and the reasoning is sound: an un-inflated Thing has
no customisation to miss, the set of Things without DNA is closed, and absent resolves to airplane
unconditionally. That decision stands and this doc does not reopen it.

**It does not extend to `spec` and `components`, and the difference is easy to miss.**

`template` is *derivable* — the fallback reconstructs it exactly, because a Thing without DNA can only be an
airplane. `spec` and `components` are **the Thing's own values**: `"Cessna"`, `"172"`, serial `SN-1`, this
engine and that propeller hub. A template declares that an airplane *has* a make field. It cannot tell you the
make. There is no fallback that reconstructs them from a template, only from fields 2–6.

So the populations differ:

| Population | `template` (12) | `spec` (10) / `components` (11) | Fields 2–6 |
|---|---|---|---|
| Migrated by the cutover | absent → resolves to airplane | **populated** by `thingPayloads.ts` | populated |
| Created by the client since the cutover | absent → resolves to airplane | **empty — nothing ever wrote them** | populated |
| Created after §4 ships | inflated | inflated | populated (until §7) |

The middle row is the problem, and **it grows every day inflate-on-write is not shipped.**

> This also reaches further than the field-removal task it was found under (#668). §6.2's degraded state renders
> "its raw spec values as unlabelled key/value pairs" — which reads `spec`. For a Thing created since the
> cutover, `spec` is empty, so the degraded state would render a name and nothing else. The one screen designed
> to never lose the user's data would lose it.

---

## 3. Decision: inflate on write, and repair the middle row

**Inflate `template`, `spec` and `components` on every Thing write** — create and edit alike. Same choke point,
one code path.

This is §5.3's "inflate on next write" rule, extended to the two fields it did not cover, and it is the right
shape for the same reason: the population of un-inflated Things shrinks to zero through ordinary use, with no
migration run.

**But it does not close the middle row on its own,** and this is where `spec` differs from `template` a second
time. §5.3 could rely on ordinary use because the fallback covered un-inflated Things "indefinitely and
correctly" in the meantime. There is no such cover here. A Thing created since the cutover and never edited
again has its data *only* in fields 2–6 — so those fields cannot be removed until every such Thing has been
written at least once, and nothing guarantees that ever happens.

Three ways out, and the choice is not obvious:

| Option                                                        | Cost                              | Risk                                                                        |
|---------------------------------------------------------------|-----------------------------------|-----------------------------------------------------------------------------|
| **A. Backfill pass** (#718)                                   | A migration, run once per account | #638 discipline; the Phase 1 precedent is a backend script                  |
| **B. Derive on read** — synthesise `spec` from 2–6 when empty | No migration; a mapping function  | The mapping lives forever; 2–6 can never be removed                         |
| **C. Never remove 2–6**                                       | Nothing                           | The schema keeps a dead shape and every new reader must know which to trust |

**Recommendation: A, and schedule it early.** B and C both keep fields 2–6 alive permanently, which means every
future reader faces "which of these two places holds the truth" — the ambiguity the removal exists to end. A is
a one-time cost that is *smallest if taken soonest*, because the set it repairs is still growing.

### 3.1 This is a hard gate, not a priority

**Inflate-on-write and the backfill ship before any user-visible Phase 3 change.** Not "early" — *first*, in the
same sense Phase 1's migration row was a hard gate. No preset, no picker, no template-driven rendering lands
until a Thing's data is in one place.

Three reasons, in increasing order of how much they cost to ignore:

**The repair set grows daily.** Every Thing created before inflate-on-write ships is one the backfill has to
find. Shipping the picker first means shipping the thing that *creates more Things* before the thing that stops
them being created broken.

**Every user-visible feature built first has to be re-verified afterwards.** A screen written while three
populations exist — migrated, un-inflated, inflated — is a screen whose correctness depends on which population
it was tested against. Building the picker, the component tree and the meter-driven log form on top of a data
shape that is about to change means testing each of them twice, and the second pass is the one nobody schedules.

**A user-visible pivot is the worst possible moment to be running a data migration.** Phase 1 was deliberately
invisible for this reason. Phase 3 is deliberately visible, and it is the phase with the most new surface — so
the migration should be finished and quiet before the surface arrives, not competing with it for attention when
something goes wrong. A bug report during the pivot should never require asking "is this the new UI, or is this
Thing's data half-migrated?"

So the phase order is **3A → 3C → everything else**, and 3C's own steps are strictly ordered (§7).

### 3.2 Testing it, without touching a real account

The stress test in Developer Options is the right instrument, and it is already most of the way there.

`StressTestViewModel` creates its Thing through `fleetManager.updateThing(data.thing)` — **the same choke point
inflate-on-write sits at.** So once §3 lands, the generator produces correctly inflated Things with no change to
it, which is a useful property in itself: it means the stress test exercises the real write path rather than a
parallel one.

More usefully, it can produce the populations the backfill has to handle:

| Population needed | How the generator makes it |
|---|---|
| Un-inflated — created since the cutover, `spec`/`components` empty | **What it produces today.** `FakeDataGenerator` writes fields 2–6 only |
| Inflated | What it produces after §3, unchanged |
| Migrated-by-cutover — both populated | The generator writing both, as `thingPayloads.ts` does |

**That means a toggle is worth adding rather than removing the old behaviour.** After inflate-on-write ships,
the generator stops being able to produce broken Things — and broken Things are exactly what the backfill needs
to be tested against. A developer-only switch that writes the pre-inflation shape keeps the fixture available.

This is all behind `isDeveloperOptionsSupported`, so none of it reaches a release build, and it generates data
in the developer's own account rather than requiring a production one to be sacrificed.

> `FakeDataGenerator` is also one of the 12 files reading fields 2–6 (§7), so it moves in step 3 like the rest.
> Worth doing deliberately: it is the one whose *output* the backfill test depends on, so changing it and the
> backfill in the same change removes the fixture the test needs.

---

## 4. The create flow

### 4.1 What creation writes

The picker chooses a canonical template; creation inflates it (§5). Concretely, one write containing:

- `template` — the inflated DNA, copied whole. Never a reference (§5).
- `spec` — the values the create form collected, keyed by the template's declared spec fields.
- `components` — the tree the template's component slots describe, instantiated.
- Fields 2–6 — **still written**, until §7 removes them. Dual-write is what makes the removal safe later, and
  it is the step Phase 2 was believed to have taken and had not.

### 4.2 Can it be wrong?

Two failure modes, both silent, both worth designing against rather than discovering.

**A template that cannot be rendered by the client that just created a Thing from it.** §6 makes
`min_app_version` an author-set contract, and a wrong value is silent. The create flow should refuse a template
whose `min_app_version` exceeds the running build *before* inflating it, not after — a Thing created and
immediately degraded (§6.2) is a worse outcome than a preset missing from the picker.

**A template that is structurally invalid.** A meter with no `key`, a component slot referencing a parent that
does not exist, a spec field whose key collides. §4.1's publishing script validates on the way out; the client
should validate on the way in, because the baked-in pool and the fetched pool arrive by different routes and
only one of them passes through the script.

> `template_system_design.md` §4 already argues this: baked-in templates should be *assets* precisely so they
> "travel the same decode-and-validate path" as fetched ones. Phase 2 could not do that (§5 below). Phase 3
> can, and this is the reason it should.

### 4.3 What the picker offers

Canonical retention is a product question, not a technical one (§5.1) — nothing references canonical templates
at render time, so nothing is obliged to keep old versions reachable. The picker offers the current version of
each preset the build knows about, plus whatever the fetch RPC has added, filtered by `min_app_version`.

---

## 5. The six presets, and the `protoc` problem

Six to author: **car, motorcycle, bike, boat, home, custom.**

**`home` is the load-bearing one, and should be built first.** PRD §4.2: a house has no make, no model, and no
serial number. §4.4: its meter list is *empty*. It is the preset that proves the template system is real rather
than aviation with the nouns swapped, and it is the one most likely to find a place where an airplane assumption
is baked into a screen. Building `car` first would find almost none of them — a car has a make, a model, a VIN
and an odometer, and maps onto the airplane shape almost field for field.

**`custom` is the floor.** It declares almost nothing (PRD §4.7: "no lexicon, no components, no meters, all rule
types, 30-day due-soon window"). Every screen has to render something sensible for it, which makes it the
cheapest test of the template-driven boundary in §6.

### 5.1 The blocker #675 names

Presets should be binary proto assets. Phase 2 shipped airplane as Kotlin because **`protoc` is not available to
Gradle in this repo** — only `grpc_tools_node_protoc`, inside the backend's `node_modules`.

That is now on the critical path in a way it was not in Phase 2, for the reason §4 states: drift becomes
possible for a preset like `car`, baked into the build that introduces it *and* served by the fetch RPC to
clients still on the previous build. Two sources, one name, no mechanism keeping them equal.

**And the publishing script (§4.1) needs `protoc` regardless.** So the work is not "port airplane to a text
proto" — it is "make `protoc` available to the build", after which both the asset pipeline and the publishing
script become possible. #675 is the first task of 3B for that reason, not a cleanup at the end of it.

### 5.2 How a preset is tested

A preset with a wrong meter key is a broken screen, not a failed build — nothing in the type system connects
`"airframe_hours"` in a template to the code that reads it.

The check that generalises: **every key a template declares must resolve.** Meter keys, component slot keys,
spec field keys, and — if `string_overrides` is ever built (§9) — resource names. Run over every template in the
pool, baked-in and fetched alike. This is the same shape as the guard `AnalyticsTaxonomyTest` applies to GA4
names, and it exists for the same reason: the failure is silent and the data is wrong rather than absent.

---

## 6. Which UI is template-driven

The boundary, stated once so it is not re-litigated per screen:

| Template-driven                         | Stays hardcoded                                    |
|-----------------------------------------|----------------------------------------------------|
| Spec field labels and order (#703)      | The *shape* of the Thing form — a list of fields   |
| Component tree — slots, labels, nesting | The tree widget itself                             |
| Meter labels, units, which exist        | The log form's structure                           |
| Which sections exist, via capabilities  | Navigation, settings, sharing, export, technicians |
| Starter task packs (§7 below)           | Everything account-scoped                          |

The right-hand column is not an oversight. Phase 2 established the rule the hard way, twice: account-scoped
surfaces take neutral copy rather than lexicon substitution (#687), and a string may only be filled from a
thing's lexicon when the surface rendering it belongs to **exactly one thing** (#682, after all 14 technician
conversions were reverted). The same rule decides UI, not just copy — a screen that spans every Thing in the
account cannot be shaped by any one Thing's template.

**Capabilities remove, they do not disable** (§10). Phase 2 wired every call site while every flag was `true`,
deliberately, so the removal path was exercised at zero behavioural risk. Phase 3 is where flags first vary, and
the wiring is already in place — this phase should be *choosing values*, not retrofitting the mechanism. If a
screen turns out to need new capability plumbing, that is a Phase 2 gap surfacing late and worth flagging as
such rather than absorbing quietly.

---

## 7. Removing the transitional fields

Strictly ordered. Each step must be **complete and deployed** before the next begins.

1. **Inflate on write** (§3) — the client populates `template`, `spec`, `components`.
2. **Backfill** (§3, option A) — repair Things created between the cutover and step 1.
3. **Move the readers** — 12 production files off fields 2–6 onto `spec` / `components`:
   `ExportManagerImpl` · `LogbookExportArchiveBuilder` · `ComponentSection` · `UrgencyScanner` ·
   `FakeDataGenerator` · `StressTestViewModel` · `OverviewTab` · `ThingDataCard` · `AirframeSection` ·
   `EditThingScreen` · `EditThingUiState` · `EditThingViewModel`
4. **Reserve** — `reserved 2, 3, 4, 5, 6;` and the names, matching the treatment fields 7 and 8 already got.

A reader moved before step 2 completes reads an empty string. That is the whole reason for the ordering, and it
is why step 3 cannot be started opportunistically alongside step 1.

> `EditThingViewModel`'s serial validation moves with step 3 — `isValid` depends on the airframe, engine, hub
> and blade serials, and `component_serial_prompt` already gates whether they are required. That gate is the
> template-driven replacement for the hardcoded rule.

---

## 8. What replaces the byte-identical guarantee

Phase 2's snapshot test proved the aviation lexicon renders **exactly** today's copy — 230 converted strings,
byte for byte. Phase 3 deliberately changes what users see, so the test cannot survive unchanged.

**It also cannot simply be deleted.** PRD §13's guardrail is *no regression in D30 retention or conversion for
the pre-launch aviation cohort*, and that cohort's copy not drifting is part of not regressing. Deleting the
only mechanical check on that, in the phase most likely to disturb it, is how a guardrail becomes a hope.

**The replacement: the snapshot narrows rather than disappears.** It keeps asserting that *the airplane lexicon*
renders today's strings byte-identically, and stops asserting anything about strings that are now
template-varying. Concretely: the recipe table stays, the airplane rendering stays pinned, and a string that
Phase 3 intentionally changes for every domain is removed from the snapshot in the same commit that changes it —
visibly, one row at a time, rather than by deleting the file.

That keeps the property that made it valuable: a change to aviation copy is either deliberate and visible in the
diff, or it fails.

---

## 9. Localisation: settled

**English only** (#677). Chinese, Japanese, Korean and Spanish are possible future targets; none is committed.

§10a now carries the per-language cost table. Nothing is built: no `string_overrides`, no ICU pipeline, no
gender / classifier / particle fields on `Noun`. Adding `string_overrides = 15` later is additive and backward
compatible, so there is **no proto deadline** forcing the decision now.

The one habit worth adopting during Phase 3, because it costs nothing: when a new string takes a lexicon
placeholder, put the substitution at a phrase boundary. `"Add %1$s"` survives translation into all four
languages; `"Delete this %1$s?"` does not.

---

## 10. Decisions

| #  | Decision                                                                                                                  | Where |
|----|---------------------------------------------------------------------------------------------------------------------------|-------|
| 1  | Inflate `template`, `spec` and `components` on every write, not only create                                               | §3    |
| 2  | Backfill the Things created between the cutover and inflate-on-write, rather than deriving on read or keeping 2–6 forever | §3    |
| 3 | **3C is a hard gate**: inflate-on-write and the backfill ship before any user-visible Phase 3 change | §3.1 |
| 3a | Test with the Developer Options stress test — it already writes through the same choke point; keep a toggle producing the pre-inflation shape as a backfill fixture | §3.2 |
| 4  | The create flow validates `min_app_version` and template structure *before* inflating                                     | §4.2  |
| 5  | Build `home` first; it is the preset that proves the system                                                               | §5    |
| 6  | Make `protoc` available to Gradle first; the asset pipeline and publishing script both need it                            | §5.1  |
| 7  | Every declared key must resolve, checked over the whole pool                                                              | §5.2  |
| 8  | Account-scoped UI stays hardcoded, on the same rule that governs account-scoped copy                                      | §6    |
| 9  | The snapshot test narrows to the airplane lexicon rather than being deleted                                               | §8    |
| 10 | English only; nothing built                                                                                               | §9    |

---

## 11. What Phase 3 builds

**Order matters.** 3A gates the phase; 3C gates everything user-visible (§3.1). The rest can be sequenced on product priority once those are done.

| Build                                                     | Defer                                                |
|-----------------------------------------------------------|------------------------------------------------------|
| **First — the gate: inflate-on-write (#717), then the backfill (#718)** | Template-aware search (Phase 4) |
| `protoc` in the build; presets as assets (#675)           | The custom template editor (Phase 4)                 |
| Six presets, `home` first                                 | Per-preset export layouts (Phase 4)                  |
| Template picker and create flow                           | Template sharing / marketplace (PRD §2 rules it out) |
| Fetch RPC + throttle, publishing script, canonical cache  | `string_overrides` and any localisation (§9)         |
| Degraded state (§6.2) — now reachable                     |                                                      |
| Template-driven spec / component / meter rendering (#703) |                                                      |
| Starter packs and their analytics (#707)                  |                                                      |
| Derived technician roles (#684)                           |                                                      |
| Transitional field removal (#668), **after** 1–3 above    |                                                      |
| Subscription rename, `PRODUCT.md` / `DESIGN.md` revisions |                                                      |

---

## 12. Corrections to existing docs

Found while writing this, and worth fixing rather than leaving to contradict each other:

- **PRD §3.3** says custom templates bring "the current 11 `CollectionKind`s to 12". `template_system_design.md`
  §5.1 supersedes this: the DNA lives on the `Thing` document, so there is **no twelfth `CollectionKind`**, and
  no new `wireName` / `schemaName` to commit to. The PRD row should say so.
- **The `Thing` proto comment** on fields 2–6 says they are "dual-written until every dogfood account has
  migrated". They are not dual-written; only the backend cutover ever wrote the other side. The comment should
  say what is true until §7 makes it moot.
