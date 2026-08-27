package dev.fanfly.wingslog.feature.notifications.viewing

import dev.fanfly.wingslog.feature.notifications.model.PendingNotification
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import wingslog.feature.notifications.sharedassets.generated.resources.Res
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_actor_fallback
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_body_aircraft_updated
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_body_record_created
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_body_record_deleted
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_body_record_updated
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_body_squawk_created
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_body_squawk_raised
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_noun_log
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_noun_squawk
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_noun_task
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_section_aircraft
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_section_logbook
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_section_squawks
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_section_tasks
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_title
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_title_squawk_created
import wingslog.feature.notifications.sharedassets.generated.resources.notification_title_priority_raised

/**
 * Turns a decoded [PushPayload] into a [PendingNotification] a [LocalNotifier] can post.
 *
 * **This file is the client half of §7.6's contract, and the only place the argument order lives.**
 * The server names a string resource and supplies the variables by name; which variable fills which
 * `%N$s` is knowledge that belongs with `strings.xml`, not with the sender — the sender cannot even
 * render the section label, which is itself localized.
 *
 * An unrecognised `titleKey`/`bodyKey` renders as an empty segment rather than throwing: a message
 * from a newer server than this build should still land in the tray under the right id, however
 * thin its text.
 */
suspend fun PushPayload.toPendingNotification(): PendingNotification =
  PendingNotification(
    id = notificationId,
    channel = channel,
    title = renderTitle(),
    body = renderBody(),
    highPriority = highPriority,
    tapTarget = tapTarget,
  )

private suspend fun PushPayload.renderTitle(): String = when (titleKey) {
  // "%1$s · %2$s" — tail number, then the TITLE-CASE section label, resolved from recordType.
  "notification_n1_title" -> getString(
    Res.string.notification_n1_title,
    tailNumber,
    sectionTitle()
  )
  // The escalation title carries no dynamic content: the tail number lives in the body.
  "notification_title_priority_raised" -> getString(Res.string.notification_title_priority_raised)
  "notification_n1_title_squawk_created" ->
    getString(Res.string.notification_n1_title_squawk_created)

  else -> ""
}

private suspend fun PushPayload.renderBody(): String = when (bodyKey) {
  // "%1$s: %2$s VERB a %3$s\n\n%4$s" — tail, actor, the record's own noun, then its own title on
  // its own line. One concrete notification per write (design decision, 2026-08-27) — there is no
  // count and nothing here is ever a summary.
  "notification_n1_body_record_created" ->
    getString(
      Res.string.notification_n1_body_record_created,
      tailNumber,
      actor(),
      noun(),
      recordTitle
    )

  "notification_n1_body_record_updated" ->
    getString(
      Res.string.notification_n1_body_record_updated,
      tailNumber,
      actor(),
      noun(),
      recordTitle
    )

  "notification_n1_body_record_deleted" ->
    getString(
      Res.string.notification_n1_body_record_deleted,
      tailNumber,
      actor(),
      noun(),
      recordTitle
    )
  // The Aircraft record has no per-record title to name, so its body is two segments, not four.
  "notification_n1_body_aircraft_updated" ->
    getString(
      Res.string.notification_n1_body_aircraft_updated,
      tailNumber,
      actor()
    )
  // The escalation bodies lead with the tail number instead: "%1$s: %2$s created a new squawk
  // issue\n\n%3$s" — tail, actor, then the squawk's own title on its own line.
  "notification_n1_body_squawk_created" ->
    getString(
      Res.string.notification_n1_body_squawk_created,
      tailNumber,
      actor(),
      recordTitle
    )

  "notification_n1_body_squawk_raised" ->
    getString(
      Res.string.notification_n1_body_squawk_raised,
      tailNumber,
      actor(),
      recordTitle
    )

  else -> ""
}

/** The server sends an empty name when the share roster had none — a revoked or unsynced member. */
private suspend fun PushPayload.actor(): String =
  actorName.takeIf { it.isNotBlank() }
    ?: getString(Res.string.notification_n1_actor_fallback)

private suspend fun PushPayload.sectionTitle(): String = getString(sectionRes())

/** The record's own singular noun for the body — "a squawk", "a task", "a logbook entry". */
private suspend fun PushPayload.noun(): String = getString(nounRes())

/**
 * Section labels are resolved from `recordType` rather than sent, because they are localized text
 * the server has no way to produce (§7.6). An unknown type falls back to the aircraft label, which
 * reads correctly for anything about the aircraft as a whole.
 */
private fun PushPayload.sectionRes(): StringResource = when (recordType) {
  "squawk" -> Res.string.notification_n1_section_squawks
  "task" -> Res.string.notification_n1_section_tasks
  "log" -> Res.string.notification_n1_section_logbook
  else -> Res.string.notification_n1_section_aircraft
}

/** Only reached from a record body (created/updated/deleted) — the aircraft body needs no noun. */
private fun PushPayload.nounRes(): StringResource = when (recordType) {
  "squawk" -> Res.string.notification_n1_noun_squawk
  "task" -> Res.string.notification_n1_noun_task
  else -> Res.string.notification_n1_noun_log
}
