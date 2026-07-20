package dev.fanfly.wingslog.feature.aircraft.dashboard.data

import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus

data class LogStats(
  val total: Long,
  val airframe: Long,
  val engine: Long,
  val propeller: Long,
  val currentEngineTime: Double? = null,
  val currentAirframeTime: Double? = null,
  val currentPropTime: Double? = null,
)

sealed interface AircraftOverviewUiState {
  data object Loading : AircraftOverviewUiState
  data object Error : AircraftOverviewUiState

  data class Success(
    val aircraft: Aircraft,
    val logStats: LogStats? = null,
    val activeTasks: List<MaintenanceTaskWithStatus> = emptyList(),
    val completedTasks: List<MaintenanceTaskWithStatus> = emptyList(),
    val recentLogs: List<MaintenanceLog> = emptyList(),
    val selectedTask: MaintenanceTaskWithStatus? = null,
    val logsForSelectedTask: List<MaintenanceLog> = emptyList(),
    val deletingTaskId: String? = null,
    val syncStates: Map<String, BlobSyncState> = emptyMap(),
    val squawks: List<SquawkWithStatus> = emptyList(),
    val aogSquawks: List<Squawk> = emptyList(),
    val selectedSquawk: SquawkWithStatus? = null,
    val logForSelectedSquawk: MaintenanceLog? = null,
    /** Caller's role on this aircraft; drives owner-only gating. `null` while it resolves. */
    val myRole: ShareRole? = null,
    /**
     * True when this aircraft lives in another account's fleet and was shared into ours. Not the
     * same as [myRole]: a co-owner of someone else's aircraft is `OWNER` *and* shared.
     */
    val shared: Boolean = false,
    /** Guest account. Sharing needs a permanent one, so its entry points are hidden entirely. */
    val isAnonymous: Boolean = false,
    /** This build ships sharing at all (#134). Off in the shipping release until GA. */
    val sharingSupported: Boolean = true,
  ) : AircraftOverviewUiState {
    /**
     * Owner-only affordances: Edit Aircraft, Delete, Manage Access. Technicians get a read-only
     * screen (they can still add maintenance). Server rules are the real enforcement (§6.3).
     */
    val canManageAircraft: Boolean get() = myRole != ShareRole.TECHNICIAN

    /**
     * Sharing is not available to a guest: redeeming and inviting both require a permanent account
     * (PRD F1), and a share must attach to an identity that survives a reinstall. Showing Manage
     * Access to a guest offers a door that only leads to a sign-in prompt.
     */
    val canOpenManageAccess: Boolean get() = sharingSupported && !isAnonymous
  }
}
