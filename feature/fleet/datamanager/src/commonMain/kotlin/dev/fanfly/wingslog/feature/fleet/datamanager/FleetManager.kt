package dev.fanfly.wingslog.feature.fleet.datamanager

import dev.fanfly.wingslog.core.model.sharing.ShareRole
import dev.fanfly.wingslog.thing.Thing
import kotlinx.coroutines.flow.Flow

/**
 * One thing on the fleet dashboard, tagged with how the current user relates to it.
 *
 * @property shared `false` for the user's own thing, `true` for one shared into their fleet by
 *   another account (rendered with a "Shared" badge).
 * @property role the current user's [ShareRole] for this thing — `SHARE_ROLE_OWNER` for own
 *   thing, otherwise the role from the member's `SharedAircraftRef` (drives UI gating, #133).
 */
data class FleetEntry(
  val thing: Thing,
  val shared: Boolean,
  val role: ShareRole,
)

interface FleetManager {
  /**
   * The current user's fleet: their own thing plus any shared into their fleet by other accounts
   * (docs/sharing §6.3). Emits `emptyList()` while signed out.
   */
  fun observeFleetDashboard(): Flow<List<FleetEntry>>

  /**
   * Writes [thing], creating it when it has no id. Returns the Thing as written — id assigned and
   * DNA inflated — because a create is the one place the caller cannot know either beforehand,
   * and the starter-pack hand-off (PRD §4.9) needs both.
   */
  suspend fun updateThing(thing: Thing): Result<Thing>

  fun loadThing(id: String): Flow<Thing?>

  suspend fun deleteThing(id: String): Result<Boolean>
}
