package dev.fanfly.wingslog.core.storage

import kotlinx.coroutines.flow.Flow

/**
 * Resolves the [EntityScope] that holds an aircraft's nested maintenance data (logs, tasks,
 * squawks, overview). Per-aircraft managers must not derive scopes from the signed-in uid any more:
 *
 * - **Own aircraft** → `aircraftChildUnsafe(myUid, aircraftId)` (unchanged).
 * - **Shared aircraft** → `aircraftChildUnsafe(hostUid, aircraftId)`, where `hostUid` comes from the
 *   member's [dev.fanfly.wingslog.core.model.sharing.SharedAircraftRef] for this id. Shared data
 *   lives in-place under the host's tree; refs are pointers, not copies (docs/sharing §6.3).
 *
 * The interface lives in `core:storage` so `logs` / `tasks` / `squawk` managers can depend on it
 * without pulling in the sharing feature; the implementation (which needs auth + the refs store)
 * is provided by `feature:sharing:datamanager` and bound via Koin — the [CloudSyncSetting] pattern.
 */
interface AircraftScopeResolver {
  /**
   * The scope for [aircraftId], re-emitting on sign-in/out and whenever the aircraft's share status
   * changes (a ref appearing/disappearing flips own ↔ shared). Emits `null` while signed out, so
   * observers can clear — mirroring the auth-gated behaviour the managers had before.
   */
  fun resolve(aircraftId: String): Flow<EntityScope?>

  /** One-shot resolution for a mutation. Throws if no user is signed in. */
  suspend fun resolveNow(aircraftId: String): EntityScope
}
