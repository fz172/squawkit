package dev.fanfly.wingslog.feature.tasks.suggestions.update.starter

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ai.AiErrorCode
import dev.fanfly.wingslog.core.ai.AiSkipped
import dev.fanfly.wingslog.core.datetime.toDisplayDateTime
import dev.fanfly.wingslog.core.datetime.toDisplayTime
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.thingNoun
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.suggestions.update.generated.resources.Res
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_pack_skipped_until

/**
 * Why the curated cards came without the model's (PRD R9a). The daily limit says when it is back:
 * the time alone when that is today, the date and time otherwise. The other reasons (the kill
 * switch, a spending ceiling) say what their failure message says.
 */
@Composable
fun AiSkipped.text(): String {
  val next = nextAvailableAt
  if (reason != AiErrorCode.DAILY_LIMIT || next == null) return reason.message()
  return stringResource(
    Res.string.starter_pack_skipped_until,
    LocalThingLexicon.current.thingNoun.singular,
    next.toDisplayWhen(Clock.System.now()),
  )
}

/** `03:10 PM` when [this] falls on [now]'s day here, `10/04/2026 03:10 PM` otherwise. */
internal fun Instant.toDisplayWhen(now: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String =
  if (toLocalDateTime(timeZone).date == now.toLocalDateTime(timeZone).date) {
    toDisplayTime(timeZone)
  } else {
    toDisplayDateTime(timeZone)
  }
