package dev.fanfly.wingslog.feature.notifications.engine

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.ForeignWriteListener
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.notifications.datamanager.NotificationPrefsManager
import dev.fanfly.wingslog.feature.notifications.datamanager.PrefsState
import dev.fanfly.wingslog.feature.notifications.model.NotificationChannel
import dev.fanfly.wingslog.feature.notifications.model.NotificationTapTarget
import dev.fanfly.wingslog.feature.notifications.model.PendingNotification
import dev.fanfly.wingslog.feature.notifications.model.allEnabled
import dev.fanfly.wingslog.feature.notifications.permission.NotificationPermission
import dev.fanfly.wingslog.feature.notifications.permission.PermissionState
import dev.fanfly.wingslog.feature.notifications.viewing.LocalNotifier
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    val recordType = kind.toRecordType() ?: return
    val aircraftId = scope.aircraftIdOrNull() ?: return
    this.scope.launch {
      runCatching { handle(recordType, aircraftId, writerUid) }
        .onFailure { log.w(it) { "N1 detection failed for ${kind.wireName}/$id" } }
    }
  }

  private suspend fun handle(recordType: RecordType, aircraftId: String, actorUid: String) {
    // Preferences and OS permission first: both are local reads, and there is no point paying for a
    // roster read to build a notification nobody will see.
    val prefs = prefsManager.observe()
      .first()
    if (prefs !is PrefsState.Resolved) return
    if (!prefs.settings.allEnabled) return
    if (!recordType.enabledIn(prefs)) return
    if (permission.observe().value != PermissionState.GRANTED) return

    // "Part of a share at all" — true for the hosting owner too, not just a member, which is what
    // makes an owner hear about their mechanic's edits.
    if (!sharingManager.observeIsShared(aircraftId).first()) return

    val post = counter.record(
      ActivityKey(
        aircraftId = aircraftId,
        recordType = recordType.wire,
        actorUid = actorUid,
      )
    ) ?: return

    notifier.post(buildNotification(recordType, aircraftId, actorUid, post))
  }

  private suspend fun buildNotification(
    recordType: RecordType,
    aircraftId: String,
    actorUid: String,
    post: ActivityPost,
  ): PendingNotification {
    val tailNumber = tailNumberOf(aircraftId)
    val actor = actorName(aircraftId, actorUid)
    val body =
      if (post.changeCount == 1) {
        getString(Res.string.notification_n1_body_single, actor, getString(recordType.lowerLabel))
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
      tapTarget = NotificationTapTarget.Aircraft(aircraftId, tab = recordType.tab),
    )
  }

  /**
   * One-shot roster read at the moment a notification fires (design §8.3), not a standing
   * subscription: a per-aircraft listener would need its own lifecycle — opened when the user has
   * any shared aircraft, torn down on sign-out or when a share ends, kept from leaking across
   * account switches — for a value needed only at this instant.
   */
  private suspend fun actorName(aircraftId: String, actorUid: String): String =
    runCatching {
      sharingManager.observeShareState(aircraftId)
        .first()
        .members
        .firstOrNull { it.uid == actorUid }
        ?.displayName
        ?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: getString(Res.string.notification_n1_actor_fallback)

  /** Falls back to the id, which is never shown in practice — the fleet always has the aircraft a write arrived for. */
  private suspend fun tailNumberOf(aircraftId: String): String =
    runCatching {
      fleetManager.observeFleetDashboard()
        .first()
        .firstOrNull { it.aircraft.id == aircraftId }
        ?.aircraft
        ?.tail_number
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
    ;

    fun enabledIn(prefs: PrefsState.Resolved): Boolean = when (this) {
      SQUAWK -> !prefs.settings.squawk_activity_disabled
      TASK -> !prefs.settings.task_activity_disabled
      LOG -> !prefs.settings.log_activity_disabled
    }
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
  }
}
