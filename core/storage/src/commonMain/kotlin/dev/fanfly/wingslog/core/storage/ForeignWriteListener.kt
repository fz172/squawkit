package dev.fanfly.wingslog.core.storage

/**
 * Called by [dev.fanfly.wingslog.feature.sync.data.PullListener] when it applies a remote write
 * authored by **someone other than the signed-in user** — the local half of an N1 collaboration
 * event (notifications design §8.2).
 *
 * Lives here, not in `feature/notifications`, because `feature/sync/data` must not depend on the
 * notifications feature (§3). Same shape and same reason as [PostWriteHook] and [CloudSyncSetting]:
 * the interface belongs to `core:storage`, the implementation is supplied by a feature and bound
 * through Koin. The default binding is absent, and `PullListener` treats that as a no-op — only
 * `engine`'s `jsMain` binds a real one, because Android and iOS receive N1 by push and running both
 * paths would notify twice.
 *
 * Implementations are fire-and-forget observers: they must not block the caller and must not throw.
 *
 * Not called for deletions. A tombstone is a change, but tombstones also arrive in bulk from
 * `TombstoneGc` sweeps and from a revoked share being reconciled away, neither of which is a
 * collaborator doing something worth interrupting anyone about. [PostWriteHook] skips deletes for
 * its own reasons; this matches it deliberately rather than by accident.
 */
fun interface ForeignWriteListener {
  fun onForeignWrite(
    kind: CollectionKind,
    scope: EntityScope,
    id: String,
    writerUid: String,
  )
}
