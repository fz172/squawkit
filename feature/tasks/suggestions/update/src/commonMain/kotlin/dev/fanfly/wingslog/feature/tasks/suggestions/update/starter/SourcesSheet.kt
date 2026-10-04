package dev.fanfly.wingslog.feature.tasks.suggestions.update.starter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.fanfly.wingslog.core.ai.AiSkipped
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.ui.layout.ContentWidth
import dev.fanfly.wingslog.core.ui.layout.LayoutTier
import dev.fanfly.wingslog.core.ui.layout.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.popup.ModalBottomSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.attachment.datamanager.QuotaChecker
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.attachment.viewing.rememberFilePicker
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.remove
import wingslog.feature.tasks.suggestions.update.generated.resources.Res
import wingslog.feature.tasks.suggestions.update.generated.resources.ai_error_owner_not_pro
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_add_documents
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_add_more
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_documents
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_limits
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_pro
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_problem_not_added
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_problem_too_large
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_problem_too_many
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_problem_unsupported
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_suggest
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_title
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_pack_checking
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_pack_skip
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * The sources sheet (design §9.3, PRD R6), opened by the AI button where the owner has Pro, or by
 * the document entry points. It asks one thing: add documents for the model to read, or not.
 *
 * - No documents yet: **Skip** starts the run from the Thing's own details; **Add documents**
 *   opens the picker.
 * - Documents added: **Add more** (up to the limit) and **Suggest**.
 *
 * Reached from a document entry point without the owner's Pro (R46): a free owner's
 * *Add documents* is marked Pro and opens the upsell; a member of a free owner's Thing is told it
 * is the owner's plan (R45), with *Suggest* alone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SourcesSheet(
  state: SourcesState,
  onAddDocuments: (List<PickedFile>) -> Unit,
  onPickError: () -> Unit,
  onRemove: (String) -> Unit,
  onUpsell: () -> Unit,
  onSuggest: () -> Unit,
  onDismiss: () -> Unit,
  onPickOnOpenHandled: () -> Unit,
) {
  val pickFiles =
    rememberFilePicker(onResult = onAddDocuments, onReadError = onPickError)

  // *Tasks from a document* (PRD R3): the picker first, or the upsell for a free owner. A member
  // of a free owner's Thing just sees the sheet, which says whose plan it is. On web a browser may
  // refuse a picker it did not see a tap open; *Add documents* is there either way.
  LaunchedEffect(state.pickOnOpen, state.isChecking) {
    if (!state.pickOnOpen || state.isChecking) return@LaunchedEffect
    onPickOnOpenHandled()
    when {
      state.documentsAllowed -> pickFiles()
      state.isOwner -> onUpsell()
    }
  }
  val locked = !state.documentsAllowed
  val memberOfFreeOwner = locked && !state.isOwner

  SheetOrDialog(onDismiss = onDismiss) {
    Text(
      text = stringResource(
        Res.string.sources_title,
        LexiconFormatter.plural(LocalThingLexicon.current.taskNoun),
      ),
      style = MaterialTheme.typography.titleLarge,
    )
    Text(
      text = if (memberOfFreeOwner) {
        stringResource(Res.string.ai_error_owner_not_pro)
      } else {
        stringResource(
          Res.string.sources_documents,
          LocalThingLexicon.current.thingNoun.singular
        )
      },
      style = MaterialTheme.typography.bodyMedium,
    )

    state.documents.forEach { document ->
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        Icon(Icons.Outlined.Description, contentDescription = null)
        Text(
          text = document.name,
          style = MaterialTheme.typography.bodyLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onRemove(document.id) }) {
          Icon(
            Icons.Default.Close,
            contentDescription = stringResource(CoreRes.string.remove)
          )
        }
      }
    }

    if (state.isChecking || state.isAdding) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        CircularProgressIndicator(
          modifier = Modifier.size(Spacing.large),
          strokeWidth = 2.dp
        )
        if (state.isChecking) {
          Text(
            text = stringResource(Res.string.starter_pack_checking),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    state.problem?.let { problem ->
      Text(
        text = problem.message(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    }

    if (!locked) {
      Text(
        text = stringResource(
          Res.string.sources_limits,
          SourcesState.MAX_DOCUMENTS_PER_RUN,
          MAX_DOCUMENT_MB,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    // Why no run can start now, and when it can (design §9.3).
    state.blocked?.let { reason ->
      Text(
        text = AiSkipped(reason, state.availableAt).text(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Row(
      modifier = Modifier.align(Alignment.End),
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      when {
        // Nothing to add: the run, as it is.
        memberOfFreeOwner -> Button(
          onClick = onSuggest,
          enabled = state.canSuggest
        ) {
          Text(stringResource(Res.string.sources_suggest))
        }

        state.documents.isEmpty() -> {
          OutlinedButton(onClick = onSuggest, enabled = state.canSuggest) {
            Text(stringResource(Res.string.starter_pack_skip))
          }
          Button(
            onClick = if (locked) onUpsell else pickFiles,
            enabled = !state.isChecking && !state.isAdding,
          ) {
            Text(stringResource(Res.string.sources_add_documents))
            if (locked) {
              Text(
                text = " " + stringResource(Res.string.sources_pro),
                style = MaterialTheme.typography.labelSmall,
              )
            }
          }
        }

        else -> {
          OutlinedButton(
            onClick = pickFiles,
            enabled = !state.atLimit && !state.isAdding,
          ) {
            Text(stringResource(Res.string.sources_add_more))
          }
          Button(onClick = onSuggest, enabled = state.canSuggest) {
            Text(stringResource(Res.string.sources_suggest))
          }
        }
      }
    }
  }
}

/**
 * A bottom sheet on a phone, a centered dialog on wider layouts (the web, tablets), as the
 * attachment picker does: a sheet stretched across a large screen reads as a banner, not a choice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetOrDialog(
  onDismiss: () -> Unit,
  content: @Composable ColumnScope.() -> Unit
) {
  if (LocalLayoutTier.current != LayoutTier.COMPACT) {
    Dialog(
      onDismissRequest = onDismiss,
      properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
      Surface(
        modifier = Modifier
          .padding(Spacing.huge)
          .widthIn(max = ContentWidth.Dialog)
          .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
          Spacing.hairline,
          MaterialTheme.colorScheme.secondaryContainer
        ),
      ) {
        DisableSelection {
          Column(
            modifier = Modifier.padding(Spacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            content = content,
          )
        }
      }
    }
  } else {
    ModalBottomSheet(
      onDismissRequest = onDismiss,
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth()
          .padding(horizontal = Spacing.xLarge)
          .padding(bottom = Spacing.xLarge),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        content = content,
      )
    }
  }
}

@Composable
private fun DocumentProblem.message(): String = when (this) {
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

private val MAX_DOCUMENT_MB =
  (QuotaChecker.MAX_AI_DOCUMENT_BYTES / (1024 * 1024)).toInt()
