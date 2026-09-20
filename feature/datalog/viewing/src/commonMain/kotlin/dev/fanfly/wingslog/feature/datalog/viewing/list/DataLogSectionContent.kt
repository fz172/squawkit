package dev.fanfly.wingslog.feature.datalog.viewing.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.dataLogNoun
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.adaptive.compose.navPillAndFabClearance
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalSnackbarHostState
import dev.fanfly.wingslog.core.ui.common.compose.AlertDialog
import dev.fanfly.wingslog.core.ui.common.compose.EmptyState
import dev.fanfly.wingslog.core.ui.common.compose.SwipeAction
import dev.fanfly.wingslog.core.ui.common.compose.SwipeActionCard
import dev.fanfly.wingslog.core.ui.common.compose.SwipeActionTone
import dev.fanfly.wingslog.core.ui.common.compose.rememberSwipeRevealController
import dev.fanfly.wingslog.core.ui.common.compose.ListRowDivider
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.attachment.viewing.FileDropTarget
import dev.fanfly.wingslog.feature.attachment.viewing.rememberFilePicker
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.delete
import wingslog.core.sharedassets.generated.resources.delete_failed
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_delete_body
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_delete_title
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_empty_title
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_recent_uploads
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_supported_formats
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_upload
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * The section body (design §10.2, PRD R2, R34, R35, R40): the description, the list newest first,
 * in-flight imports inline, and the guest card where upload needs an account. Upload is
 * [DataLogUploadFab] in the shell's FAB slot on every tier.
 */
@Composable
fun DataLogSectionContent(
  thingId: ThingId,
  onOpen: (DataLogId) -> Unit,
  onLinkAccount: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val viewModel = dataLogListViewModel(thingId)
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val lexicon = LocalThingLexicon.current
  val compact = LocalLayoutTier.current.isCompact
  val pick = rememberFilePicker(onResult = viewModel::upload)
  val revealController = rememberSwipeRevealController()
  val snackbarHostState = LocalSnackbarHostState.current
  val deleteFailed = stringResource(CoreRes.string.delete_failed)
  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        DataLogListEvent.DeleteFailed -> snackbarHostState?.showSnackbar(
          deleteFailed
        )
      }
    }
  }

  FileDropTarget(
    enabled = state.uploadGate == UploadGate.SignedIn,
    onDrop = viewModel::upload,
    modifier = modifier.fillMaxSize(),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Upload lives in the shell FAB on every tier; the header only describes the section.
      if (!compact || state.rows.isNotEmpty()) {
        Text(
          text = lexicon.data_log_description,
          style = if (compact) {
            MaterialTheme.typography.bodySmall
          } else {
            MaterialTheme.typography.bodyMedium
          },
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.fillMaxWidth()
            .padding(
              horizontal = Spacing.screenPadding,
              vertical = if (compact) Spacing.small else Spacing.large
            ),
        )
      }

      when {
        state.isLoading -> Box(
          Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator()
        }

        state.rows.isEmpty() && state.imports.isEmpty() -> {
          if (!compact && state.uploadGate == UploadGate.Guest) {
            UploadGateCard(
              onLinkAccount = onLinkAccount,
              modifier = Modifier.padding(Spacing.screenPadding)
            )
          } else {
            EmptyState(
              title = stringResource(
                Res.string.data_log_empty_title,
                lexicon.dataLogNoun.singular
              ),
              description = lexicon.empty_states?.data_log_hint.orEmpty() + "\n" +
                stringResource(Res.string.data_log_supported_formats),
              icon = Icons.Filled.ShowChart,
              actionText = if (!compact && state.uploadGate == UploadGate.SignedIn)
                stringResource(
                  Res.string.data_log_upload,
                  LexiconFormatter.titleCase(lexicon.dataLogNoun)
                ) else null,
              onActionClick = if (!compact && state.uploadGate == UploadGate.SignedIn) pick else null,
            )
          }
        }

        else -> LazyColumn(
          modifier = Modifier.fillMaxSize()
            .nestedScroll(revealController.closeOnScroll),
          contentPadding = PaddingValues(
            start = Spacing.screenPadding,
            end = Spacing.screenPadding,
            top = Spacing.small,
            bottom = navPillAndFabClearance,
          ),
          // No arrangement gap: the log rows are flat and meet a hairline, so anything above them
          // that is still a card carries its own spacing instead.
        ) {
          if (!compact && state.uploadGate == UploadGate.Guest) {
            item {
              UploadGateCard(
                onLinkAccount = onLinkAccount,
                modifier = Modifier.padding(bottom = Spacing.medium),
              )
            }
          }
          items(state.imports, key = { "import-${it.key}" }) { row ->
            ImportRowCard(
              row = row,
              onKeepBoth = { viewModel.confirmImport(row.key) },
              onDismiss = { viewModel.dismissImport(row.key) },
              onFileUnderOtherThing = { viewModel.fileUnderOtherThing(row.key) },
              onKeepHere = { viewModel.keepHere(row.key) },
              modifier = Modifier.padding(bottom = Spacing.medium),
            )
          }
          if (!compact && state.rows.isNotEmpty()) {
            item {
              Text(
                text = "${stringResource(Res.string.data_log_recent_uploads)}  ${state.rows.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.small, bottom = Spacing.small),
              )
            }
          }
          itemsIndexed(state.rows, key = { _, row -> row.id.value_ }) { index, row ->
            // The hairline goes above every row but the first, so the list never opens or closes
            // on a rule. Zeroing the arrangement is what lets the rows meet it.
            if (index > 0) ListRowDivider()
            SwipeActionCard(
              // Whoever may upload may delete; a guest browses only, so the drag is disabled.
              actions = dataLogQuickActions(
                onDelete = if (state.uploadGate == UploadGate.SignedIn) {
                  { revealController.close(); viewModel.onDeleteClick(row) }
                } else null,
              ),
              controller = revealController,
              key = row.id.value_,
            ) {
              DataLogCard(
                row = row,
                onClick = { onOpen(row.id) },
                showDetails = !compact
              )
            }
          }
        }
      }
    }
  }

  state.deleting?.let {
    AlertDialog(
      onDismissRequest = viewModel::cancelDelete,
      title = {
        Text(
          stringResource(
            Res.string.data_log_delete_title,
            LexiconFormatter.titleCase(lexicon.dataLogNoun)
          )
        )
      },
      text = { Text(stringResource(Res.string.data_log_delete_body)) },
      confirmButton = {
        TextButton(
          onClick = viewModel::confirmDelete,
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text(stringResource(CoreRes.string.delete)) }
      },
      dismissButton = {
        TextButton(onClick = viewModel::cancelDelete) {
          Text(
            stringResource(
              CoreRes.string.cancel
            )
          )
        }
      },
    )
  }
}

@Composable
private fun dataLogQuickActions(onDelete: (() -> Unit)?): List<SwipeAction> {
  val label = stringResource(CoreRes.string.delete)
  return if (onDelete == null) emptyList()
  else listOf(
    SwipeAction(
      icon = Icons.Filled.Delete,
      label = label,
      tone = SwipeActionTone.DESTRUCTIVE,
      onClick = onDelete
    )
  )
}
