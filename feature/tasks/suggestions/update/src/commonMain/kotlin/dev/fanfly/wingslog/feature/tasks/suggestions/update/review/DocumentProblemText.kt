package dev.fanfly.wingslog.feature.tasks.suggestions.update.review

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.feature.attachment.datamanager.QuotaChecker
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.suggestions.update.generated.resources.Res
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_problem_not_added
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_problem_too_large
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_problem_too_many
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_problem_unsupported

/** Why the last pick was not (all) added, under the documents picked. */
@Composable
internal fun DocumentProblem.message(): String = when (this) {
  DocumentProblem.TOO_LARGE -> stringResource(
    Res.string.sources_problem_too_large,
    MAX_DOCUMENT_MB
  )

  DocumentProblem.UNSUPPORTED -> stringResource(Res.string.sources_problem_unsupported)
  DocumentProblem.TOO_MANY -> stringResource(
    Res.string.sources_problem_too_many,
    SourcesState.MAX_DOCUMENTS_PER_RUN,
  )

  DocumentProblem.NOT_ADDED -> stringResource(Res.string.sources_problem_not_added)
}

/** The AI document cap in MB, as the limits line says it. */
internal val MAX_DOCUMENT_MB =
  (QuotaChecker.MAX_AI_DOCUMENT_BYTES / (1024 * 1024)).toInt()
