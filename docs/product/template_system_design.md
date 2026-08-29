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

**This proto is not stored per-user and gets no `CollectionKind`.** That is a deliberate departure from every
other message in the app. `CollectionKind` exists to route *user-scoped, synced* entities through `EntityStore`
and the sync engine, and every path it builds is `/users/{uid}/...`. Templates are **global** — the same
`airplane` v1 for everyone — so putting them through a per-user store would replicate one identical document
into every account and make each user's copy independently corruptible. §7 covers where they actually live.

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

## 5. Immutability

**A published template is never modified. A change is a new `version`.** This is the invariant the rest of the
design leans on, and it is worth being precise about what it buys: a user who created a Thing last year does not
get their field labels, meters, or capabilities rearranged underneath them by a server-side edit.

Every Thing pins `(template_id, template_version)` — both fields already exist and are already populated.

### 5.1 The consequence that needs deciding: old versions must remain reachable

Immutability plus pinning creates an obligation nobody has to think about until it breaks. If a Thing pins
`(airplane, 1)`, and the app bakes in only `(airplane, 3)`, and the RPC serves only the latest — then **v1 is
unreachable and that Thing cannot render.** The failure surfaces on someone's oldest, most valuable record.

Three ways out, and this doc recommends the third:

| Option | Cost |
|---|---|
| **RPC serves any requested `(id, version)`** | Backend retains every version forever. Cheap to store, but a network dependency to open a Thing — violates local-first (R1). |
| **App bakes in every version ever published** | Bundle grows without bound; a template published after this build still cannot be reached. Does not actually solve it. |
| **Cache every template the device has ever resolved, and never evict one still referenced** | Local-first preserved. Bounded by templates actually used, not templates published. |

**Recommendation: the local cache is authoritative and eviction is reference-counted.** A template is removable
only when no Thing on the device pins it. This keeps the local-first guarantee — a Thing opens offline, forever,
without asking the network what it used to look like — and it means the backend's retention policy is a
convenience rather than a correctness dependency.

> **The rejected alternative, and why it is tempting.** Snapshot the resolved template *onto each Thing*.
> Immutability then needs no mechanism at all, and Phase 1 already set the precedent by putting `spec` and
> `components` on the Thing rather than referencing the template. It was rejected because a template carries the
> lexicon, capabilities, meters, and starter tasks — copying all of it into every Thing inflates documents that
> already sync on every edit, and a corrected typo in a display string could never reach anything. Pinning by
> reference keeps the door open to publishing `(airplane, 4)` and letting users move deliberately.

---

## 6. Compatibility gating

`min_app_version` is a `versionCode` floor, checked against `version.properties`'s single monotonic
`versionCode` (currently `1400`), which is shared across platforms.

**A client refuses any template whose `min_app_version` exceeds its own.** Refused templates are absent from the
picker entirely, not shown-and-disabled — the same principle §4.8 applies to capabilities.

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

### 6.2 The gap in "don't show it": an unsupported template on an *existing* Thing

PRD §5 and the requirement as stated cover the **picker** — don't offer what you can't run. They do not cover the
case that actually reaches users:

> A Thing is created on device A running a newer build, with `(boat, 2)` requiring `versionCode ≥ 1500`. It syncs
> to device B running `1400`. **Device B cannot hide the user's Thing** — it is their data, it appears in their
> switcher, and it counts against their limit.

Hiding it is wrong: silent data disappearance is the failure mode this codebase has repeatedly refused
elsewhere. Rendering it with a fallback template is worse: it would show a boat's data under airplane labels.

**A Thing whose template cannot be resolved renders in a defined degraded state**: its name, its raw spec values
as unlabelled key/value pairs, and a prompt to update the app. It is never hidden, never silently relabelled, and
never editable — editing under a template you cannot interpret is how a client writes data that violates the
template's own rules.

This is the same shape as `BlobDownloadDriver`'s "a 404 is an answer, not an outage": an unresolvable template is
a *known* state to be represented, not an error to be swallowed.

### 6.3 The update prompt is platform-specific

"Prompt the user to update" is not one behaviour:

- **Android / iOS** — a real action: deep-link to the store listing.
- **Web** — there is no install step. The deployed bundle updates on reload, so the prompt is "reload to get the
  latest version," and a stale web client is a cache-lifetime question rather than an install one.

Worth noting that `versionCode` is bumped by `assembleRelease`, which is an *Android* task — so the web build's
relationship to that number needs settling before the floor can be trusted cross-platform (§11).

---

## 7. Where the pool lives on-device

Not in `EntityStore`. Templates are global, not user-scoped, and every path `EntityScope` builds begins
`/users/{uid}/`.

A dedicated SQLDelight table, keyed `(id, version)`, holding the encoded `ThingTemplate` bytes, outside the
`entity` table and outside the sync engine's push/pull loop. It needs none of what that machinery provides —
no dirty tracking, no watermarks, no tombstones, no per-user scoping — and inheriting them would mean a global
document pushed once per account.

This also keeps the template pool out of `CollectionKind`, which matters for a reason beyond tidiness:
adding a `CollectionKind` means choosing a `wireName` and a `schemaName`, and those are **stored identity** that
cannot be changed later without a migration (#638). Templates need no such commitment.

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

1. **Does `versionCode` mean anything on web?** It is bumped by `assembleRelease`, an Android task. If web
   deploys independently — it did on 2026-08-29 — then a single floor cannot gate all three platforms honestly.
   Either the number becomes genuinely shared, or `min_app_version` needs a per-platform form.
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
