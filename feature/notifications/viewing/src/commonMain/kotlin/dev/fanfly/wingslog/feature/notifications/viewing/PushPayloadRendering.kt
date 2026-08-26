package dev.fanfly.wingslog.feature.notifications.viewing

import dev.fanfly.wingslog.feature.notifications.model.PendingNotification
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import wingslog.feature.notifications.sharedassets.generated.resources.Res
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_actor_fallback
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_body_high_volume
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_body_plural
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_body_single
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_body_squawk_created
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_body_squawk_raised
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_section_aircraft
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_section_aircraft_lower
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_section_logbook
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_section_logbook_lower
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_section_squawks
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_section_squawks_lower
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_section_tasks
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_section_tasks_lower
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_title
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_title_high_volume
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
suspend fun PushPayload.toPendingNotification(): PendingNotification = PendingNotification(
  id = notificationId,
  channel = channel,
  title = renderTitle(),
  body = renderBody(),
  highPriority = highPriority,
  tapTarget = tapTarget,
)

private suspend fun PushPayload.renderTitle(): String = when (titleKey) {
  // "%1$s · %2$s" — tail number, then the TITLE-CASE section label, resolved from recordType.
  "notification_n1_title" -> getString(Res.string.notification_n1_title, tailNumber, sectionTitle())
  "notification_n1_title_high_volume" ->
    getString(Res.string.notification_n1_title_high_volume, tailNumber)
  // The escalation title carries no dynamic content: the tail number lives in the body.
  "notification_title_priority_raised" -> getString(Res.string.notification_title_priority_raised)
  "notification_n1_title_squawk_created" ->
    getString(Res.string.notification_n1_title_squawk_created)
  else -> ""
}

private suspend fun PushPayload.renderBody(): String = when (bodyKey) {
  // "%1$s made a change to %2$s" — actor, then the LOWER-CASE section label.
  "notification_n1_body_single" ->
    getString(Res.string.notification_n1_body_single, actor(), sectionLower())
  // "%1$s made %2$d changes to %3$s" — note the count sits between the two, not after them.
  "notification_n1_body_plural" ->
    getString(Res.string.notification_n1_body_plural, actor(), changeCount, sectionLower())
  "notification_n1_body_high_volume" -> getString(Res.string.notification_n1_body_high_volume)
  // The escalation bodies lead with the tail number instead: "%1$s: %2$s created a new squawk
  // issue\n\n%3$s" — tail, actor, then the squawk's own title on its own line.
  "notification_n1_body_squawk_created" ->
    getString(Res.string.notification_n1_body_squawk_created, tailNumber, actor(), recordTitle)
  "notification_n1_body_squawk_raised" ->
    getString(Res.string.notification_n1_body_squawk_raised, tailNumber, actor(), recordTitle)
  else -> ""
}

/** The server sends an empty name when the share roster had none — a revoked or unsynced member. */
private suspend fun PushPayload.actor(): String =
  actorName.takeIf { it.isNotBlank() } ?: getString(Res.string.notification_n1_actor_fallback)

private suspend fun PushPayload.sectionTitle(): String = getString(sectionRes(titleCase = true))

private suspend fun PushPayload.sectionLower(): String = getString(sectionRes(titleCase = false))

/**
 * Section labels are resolved from `recordType` rather than sent, because they are localized text
 * the server has no way to produce (§7.6). An unknown type falls back to the aircraft labels, which
 * read correctly for anything about the aircraft as a whole.
 */
private fun PushPayload.sectionRes(titleCase: Boolean): StringResource = when (recordType) {
  "squawk" ->
    if (titleCase) Res.string.notification_n1_section_squawks
    else Res.string.notification_n1_section_squawks_lower
  "task" ->
    if (titleCase) Res.string.notification_n1_section_tasks
    else Res.string.notification_n1_section_tasks_lower
  "log" ->
    if (titleCase) Res.string.notification_n1_section_logbook
    else Res.string.notification_n1_section_logbook_lower
  else ->
    if (titleCase) Res.string.notification_n1_section_aircraft
    else Res.string.notification_n1_section_aircraft_lower
}
