package dev.fanfly.wingslog.core.storage

/**
 * Called by [dev.fanfly.wingslog.feature.sync.data.PullListener] after it successfully writes a
 * remote entity to the local `entity` table. Implementations are fire-and-forget observers —
 * they must not block the caller and must not throw.
 *
 * The hook is NOT called for deleted entities (payload would be stale/empty).
 */
fun interface PostWriteHook {
  fun onEntityWritten(
    kind: CollectionKind,
    scope: EntityScope,
    payload: ByteArray
  )
}
