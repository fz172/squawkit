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

Two properties worth being explicit about:

- **The server filters by `app_version`.** A client never receives a template it cannot run, so the client-side
  check in §6 is a second line of defence rather than the only one. Filtering server-side also means the
  *response* stays small on old clients instead of shipping payloads they will discard.
- **The fetch is never on the critical path.** The baked-in pool is complete and self-sufficient; the RPC only
  ever *adds*. A failed fetch, an offline device, or an unauthenticated call degrades to "the presets this build
  shipped with," which is exactly the app's behaviour today. Nothing blocks on it.

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

**Per-Thing customisation is per-Thing.** A user with three airplanes has three sets of DNA. Editing one does
not touch the others, and there is no "my airplane template" to adjust once. That is the direct consequence of
DNA, and it may be the right product answer — three aircraft genuinely differ — but it is a real UX position
rather than a free one. If "edit my airplane template everywhere" is later wanted, it is a fan-out write across
Things, not a single edit.

**Every Thing edit re-uploads its DNA.** The payload is one blob; changing a tail number re-pushes the template
with it. A full template — lexicon, capabilities, spec fields, component slots, meters, starter tasks — is on
the order of a few KB, well inside Firestore's 1 MiB document limit, and Thing edits are rare compared with log
and squawk writes. Acceptable, but worth measuring rather than assuming once real templates exist.

### 5.3 The consequence that needs a decision: existing Things have no DNA

**Every Thing in production today has `template_id = "airplane"` and no `template` field.** Phase 1 backfilled
the former (#603); the latter did not exist. So this is a **stored-data change**, and adding a field is only the
easy half.

| Approach | Trade |
|---|---|
| **Server-side backfill**, a `thing-cutover`-shaped script that inflates the airplane template into all existing Things | Faithful to the design — every Thing is genuinely self-contained afterwards. Costs a migration run, and Milestone 1 is the template for how (#586). |
| **Lazy inflation on the client** — on read, if `template` is absent, inflate from the baked-in canonical named by `template_id`, non-dirtying | No migration. But the guarantee is aspirational until the Thing is next written, and it **breaks for shared Things**: a member reading a host's un-inflated Thing must fall back to their *own* baked-in pool, which is precisely the coupling the DNA model exists to remove. |

**Recommendation: server-side backfill.** The lazy path reintroduces, for an unbounded window, the dependency
this design removed — and it fails exactly where the design is most valuable, on shared Things. The population
is small and known (21 Things across 25 accounts), and Milestone 1 already proved the machinery.

Whether this lands in Phase 2 or with the picker in Phase 3 is a sequencing question (§11). It cannot land
*before* the airplane template exists as an asset (#649).

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

## 11. Open questions

1. ~~**Does `versionCode` mean anything on web?**~~ **Settled: the number becomes genuinely shared.** Web
   currently renders `1.0.260826.1` from `core/appinfo/build.gradle.kts`, which composes
   `major.minor.buildDate.patch` and never reads `versionCode` at all. It moves to the Android/iOS form —
   `1.0.260826(1399)` — so one monotonic `versionCode` gates all three platforms and `min_app_version` means the
   same thing everywhere. Tracked as a Phase 2 task; note `assembleRelease` is what *increments* the number, so
   a web-only deploy ships whatever the last release build stamped.
2. **When does the backfill run (§5.3)?** Existing Things have no DNA. Phase 2 could inflate them as soon as
   the airplane template exists (#649), or it could wait for Phase 3's picker. Earlier is safer — it closes the
   window in which a shared Thing has no DNA for its member to read — but it means a production migration run
   inside a milestone whose whole premise is that nothing user-visible changes.
3. **Should DNA be editable per Thing, and is that the product intent (§5.2)?** Three airplanes means three sets
   of DNA and no single "my airplane template" to adjust. That follows necessarily from the model; flagging it
   because it is a UX position, not a technical detail.
4. **Who publishes a canonical template, and through what?** No admin surface exists. A script following
   `grant-entitlement.mjs` is the cheap answer, and immutability makes a mistake unfixable by editing —
   a bad publish is superseded, never corrected.
5. **Do `MeterRule` / `SeasonalRule` (PRD §3.3) change `MaintenanceTask`?** That is stored data. If the change
   is additive it is free; if it renumbers or repurposes a field it is a #638-class migration. **Settle before
   Phase 3, not during.**
6. **Is the fetch authenticated?** Templates are not secret, but an unauthenticated callable is an open endpoint.
   App Check, as the existing callables use, is probably sufficient.

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
| Web's shared `versionCode` (#672) | The backfill (§5.3), if it waits for the picker |

The RPC, the cache, and the degraded state are designed here and built when a second template exists to justify
them. Building the distribution path for a pool that cannot change is speculative work that will be rewritten
before it is first exercised.

What Phase 2 **must not** defer is the shape of the protos. Field numbers are free now and a migration later
(#638) — so `ThingTemplate`, `Lexicon`, and `Capabilities` get their full field sets in Phase 2 even where no
Phase 2 code reads them.
