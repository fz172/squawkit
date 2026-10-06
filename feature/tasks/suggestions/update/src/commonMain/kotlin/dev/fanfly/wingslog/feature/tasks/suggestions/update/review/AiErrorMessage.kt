package dev.fanfly.wingslog.feature.tasks.suggestions.update.review

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ai.AiErrorCode
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.thingNoun
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.suggestions.update.generated.resources.Res
import wingslog.feature.tasks.suggestions.update.generated.resources.ai_error_app_unverified
import wingslog.feature.tasks.suggestions.update.generated.resources.ai_error_daily_limit
import wingslog.feature.tasks.suggestions.update.generated.resources.ai_error_disabled
import wingslog.feature.tasks.suggestions.update.generated.resources.ai_error_document_missing
import wingslog.feature.tasks.suggestions.update.generated.resources.ai_error_document_too_large
import wingslog.feature.tasks.suggestions.update.generated.resources.ai_error_document_unreadable
import wingslog.feature.tasks.suggestions.update.generated.resources.ai_error_invalid_output
import wingslog.feature.tasks.suggestions.update.generated.resources.ai_error_no_schedule_found
import wingslog.feature.tasks.suggestions.update.generated.resources.ai_error_not_member
import wingslog.feature.tasks.suggestions.update.generated.resources.ai_error_owner_not_pro
import wingslog.feature.tasks.suggestions.update.generated.resources.ai_error_provider_error
import wingslog.feature.tasks.suggestions.update.generated.resources.ai_error_run_in_progress
import wingslog.feature.tasks.suggestions.update.generated.resources.ai_error_sign_in_required
import wingslog.feature.tasks.suggestions.update.generated.resources.ai_error_spend_ceiling
import wingslog.feature.tasks.suggestions.update.generated.resources.ai_error_stale
import wingslog.feature.tasks.suggestions.update.generated.resources.ai_error_unavailable
import wingslog.feature.tasks.suggestions.update.generated.resources.ai_error_unknown

/**
 * What the user is told when a suggestion run is refused or fails (design §5.7, PRD R21): one
 * message per code, in the Thing's own words. `UNAVAILABLE` is the "No internet connection" of
 * PRD R51. An exhaustive `when`, so a new code cannot ship without its message.
 */
@Composable
fun AiErrorCode.message(thing: String = LocalThingLexicon.current.thingNoun.singular): String =
  when (this) {
    AiErrorCode.SIGN_IN_REQUIRED -> stringResource(Res.string.ai_error_sign_in_required)
    AiErrorCode.DISABLED -> stringResource(Res.string.ai_error_disabled)
    AiErrorCode.NOT_MEMBER -> stringResource(
      Res.string.ai_error_not_member,
      thing
    )

    AiErrorCode.OWNER_NOT_PRO -> stringResource(Res.string.ai_error_owner_not_pro)
    AiErrorCode.DAILY_LIMIT -> stringResource(
      Res.string.ai_error_daily_limit,
      thing
    )

    AiErrorCode.RUN_IN_PROGRESS -> stringResource(
      Res.string.ai_error_run_in_progress,
      thing
    )

    AiErrorCode.SPEND_CEILING -> stringResource(Res.string.ai_error_spend_ceiling)
    AiErrorCode.DOCUMENT_MISSING -> stringResource(Res.string.ai_error_document_missing)
    AiErrorCode.DOCUMENT_TOO_LARGE -> stringResource(Res.string.ai_error_document_too_large)
    AiErrorCode.DOCUMENT_UNREADABLE -> stringResource(Res.string.ai_error_document_unreadable)
    AiErrorCode.NO_SCHEDULE_FOUND -> stringResource(Res.string.ai_error_no_schedule_found)
    AiErrorCode.PROVIDER_ERROR -> stringResource(Res.string.ai_error_provider_error)
    AiErrorCode.INVALID_OUTPUT -> stringResource(Res.string.ai_error_invalid_output)
    AiErrorCode.STALE -> stringResource(Res.string.ai_error_stale)
    AiErrorCode.UNAVAILABLE -> stringResource(Res.string.ai_error_unavailable)
    AiErrorCode.APP_UNVERIFIED -> stringResource(Res.string.ai_error_app_unverified)
    AiErrorCode.UNKNOWN -> stringResource(Res.string.ai_error_unknown)
  }
