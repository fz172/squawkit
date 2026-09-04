# Comments design

**Status: shipped** (issue [#749](https://github.com/fz172/squawkit/issues/749)).

A comments tab on the Update Squawk and Update Task forms. Anyone with access to the record —
the host and every share member — can leave notes on it: troubleshooting steps, parts status,
"holding this until the 100-hr".

Related: [sharing](../sharing/aircraft_sharing_design.html) (who can see a thread),
[storage R1](../storage/storage_r1_design.md) (how a row gets to the other device).

## 1. Where a comment lives

`users/{hostUid}/thing/{thingId}/comment/{commentId}` — a per-Thing collection alongside
`squawk`, `maintenance_task` and the rest, registered as `CollectionKind.Comment`
(`wireName = "comment"`, `schemaName = "thing.Comment"`). One `SyncDocWire` document per comment,
carried by the ordinary sync engine; no feature code touches Firestore.

### Its own rows, not a repeated field on the parent

The obvious cheaper design is `repeated Comment comments` on `Squawk` and `MaintenanceTask`. It is
wrong here for one reason: conflict resolution is last-writer-wins over the **whole document**. Two
technicians commenting on the same squawk within a sync window would each write a squawk payload
containing only their own comment, and the later write would erase the earlier one — silently, with
no tombstone and nothing to reconcile. A feature whose entire purpose is more than one person
writing cannot be built on a single-document LWW cell.

Separate rows also mean an edit or a delete touches one small document, and the count on the tab
comes from the row count rather than from re-decoding the parent.

### One collection per Thing, not per record

The thread is selected by the **pair** `(parent_type, parent_id)`, both stored on the comment.
A per-record subcollection would have been tidier to query, but the sync engine attaches one pull
listener per (scope, kind): a collection per squawk means a listener per squawk. One collection per
Thing costs one listener and a client-side filter over a small list.

`parent_type` is not redundant with `parent_id`. Ids are unique in practice, but the type is what
makes "this thread belongs to a task" a fact in the data rather than an inference from a lookup —
and it is what a future backfill or export reads.

## 2. Authorship

`author_uid` is the identity the edit/delete gate checks. `author_name` is denormalized at post
time from the poster's own technician record — the same precedence
`SharingManagerImpl.publishTechnicianMirror` uses for the share roster (in-app profile name, then
the auth account name).

Denormalizing the name is deliberate. The share roster is online-only, so resolving a uid to a name
at render time would leave an offline thread full of raw uids. It also keeps the byline stable: a
comment says who wrote it *then*, and still reads correctly after that person leaves the share.

`edited_at` is unset until the author changes the text, which is what the amber "Edited …" line
reads. Saving the editor without changing anything writes nothing — opening a box is not an edit,
and branding the comment would be a lie the reader cannot check.

## 2a. Delete is a tombstone, never a removal

Deleting clears `text` and stamps `deleted_at`. The row stays, and the thread keeps its card:
struck through, muted, reading "Comment deleted on 09/03/2026 09:18 PM". Nothing in the app calls
`EntityStore.delete` for a comment.

**This is the paper trail, not a nicety.** An edit is visible — `edited_at` puts an "Edited …" line
on the card, so a rewrite is on the record for everyone who can read the thread. If a delete erased
the row, an author who wanted to rewrite without the line could delete and re-post instead, and the
audit trail would be defeated by the cheapest possible route. Keeping the tombstone means the
thread still shows that something was said here and then withdrawn.

Two consequences follow, and both are enforced in `CommentManagerImpl`:

- **A tombstone is final.** `updateComment` refuses to write to one — reviving a deleted comment
  would erase the record of the deletion.
- **Deleting twice does nothing.** Re-stamping would move the recorded time, which is the one fact
  the tombstone exists to pin down.

The tab badge counts live comments only. A tombstone still occupies a card, but the badge answers
"is there anything here to read", and a withdrawn comment is not.

## 3. Who may write

`firestore.rules` adds `comment` to `isSharedAircraftKind`, so a share member may write into the
host's `comment` subtree under the same `writerIsSelf()` attestation as logs, tasks and squawks. A
member who could read a squawk but not comment on it would have a read-only thread, which is not
the feature.

Edit and delete are the author's alone. The ⋮ menu is only rendered on your own comments, and
`CommentManagerImpl` re-checks `author_uid` against the signed-in uid before writing — a thread
other people can write into is exactly where "the UI checks" stops being sufficient. Note the limit
of that check: it is client-side, so it is a correctness guard, not a security boundary. The rules
grant any member write access to any comment document in the subtree, the same as for every other
per-Thing kind.

## 4. UI

`CommentThreadSection` (in `feature/comments/viewing`) is stateless and renders the whole tab: the
thread oldest-first, tombstones included, then the composer. The ⋮ menu appears only on a comment
that is both yours and not already deleted. Both forms render the same composable, and both drive it
through `CommentThreadController` — a UI-free state machine in `datamanager`, the same shape as
`AttachmentFormController`, so neither ViewModel re-implements drafts, the inline editor or the
menu.

The draft lives in the controller, not in a composable `remember`, so it survives the tab being
swiped away and back ([#254](https://github.com/fz172/squawkit/issues/254)). A failed post leaves
the draft in the box — the words the author typed exist nowhere else — and reports through
`errors` so the owning screen can say so in its own words.

The tab is **edit-only** on both forms: a record that has not been saved yet has no id for a
comment to point at. `taskFormTabsFor(includeComments = …)` and `squawkFormTabsFor(isEdit)` remove
it rather than disabling it, the same removal-not-disabling rule the compliance tab follows.

Timestamps render in the device's zone via `Instant.toDisplayDateTime()`. No zone abbreviation:
kotlinx-datetime cannot name a zone on every target we build for, and a label that is right on
Android and blank on web is worse than none.

## 5. Not in scope

**No notification on a new comment.** `recordTypeForKind` returns null for `comment`, so the N1
collaboration fan-out ignores it. A comment on a shared Thing arguably should notify — it is
collaboration activity by definition — but that needs a `RecordType`, a payload reader, push
message strings and a settings category, which belongs with the notifications work in
[docs/notifications](../notifications/notifications_design.md) rather than bolted on here.

**No attachments on a comment.** `AttachmentRefs` lists `Comment` among the kinds that own no
blobs. Adding one later means a proto field and a branch there — the `when` is exhaustive so it
cannot be forgotten.

**No @mentions, no reactions, no threading.** Flat, chronological, one level.
