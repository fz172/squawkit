# Design: Aircraft → Thing Proto & Data Migration (Phase 1)

> **Implementation status.** **Proposed — nothing has shipped.** This designs exactly one phase of
> [`multi_domain_maintenance_PRD.md`](multi_domain_maintenance_PRD.md) — **Phase 1** (§15): the non-UI proto,
> Firestore, and Cloud Storage migration that must close out on every account before any template-system UI
> ships. No other phase is in scope here.

**Owner:** Engineering · **Status:** Proposed · **Date:** 2026-08-27
**Related:** [Multi-domain maintenance PRD](multi_domain_maintenance_PRD.md) (§4, §6, §9, §15 — the product-level
description this doc operationalizes) · [Storage R1 design](../storage/storage_r1_design.md) ·
[Sharing design](../sharing/aircraft_sharing_design.html)

---

## 1. Scope

**In scope:** renaming the `Aircraft` proto to `Thing` (adding the fields the PRD's §6 shape needs, without
building the template system that would populate them), moving every Firestore path, Cloud Storage path, and
local-database path that encodes the literal segment `aircraft`, the `aircraft_shares` ACL tree and the
`firestore.rules` blocks that govern both it and the entity tree, and the one-time migration — local and backend
— that gets existing data there safely.

**Out of scope**, deliberately, because none of it is needed to close Phase 1 and each adds risk for no
correctness gain right now:

- The template system itself — `core:template`, `TemplateRegistry`, presets, capabilities, lexicon. Phase 1 ships
  **exactly one preset, airplane**, reproducing today's app byte-for-byte (PRD §15).
- UI-facing and API-facing renames that still read correctly under their old names: `ShellAircraft`,
  `AircraftScopeResolver`, `EntityScope.aircraftChildUnsafe`, the `feature/aircraft/*` module names, the
  `add_aircraft` string, the notification deep-link's `aircraft` path segment
  (`NotificationTapRouter.kt:86`, `PushPayload.kt:107`), and the sharing helper *function/type names*
  (`aircraftShareDocPath`, `AircraftShareDoc`, `SharingManagerImpl`'s `SHARES`/`SHARE_AIRCRAFT` constant names).
  None of these are *stored* data — they're names in code and in already-rendered UI — so leaving them as-is
  costs nothing and keeps this phase's diff centered on data correctness. §3.3 has the exact line.

---

## 2. Current state, exactly as it exists in the codebase today

### 2.1 The proto

`core/model/src/commonMain/proto/aircraft/aircraft.proto`:

```proto
message Aircraft {
  string id = 1;
  string make = 2;
  string model = 3;
  string serial = 4;
  string tail_number = 5;
  repeated Engine engine = 6;
}
```

`Engine` (`engine.proto`) carries `make/model/serial` plus one `Propeller` (`propeller.proto`, itself
`PropellerHub` + `repeated PropellerBlade`). `ComponentType` (`component_type.proto`) is the 3-value enum
(`COMPONENT_AIRFRAME` / `COMPONENT_ENGINE` / `COMPONENT_PROPELLER`) that `MaintenanceLog`, `MaintenanceTask`, and
`Squawk` switch on. All of this matches the PRD's description exactly — nothing here has drifted.

### 2.2 `CollectionKind` — one wireName changes, ten don't

`core/storage/.../CollectionKind.kt` is a sealed interface of 11 kinds, each with an independent `wireName` and
`schemaName`. Only `CollectionKind.Aircraft` (`wireName = "aircraft"`, `schemaName = "aircraft.Aircraft"`) is
touched by this migration. `MaintenanceTask`, `MaintenanceLog`, `MaintenanceOverview`, `Squawk`, `Technician`,
and the rest keep their own wireNames (`"maintenance_task"`, `"maintenance_log"`, …) completely unchanged — they
are not renamed to anything "thing"-shaped, because they were never named after the aircraft in the first place.
**The only string this migration changes at the `CollectionKind` level is `"aircraft"` → `"thing"`, once.**

`CollectionKindCoverageTest` asserts `CollectionKind.ALL` matches `sealedSubclasses` and that every `wireName` is
unique — it references the `Aircraft` *symbol*, not the string `"aircraft"`, so it keeps passing through this
rename with no edits needed. One piece of built-in regression protection, free.

### 2.3 How a path is actually built — `EntityScope` + `FirestoreRefs`

`EntityScope` (`core/storage/.../EntityScope.kt`) is a list of `(collection, doc)` segments:

```kotlin
fun userRoot(uid: String): EntityScope = EntityScope(listOf("users", uid))
fun aircraftChildUnsafe(uid: String, aircraftId: String): EntityScope =
  EntityScope(listOf("users", uid, "aircraft", aircraftId))
```

`FirestoreRefs.collection()` appends a `CollectionKind.wireName` as the leaf collection. Two consequences that
matter for this migration and are easy to get wrong:

1. **`"aircraft"` appears in two independent places that happen to collide today.** `CollectionKind.Aircraft
   .wireName` names the collection an *Aircraft/Thing document itself* lives in
   (`/users/{uid}/aircraft/{id}`). `EntityScope.aircraftChildUnsafe`'s hardcoded `"aircraft"` literal names the
   scope segment *that document's children* (logs, tasks, squawks, overview) sync under
   (`/users/{uid}/aircraft/{id}/maintenance_log/{logId}`). These are two different constants in two different
   files that both happen to read `"aircraft"` right now. **Renaming `CollectionKind.Aircraft.wireName` alone
   does not touch the second one.** Both must change, or half the tree moves and half doesn't.
2. **Nested collections don't rename.** `MaintenanceLog`'s wireName is `"maintenance_log"` regardless of what its
   parent scope segment is called — only the segment *between* `{uid}` and `{maintenance_log}` moves.

### 2.4 Cloud Storage — the same segment, a third place

`storage.rules`: *"an attachment on aircraft `acId` owned by `uid` lands at
`users/{uid}/aircraft/{acId}/blobs/...`"*. Confirmed in `LocalFirstAttachmentManagerImpl.kt:96`:

```kotlin
storage_path = "${scope.toPath().trim('/')}/blobs/$id"
```

— i.e. the *same* `EntityScope` (built by the *same* `aircraftChildUnsafe`) is reused verbatim for attachment
blobs. Fix `EntityScope` and the Storage path moves with it automatically. The Storage security rule itself needs
**no change**: its ownership check is `request.auth.uid == userId` on the path's first segment, blind to
everything after — confirmed by reading `storage.rules` directly.

### 2.4a `firestore.rules` — a fourth place, and this one *does* need a change

`storage.rules` needing no change is not evidence that `firestore.rules` doesn't either — they're separate files
guarding separate services, and `firestore.rules` hardcodes the segment directly in its match pattern:

```
match /users/{userId} {
  match /aircraft/{acId} {
    allow get: if request.auth.uid == userId || isShareMember(userId, acId);
    allow write: if request.auth.uid == userId
      || (isShareOwner(userId, acId) && writerIsSelf() && request.resource.data.deleted != true);
    match /{kind}/{docId} {
      allow read: if request.auth.uid == userId || isShareMember(userId, acId);
      allow write: if request.auth.uid == userId
        || (isSharedAircraftKind(kind) && isShareMember(userId, acId) && writerIsSelf());
    }
  }
}
```

This is the rule that authorizes every read and write to the entity tree §2.2–§2.3 describe — without a
`match /thing/{acId} { ... }` block added alongside it, a Phase 1 client pointed at the new path gets denied on
every single request. `isSharedAircraftKind(kind)` checks the *nested* collection names
(`maintenance_log`/`maintenance_task`/`maintenance_overview`/`squawk`), which are unaffected (§2.2) and need no
edit.

Unlike a Kotlin/TS source file, `firestore.rules` is **one file, deployed as a single atomic unit** — there is no
way to deploy a rules file that behaves differently per account. The fix is not to *replace* the `/aircraft/`
block with a `/thing/` one; it's to **add the new block alongside the old one**, so both paths are simultaneously
authorized for the entire span of the rollout, and only remove the old block once every account has migrated
(§5.4). This is a strictly easier version of the Cloud Function trigger problem in §2.7 — one file with two match
blocks, not two separate function deployments — but it is the same *shape* of problem, and missing it produces
the same failure: an account that looks migrated gets `PERMISSION_DENIED` on everything.

### 2.5 Every other hardcoded `"aircraft"` path literal — the real inventory

`EntityScope.aircraftChildUnsafe`'s string is not the only hardcoded occurrence. Grepping the client and backend
for the literal turned up ten more call sites that independently assume the segment is `"aircraft"`, most of them
building a SQL `LIKE` prefix or parsing a path string rather than calling `FirestoreRefs`/`EntityScope`:

| File | What it does with the literal |
|---|---|
| `feature/sync/data/.../SyncEngine.kt:594` | `schedulePendingBlobs` builds `"/users/$hostUid/aircraft/$aircraftId/%"` to scan pending blob uploads for shared aircraft. |
| `feature/sync/data/.../PushWorker.kt:214,236,256` | Parses `EntityScope.toPath()` back into segments and checks `parts[2] == "aircraft"` to route a dirty row's push to the right tree; also builds the same `"/users/$hostUid/aircraft/$aircraftId/%"` prefix. |
| `feature/sync/data/.../blob/AttachmentBroker.kt:48,59` | Parses a blob's scope-path segments and checks `segs[2] != "aircraft"` to decide own-tree vs. broker (shared) upload routing. |
| `feature/sync/data/.../SharedScopeJanitor.kt:83,88,147,158` | Four more `"/users/$hostUid/aircraft/$aircraftId/%"` prefix constructions, used to purge local data when a share ends. |
| `feature/notifications/engine/.../WebForeignWriteDetector.kt:283` | JS-only: checks `it[2] == "aircraft"` against a Firestore snapshot's path segments to detect a foreign (shared-scope) write. |
| `backend/.../storage/storageSweep.ts:207,261` | Server-side Storage sweep: `getFiles({ prefix: users/${uid}/aircraft/${acId}/blobs/ })` and `adminDb.doc(users/${uid}/aircraft/${acId})`. |
| `backend/.../account/deleteMyAccount.ts:100,120,147` | Account deletion cascade: reads `shared_aircraft_ref`, deletes attachment bytes under the `aircraft`-scoped prefix. |
| `backend/.../notifications/onRecordWritten.ts:47,108` | **Cloud Function trigger document paths** — see §5.2, this one is not a simple string swap. |
| `backend/.../storage/onRecordDeleted.ts:37` | Same trigger-path issue. |
| `backend/.../sharing/onAircraftDeleted.ts:24` | Same trigger-path issue. |

(The ACL tree's own client-side literal, `SharingManagerImpl.kt`'s `SHARE_AIRCRAFT` constant, is a structurally
different case — covered in full in §2.9, not folded in here.)

None of these are exotic — they're all doing the same thing `EntityScope` does (build or parse
`users/{uid}/aircraft/{id}/...`), just without going through it. That's the actual migration risk: not that the
rename is hard, but that it's easy to fix the two "obvious" places (`CollectionKind`, `EntityScope`) and ship with
silent path mismatches in the sync engine's edge cases (shared aircraft, account deletion, foreign-write
detection) that only show up for a technician on a shared plane or when someone deletes their account — exactly
the paths least likely to be hit in manual dogfood testing.

### 2.6 The subtle one: `Attachment.storage_path` is denormalized *inside every payload*

`Attachment` (`attachment.proto`) is embedded as `repeated Attachment attachments` inside `MaintenanceLog`
(field 9), `MaintenanceTask` (field 13), and `Squawk` (field 8). Its `storage_path` field
(confirmed in `LocalFirstAttachmentManagerImpl.kt:96`) is not a live-computed value — it is the literal
`scope.toPath()` string, **written once at attachment-creation time and stored as proto bytes inside the parent
entity's `payload` column.**

This means moving the physical blob (§2.4) and moving the parent document's Firestore path (§2.2) is **not
sufficient**. Every `MaintenanceLog`, `MaintenanceTask`, and `Squawk` payload that has ever had an attachment
added carries its own frozen copy of the old `.../aircraft/.../blobs/...` path string, baked into proto bytes,
independent of the path the document itself lives at. Missing this produces the worst kind of migration bug: the
document moves cleanly, the blob moves cleanly, and the app still 404s trying to open an attachment — because it
followed the stale string still sitting inside the payload it just correctly fetched from the new location.

This has to be fixed by **decoding the payload, rewriting `storage_path` on every embedded `Attachment`, and
re-encoding** — a proto-level edit, not a byte- or string-level one (proto3 length-prefixes strings, so splicing
`"aircraft"` → `"thing"` inside raw bytes without going through the generated message API would corrupt the
encoding the moment the two strings aren't the same length, which they aren't). §4.2 covers exactly this step.

### 2.7 Cloud Function triggers can't be migrated per-account — a sequencing constraint, not a string swap

Four Cloud Functions register a **literal, deploy-time** Firestore trigger path:

```
onNotifiableRecordWritten   users/{uid}/aircraft/{acId}/{kind}/{docId}   (onRecordWritten.ts:47)
onNotifiableAircraftWritten users/{uid}/aircraft/{acId}                   (onRecordWritten.ts:108)
onAircraftDeleted           users/{uid}/aircraft/{acId}                   (onAircraftDeleted.ts:24)
onRecordDeleted             users/{uid}/aircraft/{acId}/{kind}/{docId}   (onRecordDeleted.ts:37)
```

Cloud Functions v2 Firestore triggers don't support an "either segment" wildcard, and a **function deploy is
global** — it is not possible to have one deployed function watching `.../aircraft/...` for not-yet-migrated
accounts while another watches `.../thing/...` for already-migrated ones *by deploying two versions of the same
function*. The only way to cover both is to **deploy a second, temporary set of functions on the new path
alongside the existing ones**, so that for the entire span between the first account's cutover and the last,
notifications, the blob-cleanup cascade, and the sharing-deletion cascade all keep firing correctly regardless of
which path a given account's data is currently on. The old, `aircraft`-triggered functions are only deleted once
every dogfood account has cut over — this is the backend's own version of the PRD's "transitional, not
permanent" dual-write, forced by how Cloud Functions triggers work rather than by client version skew.

At under 50 accounts migrated in what is realistically one operator sitting, this window is short — but it is
not zero, and skipping it means a not-yet-migrated account silently stops getting notifications, deletion
cascades, or blob cleanup for the duration.

### 2.8 Local database schema — what actually needs rewriting on-device

From `core/storage/.../db/Schema.sq`, four tables carry a `scope_path` (or, for blobs, both `scope_path` and
`remote_path`) that encodes the literal segment, plus one column that encodes the collection name itself:

| Table | Column(s) | Why it matters |
|---|---|---|
| `entity` | `collection` | For rows where `collection = 'aircraft'` (the Thing's own row): once `CollectionKind.Aircraft.wireName` becomes `"thing"`, `CollectionKind.fromWire("aircraft")` throws (`byWire` no longer has that key) — **existing local rows become unreadable the instant the new build launches**, unless this column is rewritten first. |
| `entity` | `scope_path` | For rows nested under an aircraft (logs/tasks/squawks/overview): contains `.../aircraft/{id}/...`. |
| `entity` | `payload` (BLOB) | Contains the proto-encoded `Attachment.storage_path` strings described in §2.6, for any `MaintenanceLog`/`MaintenanceTask`/`Squawk` row that has attachments. |
| `entity` | `payload_schema` | Forensic-only (never read for branching logic — confirmed by grep), but stale `"aircraft.Aircraft"` after the rename is worth fixing in the same pass since it's free. |
| `blob_object` | `scope_path`, `remote_path` | Mirrors the same literal for the local blob index. |
| `sync_cursor` | `scope_path` | Keyed by `(uid, collection, scope_path)` — an unrewritten cursor silently starts a redundant re-hydration of the *old*-looking scope rather than resuming the *new* one, wasting a full history re-pull per aircraft. |
| `urgency_watermark` | `scope_path` | If left unrewritten (or naively dropped instead of rewritten), the notification engine treats every existing overdue item as *never scanned* and can re-report crossings a user already saw — see the existing code comment on `reassignWatermarks` in `Schema.sq` for why this exact mistake is called out there for the *account-merge* case. |

**There is already a working precedent for exactly this shape of migration in this codebase**:
`LocalAccountMigrator` (`core/storage/.../LocalAccountMigrator.kt`) re-keys a guest's local data from one uid to
another on account upgrade, using `reassignEntities` / `reassignBlobs` / `reassignWatermarks` plus
conflict-clearing queries (`deleteReassignConflicts`, `deleteWatermarkReassignConflicts`), all inside one
`DatabaseWriteLock`-guarded transaction, and is explicitly documented as **idempotent**: "once moved, no rows
match `[fromUid]` anymore, so a re-run is a no-op." §4.1 designs this migration as a sibling of that class,
reusing the same idioms rather than inventing new ones.

### 2.9 The share ACL tree — a fifth place, and the one with a real sequencing constraint

`aircraft_shares` is a structurally separate Firestore collection from everything above — plain-field documents
(not `SyncDocWire` entities) holding invite/role data for aircraft sharing, read directly by security rules
(`aircraft_shares/{hostUid}/aircraft/{acId}`, with `members/{uid}` and `invites/{tokenHash}` beneath it). It
reuses the literal `"aircraft"` as its subcollection name, independently of everything in §2.2–§2.8, and needs to
move too — but it can't move the same way, for a reason worth being precise about.

**Where the string lives.** `backend/.../sharing/sharingModels.ts` is, by its own doc comment, "the single
source of truth for collection names, document paths" for this tree:

```ts
export const AIRCRAFT_SHARES_COLLECTION = "aircraft_shares";
export const SHARE_AIRCRAFT_SUBCOLLECTION = "aircraft";
export function aircraftShareDocPath(hostUid, aircraftId) { … }   // ${AIRCRAFT_SHARES_COLLECTION}/${hostUid}/${SHARE_AIRCRAFT_SUBCOLLECTION}/${aircraftId}
export function shareMemberDocPath(hostUid, aircraftId, uid) { … }
```

All eleven backend consumers (`createAircraftShareInvite.ts`, `redeemAircraftShareInvite.ts`,
`revokeAircraftShare.ts`, `updateAircraftShareRole.ts`, `onAircraftDeleted.ts`, `blobBroker.ts`,
`projectAttachmentEntitlement.ts`, `audience.ts`, `deleteMyAccount.ts`, plus `sharingModels.ts` itself) import
these constants/helpers rather than hardcoding the literal — confirmed by grep, none of them holds an independent
copy. Renaming the two constants' *values* is therefore a one-file change that cascades correctly everywhere on
the backend. The one place with an independent copy is the client:
`SharingManagerImpl.kt:556-557` (`SHARES = "aircraft_shares"`, used at line 76 to build
`firestore.collection(SHARES).document(hostUid).collection(SHARE_AIRCRAFT).document(acId)`), needed because the
client makes its own direct `get()` reads against this tree (member roster, invite list) under the rules in
§2.4a.

**Why it can't move per-account like the entity tree does.** The entity tree's per-account safety (§5.1) rests
on one property: *a given account's devices only ever read the path its own already-migrated backend put data
at*, because the client build that knows about `/thing/` ships to that account only after its backend has cut
over. The ACL tree breaks that property, in two independent ways:

1. **The six sharing functions are `onCall` HTTPS callables, not per-document triggers** (confirmed — none of
   `createAircraftShareInvite`/`redeemAircraftShareInvite`/`revokeAircraftShare`/`updateAircraftShareRole`/
   `previewAircraftShareInvite`/`cancelAircraftShareInvite` register an `onDocumentWritten`/`onDocumentCreated`
   trigger). A callable's code is **one globally-deployed version answering every account's calls**. Flipping
   `sharingModels.ts`'s constants is not "redeploy and it takes effect for accounts that have migrated" — it
   takes effect for *every* account's next call, migrated or not, the instant it's deployed.
2. **`firestore.rules`' `shareRole()` function is shared** by the entity tree's access checks (`isShareMember`/
   `isShareOwner` inside the `/thing/{acId}` block, §2.4a) and by the ACL tree's own match block. It has to point
   at **one** location — wherever the ACL data actually, currently lives — because rules, like the callables, are
   deployed once, globally: `get(/databases/$(database)/documents/aircraft_shares/$(hostUid)/aircraft/$(acId))`
   either reads the real ACL or it doesn't; there is no "for this account, look elsewhere" available to it.

Point 2 forces the conclusion: **as long as any account's ACL data still lives at `aircraft_shares/...`,
`shareRole()` must keep pointing there — which means the ACL tree cannot be copied and cut over per-account the
way the entity tree is.** It has to move in **one global step, after every account's entity/blob migration is
already complete**, with the callables' constants, the client's `SharingManagerImpl` constants, and
`shareRole()`'s target path all flipping together. §5.4 designs this step; no fallback-read or dual-deployment
logic is needed anywhere for the ACL tree specifically, *because* it waits until there's nothing left to be
mid-migration about.

---

## 3. Target state

### 3.1 Proto

Exactly the PRD's §6 shape — reproduced here so this doc is self-contained:

```proto
// aircraft.proto → thing.proto (message renamed; new fields added, nothing removed or renumbered)
message Thing {
  string id = 1;

  // --- transitional, dual-written until every dogfood account has migrated (§6, §9) ---
  string make = 2;
  string model = 3;
  string serial = 4;
  string tail_number = 5;
  repeated Engine engine = 6;

  // --- new, permanent ---
  string template_id = 7;          // "airplane" for every Thing in Phase 1 — no other value exists yet
  int32 template_version = 8;
  string name = 9;
  repeated SpecValue spec = 10;    // mirrors of make/model/serial/tail_number — see §4.2
  repeated Component components = 11;
}
```

`SpecValue` and `Component` are declared but Phase 1 populates them with exactly one shape — the airframe +
engine + propeller tree, deterministically derived from the existing `Engine`/`Propeller` messages (§4.2). No
other component shape, no other spec-field set, and no template other than `"airplane"` exists until Phase 3.

> **`option java_package = "dev.fanfly.wingslog.aircraft"` does not change, and never needs a migration when it
> eventually does.** `aircraft.proto` has no `package` statement — only this Kotlin/Java-only codegen option,
> shared by every proto in the directory (`Engine`, `Propeller`, `MaintenanceLog`, `Squawk`, `Technician`,
> `Attachment`, `ComponentType`, not just `Thing`). It governs source-code organization and nothing else: proto3's
> binary wire format encodes field numbers and types, never the source package, so this string leaves no trace in
> a Firestore document, a Storage object, or a local `entity`/`blob_object` row. `CollectionKind.schemaName`
> (`"thing.Thing"`, §2.2) looks derived from a proto package but isn't — there is no `package` statement to derive
> it from, and it's already an independent hand-picked string. Renaming `java_package` — if it ever happens — is a
> compiler-verified source refactor with zero runtime or stored-data effect, the same risk class as
> `AircraftScopeResolver` (§3.3), not the class of anything else in this document. It belongs with that later
> identifier cleanup, not with this migration, and needs no backend script, no grace window, and no retry logic
> to go with it.

### 3.2 Every path, before and after

| What | Before | After |
|---|---|---|
| `CollectionKind.Aircraft` (the Kotlin symbol itself — §8 #1) | `Aircraft` | `Thing` |
| `CollectionKind.Aircraft.wireName` | `"aircraft"` | `"thing"` |
| `CollectionKind.Aircraft.schemaName` | `"aircraft.Aircraft"` | `"thing.Thing"` |
| `EntityScope.aircraftChildUnsafe`'s literal | `"aircraft"` | `"thing"` |
| Thing's own Firestore doc | `/users/{uid}/aircraft/{id}` | `/users/{uid}/thing/{id}` |
| Nested Firestore (e.g. a log) | `/users/{uid}/aircraft/{id}/maintenance_log/{logId}` | `/users/{uid}/thing/{id}/maintenance_log/{logId}` |
| Attachment blob (Cloud Storage) | `users/{uid}/aircraft/{id}/blobs/{blobId}` | `users/{uid}/thing/{id}/blobs/{blobId}` |
| `Attachment.storage_path` (inside payload) | `users/{uid}/aircraft/{id}/blobs/{blobId}` | `users/{uid}/thing/{id}/blobs/{blobId}` |
| Local `entity.collection` for the Thing's own row | `'aircraft'` | `'thing'` |
| Local `entity.scope_path`, `blob_object.scope_path`/`remote_path`, `sync_cursor.scope_path`, `urgency_watermark.scope_path` | contains `/aircraft/` | contains `/thing/` |
| Cloud Function trigger paths (×4, §2.7) | `users/{uid}/aircraft/{acId}...` | `users/{uid}/thing/{acId}...` (deployed *alongside* the old ones during the rollout, §5.2) |
| `firestore.rules` entity match block (§2.4a) | `match /users/{userId} { match /aircraft/{acId} { ... } }` | `match /thing/{acId} { ... }` added *alongside* the old block for the whole rollout, removed only at the end (§5.4) |
| `AIRCRAFT_SHARES_COLLECTION` / `SHARE_AIRCRAFT_SUBCOLLECTION` (§2.9) | `"aircraft_shares"` / `"aircraft"` | `"thing_shares"` / `"thing"` — flipped in **one global step after every account's entity migration**, not per account (§2.9, §5.4) |
| `firestore.rules` ACL match block + `shareRole()`'s target (§2.9) | `match /aircraft_shares/{hostUid}/aircraft/{acId} { ... }` | `match /thing_shares/{hostUid}/thing/{acId} { ... }` added alongside the old block early (harmless while unused), `shareRole()` repointed only at the final step (§5.4) |
| Client `SharingManagerImpl.SHARES` / `SHARE_AIRCRAFT` constant values | `"aircraft_shares"` / `"aircraft"` | `"thing_shares"` / `"thing"` — ships in the follow-up release that pairs with the final ACL cutover (§5.4), *not* the main Phase 1 build |

### 3.3 What is explicitly *not* renamed in Phase 1, and why

| Stays as-is | Why |
|---|---|
| `AircraftScopeResolver` (interface + impl), its `resolve(aircraftId: String)` signature | Names a stable concept — "where does this Thing's data live" — that doesn't change meaning. Renaming it touches ~10 call sites for zero behavior change. Defer to Phase 3's broader identifier pass (PRD §3.3: `ShellAircraft` → `ShellThing` happens there too). |
| `EntityScope.aircraftChildUnsafe` function name (only its body's literal changes) | Same reasoning — the function's contract ("this Thing's nested-data scope") is unchanged. |
| `feature/aircraft/*` module names and packages | Renaming a Gradle module is its own, larger-blast-radius change (settings.gradle.kts, every internal import) that buys nothing for Phase 1's goal. |
| `add_aircraft` and other `strings.xml` entries, `ShellAircraft`, the adaptive shell's section labels | UI-facing; explicitly Phase 2/3 work per the PRD. Phase 1 must be invisible, which these already are. |
| `NotificationTapRouter`'s / `PushPayload`'s `"aircraft"` deep-link segment | A URL scheme segment for tap routing, not stored data — unrelated namespace to everything in §2.5–2.8. |
| `aircraftShareDocPath`/`shareMemberDocPath`/`AircraftShareDoc` (function and type *names*), `SharingManagerImpl`'s `SHARES`/`SHARE_AIRCRAFT` constant *names* | Only their *values* change (§2.9, §3.2) — same "rename identity, not every name that mentions it" principle as the rest of this table. |

The dividing line: **rename what is proto, wire, or storage identity; leave alone what is only a name in code or
in already-rendered UI.**

---

## 4. Local migration

### 4.1 `LocalThingPathMigrator` — modeled directly on `LocalAccountMigrator`

A new class, same package as `LocalAccountMigrator`, same shape: `DatabaseWriteLock`-guarded, one transaction,
idempotent by construction (no "have I run" flag needed — once no row matches `/aircraft/`, every query below is
a no-op, exactly like `LocalAccountMigrator.reassign`'s own idempotency argument).

Unlike the account-merge case (which moves a *variable-length* uid prefix via `substr`), this migration replaces
a *fixed* literal at a *fixed* position, which is simpler — a straight `REPLACE()`:

```sql
-- New queries, same file as the existing reassign* queries in Schema.sq.

fixThingCollection:
UPDATE entity SET collection = 'thing', payload_schema = 'thing.Thing'
WHERE collection = 'aircraft';

fixThingScopePaths:
UPDATE entity SET scope_path = REPLACE(scope_path, '/aircraft/', '/thing/')
WHERE scope_path LIKE '%/aircraft/%';

fixBlobScopePaths:
UPDATE blob_object
SET scope_path  = REPLACE(scope_path, '/aircraft/', '/thing/'),
    remote_path = REPLACE(remote_path, '/aircraft/', '/thing/')
WHERE scope_path LIKE '%/aircraft/%' OR remote_path LIKE '%/aircraft/%';

fixSyncCursorScopePaths:
UPDATE sync_cursor SET scope_path = REPLACE(scope_path, '/aircraft/', '/thing/')
WHERE scope_path LIKE '%/aircraft/%';

fixWatermarkScopePaths:
UPDATE urgency_watermark SET scope_path = REPLACE(scope_path, '/aircraft/', '/thing/')
WHERE scope_path LIKE '%/aircraft/%';
```

`REPLACE()` is safe here specifically because IDs are opaque generated strings (`generateRandomId()`) that cannot
themselves contain a `/` — there is exactly one occurrence of the literal per path, at a fixed position, so a
blind string replace can't mismatch a different segment.

Rewriting `sync_cursor` and `urgency_watermark` **in place** (rather than dropping them, which would be simpler
but wrong) is deliberate: dropping cursors forces a full re-hydration of every migrated aircraft's history on
next launch (wasted bandwidth and a slow first-launch-after-update), and dropping watermarks re-arms every
already-acknowledged overdue item as if newly crossed — the exact failure mode the existing `Schema.sq` comment
on `reassignWatermarks` warns about for the unrelated account-merge case. Rewriting preserves both.

### 4.2 The part that isn't SQL: rewriting embedded `Attachment.storage_path` and backfilling `spec`/`components`

This step must go through the generated proto API, not raw bytes (§2.6). For every `entity` row where
`collection` is `MaintenanceLog`, `MaintenanceTask`, or `Squawk`:

1. Decode `payload` as the corresponding message.
2. For each `Attachment` in its `attachments` list with a non-empty `storage_path`, replace the `/aircraft/`
   segment with `/thing/` (string-level is fine *here*, since we're operating on an already-decoded Kotlin
   `String` field, not raw proto bytes).
3. Re-encode, write back via the existing `upsert` query — **not marked dirty, no push** (§4.3).

For every `entity` row where `collection` is (soon-to-be) `Thing`:

1. Decode as `Thing` (the renamed message reading the still-populated legacy fields 2–6).
2. Compute, deterministically, exactly the fields the PRD's §9.1 already specifies: `template_id = "airplane"`,
   `name` from `tail_number` or `"$make $model"`, `spec` mirroring 2–5, and a `components` tree — one `airframe`
   component carrying the Thing's own make/model/serial, one `engine` child per `Engine`, one `propeller`
   grandchild per engine with `hub`/`blade` children — with every `Component.id` **derived deterministically**
   from `(thing_id, slot_key, index)`, per the PRD's own load-bearing requirement (§9.1: covered by a test that
   migrates the same payload twice and asserts identical output).
3. Re-encode, write back the same non-dirtying way.

Both passes run inside the same `LocalThingPathMigrator` transaction as §4.1's SQL, so a crash mid-migration
can't leave the database in a state where paths moved but payloads didn't, or vice versa.

### 4.3 Invocation

Called once, early in app startup — before `SyncEngine.start()` — analogous to where `LocalAccountMigrator` is
invoked from the account-upgrade flow, except this one is unconditional rather than user-triggered: every device
that launches the Phase 1 build runs it, regardless of whether this is a fresh install or an upgrade. On a fresh
install there is nothing to migrate and every query above is a no-op immediately.

Non-dirtying is essential here, exactly as the PRD states (§9.1): this pass must **not** set `dirty = 1` or
`remote_updated_at = NULL` on the rows it rewrites, because by the time a device runs this migration, the backend
has *already* moved this account's data to `/thing/...` (§5.1) — pushing these rows again would be redundant at
best and a spurious write racing the backend's own copy at worst.

---

## 5. Backend migration

### 5.1 The cutover script — one global batch run, not N per-account invocations

This is a **developer-managed global migration, run as a single batch job, not per-user work spread over a long
window.** The developer already has ground truth for "every account" by listing the `users` collection directly
(`adminDb.collection("users").listDocuments()`) rather than tracking uids by hand — so the script's outer loop is
"every uid in that list," and the whole batch runs to completion (or to a reported set of failures) in one
sitting, not staggered account-by-account over days.

A one-off Node script under `backend/firebase/functions` (Admin SDK access to both Firestore and Cloud Storage
already configured there — see `config/firebaseAdmin.ts`):

```
for each uid in adminDb.collection("users").listDocuments():
  try:
    migrate(uid)      // steps 1-4 below, for every aircraft this uid owns
    results.success.push(uid)
  catch (e):
    results.failed.push({ uid, error: e })
report(results)        // printed at the end of the run: N succeeded, M failed (with uid + cause)
```

Per uid (shared aircraft live in the host's tree per `AircraftScopeResolverImpl`, so iterating owners covers
every aircraft exactly once, members included):

1. **Enumerate.** List every `/users/{uid}/aircraft/*` document.
2. **Copy Firestore.** For each aircraft doc: copy it to `/users/{uid}/thing/{id}`, then copy its
   `maintenance_log`, `maintenance_task`, `maintenance_overview`, and `squawk` subcollections beneath the new
   path. Copy the raw `SyncDocWire` envelope (base64 `payload` and all) unchanged — **do not** attempt the
   `spec`/`components` backfill or the embedded `storage_path` rewrite server-side; that's §4.2's job, run
   identically on every device from the same deterministic inputs, which is exactly what makes it safe to compute
   twice (once here implicitly by *not* computing it, and once for real on-device) rather than needing the two
   computations to agree.
3. **Copy Storage.** For each aircraft: copy every object under `users/{uid}/aircraft/{acId}/blobs/**` to
   `users/{uid}/thing/{acId}/blobs/**`, verifying size (or checksum, since `Attachment.sha256` is already on
   hand from the proto — cheap to verify against) before touching the source.
4. **Verify.** Confirm document counts and blob byte totals match between old and new paths for this uid.

**No delete here.** Unlike an earlier draft of this design, step 5 is not "delete the old path once this
account's copy is verified" — deletion is a **separate, later invocation** of the script, scoped to accounts past
the §7 grace window, and is what actually retires the old data (§7). Keeping copy and delete as different runs is
what makes "try again for the accounts that failed" safe: a failed or partial copy never risks the source data,
because nothing is deleted until a completely separate, later pass confirms it.

**Failure isolation is a requirement, not a nicety.** One account's copy failing — a malformed legacy doc, a
transient network error, an oversized attachment — must not abort the batch for the other 49. The `try`/`catch`
per uid above is the whole mechanism: the script always finishes its pass over every uid and always ends with a
report, never a stack trace partway through the list. **Re-running the whole script (or a `--only` filter over
just the reported failures) is safe and idempotent** — step 2's copy is naturally re-runnable (overwriting an
already-correct destination doc is a no-op in effect), so "run it again" is the actual retry mechanism, not a
special resume path. The developer repeats this until a run reports zero failures — that report **is** the
answer to "has every account migrated," addressed further in §5.4.

Run with no concurrent client writes for the duration of a pass — the one place a write landing mid-copy would be
lost — which is realistic to arrange for a batch covering the whole (currently small, developer-controlled)
account base in one sitting, rather than something that has to hold for a long-running rollout. This does not
scale to store distribution; see the PRD's §9 for the explicit note that this whole approach is for the current
stage only.

### 5.2 Cloud Function + rules redeploy — brackets the batch, not a per-account window

Per §2.7 and §2.4a, deploy, together, in one release, **before running §5.1's batch**:

- New versions of all four Cloud Functions listening on `users/{uid}/thing/{acId}...`, **alongside** the existing
  `aircraft`-triggered ones (two functions per trigger, temporarily, not a replace).
- A `firestore.rules` update adding the `match /thing/{acId} { ... }` block **alongside** the existing
  `match /aircraft/{acId} { ... }` block (§2.4a) — inert until the batch starts writing to `/thing/...`.

Because §5.1 is now one global batch rather than a rollout spread over time, this dual-deployed window is short
by construction — the span from "deploy this" to "the batch's final retry reports zero failures," realistically
one sitting, not days. It still isn't zero, so it isn't skippable: deploying before the batch runs leaves the new
triggers/rules idle but harmless; the *removal* of the old triggers/rules must wait until §5.1 reports full
success **and** the §7 grace window has elapsed, not just "the batch finished" — a device that hasn't picked up
the new client build yet is still reading the old path during the grace window, and needs the old triggers/rules
to keep working until it updates.

### 5.3 Dry-run mode

Given real dogfood user data is at stake even at this scale, the script should support a `--dry-run` flag that
performs steps 1–4 against a read-only copy check (list + count + checksum comparison) without writing to
`/thing/...` — surfacing exactly what *would* move, so the developer can eyeball document/blob counts across the
whole batch before committing.

### 5.4 The ACL tree cutover — one global step, after every account, not folded into §5.1

Per §2.9's constraint (`shareRole()` and the sharing callables are each one globally-deployed thing, not
per-account), the ACL tree does not move alongside each account's entity data in §5.1. It moves once, in a single
maintenance window, gated on **§5.1's batch script reporting zero failures across every uid** — that report is
the confirmation this step needs (§8 #4), not a manually-maintained checklist:

1. **Copy.** For every host with a share (likely a small fraction of the whole account base — this is a sweep
   over `aircraft_shares/*`, not a per-account loop), copy `aircraft_shares/{hostUid}/aircraft/{acId}` and its
   `members/*` and `invites/*` subcollections to `thing_shares/{hostUid}/thing/{acId}`.
2. **Verify**, same shape as §5.1 step 4.
3. **Deploy, together, in one release:** the `sharingModels.ts` constant flip (`AIRCRAFT_SHARES_COLLECTION` →
   `"thing_shares"`, `SHARE_AIRCRAFT_SUBCOLLECTION` → `"thing"`), `firestore.rules`' `shareRole()` repointed at
   `thing_shares/.../thing/...`, and a client release with `SharingManagerImpl`'s `SHARES`/`SHARE_AIRCRAFT`
   constants updated to match. These three must land together — a callable pointed at the new collection while
   `shareRole()` still reads the old one (or vice versa) reintroduces exactly the split-brain this design
   otherwise avoids.
4. **Delete old, after the same 7-day grace window as §7.** A device still running the pre-cutover client build
   has the old `SharingManagerImpl` constants compiled in until it updates, so `aircraft_shares` and its
   `firestore.rules` match block stay readable for the same reason the entity tree's old path does — to not break
   a device that hasn't picked up the paired client release from step 3 yet. (The *entity* tree's old
   `match /aircraft/{acId}` block and its Cloud Function triggers are cleaned up independently in §5.2, on the
   same grace-window timing — that cleanup doesn't wait on this step, and this step doesn't wait on it.) Once both
   cleanups have run, every trace of the `aircraft` segment is gone from both Firestore trees.

No dry-run flag is strictly needed here the way §5.3 is for §5.1 — the ACL tree is orders of magnitude smaller
(one doc plus a handful of members/invites per shared aircraft, not full log/task/squawk history) — but reusing
the same copy-verify-then-delete shape costs nothing.

---

## 6. Testing & verification

- **`LocalThingPathMigratorTest`**, modeled on the existing account-merge migrator's test: seed rows with
  `/aircraft/`-shaped `scope_path`/`collection` values (including at least one `MaintenanceLog` with an
  attachment, to exercise §4.2's payload rewrite), run the migrator, assert every table's rows now read
  `/thing/`, assert a second run is a no-op (row-for-row identical), and assert the decoded `Attachment
  .storage_path` no longer contains `/aircraft/`.
- **The PRD's own regression bar still applies unchanged**: the existing `TaskDueManager` suite must pass
  unmodified against Thing-shaped data (PRD §7), and a byte-identical snapshot test proves the airplane lexicon
  is untouched (PRD §10) — neither is new to this doc, both gate this phase too.
- **A round-trip test proving Wire's unknown-field retention survives a pre-migration client's edit** — the PRD
  already calls for this (§9.2); it's listed here because it's this design's proto shape it has to hold against.
- **Cutover script dry-run against a seeded emulator account** before running it against any real dogfood
  account, using the existing Firestore/Storage emulator harness already set up for `backend/firebase/functions`
  tests (see the emulator + vitest setup already in place there).
- **Batch-runner test against a seeded multi-account emulator fixture**, with at least one uid engineered to fail
  (a malformed doc, a simulated write error): assert the run still processes every other uid, the failing uid
  shows up in the report, and re-running the script — either the full batch or filtered to just that uid —
  succeeds once the underlying issue is fixed, without re-copying or disturbing the uids that already succeeded.
  This is the test for the §5.1/§8 #4 retry-until-clean design, not just the copy logic itself.
- **`firestore-rules.test.ts` and `sharing-rules.test.ts`** already assert against hardcoded `aircraft`/
  `aircraft_shares` path fixtures (e.g. `users/alice/aircraft/ac1`, `` `aircraft_shares/${HOST}/aircraft/${AC}` ``
  — confirmed by grep). During the §5.2/§5.4 dual-block window these need a **parallel set of assertions against
  the `/thing/`-shaped and `thing_shares`-shaped paths**, not a find-and-replace of the existing ones — the
  existing `aircraft`-shaped assertions must keep passing for as long as the old rules block is deployed, since
  that's exactly what a not-yet-migrated account is relying on. Once §5.2's and §5.4's old-block removals ship,
  the old assertions flip to `assertFails` (proving the dead path is actually dead, not just unused) rather than
  being deleted outright.

---

## 7. Rollback / safety margin

The PRD's §9.1 says old paths are "deleted once the copy is confirmed" — this design adds one concrete
strengthening: **don't delete on the same run that verifies.** Keep the old `/aircraft/...` Firestore
subcollections and Storage objects for a **7-day grace window** (§8 #3) after §5.1's batch reports a given
account copied successfully, and run deletion as a separate, later invocation of the script scoped to "accounts
verified more than 7 days ago." This buys two things, not one: it turns "the copy looked right but something was
subtly wrong" from a data-loss incident into a re-run, and — now that §5.1 is one global batch rather than a
staggered per-account rollout (per the earlier clarification that this is developer-managed, not per-user, work)
— it's also the thing that keeps a device working normally for the days it takes the Phase 1 client build to
actually reach it after the backend batch completes. The backend can finish in an afternoon; getting the build
onto every dogfood device does not happen in the same afternoon, and the old path staying alive for those 7 days
is what keeps a not-yet-updated device from breaking in the gap. This is why deletion is scoped to *time since
verified*, not *time since the batch ran* — an account whose retry succeeds on day 3 gets its own 7 days from
then, not from day 1's first attempt.

---

## 8. Decisions

1. **`CollectionKind.Aircraft` (the Kotlin symbol) is renamed to `CollectionKind.Thing` in Phase 1** — not just
   its `wireName` value. It directly names the proto type being swapped (§3.1), the rename is mechanical and
   compiler-verified end to end, and leaving the Kotlin symbol `Aircraft` pointing at a message literally named
   `Thing` would be a standing readability trap for exactly the reason `schemaName` changes to `"thing.Thing"` in
   the first place. Reflected in §3.2's path table.
2. **`LocalThingPathMigrator` is invoked once, early in app startup, before `SyncEngine.start()`** —
   `LocalAccountMigrator`-style (§4.3). The exact call site (Koin module, `composeApp` init, or a dedicated
   startup orchestrator) is left to whoever implements this; it isn't a design decision, just a wiring detail.
3. **The grace window in §7 is 7 days.** Applied consistently to both the entity tree's old paths (§7) and the
   ACL tree's old paths (§5.4 step 4) — a device hasn't necessarily updated within a day of the backend batch
   completing, and 7 days is comfortable room for that without meaningfully delaying final cleanup.
4. **"Every account has completed §5.1" is confirmed by the batch script's own report, not a manual checklist.**
   Per the developer's own framing: since every uid is enumerable directly from the Firestore `users` collection,
   the script runs as one pass over that full list with a `try`/`catch` per uid (§5.1), reports which uids
   succeeded and which failed, and gets **re-run** — against the whole list or just the reported failures — until
   a run comes back with zero failures. *That* report is the gate on §5.4, not developer judgment about whether
   everyone's been covered. This also confirms the overall shape of this migration: **one developer-managed,
   global batch operation**, not per-user work spread over a long window — which is why §5.1, §5.2, and §7 above
   are written the way they are.

---

## 9. Action plan

Every task this design implies, grouped into phases with a hard ordering constraint *between* phases. Within a
phase, tasks are independent of each other unless a "Depends on" cell says otherwise, so they can be worked in
parallel or in any order. **Ref** points at the section that specifies the task in detail.

### Phase A — Client-side code (parallelizable; all land in one branch, ships in Phase E)

| # | Task | Depends on | Ref |
|---|---|---|---|
| A1 | Rename `aircraft.proto`'s `Aircraft` message → `Thing`; rename the file → `thing.proto`; add fields 7–11 (`template_id`, `template_version`, `name`, `spec`, `components`) and declare `SpecValue`/`Component`. Keep `java_package = "dev.fanfly.wingslog.aircraft"` unchanged — it's Kotlin/Java-only, carries no wire or stored-data identity, and renaming it later (if ever) needs no migration of its own (§3.1). | — | §3.1, §3.3 |
| A2 | Regenerate Kotlin (Wire) proto bindings from A1; fix every resulting compiler error at `Aircraft`-referencing call sites (mechanical rename to `Thing`). | A1 | §8 #1 |
| A3 | Rename `CollectionKind.Aircraft` (Kotlin symbol) → `CollectionKind.Thing`; set `wireName = "thing"`, `schemaName = "thing.Thing"`; fix every call site (compiler-verified). | — | §2.2, §3.2, §8 #1 |
| A4 | Update `EntityScope.aircraftChildUnsafe`'s internal literal `"aircraft"` → `"thing"`. Do not rename the function. | — | §2.3, §3.3 |
| A5 | Fix the ten hardcoded-literal call sites: `SyncEngine.kt:594`; `PushWorker.kt:214,236,256`; `AttachmentBroker.kt:48,59`; `SharedScopeJanitor.kt:83,88,147,158`; `WebForeignWriteDetector.kt:283`; `storageSweep.ts:207,261`; `deleteMyAccount.ts:100,120,147`. | — | §2.5 |
| A6 | Write `LocalThingPathMigrator` (new class, `core/storage`, sibling of `LocalAccountMigrator`): the five SQL queries (`fixThingCollection`, `fixThingScopePaths`, `fixBlobScopePaths`, `fixSyncCursorScopePaths`, `fixWatermarkScopePaths`) plus the proto-level pass that rewrites embedded `Attachment.storage_path` on `MaintenanceLog`/`MaintenanceTask`/`Squawk` rows and backfills `spec`/`components`/`template_id`/`name` (deterministic component IDs) on Thing rows — all inside one `DatabaseWriteLock`-guarded transaction. | A1, A2 | §4.1, §4.2 |
| A7 | Wire `LocalThingPathMigrator`'s invocation into app startup, before `SyncEngine.start()`. | A6 | §4.3, §8 #2 |
| A8 | Write `LocalThingPathMigratorTest`: seed `/aircraft/`-shaped rows (incl. a `MaintenanceLog` with an attachment), run, assert rewritten, assert a second run is a no-op, assert `Attachment.storage_path` fixed. | A6 | §6 |
| A9 | Confirm `CollectionKindCoverageTest` still passes unmodified (no code change expected — it asserts against the symbol, not the string). | A3 | §2.2 |
| A10 | Confirm the existing `TaskDueManager` regression suite and the byte-identical lexicon snapshot test still pass unmodified against Thing-shaped data. | A6 | §6, PRD §7/§10 |
| A11 | Write the round-trip test proving Wire's unknown-field retention across a pre-migration client's edit of Thing-shaped data. | A1, A2 | §6, PRD §9.2 |

### Phase B — Backend script + rules/functions code (parallelizable; independent of Phase A)

| # | Task | Depends on | Ref |
|---|---|---|---|
| B1 | Write the backend cutover script (`backend/firebase/functions`, one-off Node/Admin SDK): enumerate every uid via `adminDb.collection("users").listDocuments()`; per uid, `try`/`catch` around enumerate → copy Firestore → copy Storage (checksum-verified) → verify counts; collect a success/failure report. Explicitly do **not** attempt the `spec`/`components`/`storage_path` rewrite server-side — that's A6, run identically on-device. | — | §5.1 |
| B2 | Add a `--dry-run` mode to the script (read-only count/checksum comparison, no writes). | B1 | §5.3 |
| B3 | Add a targeted-retry mode (`--only <uids>`) so a failed subset can be re-run without repeating the whole batch. | B1 | §5.1 |
| B4 | Write a separate deletion pass (a mode of the same script, or a sibling script): delete old Firestore subcollections + old Storage objects, scoped to accounts whose copy was verified more than 7 days ago. | B1 | §7 |
| B5 | Write the batch-runner failure-isolation/retry test against a seeded multi-account emulator fixture with one uid engineered to fail: assert the rest still process, the failure is reported, and a targeted re-run succeeds without disturbing already-migrated uids. | B1, B3 | §6 |
| B6 | Update `sharingModels.ts`'s `AIRCRAFT_SHARES_COLLECTION`/`SHARE_AIRCRAFT_SUBCOLLECTION` values — **write now, hold on a branch; do not deploy until Phase G.** | — | §2.9, §3.2, §5.4 |
| B7 | Add a new `match /thing/{acId} { ... }` block to `firestore.rules`, alongside the existing `match /aircraft/{acId} { ... }` block, mirroring `isShareMember`/`isShareOwner`/`writerIsSelf`/`isSharedAircraftKind`. | — | §2.4a, §3.2 |
| B8 | Add a new `match /thing_shares/{hostUid}/thing/{acId} { ... }` block to `firestore.rules`, alongside the existing ACL block — added now, inert until Phase G. Do **not** repoint `shareRole()` yet. | — | §2.9, §3.2 |
| B9 | Write new versions of the four Cloud Functions (`onNotifiableRecordWritten`, `onNotifiableAircraftWritten`, `onAircraftDeleted`, `onRecordDeleted`) registered on `users/{uid}/thing/{acId}...`, to deploy *alongside* (not replacing) the existing ones. | — | §2.7, §5.2 |
| B10 | Update `firestore-rules.test.ts` and `sharing-rules.test.ts` with a parallel set of assertions against the `/thing/`- and `thing_shares`-shaped paths, keeping every existing `aircraft`-shaped assertion passing. | B7, B8 | §6 |
| B11 | Test the cutover script end-to-end against the Firestore/Storage emulator with a seeded fixture. | B1, B2 | §6 |

### Phase C — Deploy dual backend infrastructure (gates Phase D)

| # | Task | Depends on | Ref |
|---|---|---|---|
| C1 | Deploy, together, in one release: the new `/thing/{acId}` and `/thing_shares/...` `firestore.rules` blocks (B7, B8) and the four new dual Cloud Functions (B9) — alongside everything existing, unchanged. | B7, B8, B9, B10 | §5.2 |

### Phase D — Run the entity/blob migration batch (must reach zero failures before Phase E/G)

| # | Task | Depends on | Ref |
|---|---|---|---|
| D1 | Run the cutover script in `--dry-run` mode against the full account list; review the report. | C1, B2, B11 | §5.3 |
| D2 | Run the cutover script for real against the full account list; review the success/failure report. | D1 | §5.1 |
| D3 | Re-run (targeted at reported failures via B3, or the whole batch again) until a run reports **zero failures across every uid**. This report is the gate for distributing the client build (E2) and for starting the ACL cutover (Phase G). | D2 | §5.1, §8 #4 |

### Phase E — Ship the Phase 1 client build

| # | Task | Depends on | Ref |
|---|---|---|---|
| E1 | Cut the Phase 1 client release bundling A1–A11. Confirm no template-picker UI and no non-airplane preset exist in it — the app must be behaviorally invisible. Building/tagging this release does not require D3 to be done yet. | A2, A3, A4, A5, A6, A7, A8, A9, A10, A11 | §1, PRD §15 Phase 1 |
| E2 | Distribute the build to all dogfood devices. **Must not happen before D3** — a device on this build reads `/thing/...`, which must already hold this account's data. | E1, D3 | §5.1, PRD §9 |

### Phase F — Grace window + entity-tree cleanup (independent of Phase G's own timing)

| # | Task | Depends on | Ref |
|---|---|---|---|
| F1 | Hold 7 days from each account's own verified-copy timestamp (D2/D3) — old Firestore/Storage paths, old Cloud Functions, and the old rules block all stay live throughout. | D3, E2 | §7, §8 #3 |
| F2 | Run the deletion pass (B4) for every account whose 7-day window has elapsed: delete old Firestore subcollections + old Storage objects. | F1 | §7 |
| F3 | Once every account is cleaned up: remove the old (`aircraft`-triggered) Cloud Functions and the old `match /aircraft/{acId}` `firestore.rules` block, in a second release. | F2 | §5.2 |
| F4 | Flip the corresponding `firestore-rules.test.ts` assertions for the removed block from `assertSucceeds` to `assertFails`, proving it's actually dead. | F3 | §6 |

### Phase G — ACL tree global cutover (gated on D3; independent of Phase F's completion)

| # | Task | Depends on | Ref |
|---|---|---|---|
| G1 | Sweep `aircraft_shares/*` for every host with a share; copy each ACL doc plus its `members/*`/`invites/*` subcollections to `thing_shares/{hostUid}/thing/{acId}`. No delete yet. | D3 | §5.4 |
| G2 | Verify document counts match between old and new ACL paths. | G1 | §5.4 |
| G3 | Deploy, together, in one release: the `sharingModels.ts` constant flip (B6), `firestore.rules`' `shareRole()` repointed at `thing_shares/.../thing/...`, and a client release updating `SharingManagerImpl`'s `SHARES`/`SHARE_AIRCRAFT` constants to match. | G2, B6 | §5.4 |
| G4 | Distribute this follow-up client release to all dogfood devices. | G3 | §5.4 |
| G5 | Hold the same 7-day grace window for devices to pick up the follow-up release. | G4 | §5.4, §8 #3 |
| G6 | Delete the old `aircraft_shares` tree and its `firestore.rules` ACL match block. | G5 | §5.4 |
| G7 | Flip `sharing-rules.test.ts` assertions for the removed ACL block to `assertFails`. | G6 | §6 |

### Phase H — Wrap-up

| # | Task | Depends on | Ref |
|---|---|---|---|
| H1 | Update this doc's and the PRD's "Implementation status" banners to reflect that Phase 1 has shipped. | F3, G6 | AGENTS.md § Design Docs |
