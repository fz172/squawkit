package dev.fanfly.wingslog.feature.tasks.suggestions.update.review

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.feature.tasks.suggestions.datamanager.SuggestionStage
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.suggestions.update.generated.resources.Res
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_stage_extracting
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_stage_finding
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_stage_other
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_stage_reading
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_stage_recalling
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_stage_validating
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_stage_waiting
import wingslog.feature.tasks.suggestions.update.generated.resources.suggestions_suggesting

/**
 * The model run's progress line (PRD R19): what the pipeline says it is doing, with the document
 * it names where it names one. Null [stage] is a run not started yet; [SuggestionStage.OTHER]
 * reads as generic progress.
 */
@Composable
fun stageText(stage: SuggestionStage?, stageArg: String?): String {
  val thing = LocalThingLexicon.current.thingNoun.singular
  val document = stageArg.orEmpty()
  return when (stage) {
    null -> stringResource(Res.string.suggestions_stage_waiting)
    SuggestionStage.RECALLING_SCHEDULE -> stringResource(
      Res.string.suggestions_stage_recalling,
      thing
    )

    SuggestionStage.TAILORING -> stringResource(Res.string.suggestions_suggesting, thing)
    SuggestionStage.VALIDATING -> stringResource(Res.string.suggestions_stage_validating)
    SuggestionStage.READING_DOCUMENT -> stringResource(
      Res.string.suggestions_stage_reading,
      document
    )

    SuggestionStage.FINDING_SCHEDULE -> stringResource(
      Res.string.suggestions_stage_finding,
      document
    )

    SuggestionStage.EXTRACTING_SCHEDULE -> stringResource(
      Res.string.suggestions_stage_extracting,
      document
    )

    SuggestionStage.OTHER -> stringResource(Res.string.suggestions_stage_other)
  }
}
