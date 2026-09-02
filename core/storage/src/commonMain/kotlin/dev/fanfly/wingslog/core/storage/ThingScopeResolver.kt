package dev.fanfly.wingslog.core.storage

import kotlinx.coroutines.flow.Flow

/**
 * Resolves the [EntityScope] that holds a thing's nested maintenance data (logs, tasks,
 * squawks, overview). Per-thing managers must not derive scopes from the signed-in uid any more:
 *
 * - **Own thing** → `thingChildUnsafe(myUid, thingId)` (unchanged).
 * - **Shared thing** → `thingChildUnsafe(hostUid, thingId)`, where `hostUid` comes from the
 *   member's [dev.fanfly.wingslog.core.model.sharing.SharedAircraftRef] for this id. Shared data
 *   lives in-place under the host's tree; refs are pointers, not copies (docs/sharing §6.3).
 *
 * The interface lives in `core:storage` so `logs` / `tasks` / `squawk` managers can depend on it
 * without pulling in the sharing feature; the implementation (which needs auth + the refs store)
 * is provided by `feature:sharing:datamanager` and bound via Koin — the [CloudSyncSetting] pattern.
 */
interface ThingScopeResolver {
  /**
   * The scope for [thingId], re-emitting on sign-in/out and whenever the thing's share status
   * changes (a ref appearing/disappearing flips own ↔ shared). Emits `null` while signed out, so
   * observers can clear — mirroring the auth-gated behaviour the managers had before.
   */
  fun resolve(thingId: String): Flow<EntityScope?>

  /** One-shot resolution for a mutation. Throws if no user is signed in. */
  suspend fun resolveNow(thingId: String): EntityScope
}
