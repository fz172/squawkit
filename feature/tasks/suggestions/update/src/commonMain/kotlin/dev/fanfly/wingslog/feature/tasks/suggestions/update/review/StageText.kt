package dev.fanfly.wingslog.feature.tasks.suggestions.update.review

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.thingNoun
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
 * The model run's progress line (PRD R19): what the pipeline says it is doing, by its stage key
 * (backend `tasks/pipeline.ts`), with the document it names where it names one. Null [stage] is a
 * run not started yet; a key this build does not know reads as generic progress.
 */
@Composable
fun stageText(stage: String?, stageArg: String?): String {
  val thing = LocalThingLexicon.current.thingNoun.singular
  val document = stageArg.orEmpty()
  return when (stage) {
    null -> stringResource(Res.string.suggestions_stage_waiting)
    "recalling_schedule" -> stringResource(
      Res.string.suggestions_stage_recalling,
      thing
    )

    "tailoring" -> stringResource(Res.string.suggestions_suggesting, thing)
    "validating" -> stringResource(Res.string.suggestions_stage_validating)
    "reading_document" -> stringResource(
      Res.string.suggestions_stage_reading,
      document
    )

    "finding_schedule" -> stringResource(
      Res.string.suggestions_stage_finding,
      document
    )

    "extracting_schedule" -> stringResource(
      Res.string.suggestions_stage_extracting,
      document
    )

    else -> stringResource(Res.string.suggestions_stage_other)
  }
}
