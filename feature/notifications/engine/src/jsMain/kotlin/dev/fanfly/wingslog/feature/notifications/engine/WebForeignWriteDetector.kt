package dev.fanfly.wingslog.feature.notifications.engine

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.ForeignWriteListener
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.notifications.datamanager.NotificationPrefsManager
import dev.fanfly.wingslog.feature.notifications.datamanager.PrefsState
import dev.fanfly.wingslog.feature.notifications.engine.WebForeignWriteDetector.Companion.ROSTER_READ_TIMEOUT
import dev.fanfly.wingslog.feature.notifications.model.NotificationChannel
import dev.fanfly.wingslog.feature.notifications.model.NotificationTapTarget
import dev.fanfly.wingslog.feature.notifications.model.PendingNotification
import dev.fanfly.wingslog.feature.notifications.model.allEnabled
import dev.fanfly.wingslog.feature.notifications.model.collaborationEnabled
import dev.fanfly.wingslog.feature.notifications.permission.NotificationPermission
import dev.fanfly.wingslog.feature.notifications.permission.PermissionState
import dev.fanfly.wingslog.feature.notifications.viewing.LocalNotifier
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import wingslog.feature.notifications.sharedassets.generated.resources.Res
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_actor_fallback
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_body_plural
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_body_single
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_section_logbook
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_section_logbook_lower
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_section_squawks
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_section_squawks_lower
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_section_tasks
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_section_tasks_lower
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_title
import kotlin.time.Duration.Companion.seconds

/**
 * N1 on web, with no backend at all (design §8).
 *
 * An open tab already runs the sync engine, so it already receives a collaborator's write the
 * instant Firestore delivers it, and `RemoteEntity.writerUid` carries rules-enforced authorship on
 * the envelope. A write we applied, authored by someone else, on an aircraft that is part of a
 * share, **is** an N1 event — the same test the server-side trigger makes, run locally.
 *
 * **Bound in `jsMain` only.** Android and iOS receive N1 by push; running both paths would
 * double-notify. That is also why this lives in `engine` rather than `viewing`: deciding whether an
 * event deserves a notification is the scanner's kind of job, not the notifier's.
 *
 * Ordering note: the share check and the actor read are suspending, so writes are handled on
 * [scope] rather than inline — the sync engine must not wait on a notification decision. Each write
 * is an independent launch, so a slow roster read cannot delay the next one.
 */
class WebForeignWriteDetector(
  private val sharingManager: SharingManager,
  private val fleetManager: FleetManager,
  private val prefsManager: NotificationPrefsManager,
  private val permission: NotificationPermission,
  private val notifier: LocalNotifier,
  private val counter: ActivityCounter = ActivityCounter(),
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ForeignWriteListener {

  override fun onForeignWrite(
    kind: CollectionKind,
    scope: EntityScope,
    id: String,
    writerUid: String,
  ) {
    val recordType = kind.toRecordType()
    if (recordType == null) {
      log.d { "N1 skipped: ${kind.wireName} is not collaboration activity" }
      return
    }
    val aircraftId = scope.aircraftIdOrNull()
    if (aircraftId == null) {
      log.d { "N1 skipped: ${scope.toPath()} is not an aircraft scope" }
      return
    }
    log.d { "N1 foreign write: ${kind.wireName}/$id on $aircraftId by $writerUid" }
    this.scope.launch {
      runCatching { handle(recordType, aircraftId, writerUid) }
        .onFailure { log.w(it) { "N1 detection failed for ${kind.wireName}/$id" } }
    }
  }

  private suspend fun handle(
    recordType: RecordType,
    aircraftId: String,
    actorUid: String
  ) {
    // Preferences and OS permission first: both are local reads, and there is no point paying for a
    // roster read to build a notification nobody will see.
    val prefs = prefsManager.observe()
      .first()
    if (prefs !is PrefsState.Resolved) {
      log.d { "N1 skipped: preferences unresolved" }
      return
    }
    if (!prefs.settings.allEnabled) {
      log.d { "N1 skipped: all notifications off" }
      return
    }
    if (!prefs.settings.collaborationEnabled) {
      log.d { "N1 skipped: collaboration activity off in preferences" }
      return
    }
    val permissionState = permission.observe().value
    if (permissionState != PermissionState.GRANTED) {
      log.d { "N1 skipped: OS permission is $permissionState" }
      return
    }

    // ONE roster read, used for both the share test and the actor name below.
    //
    // Deliberately not `observeIsShared(acId).first()`: that flow combines the local ref with an
    // online roster listener seeded `onStart { emit(false) }` so a combine cannot hang offline. For
    // a member the ref half is true immediately, but for the **host** — who has no ref to their own
    // aircraft — the first emission is the seed, so `.first()` returns false before Firestore ever
    // answers. N1 would then fire for members and never for owners, which is backwards: an owner
    // hearing about their mechanic's edits is the case the feature exists for.
    val roster = withTimeoutOrNull(ROSTER_READ_TIMEOUT) {
      sharingManager.observeShareState(aircraftId)
        .first()
    }
    if (roster == null) {
      // Offline, or the roster listener never answered. Staying silent is the safe direction: the
      // alternative is notifying about an aircraft we cannot confirm is shared.
      log.d { "N1 skipped: roster read timed out for $aircraftId" }
      return
    }
    // More than one member means a share exists — true for the host and every member alike.
    if (roster.members.size <= 1) {
      log.d { "N1 skipped: $aircraftId has ${roster.members.size} member(s), accessDenied=${roster.accessDenied}" }
      return
    }

    val post = counter.record(
      ActivityKey(
        aircraftId = aircraftId,
        recordType = recordType.wire,
        actorUid = actorUid,
      )
    )
    if (post == null) {
      log.d { "N1 counted but throttled (within ${ActivityCounter.MIN_REPOST_INTERVAL})" }
      return
    }

    val actor = roster.members.firstOrNull { it.uid == actorUid }
      ?.displayName
      ?.takeIf { it.isNotBlank() }
      ?: getString(Res.string.notification_n1_actor_fallback)
    log.i { "N1 posting: $aircraftId ${recordType.wire} x${post.changeCount} by $actor" }
    // Built and posted in two statements rather than one nested call, so these two log lines sit on
    // either side of the suspending build. A build that never returns used to look identical to a
    // notification that was posted and not drawn — the log said "posting", and nothing followed.
    val notification =
      buildNotification(recordType, aircraftId, actorUid, actor, post)
    log.d { "N1 built ${notification.id}, handing to the notifier" }
    notifier.post(notification)
    log.d { "N1 posted ${notification.id}" }
  }

  private suspend fun buildNotification(
    recordType: RecordType,
    aircraftId: String,
    /** The stable id the tag is keyed on — a display name can change mid-session. */
    actorUid: String,
    /** What the body says. */
    actor: String,
    post: ActivityPost,
  ): PendingNotification {
    val tailNumber = tailNumberOf(aircraftId)
    // Between this and "N1 built" there is nothing but string resource loads, so the pair of lines
    // says which half of the build is slow or stuck without another round of guessing.
    log.d { "N1 tail number resolved for $aircraftId, rendering strings" }
    val body =
      if (post.changeCount == 1) {
        getString(
          Res.string.notification_n1_body_single,
          actor,
          getString(recordType.lowerLabel)
        )
      } else {
        getString(
          Res.string.notification_n1_body_plural,
          actor,
          post.changeCount,
          getString(recordType.lowerLabel),
        )
      }
    return PendingNotification(
      // The tag §8.4 specifies. sessionStart is what rolls it when a working session ends, so a
      // finished session's tray entry is left alone instead of being overwritten by the next one.
      id = "n1:$aircraftId:${recordType.wire}:$actorUid:${post.sessionStart.toEpochMilliseconds()}",
      channel = NotificationChannel.COLLABORATION,
      title = getString(
        Res.string.notification_n1_title,
        tailNumber,
        getString(recordType.titleLabel),
      ),
      body = body,
      // Collaboration activity is never high priority — that is what N2's urgency tiers are for,
      // and §7.3 is explicit that an activity summary must never replace a grounding alert.
      highPriority = false,
      tapTarget = NotificationTapTarget.Aircraft(
        aircraftId,
        tab = recordType.tab
      ),
    )
  }

  /**
   * Falls back to the id, which is never shown in practice — the fleet always has the aircraft a
   * write arrived for.
   *
   * **Bounded, for the same reason the roster read above is.** `observeFleetDashboard()` is
   * `authStateChanged.flatMapLatest { combine(ownAircraft, sharedAircraft) }`, and a `combine`
   * emits nothing until every source has emitted once — so a single source that never answers makes
   * `.first()` suspend rather than fail. `runCatching` does not help with that: there is no
   * exception, just a coroutine that never returns, and since this is evaluated as the argument to
   * `notifier.post(...)` the notification is silently never posted. A title reading as the aircraft
   * id is a bad title; no notification at all is a lost one.
   */
  private suspend fun tailNumberOf(aircraftId: String): String =
    runCatching {
      withTimeoutOrNull(FLEET_READ_TIMEOUT) {
        fleetManager.observeFleetDashboard()
          .first()
          .firstOrNull { it.aircraft.id == aircraftId }
          ?.aircraft
          ?.tail_number
      } ?: run {
        log.w { "N1 tail number read timed out for $aircraftId; falling back to the id" }
        null
      }
    }.getOrNull() ?: aircraftId

  /** The three record types §8 treats as collaboration activity. Anything else is not N1. */
  private enum class RecordType(
    val wire: String,
    val tab: String,
    val titleLabel: StringResource,
    val lowerLabel: StringResource,
  ) {
    SQUAWK(
      "squawk",
      "squawks",
      Res.string.notification_n1_section_squawks,
      Res.string.notification_n1_section_squawks_lower,
    ),
    TASK(
      "task",
      "tasks",
      Res.string.notification_n1_section_tasks,
      Res.string.notification_n1_section_tasks_lower,
    ),
    LOG(
      "log",
      "logs",
      Res.string.notification_n1_section_logbook,
      Res.string.notification_n1_section_logbook_lower,
    ),
  }

  private fun CollectionKind.toRecordType(): RecordType? = when (this) {
    CollectionKind.Squawk -> RecordType.SQUAWK
    CollectionKind.MaintenanceTask -> RecordType.TASK
    CollectionKind.MaintenanceLog -> RecordType.LOG
    // Aircraft edits, overviews, technicians and the rest are not collaboration *activity* in the
    // sense §8 means — no settings toggle covers them, so notifying would be unmutable.
    else -> null
  }

  /**
   * Per-aircraft scopes are `/users/{hostUid}/aircraft/{acId}/`, so the id is the fourth segment.
   * A top-level scope has no aircraft and is not N1.
   */
  private fun EntityScope.aircraftIdOrNull(): String? =
    segments.takeIf { it.size >= 4 && it[2] == "aircraft" }
      ?.get(3)

  private companion object {
    val log = Logger.withTag("WebForeignWriteDetector")

    /** The roster is an online-only listener; offline it never answers, so the read is bounded. */
    val ROSTER_READ_TIMEOUT = 5.seconds

    /** Same hazard as [ROSTER_READ_TIMEOUT], on a flow that only decorates the title. */
    val FLEET_READ_TIMEOUT = 5.seconds
  }
}
