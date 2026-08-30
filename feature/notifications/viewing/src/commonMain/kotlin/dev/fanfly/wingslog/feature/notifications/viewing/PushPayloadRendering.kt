package dev.fanfly.wingslog.feature.notifications.viewing

import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.squawkNoun
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.feature.notifications.model.PendingNotification
import dev.fanfly.wingslog.thing.Lexicon
import org.jetbrains.compose.resources.getString
import wingslog.feature.notifications.sharedassets.generated.resources.Res
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_actor_fallback
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_body_record_created
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_body_record_deleted
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_body_record_updated
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_body_squawk_created
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_body_squawk_raised
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_body_thing_updated
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_noun_log
import wingslog.feature.notifications.sharedassets.generated.resources.notification_n1_noun_task
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
suspend fun PushPayload.toPendingNotification(lexicon: Lexicon): PendingNotification =
  PendingNotification(
    id = notificationId,
    channel = channel,
    title = renderTitle(lexicon),
    body = renderBody(lexicon),
    highPriority = highPriority,
    tapTarget = tapTarget,
  )

private suspend fun PushPayload.renderTitle(lexicon: Lexicon): String =
  when (titleKey) {
    // "%1$s · %2$s" — tail number, then the TITLE-CASE section label, resolved from recordType.
    "notification_n1_title" -> getString(
      Res.string.notification_n1_title,
      tailNumber,
      sectionTitle(lexicon),
    )
    // The escalation title carries no dynamic content: the tail number lives in the body.
    "notification_title_priority_raised" -> getString(Res.string.notification_title_priority_raised)
    "notification_n1_title_squawk_created" ->
      getString(Res.string.notification_n1_title_squawk_created)

    else -> ""
  }

private suspend fun PushPayload.renderBody(lexicon: Lexicon): String =
  when (bodyKey) {
    // "%1$s: %2$s VERB a %3$s\n\n%4$s" — tail, actor, the record's own noun, then its own title on
    // its own line. One concrete notification per write (design decision, 2026-08-27) — there is no
    // count and nothing here is ever a summary.
    "notification_n1_body_record_created" ->
      getString(
        Res.string.notification_n1_body_record_created,
        tailNumber,
        actor(),
        noun(lexicon),
        recordTitle
      )

    "notification_n1_body_record_updated" ->
      getString(
        Res.string.notification_n1_body_record_updated,
        tailNumber,
        actor(),
        noun(lexicon),
        recordTitle
      )

    "notification_n1_body_record_deleted" ->
      getString(
        Res.string.notification_n1_body_record_deleted,
        tailNumber,
        actor(),
        noun(lexicon),
        recordTitle
      )
    // The Thing record has no per-record title to name, so its body is two segments, not four.
    //
    // The key keeps "aircraft" while the resource it renders is now ..._thing_updated. That is not
    // an oversight: bodyKey is a **wire value** the backend sends (pushMessages.ts), so renaming it
    // here would stop matching every push already in flight and every one sent by a function that
    // has not been redeployed. Wire identity is grandfathered — see #638.
    "notification_n1_body_aircraft_updated" ->
      getString(
        Res.string.notification_n1_body_thing_updated,
        tailNumber,
        actor(),
        lexicon.thingNoun.singular,
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

/**
 * Section labels are resolved from `recordType` rather than sent, because they are localized text
 * the server has no way to produce (§7.6). An unknown type falls back to the thing label, which
 * reads correctly for anything about the thing as a whole.
 *
 * **Only the thing branch reads the lexicon; the other three keep a string resource.** That is not
 * an oversight mid-refactor, and each has its own reason.
 *
 * "Tasks" and "Logbook" are shorter than what the lexicon gives — this app says "Maintenance Task"
 * and "Work Log" everywhere except here — so substituting would reword them, which the
 * byte-identical test (#658) correctly refuses. Which wording survives is a product decision,
 * tracked in #683, and the short forms here may well be the ones that got it right.
 *
 * "Squawks" *is* the lexicon word, but `notification_n1_section_squawks` is shared with
 * `WebForeignWriteDetector`, whose section enum stores a `StringResource` rather than rendering
 * one. Deleting it there is a bigger change than this, and belongs with #661.
 */
private suspend fun PushPayload.sectionTitle(lexicon: Lexicon): String =
  when (recordType) {
    "squawk" -> getString(Res.string.notification_n1_section_squawks)
    "task" -> getString(Res.string.notification_n1_section_tasks)
    "log" -> getString(Res.string.notification_n1_section_logbook)
    else -> LexiconFormatter.titleCase(lexicon.thingNoun)
  }

/**
 * The record's own singular noun for the body — "a squawk", "a task", "a logbook entry".
 *
 * Same split, same reason as [sectionTitle].
 */
private suspend fun PushPayload.noun(lexicon: Lexicon): String =
  when (recordType) {
    "squawk" -> lexicon.squawkNoun.singular
    "task" -> getString(Res.string.notification_n1_noun_task)
    else -> getString(Res.string.notification_n1_noun_log)
  }
