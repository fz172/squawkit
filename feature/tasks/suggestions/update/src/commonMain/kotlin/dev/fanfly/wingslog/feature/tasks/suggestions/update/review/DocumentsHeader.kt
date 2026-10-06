package dev.fanfly.wingslog.feature.tasks.suggestions.update.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.rpc.suggesttasks.IdentifiedDocument
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.suggestions.update.generated.resources.Res
import wingslog.feature.tasks.suggestions.update.generated.resources.review_document_mismatch
import wingslog.feature.tasks.suggestions.update.generated.resources.review_document_revision
import wingslog.feature.tasks.suggestions.update.generated.resources.review_documents_from

/**
 * The review's header for a run that read documents (design §9.5): what each turned out to be,
 * and a warning for one that looks like it is for a different Thing, so the user checks those
 * cards before adding them.
 */
@Composable
internal fun DocumentsHeader(documents: List<IdentifiedDocument>) {
  if (documents.isEmpty()) return
  val thing = LocalThingLexicon.current.thingNoun.singular
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    Text(
      text = stringResource(
        Res.string.review_documents_from,
        documents.map { it.label() }
          .joinToString(" · "),
      ),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    documents.filterNot { it.matches_thing }
      .forEach { document ->
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
          Icon(
            Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(Spacing.large),
          )
          Text(
            text = stringResource(
              Res.string.review_document_mismatch,
              document.name,
              thing
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
  }
}

/** "Rotax 915 iS MM rev 3": the title the run read, else the file's name. */
@Composable
private fun IdentifiedDocument.label(): String {
  val title = displayTitle()
  return if (revision.isBlank()) title else stringResource(
    Res.string.review_document_revision,
    title,
    revision
  )
}

/** The title the run read in the document, else the file's own name. */
internal fun IdentifiedDocument.displayTitle(): String = title.ifBlank { name }
