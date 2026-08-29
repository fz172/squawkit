# Design: The Template System — Definition, Distribution, and Resolution

> **Implementation status.** **Design settled 2026-08-29; nothing has shipped.** Every open question in §11 has
> been decided. This designs the machinery Phase 2 needs
> (`core:template`, the template definition protos, the registry, and the distribution path) plus the
> compatibility rules Phases 3–4 depend on. It does **not** design the six non-airplane presets; that is Phase 3
> product work.

**Owner:** Engineering · **Status:** Proposed · **Date:** 2026-08-29
**Related:** [Multi-domain maintenance PRD](multi_domain_maintenance_PRD.md) (§4 the config blocks, §4.5 lexicon,
§4.8 capabilities, §15 rollout) · [Aircraft → Thing migration](thing_migration_design.md) (the phase this builds
on, and the source of the wire-identity discipline below)

---

## 1. Scope

**In scope:** how a template is *defined* (the proto), how it *reaches a device* (baked in plus a fetch RPC), how
it is *versioned* (immutably), how a client decides it can *handle* one, and what happens when it cannot.

**Out of scope:** the content of the six non-airplane presets, the template picker UI, the custom-template
editor (Phase 4), and any change to the entity protos themselves.

---

## 2. Current state — what Phase 1 actually left behind

Worth stating precisely, because the PRD is wrong about it.

**PRD §15's Phase 1 row asserts that `core:template` and `TemplateRegistry` exist.** They do not.
`thing_migration_design.md` §1 explicitly placed the template system out of Phase 1 scope, and Phase 1 shipped
the proto rename, the path migration, and nothing else. Issue #669 tracks correcting that row.

What Phase 1 *did* leave, and what this design gets to rely on:

| Exists today | Where |
|---|---|
| `Thing` with `template_id` (7) and `template_version` (8) | `core/model/.../proto/thing/thing.proto` |
| `Spec`, `Component` | same directory |
| Every Thing carrying `template_id = "airplane"`, backfilled | production, 25 accounts (#603) |
| A callable-RPC precedent with its own proto package | `proto/rpc/request_export_delivery/` |
| A single monotonic `versionCode` | `version.properties` (`versionCode=1400`) |

**No `ThingTemplate`, `Lexicon`, or `Capabilities` proto exists.** The PRD shows all three, but as illustrations.
Writing them is task #647 / #650 and this doc's §3.

The two fields `template_id` and `template_version` being *already populated in production* is the single most
useful thing Phase 1 left: the resolution path below has real data to resolve against on day one, rather than a
backfill to arrange first.

---

## 3. The definition language

A template is a proto. Baked-in presets and fetched templates are **the same message**, which is the property
that makes one parser, one validator, and one registry serve both.

```proto
// thing/template.proto — java_package = "dev.fanfly.wingslog.thing"
message ThingTemplate {
  string id = 1;                  // "airplane" · "car" · "home"
  int32 version = 2;              // immutable once published; see §5
  int32 min_app_version = 3;      // versionCode floor; see §6

  Lexicon lexicon = 4;            // §4.5 of the PRD
  Capabilities capabilities = 5;  // §4.8
  repeated SpecField spec_fields = 6;
  repeated ComponentSlot component_slots = 7;
  repeated MeterDef meters = 8;
  repeated StarterTask starter_tasks = 9;

  string display_name = 10;
  string icon = 11;
  int32 sort_order = 12;
}
```

`(id, version)` is the identity. Everything else is payload.

**One message, two lifecycles.** The same `ThingTemplate` serves the global canonical pool and the copy inflated
into each Thing as its DNA (§5). The canonical pool is a local cache; the DNA is a field on `Thing` and needs no
storage of its own. That distinction decides what is immutable, what syncs, and what a share member can read.

---

## 4. Distribution: baked in, then fetched

Two sources, in priority order, resolving to one pool.

**Baked in.** The app ships the presets current at build time, as **binary proto assets** rather than as Kotlin
that constructs the messages. That is not a packaging preference — it means baked-in and fetched templates travel
the same decode-and-validate path. Constructing them in Kotlin creates a second path that is not exercised by
the fetch tests and will drift.

**Fetched.** A callable RPC returns templates the app did not ship with, so **introducing a template does not
require an app update** — the requirement that motivates the whole design. It follows the existing
`request_export_delivery` shape: a proto request/response pair under `proto/rpc/`, invoked as a Firebase callable.

```proto
// rpc/fetch_templates/fetch_templates.proto
message FetchTemplatesRequest {
  int32 app_version = 1;                  // caller's versionCode
  repeated TemplateRef known = 2;         // (id, version) already held
}
message FetchTemplatesResponse {
  repeated ThingTemplate templates = 1;   // only what the caller lacks AND can run
  int32 poll_after_seconds = 2;
}
```

**Authentication: App Check *and* a signed-in user.** `enforceAppCheck: true` plus `requireAuthenticatedApp` —
the same pair `requestExportDelivery` and `createAircraftShareInvite` already use.

The user identity is **not** there for authorization. Canonical templates are neither per-user nor secret, and
every caller sees the same pool. It is there to make throttling real.

**Throttled at roughly 10 calls per day, keyed on `uid`.** An earlier revision of this section required App
Check alone and keyed the limit on the install id `device_config` holds for push registration. That was weak in
a way worth recording: **an install id is client-supplied and rotatable**, so the limit it enforces is advisory
— a client that wants more calls simply presents a new one. `request.auth.uid` is issued by Firebase Auth and
cannot be rotated at will, which is the difference between a throttle and a suggestion.

This follows the established pattern rather than inventing one: invite-code dereferencing is rate-limited per
uid through `rateLimit.ts`, with state under `invite_attempts/{uid}` — a collection `firestore.rules` already
locks to functions-only.

**Anonymous sign-in counts.** Guests have a uid like anyone else, so they are throttled normally rather than
excluded. And a client with no session at all is not blocked from creating Things — it simply never calls this,
and uses the baked-in pool, which is complete by construction (below). Sign-in gates *new* templates, never the
ability to use the app.

Two further properties:

- **The server filters by `app_version`.** A client never receives a template it cannot run, so the client-side
  check in §6 is a second line of defence rather than the only one. Filtering server-side also means the
  *response* stays small on old clients instead of shipping payloads they will discard.
- **The fetch is never on the critical path.** The baked-in pool is complete and self-sufficient; the RPC only
  ever *adds*. A failed fetch, an offline device, or a throttled call degrades to "the presets this build shipped
  with," which is exactly the app's behaviour today. Nothing blocks on it. `poll_after_seconds` lets the server
  pace clients without them guessing.

### 4.1 The global cap

The per-uid limit stops one account looping. It does **not** stop a systemic event — a bad client release
polling on a timer, across every install at once — and that is the failure this cap exists for.

**Budget: 100,000 calls/day**, sized as 100,000 users × 1 call/day.

**That sizing has no headroom, and should be a configured value rather than a constant.** At exactly expected
load, the first genuinely busy day trips it: a user who opens the app on a phone and a tablet spends two calls,
so does one who force-quits and reopens. The cap's job is catching a runaway three orders of magnitude out
(a one-second poll loop across 100k installs is ~8.6 *billion* calls/day), not trimming a 20% overshoot — so it
wants deliberate slack, and it wants raising without a deploy when the user base grows.

**Counting it needs a sharded counter, not one document.** Firestore sustains roughly **one write per second per
document**. 100,000/day averages ~1.2/sec, which already sits at the limit before accounting for the morning
peak every consumer app has. A single `quota/{date}` document would go into contention exactly when load is
highest — the moment the cap most needs to be accurate.

Two workable shapes:

- **Sharded counter** — `template_fetch_quota/{date}/shards/{0..9}`, each call incrementing a random shard,
  the total read as a sum. The standard Firestore answer; ten shards give ten times the write ceiling.
- **Derive it from the per-uid documents** — the throttle in §4 already writes one document per uid per day, so
  a scheduled function can count them and publish a running total with **no extra write on the request path**.
  The count lags by the schedule interval, which is fine for a backstop and not fine for a hard limit.

The second is cheaper and reuses state that has to exist anyway. The first is exact. For a cap whose purpose is
catching runaway rather than metering, the lag is acceptable — but this is a real choice and the doc should not
pretend otherwise.

**Tripping the cap must not surface as an error.** The client already treats a failed fetch as "use the baked-in
pool" (§4), and that path is correct and complete. So a capped response is an ordinary **success with zero
templates and a long `poll_after_seconds`** — invisible to the user, and it sheds load instead of provoking a
retry storm from clients treating failure as transient.

Better still, `poll_after_seconds` should widen *as the budget depletes* rather than only at the wall. A cap
that trips at 09:00 and starves everyone until midnight is a worse outcome than one that slows everybody down
from 70% consumption onward.

### 4.2 Publishing: source-controlled text protos

Canonical templates are **authored as text-format protos, checked into the repo**, and published by an admin
script. Not authored in a console, not hand-encoded.

```
templates/
  airplane.v1.textproto
  car.v1.textproto
```

Source control is the point: a template is a product artifact that gets reviewed, diffed, and blamed like code.
Immutability makes that doubly true — a published template can never be corrected, only superseded (§5), so the
review that matters happens before publication, which means it has to happen against a file in a PR.

**The same file produces both consumers.** `protoc --encode=ThingTemplate` compiles a `.textproto` to binary, so
the **baked-in asset** (§4) and the **published canonical** are built from one source and cannot drift. That is
not a convenience — a baked-in `airplane` that disagrees with the served `airplane` of the same version would be
a silent, per-device inconsistency with no natural detector.

The script follows `grant-entitlement.mjs`: reads the file, validates, prints the resolved project, and confirms
before writing. Validation is the interesting part, because publication is irreversible:

- `(id, version)` must not already exist — **refuse, never overwrite.** This is the guard that makes
  immutability real rather than a convention.
- `min_app_version` must be set and not in the future relative to the current `versionCode`.
- Every enum value must be one this repo's protos define, so a typo in a capability name fails at publish
  rather than on a user's device.

Where the published bytes live — a Firestore collection keyed `(id, version)`, or Cloud Storage objects served
by the callable — is an implementation choice with no user-visible consequence. Firestore is the smaller step:
the callable already has `adminDb`, and the documents are a few KB.

---

## 5. Canonical templates, and the Thing's DNA

Two kinds of template, and the distinction decides everything below.

| | **Canonical** | **Inflated (the Thing's DNA)** |
|---|---|---|
| Scope | global, one copy for everyone | one Thing |
| Mutability | **immutable** — a change is a new `version` | the user's to edit, per Thing |
| Source | baked-in assets + fetch RPC (§4) | copied from canonical at creation, or authored from scratch |
| Purpose | **the picker, and nothing else** | rendering that Thing |
| Storage | a local cache (§7.1) | **a field on the `Thing` message itself** (§7.2) |

**At creation, the chosen canonical template is inflated into the Thing and becomes its DNA.** From that moment
the Thing carries everything needed to render itself. Customising, or authoring from scratch, produces the same
thing: DNA belonging to that Thing.

```proto
message Thing {
  // ... fields 1-11 as shipped in Phase 1 ...
  ThingTemplate template = 12;   // the DNA — inflated at creation, never a reference
}
```

`template_id` (7) and `template_version` (8) survive as **provenance** — which canonical template this DNA came
from — not as a lookup key. Nothing resolves through them at render time.

### 5.1 What self-containment buys

Three problems that earlier revisions of this doc spent sections solving simply do not arise.

**Canonical retention.** If Things referenced canonical `(id, version)`, the canonical pool would be obliged to
keep every version reachable forever, or a Thing on `(airplane, 1)` could not render on a build shipping only
v3. Nothing references canonical templates at render time, so nothing asks. Canonical retention becomes a
product question about what the picker offers.

**The sync race.** A previous revision put the user's template in a separate synced document, which meant a
device could hold a Thing whose template had not arrived yet — an unresolvable Thing for as long as the pull
took. **One document cannot arrive before itself.** The race is gone, not mitigated.

**Sharing.** `firestore.rules` grants a member access to the Thing document itself
(`allow get: if ... isShareMember(userId, acId)`). The DNA is *in* that document, so a technician gets it with
the read they already make — **no new `isSharedAircraftKind` entry, no new match block, no grant over a
user-root collection, and no privacy question about which kinds of thing the host owns.** The earlier revision
needed all four.

It also means **no twelfth `CollectionKind`**, despite PRD §3.3 anticipating one. Nothing new syncs; the `Thing`
payload simply got bigger. That avoids committing to a `wireName` and `schemaName`, which are stored identity
and cannot change later without a migration (#638).

### 5.2 What it costs, stated honestly

**Per-Thing customisation is per-Thing — and for now there is none.** *Decided: DNA is write-once at creation
and not editable.* That removes the cost this section was written to flag, for as long as it holds: with no edit
path, three airplanes having three identical sets of DNA is invisible.

It becomes real if editing ever ships, and the model fixes the answer in advance: editing is **per-Thing**.
There is no "my airplane template" to adjust once, and making one appear later would be a fan-out write across
every Thing sharing a provenance, not a single edit. Worth knowing before the feature is promised.

**Every Thing edit re-uploads its DNA.** The payload is one blob; changing a tail number re-pushes the template
with it. A full template — lexicon, capabilities, spec fields, component slots, meters, starter tasks — is on
the order of a few KB, well inside Firestore's 1 MiB document limit, and Thing edits are rare compared with log
and squawk writes. Acceptable, but worth measuring rather than assuming once real templates exist.

### 5.3 Existing Things: no backfill

**No migration.** Every Thing in production predates the template system entirely, so there is no template data
to move — the field is simply absent, and absent is a state the resolver can answer without help.

An earlier revision of this section recommended a server-side backfill, on the grounds that lazy resolution
would make a member fall back to their own baked-in pool when reading a host's un-inflated Thing, reintroducing
the canonical-pool dependency this model removes. **That reasoning was wrong**, and it is worth recording why,
because it is a plausible-sounding argument:

> An un-inflated Thing has **no customisation to miss**. It was created before templates existed. Falling back
> to the canonical airplane template is not an approximation of its DNA — it *is* its DNA, arrived at by a
> different route. There is nothing for the host and member to disagree about.

So the rule is simply:

**Absent `template` resolves to the baked-in canonical template named by `template_id`.** Phase 1 backfilled
`template_id = "airplane"` onto every production Thing (#603), which is exactly the handle that makes this work
with no migration — the provenance fields are load-bearing after all, just not at render time for inflated
Things.

`template_id` therefore **stays** rather than being replaced by `template`. It costs two fields already
populated, and it is the only thing standing between a legacy Thing and an unresolvable one. Removing it would
force an unconditional "assume airplane" default — correct today, and precisely the kind of implicit assumption
that becomes wrong the moment a second preset exists.

**Inflate on next write.** When a Thing without DNA is next saved, the resolved template is written into it. No
migration run, no separate pass, and the population of reference-resolved Things shrinks to zero through
ordinary use. Until then the fallback covers them, indefinitely and correctly.

The one residual: if a canonical airplane v2 is ever published with different labels, two clients on different
builds could render the *same legacy Thing* slightly differently until it is next written. Small, self-healing,
and only reachable in the window before that Thing's first save after this ships.

---

## 6. Compatibility gating

`min_app_version` is a `versionCode` floor, checked against `version.properties`'s single monotonic
`versionCode` (currently `1400`), which is shared across platforms.

**A client refuses any canonical template whose `min_app_version` exceeds its own** — absent from the picker
entirely, not shown-and-disabled, the same principle §4.8 applies to capabilities.

### 6.1 What this actually protects against

Not parsing. Proto3 round-trips unknown fields, and Phase 1 proved it for exactly this schema
(`ThingUnknownFieldRetentionTest`). A client can *decode* a template from the future without difficulty.

What it protects against is **semantics**: a template that declares a capability, section, or meter type the
build has no code for. The client parses it perfectly and then renders something incoherent.

That makes `min_app_version` an **author-set contract**, and a wrong value is silent — the template works until
it reaches a client that hits the unimplemented path. So the client should not trust it alone:

**Belt and braces: a client also refuses a template that references an enum value it does not recognise.**
Proto3 surfaces unknown enum values distinctly, so "this template names a capability I have never heard of" is
mechanically detectable, independent of whether the author set the floor correctly. `min_app_version` is the
declared contract; unknown-enum detection is the check that does not depend on anyone getting it right.

### 6.2 The unresolvable-template state, which is now routine rather than exotic

"Don't show it" is the right answer for the **picker**. It is not available for a Thing that already exists.

The DNA model removes the case an earlier revision worried about — a Thing arriving before its template — because
one document cannot arrive before itself. **One case survives, and it is the one that matters:**

> A Thing is created on a device running a newer build, with DNA declaring `min_app_version` above the reader's
> `versionCode`, or naming a capability enum the reader has no code for. It syncs to an older device. The DNA is
> right there in the document, fully readable — and still not renderable.

The Thing may not be hidden. It is the user's data, it appears in their switcher, it counts against their limit,
and silent disappearance is the failure mode this codebase refuses elsewhere. Rendering it under a *fallback*
template is worse — that shows a boat's data with airplane labels.

**A Thing whose DNA cannot be interpreted renders in a defined degraded state**: its name, its raw spec values
as unlabelled key/value pairs, and a prompt to update. Never hidden, never relabelled under a fallback, and
**never editable** — editing under a template you cannot interpret is how a client writes data violating rules it
cannot see.

Note this state is now *permanent until the app updates*, not transient. That makes the copy simpler than the
earlier design needed — there is no "loading" case to distinguish — but it also means the prompt is the whole
remedy, which is why §6.3 matters.

This is the same shape as `BlobDownloadDriver`'s "a 404 is an answer, not an outage": an unresolvable template is
a known state to represent, not an error to swallow.

### 6.3 The update prompt is platform-specific

- **Android / iOS** — a real action: deep-link to the store listing.
- **Web** — there is no install step. The deployed bundle updates on reload, so the prompt is "reload," and a
  stale web client is a cache-lifetime question rather than an install one.

Web reads the same `versionCode` once §11 #1's task lands. Until then the web build renders a version string
that omits it entirely, so a floor check on web has nothing to compare against — which is why that task gates
any use of `min_app_version`, not just its display.

---

## 7. Storage

The two pools are stored by completely different machinery, which is the clearest evidence they are actually
two things.

### 7.1 Canonical: a local cache, no `CollectionKind`

Global, identical for everyone, and not the user's data. A dedicated SQLDelight table keyed `(id, version)`
holding encoded bytes — outside the `entity` table and outside the sync engine.

It needs nothing that machinery provides: no dirty tracking, no watermarks, no tombstones, no per-user scoping.
Routing it through `EntityStore` would replicate one identical document into every account and make each user's
copy independently corruptible.

It also gets **no `CollectionKind`**, and that is worth being deliberate about: adding one means choosing a
`wireName` and a `schemaName`, which are **stored identity** that cannot change later without a migration
(#638). The canonical pool needs no such commitment.

### 7.2 DNA: a field on `Thing`, and therefore no storage of its own

The inflated template is a field on the `Thing` message (§5). It is stored, synced, shared, exported, and
tombstoned by exactly the machinery that already handles Things — because it *is* a Thing.

Nothing further to design. No new table, no new `CollectionKind`, no new rules block, no eviction policy, no
sync ordering.

> Two earlier revisions of this doc worked hard here — one proposing a reference-counted cache of canonical
> versions, the next a twelfth `CollectionKind` plus a share grant plus a privacy trade-off. Both were solving
> problems created by keeping the template *outside* the Thing. Recording that because the next person to
> propose "surely the template should be its own entity, to avoid duplication" should see what that costs before
> re-deriving it.

---

## 8. Resolution and its failure modes

`TemplateRegistry.resolve(id, version): ThingTemplate?` — nullable, deliberately.

Phase 1's `CollectionKind.fromWire` chose `error()` on an unknown name, and that was right there: an unknown
collection means a corrupt local database, and failing loudly is better than guessing. **The equivalent choice
here would be wrong**, because an unresolvable template is an ordinary, expected state — a Thing from a newer
build, or a template not yet fetched — and it must degrade (§6.2) rather than crash.

Resolution order: local cache → baked-in assets → unresolved. The RPC is never consulted synchronously during
resolution; it populates the cache in the background.

`template_id` empty is treated as `("airplane", 1)`. Phase 1 backfilled every production Thing (#603), so this
path should be unreachable — but "should be unreachable" is not "is unreachable," and the alternative is an
unresolvable Thing for a document written by something the migration missed.

---

## 9. Lexicon scope

PRD §4.5 puts the lexicon at the Thing scope; §8.5 says a mixed account falls back to the generic noun. Making
that concrete:

| Surface | Resolves from |
|---|---|
| Any screen scoped to one Thing | that Thing's template |
| Switcher, settings, export history, account screens | the **generic** lexicon |
| OS notification channel names | the generic lexicon, always — one channel set per device, not per template |

The generic lexicon is a real, authored lexicon (`thing`/`things`, `issue`/`issues`), not the airplane one with
values blanked. On a single-airplane account the generic lexicon is still what settings screens use — so if it
reads wrong there, that is visible in Phase 2 rather than latent until someone adds a boat.

---

## 10. Capabilities remove, they do not disable

§4.8 is explicit: off means the UI is *gone*. The mechanism matters, because retrofitting removal onto
show/hide means auditing every call site twice.

- **Sections** (Dashboard / Defects / Tasks / Logs) come from `capabilities.sections` as an ordered list the
  shell renders. Not a set of booleans each screen checks — one list, one place.
- **Fields and actions** are guarded at the composable that emits them, returning early rather than passing an
  `enabled = false`.
- **Priorities** come from `capabilities.priorities`; the enum keeps every value (it is stored data), and the
  template decides which are *offered*.

---

## 11. Decisions

Settled with the developer, 2026-08-29. Nothing in this design is open.

1. **DNA is not editable.** Write-once at creation. If editing ever ships it is **per-Thing** (§5.2).
2. **Canonical templates are published by an admin script from source-controlled `.textproto` files** (§4.2).
   The same file compiles to the baked-in asset, so served and bundled copies cannot drift.
3. **The fetch callable requires App Check *and* a signed-in user**, throttled ~10/day keyed on `uid` (§4),
   under a **global daily cap** sized at 100,000 calls (§4.1). The uid is for throttling, not authorization —
   an install id would be rotatable and the limit advisory. The global cap needs a sharded counter and
   deliberate headroom; see §4.1 for why the expected-load figure is the wrong number to cap at.
4. **`MeterRule` and `SeasonalRule` are additive. No migration.** (§11.1)

### 11.1 `MeterRule` / `SeasonalRule`: additive

*The question was asked badly the first time; restated here with the proto in front of it, because the
conclusion only makes sense alongside what it rejected.*

PRD §3.3 says **"`EngineHourRule` generalizes to `MeterRule`; a `SeasonalRule` is added"** — one sentence, in a
table of things that "transfer unchanged." But `EngineHourRule` is not a UI concept. It is a stored proto inside
a `oneof`, in every `MaintenanceTask` document users already have:

```proto
// thing/maintenance_task.proto, as it exists in production today
message EngineHourRule { float interval_hours = 1; }

oneof rule {
  TimeRule time_rule = 1;
  EngineHourRule engine_hour_rule = 2;   // <- this
  OnConditionRule on_condition_rule = 3;
  LinkedRule linked_rule = 4;
  ImmediateRule immediate_rule = 5;
}
```

**Decision: extend the `oneof`. Leave field 2 alone.**

```proto
oneof rule {
  TimeRule time_rule = 1;
  EngineHourRule engine_hour_rule = 2;   // kept; no longer written after Phase 3
  OnConditionRule on_condition_rule = 3;
  LinkedRule linked_rule = 4;
  ImmediateRule immediate_rule = 5;
  MeterRule meter_rule = 6;              // new
  SeasonalRule seasonal_rule = 7;        // new
}
```

Existing tasks keep field 2 and keep working. New tasks write field 6. The reader understands both, and field 2
drains naturally as tasks are edited — no sweep, no deadline, and it may never reach zero, which is fine. The
standing cost is one deprecated field and a reader that handles two shapes.

**What this avoids.** Reusing field 2 for `MeterRule` would put a `float` (`interval_hours = 1`) and a `string`
(`meter_key = 1`) at the same field number. Existing tasks would not read as empty — they would decode to
garbage or throw. That is Milestone 1 over again: a global batch, a grace window, dual-deployed triggers, a
coordinated release across three platforms. For a rename.

**And it was never a rename.** `float interval_hours` cannot express "every 3000 miles" — there is no unit in
it. `MeterRule` needs a meter key and an interval, so it is a genuinely different message that happens to
supersede a narrower one. Once that is clear, the in-place option is not "the tidier of two renames"; it is
destroying one message to make room for an unrelated one.

`ForceCompliedStatus.complied_engine_hours` (a `float`, field 2) carries the same question and takes the same
answer: add a meter-shaped companion field, leave the float where it is.

**Consequence for Phase 3.** The meter-driven log form reads whichever field is present, and writes only the new
one. Anything that computes due-status must handle both — that is `TaskDueManager`, whose existing regression
suite (PRD §7) is the thing that proves the old shape still works after the new one lands.

---

## 12. What Phase 2 builds

Phase 2 needs the machinery, with exactly one preset, and **no user-visible change**.

| Build now | Defer |
|---|---|
| `ThingTemplate`, `Lexicon`, `Capabilities` protos (#647, #650) | The six non-airplane presets (Phase 3) |
| `core:template` + `TemplateRegistry`, baked-in resolution (#648) | The fetch RPC and its cache table (§4, §7.1) |
| The airplane template as a baked-in asset (#649) | The picker and create flow (Phase 3) |
| Lexicon plumbing, capability wiring (#652–#660) | The degraded state (§6.2) — nothing can trigger it yet |
| `Thing.template` field 12, reserved now (#647) | Inflating DNA at creation — there is no create flow until Phase 3 |
| Web's shared `versionCode` (#672) | Inflate-on-write (§5.3) — nothing writes DNA until the picker exists |

The RPC, the cache, and the degraded state are designed here and built when a second template exists to justify
them. Building the distribution path for a pool that cannot change is speculative work that will be rewritten
before it is first exercised.

What Phase 2 **must not** defer is the shape of the protos. Field numbers are free now and a migration later
(#638) — so `ThingTemplate`, `Lexicon`, and `Capabilities` get their full field sets in Phase 2 even where no
Phase 2 code reads them.
