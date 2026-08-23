package dev.fanfly.wingslog.feature.notifications.engine

import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.core.model.settings.NotificationSettings
import dev.fanfly.wingslog.core.storage.AircraftScopeResolver
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetEntry
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.notifications.datamanager.NotificationPrefsManager
import dev.fanfly.wingslog.feature.notifications.datamanager.PrefsState
import dev.fanfly.wingslog.feature.notifications.model.NotificationChannel
import dev.fanfly.wingslog.feature.notifications.model.NotificationTapTarget
import dev.fanfly.wingslog.feature.notifications.model.PendingNotification
import dev.fanfly.wingslog.feature.notifications.model.UrgencyRank
import dev.fanfly.wingslog.feature.notifications.model.UrgencyTier
import dev.fanfly.wingslog.feature.notifications.model.allEnabled
import dev.fanfly.wingslog.feature.notifications.model.aogEnabled
import dev.fanfly.wingslog.feature.notifications.model.dueSoonEnabled
import dev.fanfly.wingslog.feature.notifications.model.overdueEnabled
import dev.fanfly.wingslog.feature.notifications.model.reportableTier
import dev.fanfly.wingslog.feature.notifications.model.squawkPriorityEnabled
import dev.fanfly.wingslog.feature.notifications.model.urgencyRank
import dev.fanfly.wingslog.feature.notifications.permission.NotificationPermission
import dev.fanfly.wingslog.feature.notifications.permission.PermissionState
import dev.fanfly.wingslog.feature.notifications.viewing.LocalNotifier
import dev.fanfly.wingslog.feature.squawk.model.toWithStatus
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager
import dev.gitlive.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import wingslog.feature.notifications.sharedassets.generated.resources.Res
import wingslog.feature.notifications.sharedassets.generated.resources.notification_body_due_soon_plural
import wingslog.feature.notifications.sharedassets.generated.resources.notification_body_due_soon_single
import wingslog.feature.notifications.sharedassets.generated.resources.notification_body_grounded_plural
import wingslog.feature.notifications.sharedassets.generated.resources.notification_body_grounded_single
import wingslog.feature.notifications.sharedassets.generated.resources.notification_body_overdue_plural
import wingslog.feature.notifications.sharedassets.generated.resources.notification_body_overdue_single
import wingslog.feature.notifications.sharedassets.generated.resources.notification_body_priority_raised_plural
import wingslog.feature.notifications.sharedassets.generated.resources.notification_body_priority_raised_single
import wingslog.feature.notifications.sharedassets.generated.resources.notification_title_due_soon
import wingslog.feature.notifications.sharedassets.generated.resources.notification_title_grounded
import wingslog.feature.notifications.sharedassets.generated.resources.notification_title_overdue
import wingslog.feature.notifications.sharedassets.generated.resources.notification_title_priority_raised
import wingslog.feature.notifications.sharedassets.generated.resources.squawk_priority_label_high
import wingslog.feature.notifications.sharedassets.generated.resources.squawk_priority_label_low
import wingslog.feature.notifications.sharedassets.generated.resources.squawk_priority_label_medium
import wingslog.feature.notifications.sharedassets.generated.resources.squawk_priority_label_resolved

/**
 * The entire N2 decision, in shared code (design §6.3). The one class that justifies `engine`'s
 * wide fan-in — everything it reads is read through an existing manager, and it never re-implements
 * due-status or priority logic of its own.
 *
 * One scan at a time: [mutex] makes a foreground scan arriving while a scheduled one runs wait
 * rather than double-report — reentrancy is real now that [UrgencyScanScheduler] can fire a
 * background scan while the app is being opened.
 */
class UrgencyScanner(
  private val auth: FirebaseAuth,
  private val prefsManager: NotificationPrefsManager,
  private val permission: NotificationPermission,
  private val fleetManager: FleetManager,
  private val scopeResolver: AircraftScopeResolver,
  private val taskDueManager: TaskDueManager,
  private val logManager: MaintenanceLogManager,
  entityStoreFactory: EntityStoreFactory,
  private val watermarkStore: UrgencyWatermarkStore,
  private val notifier: LocalNotifier,
) {

  private val mutex = Mutex()

  // Read straight from the entity layer, not TaskDataManager/SquawkManager — those decode away
  // StorageEntity.writerUid, which the seeding rule (design §6.4) needs and no domain manager
  // exposes for tasks or squawks (only MaintenanceLogManager.observeLogAuthors does, log-only).
  private val taskStore: EntityStore<MaintenanceTask> =
    entityStoreFactory.create(CollectionKind.MaintenanceTask)
  private val squawkStore: EntityStore<Squawk> =
    entityStoreFactory.create(CollectionKind.Squawk)

  suspend fun scan(trigger: ScanTrigger): ScanResult = mutex.withLock {
    val uid = auth.currentUser?.uid ?: return@withLock ScanResult.NoUser

    // .first() on each flow, on the caller's dispatcher: these are SQLDelight-backed flows, so this
    // reads the current value and detaches rather than staying subscribed for the scan's duration.
    val prefsState = prefsManager.observe()
      .first()
    if (prefsState !is PrefsState.Resolved) return@withLock ScanResult.PrefsUnresolved
    val settings = prefsState.settings
    if (!settings.allEnabled) return@withLock ScanResult.Disabled
    if (permission.observe().value != PermissionState.GRANTED) return@withLock ScanResult.NoPermission

    val fleet = fleetManager.observeFleetDashboard()
      .first()
    var posted = 0
    for (entry in fleet) {
      posted += scanAircraft(uid, entry, settings)
    }
    ScanResult.Completed(posted)
  }

  /** One aircraft's full cycle: rank every record, post crossings, then commit and prune. Returns the count posted. */
  private suspend fun scanAircraft(
    uid: String,
    entry: FleetEntry,
    settings: NotificationSettings,
  ): Int {
    val aircraftId = entry.aircraft.id
    val tailNumber = entry.aircraft.tail_number
    // Scope comes from the resolver, never the signed-in uid — a shared aircraft's records live in
    // the host's tree (design §6.3).
    val scope = scopeResolver.resolveNow(aircraftId)

    val existingWatermarks =
      watermarkStore.selectInScopePrefix(uid, scope.toPath() + "%")
    // Seeding (design §6.4): no watermark row anywhere under this scope means this device has never
    // scanned this aircraft before — every record seeds silently regardless of who wrote it.
    val aircraftKnown = existingWatermarks.isNotEmpty()
    val watermarkByKey =
      existingWatermarks.associateBy { it.collection to it.id }

    val taskRows = taskStore.observeAll(scope)
      .first()
    val logs = logManager.observeLogs(aircraftId)
      .first()
    val squawkRows = squawkStore.observeAll(scope)
      .first()
    val allTasks = taskRows.map { it.value }

    val crossings = mutableListOf<Crossing>()
    val taskCommits = mutableListOf<RecordRank>()
    for (row in taskRows) {
      val status =
        taskDueManager.computeNextDue(row.value, logs, allTasks).status
      val rank = status.urgencyRank()
      taskCommits += RecordRank(row.id, rank)
      val tier = status.reportableTier()
      val previousRank =
        crossingBaseline(
          uid,
          row.writerUid,
          rank,
          watermarkByKey[CollectionKind.MaintenanceTask to row.id],
          aircraftKnown
        )
      if (tier != null && previousRank != null) {
        crossings += Crossing(
          tier = tier,
          collection = CollectionKind.MaintenanceTask,
          recordId = row.id,
          title = row.value.title,
          tapTarget = NotificationTapTarget.Task(aircraftId, row.id),
        )
      }
    }

    val squawkCommits = mutableListOf<RecordRank>()
    for (row in squawkRows) {
      val withStatus = row.value.toWithStatus()
      val rank = withStatus.urgencyRank()
      squawkCommits += RecordRank(row.id, rank)
      val tier = withStatus.reportableTier()
      val previousRank =
        crossingBaseline(
          uid,
          row.writerUid,
          rank,
          watermarkByKey[CollectionKind.Squawk to row.id],
          aircraftKnown
        )
      if (tier != null && previousRank != null) {
        crossings += Crossing(
          tier = tier,
          collection = CollectionKind.Squawk,
          recordId = row.id,
          title = row.value.title,
          tapTarget = NotificationTapTarget.Squawk(aircraftId, row.id),
          previousRank = previousRank,
        )
      }
    }

    // Drop crossings whose tier is switched off in prefs (design §6.3 step 6).
    val reportable = crossings.filter { settings.tierEnabled(it.tier) }

    // At most one notification per (aircraft, tier) — group into a summary once there is more than
    // one crossing (design §6.5).
    val notifications = reportable.groupBy { it.tier }
      .map { (tier, group) ->
        buildNotification(
          aircraftId,
          tailNumber,
          tier,
          group
        )
      }

    // Post, then commit (design §6.7): a duplicate notification if the process dies here is a minor
    // annoyance, a dropped one is the failure this feature exists to prevent.
    notifications.forEach { notifier.post(it) }

    for (commit in taskCommits) {
      watermarkStore.upsert(
        uid,
        CollectionKind.MaintenanceTask,
        scope,
        commit.id,
        commit.rank.value
      )
    }
    watermarkStore.pruneNotIn(
      uid,
      CollectionKind.MaintenanceTask,
      scope,
      taskCommits.map { it.id })

    for (commit in squawkCommits) {
      watermarkStore.upsert(
        uid,
        CollectionKind.Squawk,
        scope,
        commit.id,
        commit.rank.value
      )
    }
    watermarkStore.pruneNotIn(
      uid,
      CollectionKind.Squawk,
      scope,
      squawkCommits.map { it.id })

    return notifications.size
  }

  /**
   * The rank this record's crossing is *against* this scan, or `null` if it isn't a reportable
   * crossing at all (design §6.3 step 5, folded together with §6.4's seeding rule — both are the
   * same "what do we compare [rank] against" question):
   *
   * - An existing watermark row → the normal diff, against its stored rank.
   * - No row, and the whole aircraft has never been scanned on this device → always `null`: every
   *   record on a newly-seen aircraft seeds silently, no exceptions.
   * - No row, but the aircraft is known (a brand-new id) → `writerUid == uid` seeds silently at the
   *   current rank (you filed it, you know); anyone else or an unknown writer seeds at rank 0, so an
   *   already-urgent record reports on this same scan — the refinement PRD §6.4 needs so a
   *   collaborator's new AOG squawk isn't seeded silently on the owner's device.
   */
  private fun crossingBaseline(
    uid: String,
    writerUid: String?,
    rank: UrgencyRank,
    existing: UrgencyWatermark?,
    aircraftKnown: Boolean,
  ): UrgencyRank? {
    val baseline = when {
      existing != null -> UrgencyRank(existing.rank)
      !aircraftKnown -> return null
      writerUid == uid -> return null
      else -> UrgencyRank.RESOLVED
    }
    return if (rank > baseline) baseline else null
  }

  private fun NotificationSettings.tierEnabled(tier: UrgencyTier): Boolean =
    when (tier) {
      UrgencyTier.GROUNDED -> aogEnabled
      UrgencyTier.PRIORITY_RAISED -> squawkPriorityEnabled
      UrgencyTier.OVERDUE -> overdueEnabled
      UrgencyTier.DUE_SOON -> dueSoonEnabled
    }

  private suspend fun buildNotification(
    aircraftId: String,
    tailNumber: String,
    tier: UrgencyTier,
    group: List<Crossing>,
  ): PendingNotification {
    val single = group.singleOrNull()
    val title = getString(tier.titleRes())
    val body: String
    // Deterministic ids so a re-scan replaces rather than stacks (design §6.5): a summary is keyed
    // by (aircraft, tier) since it has no one record to point at, but a single crossing is keyed by
    // (collection, recordId) specifically — NOT (aircraft, tier) — so a later scan's single crossing
    // for a *different* record in the same tier gets its own tray slot instead of silently
    // overwriting a still-unread notification about the first one.
    val id: String
    if (single != null) {
      body = buildSingleBody(tier, tailNumber, single)
      id = "urgency:${single.collection.wireName}:${single.recordId}"
    } else {
      body = getString(tier.pluralBodyRes(), tailNumber, group.size)
      id = "urgency:$aircraftId:${tier.name}"
    }
    return PendingNotification(
      id = id,
      channel = if (tier == UrgencyTier.GROUNDED) NotificationChannel.GROUNDED else NotificationChannel.URGENCY_UPDATE,
      title = title,
      body = body,
      highPriority = tier == UrgencyTier.GROUNDED || tier == UrgencyTier.OVERDUE,
      tapTarget = single?.tapTarget
        ?: NotificationTapTarget.Aircraft(aircraftId, tab = tier.toAircraftTab()),
    )
  }

  // Three lines — tail + what changed, the record's own (possibly long) title on its own line, then
  // a tap hint — rather than folding a user-authored title into one run-on sentence.
  private suspend fun buildSingleBody(
    tier: UrgencyTier,
    tailNumber: String,
    crossing: Crossing
  ): String =
    if (tier == UrgencyTier.PRIORITY_RAISED) {
      val fromLabel = getString(
        (crossing.previousRank ?: UrgencyRank.RESOLVED).squawkPriorityLabelRes()
      )
      // PRIORITY_RAISED only ever reports at HIGH (reportableTier() in :model) — AOG has its own
      // Grounded tier — so the "to" side of "from X to Y" is always HIGH, never computed per-crossing.
      val toLabel = getString(UrgencyRank(3).squawkPriorityLabelRes())
      getString(
        tier.singleBodyRes(),
        tailNumber,
        fromLabel,
        toLabel,
        crossing.title
      )
    } else {
      getString(tier.singleBodyRes(), tailNumber, crossing.title)
    }

  private fun UrgencyRank.squawkPriorityLabelRes(): StringResource =
    when (value) {
      0 -> Res.string.squawk_priority_label_resolved
      1 -> Res.string.squawk_priority_label_low
      2 -> Res.string.squawk_priority_label_medium
      else -> Res.string.squawk_priority_label_high
    }

  // NotificationTapTarget.Aircraft.tab wire values (design §5.3 / P2.9) — a summary notification
  // with no single record to point at still tells NotificationTapRouter which shell section to land
  // on (PRD §6.6: "tapping a summary opens that aircraft's task list filtered to the tier"). Plain
  // strings, not ShellSection directly: :engine cannot depend on core:ui:adaptive, and
  // AdaptiveShellViewModel is what actually interprets these.
  private fun UrgencyTier.toAircraftTab(): String = when (this) {
    UrgencyTier.GROUNDED, UrgencyTier.PRIORITY_RAISED -> "squawks"
    UrgencyTier.OVERDUE, UrgencyTier.DUE_SOON -> "tasks"
  }

  private fun UrgencyTier.titleRes() = when (this) {
    UrgencyTier.GROUNDED -> Res.string.notification_title_grounded
    UrgencyTier.PRIORITY_RAISED -> Res.string.notification_title_priority_raised
    UrgencyTier.OVERDUE -> Res.string.notification_title_overdue
    UrgencyTier.DUE_SOON -> Res.string.notification_title_due_soon
  }

  private fun UrgencyTier.singleBodyRes() = when (this) {
    UrgencyTier.GROUNDED -> Res.string.notification_body_grounded_single
    UrgencyTier.PRIORITY_RAISED -> Res.string.notification_body_priority_raised_single
    UrgencyTier.OVERDUE -> Res.string.notification_body_overdue_single
    UrgencyTier.DUE_SOON -> Res.string.notification_body_due_soon_single
  }

  private fun UrgencyTier.pluralBodyRes() = when (this) {
    UrgencyTier.GROUNDED -> Res.string.notification_body_grounded_plural
    UrgencyTier.PRIORITY_RAISED -> Res.string.notification_body_priority_raised_plural
    UrgencyTier.OVERDUE -> Res.string.notification_body_overdue_plural
    UrgencyTier.DUE_SOON -> Res.string.notification_body_due_soon_plural
  }

  private data class RecordRank(val id: String, val rank: UrgencyRank)

  private data class Crossing(
    val tier: UrgencyTier,
    val collection: CollectionKind,
    val recordId: String,
    val title: String,
    val tapTarget: NotificationTapTarget,
    val previousRank: UrgencyRank? = null,
  )
}
