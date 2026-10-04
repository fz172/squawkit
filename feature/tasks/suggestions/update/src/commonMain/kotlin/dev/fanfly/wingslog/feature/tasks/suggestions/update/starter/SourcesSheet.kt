package dev.fanfly.wingslog.feature.tasks.suggestions.update.starter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ai.AiSkipped
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.taskNoun
import dev.fanfly.wingslog.core.template.thingNoun
import dev.fanfly.wingslog.core.ui.popup.ModalBottomSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.attachment.datamanager.QuotaChecker
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.attachment.viewing.rememberCameraCapture
import dev.fanfly.wingslog.feature.attachment.viewing.rememberFilePicker
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.remove
import wingslog.feature.attachment.sharedassets.generated.resources.choose_file
import wingslog.feature.attachment.sharedassets.generated.resources.take_photo
import wingslog.feature.tasks.suggestions.update.generated.resources.Res
import wingslog.feature.tasks.suggestions.update.generated.resources.ai_error_owner_not_pro
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_documents
import wingslog.feature.tasks.suggestions.update.generated.resources.starter_pack_checking
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_limits
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_pro
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_problem_not_added
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_problem_too_large
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_problem_too_many
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_problem_unsupported
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_suggest
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_title
import wingslog.feature.tasks.suggestions.update.generated.resources.sources_without_documents
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.attachment.sharedassets.generated.resources.Res as AttachRes

/**
 * The sources sheet (design §9.3, PRD R6): the documents for the model to read, and *Suggest*.
 * Documents are optional; without them the run works from the Thing's own details.
 *
 * Documents need the Thing owner's Pro (R46). A free owner sees the add buttons marked Pro, and
 * they open the upsell; a member of a free owner's Thing is told it is the owner's plan, with
 * nothing to tap (R45).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SourcesSheet(
  state: SourcesState,
  cameraSupported: Boolean,
  onAddDocuments: (List<PickedFile>) -> Unit,
  onPickError: () -> Unit,
  onRemove: (String) -> Unit,
  onUpsell: () -> Unit,
  onSuggest: () -> Unit,
  onDismiss: () -> Unit,
  onPickOnOpenHandled: () -> Unit,
) {
  val pickFiles = rememberFilePicker(onResult = onAddDocuments, onReadError = onPickError)
  val takePhoto = rememberCameraCapture(onResult = onAddDocuments, onError = onPickError)

  // *Tasks from a document* (PRD R3): the picker first, or the upsell for a free owner. A member
  // of a free owner's Thing just sees the sheet, which says whose plan it is. On web a browser may
  // refuse a picker it did not see a tap open; *Choose file* is there either way.
  LaunchedEffect(state.pickOnOpen, state.isChecking) {
    if (!state.pickOnOpen || state.isChecking) return@LaunchedEffect
    onPickOnOpenHandled()
    when {
      state.documentsAllowed -> pickFiles()
      state.isOwner -> onUpsell()
    }
  }
  val thingNoun = LocalThingLexicon.current.thingNoun.singular

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth()
        .padding(horizontal = Spacing.xLarge)
        .padding(bottom = Spacing.xLarge),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Text(
        text = stringResource(
          Res.string.sources_title,
          LexiconFormatter.plural(LocalThingLexicon.current.taskNoun),
        ),
        style = MaterialTheme.typography.titleLarge,
      )
      Text(
        text = stringResource(Res.string.sources_documents),
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
            Icon(Icons.Default.Close, contentDescription = stringResource(CoreRes.string.remove))
          }
        }
      }

      when {
        state.isChecking -> Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
          CircularProgressIndicator(modifier = Modifier.size(Spacing.large), strokeWidth = 2.dp)
          Text(
            text = stringResource(Res.string.starter_pack_checking),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        // A member cannot buy the owner's plan: say whose it is, and offer nothing (R45).
        !state.documentsAllowed && !state.isOwner -> Text(
          text = stringResource(Res.string.ai_error_owner_not_pro),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        else -> {
          val locked = !state.documentsAllowed
          val enabled = locked || (!state.atLimit && !state.isAdding)
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
          ) {
            AddButton(
              label = stringResource(AttachRes.string.choose_file),
              icon = { Icon(Icons.Outlined.UploadFile, contentDescription = null) },
              locked = locked,
              enabled = enabled,
              onClick = if (locked) onUpsell else pickFiles,
            )
            if (cameraSupported) {
              AddButton(
                label = stringResource(AttachRes.string.take_photo),
                icon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                locked = locked,
                enabled = enabled,
                onClick = if (locked) onUpsell else takePhoto,
              )
            }
            if (state.isAdding) {
              CircularProgressIndicator(
                modifier = Modifier.size(Spacing.large),
                strokeWidth = 2.dp,
              )
            }
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
        }
      }

      state.problem?.let { problem ->
        Text(
          text = problem.message(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }

      if (state.documents.isEmpty()) {
        Text(
          text = stringResource(Res.string.sources_without_documents, thingNoun),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      // Why no run can start now, and when it can (design §9.3).
      state.blocked?.let { reason ->
        Text(
          text = AiSkipped(reason, state.availableAt).text(),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
        )
      }

      Button(
        onClick = onSuggest,
        enabled = state.canSuggest,
        modifier = Modifier.align(Alignment.End),
      ) {
        Text(stringResource(Res.string.sources_suggest))
      }
    }
  }
}

@Composable
private fun AddButton(
  label: String,
  icon: @Composable () -> Unit,
  locked: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  OutlinedButton(onClick = onClick, enabled = enabled) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      icon()
      Text(label)
      if (locked) {
        Text(
          text = stringResource(Res.string.sources_pro),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}

@Composable
private fun DocumentProblem.message(): String = when (this) {
  DocumentProblem.TOO_LARGE -> stringResource(Res.string.sources_problem_too_large, MAX_DOCUMENT_MB)
  DocumentProblem.UNSUPPORTED -> stringResource(Res.string.sources_problem_unsupported)
  DocumentProblem.TOO_MANY -> stringResource(
    Res.string.sources_problem_too_many,
    SourcesState.MAX_DOCUMENTS_PER_RUN,
  )

  DocumentProblem.NOT_ADDED -> stringResource(Res.string.sources_problem_not_added)
}

private val MAX_DOCUMENT_MB = (QuotaChecker.MAX_AI_DOCUMENT_BYTES / (1024 * 1024)).toInt()
