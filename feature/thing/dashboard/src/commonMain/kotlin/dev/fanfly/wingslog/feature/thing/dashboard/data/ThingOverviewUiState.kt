package dev.fanfly.wingslog.feature.thing.dashboard.data

import dev.fanfly.wingslog.core.template.DegradedReason
import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.Squawk
import dev.fanfly.wingslog.thing.Thing

data class LogStats(
  val total: Long,
  val airframe: Long,
  val engine: Long,
  val propeller: Long,
  val currentEngineTime: Double? = null,
  val currentAirframeTime: Double? = null,
  val currentPropTime: Double? = null,
) {
  /**
   * The current reading for a meter key, or null when nothing stores one.
   *
   * The three fields above are the only readings that exist: a log carries `airframe_time`,
   * `engine_hour` and `prop_time` and nothing else, so a template declaring an odometer has no
   * source here. Null rather than 0.0 on purpose — an invented zero renders as a real measurement.
   * #730 replaces this mapping with per-meter-key readings on the log itself.
   */
  fun valueFor(meterKey: String): Double? = when (meterKey) {
    MeterKeys.AIRFRAME_HOURS -> currentAirframeTime
    MeterKeys.ENGINE_HOURS -> currentEngineTime
    MeterKeys.PROP_HOURS -> currentPropTime
    else -> null
  }
}

sealed interface ThingOverviewUiState {
  data object Loading : ThingOverviewUiState
  data object Error : ThingOverviewUiState

  /**
   * DNA this build cannot interpret (`template_system_design.md` §6.2).
   *
   * Distinct from [Error], which means the Thing did not load. This one *did* load — it is intact,
   * it stays in the switcher, and it counts against the fleet limit. What is missing is the code to
   * render it, so it shows the [thing]'s raw spec rather than a fallback template's labels.
   */
  data class Degraded(
    val thing: Thing,
    val reason: DegradedReason,
  ) : ThingOverviewUiState

  data class Success(
    val thing: Thing,
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
    /** Caller's role on this thing; drives owner-only gating. `null` while it resolves. */
    val myRole: ShareRole? = null,
    /**
     * True when this thing lives in another account's fleet and was shared into ours. Not the
     * same as [myRole]: a co-owner of someone else's thing is `OWNER` *and* shared.
     */
    val shared: Boolean = false,
    /** Guest account. Sharing needs a permanent one, so its entry points are hidden entirely. */
    val isAnonymous: Boolean = false,
  ) : ThingOverviewUiState {
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
    val canOpenManageAccess: Boolean get() = !isAnonymous
  }
}
