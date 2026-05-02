package dev.fanfly.wingslog.core.sync

import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.FirebaseFirestore

/**
 * Path mapping from local storage coordinates to Firestore paths.
 *
 * The mapping is mechanical: an [EntityScope] is a sequence of `(collection, doc)` segments, and a
 * [CollectionKind] is appended as the leaf collection identified by [CollectionKind.wireName]. The
 * document id is the same id used in the local [entity] table.
 *
 * Examples
 * - `Aircraft` at `userRoot("u1")` with id `ac1` → `/users/u1/aircraft/ac1`
 * - `MaintenanceLog` at `aircraftChild("u1", "ac1")` with id `l9` →
 *   `/users/u1/aircraft/ac1/maintenance_log/l9`
 *
 * Keep this the **only** place that knows about Firestore paths, so renames or restructures in R2
 * touch a single file.
 */
object FirestoreRefs {

  fun collection(
    firestore: FirebaseFirestore,
    kind: CollectionKind,
    scope: EntityScope,
  ): CollectionReference {
    val segments = scope.segments
    require(segments.size >= 2 && segments.size % 2 == 0) {
      "EntityScope must have an even number of segments (collection/doc pairs); got $segments"
    }
    var doc: DocumentReference = firestore.collection(segments[0]).document(segments[1])
    var i = 2
    while (i + 1 < segments.size) {
      doc = doc.collection(segments[i]).document(segments[i + 1])
      i += 2
    }
    return doc.collection(kind.wireName)
  }

  fun document(
    firestore: FirebaseFirestore,
    kind: CollectionKind,
    scope: EntityScope,
    id: String,
  ): DocumentReference = collection(firestore, kind, scope).document(id)
}
