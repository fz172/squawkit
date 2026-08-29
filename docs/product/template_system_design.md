# Design: The Template System — Definition, Distribution, and Resolution

> **Implementation status.** **Proposed — nothing has shipped.** This designs the machinery Phase 2 needs
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

**One message, two lifecycles.** The same `ThingTemplate` is used for the global canonical pool and for the
per-user copy a Thing actually resolves against (§5). Only the second is a synced entity with a `CollectionKind`;
the first is a local cache. §7 covers both, and the distinction decides more than storage layout — it decides
what is immutable, what syncs, and what a share member can read.

Owned copies add two fields recording their provenance:

```proto
  string source_id = 13;       // the canonical id this was copied from
  int32 source_version = 14;   // and its version — see §5.3
```

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

Two properties worth being explicit about:

- **The server filters by `app_version`.** A client never receives a template it cannot run, so the client-side
  check in §6 is a second line of defence rather than the only one. Filtering server-side also means the
  *response* stays small on old clients instead of shipping payloads they will discard.
- **The fetch is never on the critical path.** The baked-in pool is complete and self-sufficient; the RPC only
  ever *adds*. A failed fetch, an offline device, or an unauthenticated call degrades to "the presets this build
  shipped with," which is exactly the app's behaviour today. Nothing blocks on it.

---

## 5. Two pools: canonical and owned

The system has **two kinds of template**, and almost every question below resolves differently for each.

| | **Canonical** | **Owned** |
|---|---|---|
| Scope | global, one copy for everyone | one user's tree |
| Mutability | **immutable** — a change is a new `version` | the user's to edit |
| Source | baked-in assets + fetch RPC (§4) | copied from canonical on first use, or authored from scratch |
| Purpose | **the picker, and nothing else** | rendering the user's Things |
| Synced? | no — a local cache (§7.1) | **yes** — an ordinary synced entity (§7.2) |

The rule that connects them: **the moment a template is used to create a Thing, the user gets their own copy.**
The same applies to customising a canonical template and to authoring one from scratch — both produce an owned
template, and owned templates are what Things actually resolve against.

### 5.1 What this buys, and what it retires

An earlier revision of this doc spent a section on a problem this design does not have. If Things pinned
*canonical* `(id, version)` pairs, then immutability plus pinning would oblige the canonical pool to keep every
version reachable forever — otherwise a Thing pinned to `(airplane, 1)` could not render on a build that ships
only v3. That section recommended a reference-counted cache to solve it.

**Copy-on-use dissolves it.** The template a Thing needs travels with the user's own data, in the user's own
tree, synced by the machinery that already syncs everything else. The canonical pool never has to answer "what
did v1 look like," because nothing asks it. Canonical retention becomes a product convenience — how far back the
picker offers — rather than a correctness requirement.

It also makes the immutability guarantee stronger than versioning alone could. A user is insulated from canonical
changes not because the app is careful to pin a version, but because **their template is not the canonical one**.
There is no path by which a server-side edit reaches an existing Thing, including no path through a bug.

### 5.2 The question that decides everything else: does one owned template serve many Things?

A user with three airplanes creates them from the same canonical template. Do they end up with **one** owned
template or **three**?

This is a product question, not an implementation detail, and it decides the storage layout in §7:

- **One shared copy** — customising "my airplane template" changes all three airplanes. Matches the intuition
  that it is *a template*, a thing you keep and adjust.
- **A copy per Thing** — the three drift independently; editing one leaves the others alone. Matches the
  intuition that the template is *baked into* the Thing at creation, which is the phrasing the requirement uses.

**This doc assumes one shared copy per `(id, version)` per user**, because "mutate it and it becomes theirs"
reads as owning a template rather than owning N snapshots, and because the alternative multiplies storage by the
number of Things for no user-visible gain. **It needs confirming** — reversing it later is a data migration
(§11 #1).

### 5.3 Versioning within owned templates

Canonical `version` is a publication counter, immutable by definition. An owned template's version is seeded
from the canonical one it was copied from, and then means something different: it is *provenance*, not identity.

That has a consequence worth stating plainly: after a user customises, `(airplane, 3)` in **their** tree and
`(airplane, 3)` in the canonical pool are no longer the same object. That is correct and intended — the owned
copy is authoritative for their Things — but it means `(id, version)` is not globally meaningful, and any code
that compares them across the boundary is wrong. Owned templates therefore carry `source_id` and
`source_version` recording what they were copied from, distinct from their own identity.

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

"Don't show it" is the right answer for the **picker**. It is not available for a Thing that already exists, and
copy-on-use makes that case *common* rather than a version-skew curiosity:

> **The ordinary case.** A Thing and its owned template are two documents. They sync independently. A device that
> has pulled the Thing but not yet its template has an unresolvable Thing — for seconds, or for as long as the
> pull takes on a cold start.

> **The version-skew case.** A Thing is created on a device running a newer build, with a template requiring
> `versionCode ≥ 1500`. It syncs to a device on `1400`.

Neither may hide the Thing. It is the user's data, it appears in their switcher, it counts against their limit,
and silent disappearance is the failure mode this codebase refuses elsewhere. Rendering it under a *fallback*
template is worse — that shows a boat's data with airplane labels.

**A Thing whose template cannot be resolved renders in a defined degraded state**: its name, its raw spec values
as unlabelled key/value pairs, and — for the version-skew case only — a prompt to update. Never hidden, never
relabelled, and **never editable**, because editing under a template you cannot interpret is how a client writes
data violating rules it cannot see.

Because the sync race is transient and the version skew is not, the two need different copy: "loading" versus
"this needs a newer app." The distinguishing signal is whether the template is *absent* or *present but too new*.

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

### 7.2 Owned: a synced entity, and therefore a `CollectionKind`

Owned templates are ordinary user data — created by the user, mutated by the user, needed on every device.
So they sync like everything else, and they **do** take a `CollectionKind`, bringing the current 11 to 12
exactly as PRD §3.3 anticipated.

> An earlier revision of this doc argued templates should get no `CollectionKind` at all. That was correct for
> the canonical pool and wrong for owned templates — it followed from assuming Things resolve against canonical
> templates, which copy-on-use replaced.

Its `wireName` and `schemaName` are a permanent commitment. Per the convention now in `CollectionKind`'s KDoc,
they take **Thing vocabulary** — `thing_template` / `thing.ThingTemplate`.

### 7.3 Where the owned template sits — and why sharing decides it

This is the placement question, and the sharing model constrains it more than storage cost does.

`firestore.rules` grants a share member access to exactly
`/users/{host}/thing/{acId}/{kind}/{docId}` where `isSharedAircraftKind(kind)` — currently
`maintenance_log`, `maintenance_task`, `maintenance_overview`, `squawk`. **User-root collections are invisible to
members**; that is why the technician mirror is *copied into* `ShareMemberDoc.technicianMirror` rather than
granted from the host's `technician` records.

So:

| Placement | Sharing | Cost |
|---|---|---|
| **User-root** `/users/{uid}/thing_template/{id}` | ✗ members cannot read it — **every shared Thing renders degraded** unless a new grant is added | one copy per user; supports §5.2's shared-copy model directly |
| **Per-Thing** `/users/{uid}/thing/{acId}/thing_template/{id}` | ✓ works by adding one entry to `isSharedAircraftKind` | one copy per Thing; contradicts §5.2 |
| **Embedded in the Thing document** | ✓ automatic — members already `get` the Thing doc | inflates a document that re-syncs on every edit; contradicts §5.2 |

**Recommendation: user-root, plus an explicit share grant.** It is the only option consistent with §5.2's shared
copy, and the grant is bounded work — a `match` block for `thing_template` keyed by the *host*, readable by
anyone who is a member of any of that host's shares.

The privacy cost is real and should be stated rather than glossed: a member of one shared aircraft could read
the host's whole template collection, which leaks *which kinds of thing the host owns* — not their data, but a
signal. If that is unacceptable, the technician-mirror precedent applies: copy the resolved template into the
share, and accept the duplication.

**This is the decision most worth reviewing**, because it is simultaneously a sharing-rules change, a privacy
tradeoff, and the thing §5.2 depends on.

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

## 11. Open questions

1. ~~**Does `versionCode` mean anything on web?**~~ **Settled: the number becomes genuinely shared.** Web
   currently renders `1.0.260826.1` from `core/appinfo/build.gradle.kts`, which composes
   `major.minor.buildDate.patch` and never reads `versionCode` at all. It moves to the Android/iOS form —
   `1.0.260826(1399)` — so one monotonic `versionCode` gates all three platforms and `min_app_version` means the
   same thing everywhere. Tracked as a Phase 2 task; note `assembleRelease` is what *increments* the number, so
   a web-only deploy ships whatever the last release build stamped.
2. **Who publishes a template, and through what?** No admin surface exists. A script following
   `grant-entitlement.mjs` is the cheap answer, and immutability makes a mistake unfixable by editing —
   a bad publish is superseded, never corrected.
3. **Do `MeterRule` / `SeasonalRule` (PRD §3.3) change `MaintenanceTask`?** That is stored data. If the change
   is additive it is free; if it renumbers or repurposes a field it is a #638-class migration. **Settle before
   Phase 3, not during.**
4. **Is the fetch authenticated?** Templates are not secret, but an unauthenticated callable is an open endpoint.
   App Check, as the existing callables use, is probably sufficient.

---

## 12. What Phase 2 builds

Phase 2 needs the machinery, with exactly one preset, and **no user-visible change**.

| Build now | Defer |
|---|---|
| `ThingTemplate`, `Lexicon`, `Capabilities` protos (#647, #650) | The six non-airplane presets (Phase 3) |
| `core:template` + `TemplateRegistry`, baked-in resolution (#648) | The fetch RPC and its cache table (§4, §7) |
| The airplane template as a baked-in asset (#649) | The picker and create flow (Phase 3) |
| Lexicon plumbing, capability wiring (#652–#660) | The degraded state (§6.2) — nothing can trigger it yet |

The RPC, the cache, and the degraded state are designed here and built when a second template exists to justify
them. Building the distribution path for a pool that cannot change is speculative work that will be rewritten
before it is first exercised.

What Phase 2 **must not** defer is the shape of the protos. Field numbers are free now and a migration later
(#638) — so `ThingTemplate`, `Lexicon`, and `Capabilities` get their full field sets in Phase 2 even where no
Phase 2 code reads them.
