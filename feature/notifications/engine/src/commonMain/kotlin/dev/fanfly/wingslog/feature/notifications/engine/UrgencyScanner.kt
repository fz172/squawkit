package dev.fanfly.wingslog.feature.notifications.engine

import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.core.model.settings.NotificationSettings
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetEntry
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.logs.datamanager.MaintenanceLogManager
import dev.fanfly.wingslog.feature.notifications.analytics.UrgencyTelemetry
import dev.fanfly.wingslog.feature.notifications.datamanager.NotificationPrefsManager
import dev.fanfly.wingslog.feature.notifications.datamanager.PrefsState
import dev.fanfly.wingslog.feature.notifications.model.NotificationChannel
import dev.fanfly.wingslog.feature.notifications.model.NotificationTapTarget
import dev.fanfly.wingslog.feature.notifications.model.PendingNotification
import dev.fanfly.wingslog.feature.notifications.model.ScanTrigger
import dev.fanfly.wingslog.feature.notifications.model.UrgencyRank
import dev.fanfly.wingslog.feature.notifications.model.UrgencyTier
import dev.fanfly.wingslog.feature.notifications.model.allEnabled
import dev.fanfly.wingslog.feature.notifications.model.priorityDueEnabled
import dev.fanfly.wingslog.feature.notifications.model.reportableTier
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
import wingslog.feature.notifications.sharedassets.generated.resources.notification_body_overdue_plural
import wingslog.feature.notifications.sharedassets.generated.resources.notification_body_overdue_single
import wingslog.feature.notifications.sharedassets.generated.resources.notification_body_priority_raised_plural
import wingslog.feature.notifications.sharedassets.generated.resources.notification_body_priority_raised_single
import wingslog.feature.notifications.sharedassets.generated.resources.notification_title_due_soon
import wingslog.feature.notifications.sharedassets.generated.resources.notification_title_overdue
import wingslog.feature.notifications.sharedassets.generated.resources.notification_title_priority_raised
import wingslog.feature.notifications.sharedassets.generated.resources.squawk_priority_label_aog
import wingslog.feature.notifications.sharedassets.generated.resources.squawk_priority_label_high
import wingslog.feature.notifications.sharedassets.generated.resources.squawk_priority_label_low
import wingslog.feature.notifications.sharedassets.generated.resources.squawk_priority_label_medium
import wingslog.feature.notifications.sharedassets.generated.resources.squawk_priority_label_resolved
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

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
  private val scopeResolver: ThingScopeResolver,
  private val taskDueManager: TaskDueManager,
  private val logManager: MaintenanceLogManager,
  entityStoreFactory: EntityStoreFactory,
  private val watermarkStore: UrgencyWatermarkStore,
  private val notifier: LocalNotifier,
  private val lastScanStore: LastScanStore,
  private val telemetry: UrgencyTelemetry = UrgencyTelemetry.NoOp,
  private val clock: Clock = Clock.System,
  private val sessionDebounce: Duration = SESSION_SCAN_DEBOUNCE,
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

    // Only the session trigger debounces (design §6.6): an app opened repeatedly should not rescan
    // every time, while the periodic job is already spaced at the cadence this would enforce and a
    // manual scan is someone explicitly asking. Checked before prefs so a debounced boundary costs
    // one config read rather than a full fleet walk.
    if (trigger == ScanTrigger.SESSION_BOUNDARY) {
      val last = lastScanStore.lastScanAt(uid)
      if (last != null && clock.now() - last < sessionDebounce) return@withLock ScanResult.Debounced
    }

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
    var tally = Tally()
    for (entry in fleet) {
      tally += scanThing(uid, entry, settings)
    }
    // Recorded for every completed scan whatever the trigger, not just session ones: a scheduled
    // scan that just walked the fleet is exactly what the next session boundary should debounce
    // against. The early exits above deliberately do not record — they did no work, so there is
    // nothing to space out, and re-checking them on the next boundary costs a config read.
    // Reported before the record is written so a crash between the two loses the diagnostic, not
    // the metric — the metric is the one that cannot be reconstructed later.
    telemetry.urgencyNotificationsPosted(
      trigger = trigger,
      count = tally.posted,
      sharedFleet = fleet.any { it.shared },
    )
    lastScanStore.record(
      uid,
      ScanRecord(
        at = clock.now(),
        trigger = trigger,
        recordsExamined = tally.examined,
        crossingsFound = tally.crossings,
        crossingsSuppressed = tally.suppressed,
        notificationsPosted = tally.posted,
      ),
    )
    ScanResult.Completed(tally.posted)
  }

  /** Per-thing counts, summed across the fleet for design §11's diagnostics. */
  private data class Tally(
    val examined: Int = 0,
    val crossings: Int = 0,
    val suppressed: Int = 0,
    val posted: Int = 0,
  ) {
    operator fun plus(other: Tally) = Tally(
      examined = examined + other.examined,
      crossings = crossings + other.crossings,
      suppressed = suppressed + other.suppressed,
      posted = posted + other.posted,
    )
  }

  /** One thing's full cycle: rank every record, post crossings, then commit and prune. */
  private suspend fun scanThing(
    uid: String,
    entry: FleetEntry,
    settings: NotificationSettings,
  ): Tally {
    val thingId = entry.thing.id
    val tailNumber = entry.thing.tail_number
    // Scope comes from the resolver, never the signed-in uid — a shared thing's records live in
    // the host's tree (design §6.3).
    val scope = scopeResolver.resolveNow(thingId)

    val existingWatermarks =
      watermarkStore.selectInScopePrefix(uid, scope.toPath() + "%")
    // Seeding (design §6.4): no watermark row anywhere under this scope means this device has never
    // scanned this thing before — every record seeds silently regardless of who wrote it.
    val aircraftKnown = existingWatermarks.isNotEmpty()
    val watermarkByKey =
      existingWatermarks.associateBy { it.collection to it.id }

    val taskRows = taskStore.observeAll(scope)
      .first()
    val logs = logManager.observeLogs(thingId)
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
          tapTarget = NotificationTapTarget.Task(thingId, row.id),
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
          tapTarget = NotificationTapTarget.Squawk(thingId, row.id),
          previousRank = previousRank,
          newRank = rank,
        )
      }
    }

    // Drop crossings if priority/due updates are switched off in prefs (design §6.3 step 6). One
    // flag now covers all three tiers (design decision, 2026-08-26).
    val reportable = if (settings.priorityDueEnabled) crossings else emptyList()

    // At most one notification per (thing, tier) — group into a summary once there is more than
    // one crossing (design §6.5).
    val notifications = reportable.groupBy { it.tier }
      .map { (tier, group) ->
        buildNotification(
          thingId,
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

    return Tally(
      examined = taskRows.size + squawkRows.size,
      crossings = crossings.size,
      suppressed = crossings.size - reportable.size,
      posted = notifications.size,
    )
  }

  /**
   * The rank this record's crossing is *against* this scan, or `null` if it isn't a reportable
   * crossing at all (design §6.3 step 5, folded together with §6.4's seeding rule — both are the
   * same "what do we compare [rank] against" question):
   *
   * - An existing watermark row → the normal diff, against its stored rank.
   * - No row, and the whole thing has never been scanned on this device → always `null`: every
   *   record on a newly-seen thing seeds silently, no exceptions.
   * - No row, but the thing is known (a brand-new id) → `writerUid == uid` seeds silently at the
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

  private suspend fun buildNotification(
    thingId: String,
    tailNumber: String,
    tier: UrgencyTier,
    group: List<Crossing>,
  ): PendingNotification {
    val single = group.singleOrNull()
    val title = getString(tier.titleRes())
    val body: String
    // Deterministic ids so a re-scan replaces rather than stacks (design §6.5): a summary is keyed
    // by (thing, tier) since it has no one record to point at, but a single crossing is keyed by
    // (collection, recordId) specifically — NOT (thing, tier) — so a later scan's single crossing
    // for a *different* record in the same tier gets its own tray slot instead of silently
    // overwriting a still-unread notification about the first one.
    val id: String
    if (single != null) {
      body = buildSingleBody(tier, tailNumber, single)
      id = "urgency:${single.collection.wireName}:${single.recordId}"
    } else {
      body = getString(tier.pluralBodyRes(), tailNumber, group.size)
      id = "urgency:$thingId:${tier.name}"
    }
    return PendingNotification(
      id = id,
      channel = NotificationChannel.URGENCY_UPDATE,
      title = title,
      body = body,
      highPriority = tier == UrgencyTier.OVERDUE,
      tapTarget = single?.tapTarget
        ?: NotificationTapTarget.Aircraft(
          thingId,
          tab = tier.toAircraftTab()
        ),
    )
  }

  // Two paragraphs — tail + what changed, then the record's own (possibly long) title on its own
  // line — rather than folding a user-authored title into one run-on sentence. No tap hint: every
  // notification is tappable, so saying so on each one is noise.
  private suspend fun buildSingleBody(
    tier: UrgencyTier,
    tailNumber: String,
    crossing: Crossing
  ): String =
    if (tier == UrgencyTier.PRIORITY_RAISED) {
      val fromLabel = getString(
        (crossing.previousRank ?: UrgencyRank.RESOLVED).squawkPriorityLabelRes()
      )
      // Computed per-crossing, not assumed: PRIORITY_RAISED reports at HIGH or AOG now that AOG
      // folds into it (design decision, 2026-08-26), so "to" can no longer be hardcoded to HIGH.
      // Only squawk crossings ever carry this tier, so newRank is always set here.
      val toLabel = getString(
        checkNotNull(crossing.newRank) { "PRIORITY_RAISED crossing with no newRank" }
          .squawkPriorityLabelRes()
      )
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
      3 -> Res.string.squawk_priority_label_high
      else -> Res.string.squawk_priority_label_aog
    }

  // NotificationTapTarget.Aircraft.tab wire values (design §5.3 / P2.9) — a summary notification
  // with no single record to point at still tells NotificationTapRouter which shell section to land
  // on (PRD §6.6: "tapping a summary opens that aircraft's task list filtered to the tier"). Plain
  // strings, not ShellSection directly: :engine cannot depend on core:ui:adaptive, and
  // AdaptiveShellViewModel is what actually interprets these.
  private fun UrgencyTier.toAircraftTab(): String = when (this) {
    UrgencyTier.PRIORITY_RAISED -> "squawks"
    UrgencyTier.OVERDUE, UrgencyTier.DUE_SOON -> "tasks"
  }

  private fun UrgencyTier.titleRes() = when (this) {
    UrgencyTier.PRIORITY_RAISED -> Res.string.notification_title_priority_raised
    UrgencyTier.OVERDUE -> Res.string.notification_title_overdue
    UrgencyTier.DUE_SOON -> Res.string.notification_title_due_soon
  }

  private fun UrgencyTier.singleBodyRes() = when (this) {
    UrgencyTier.PRIORITY_RAISED -> Res.string.notification_body_priority_raised_single
    UrgencyTier.OVERDUE -> Res.string.notification_body_overdue_single
    UrgencyTier.DUE_SOON -> Res.string.notification_body_due_soon_single
  }

  private fun UrgencyTier.pluralBodyRes() = when (this) {
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
    /**
     * The rank this crossing landed at — only set for squawks. `PRIORITY_RAISED` no longer reports
     * exclusively at HIGH now that AOG folds into it (design decision, 2026-08-26), so
     * [buildSingleBody] needs the actual landing rank per crossing rather than assuming HIGH.
     */
    val newRank: UrgencyRank? = null,
  )

  companion object {
    /**
     * Design §6.6's target cadence, superseding the earlier 4h — the scan is N2's only detection
     * mechanism, so spacing session scans further apart is pure added latency.
     */
    val SESSION_SCAN_DEBOUNCE: Duration = 2.hours
  }
}
