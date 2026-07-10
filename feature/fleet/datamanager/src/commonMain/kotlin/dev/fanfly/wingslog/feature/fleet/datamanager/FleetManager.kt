package dev.fanfly.wingslog.feature.fleet.datamanager

import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.core.model.sharing.ShareRole
import kotlinx.coroutines.flow.Flow

/**
 * One aircraft on the fleet dashboard, tagged with how the current user relates to it.
 *
 * @property shared `false` for the user's own aircraft, `true` for one shared into their fleet by
 *   another account (rendered with a "Shared" badge).
 * @property role the current user's [ShareRole] for this aircraft — `SHARE_ROLE_OWNER` for own
 *   aircraft, otherwise the role from the member's `SharedAircraftRef` (drives UI gating, #133).
 */
data class FleetEntry(
  val aircraft: Aircraft,
  val shared: Boolean,
  val role: ShareRole,
)

interface FleetManager {
  /**
   * The current user's fleet: their own aircraft plus any shared into their fleet by other accounts
   * (docs/sharing §6.3). Emits `emptyList()` while signed out.
   */
  fun observeFleetDashboard(): Flow<List<FleetEntry>>

  suspend fun updateAircraft(aircraft: Aircraft): Result<Boolean>

  fun loadAircraft(id: String): Flow<Aircraft?>

  suspend fun deleteAircraft(id: String): Result<Boolean>
}
