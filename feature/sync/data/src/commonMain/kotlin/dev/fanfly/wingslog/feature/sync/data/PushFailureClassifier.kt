package dev.fanfly.wingslog.feature.sync.data

import dev.gitlive.firebase.auth.FirebaseAuthException
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import dev.gitlive.firebase.firestore.code

/**
 * Classifies a push exception into a banner-worthy [SyncFailure], or `null` for transient errors
 * that will recover on the next reconnect. The split mirrors §5.2 of the storage R1 design doc.
 *
 * Transient (returns `null`):
 * - `UNAVAILABLE`, `DEADLINE_EXCEEDED`, `INTERNAL`, `RESOURCE_EXHAUSTED`, `ABORTED`, `CANCELLED`
 *   — network/server hiccups; the dirty row stays queued and next notification re-tries.
 *
 * Auth-class (returns [SyncFailure.AuthExpired]):
 * - Any [FirebaseAuthException].
 * - Firestore `PERMISSION_DENIED` and `UNAUTHENTICATED` codes — typically expired token or rules
 *   that flipped underneath us; auto-retry won't fix them.
 *
 * Other (returns [SyncFailure.Push]):
 * - Anything else, including Firestore validation codes (`INVALID_ARGUMENT`, `FAILED_PRECONDITION`,
 *   `OUT_OF_RANGE`, `NOT_FOUND`, `ALREADY_EXISTS`, `DATA_LOSS`, `UNIMPLEMENTED`, `UNKNOWN`) and
 *   non-Firebase exceptions. These deserve a banner because retry alone won't resolve them.
 */
internal fun classifyPushFailure(throwable: Throwable): SyncFailure? {
  if (throwable is FirebaseAuthException) return SyncFailure.AuthExpired()
  if (throwable is FirebaseFirestoreException) {
    return when (throwable.code) {
      FirestoreExceptionCode.UNAVAILABLE,
      FirestoreExceptionCode.DEADLINE_EXCEEDED,
      FirestoreExceptionCode.INTERNAL,
      FirestoreExceptionCode.RESOURCE_EXHAUSTED,
      FirestoreExceptionCode.ABORTED,
      FirestoreExceptionCode.CANCELLED,
        -> null

      FirestoreExceptionCode.PERMISSION_DENIED,
      FirestoreExceptionCode.UNAUTHENTICATED,
        -> SyncFailure.AuthExpired()

      else -> SyncFailure.Push("Sync error: ${throwable.message ?: throwable.code.name}")
    }
  }
  return SyncFailure.Push("Sync error: ${throwable.message ?: throwable::class.simpleName ?: "unknown"}")
}

/**
 * True when [throwable] is a Firestore `PERMISSION_DENIED`. On a **shared** scope this is the
 * reliable "the rules denied us because we were revoked" signal (rules don't flap — transient
 * problems surface as `UNAVAILABLE`/`DEADLINE_EXCEEDED`/etc.), so the sync engine treats it as a
 * revocation and reconciles locally rather than showing an auth banner. See docs/sharing §5.4.
 */
internal fun isPermissionDenied(throwable: Throwable): Boolean =
  throwable is FirebaseFirestoreException &&
    throwable.code == FirestoreExceptionCode.PERMISSION_DENIED
