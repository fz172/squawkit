package dev.fanfly.wingslog.feature.datalog.viewing.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.dataLogNoun
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalNavPillClearance
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalSnackbarHostState
import dev.fanfly.wingslog.core.ui.common.compose.AlertDialog
import dev.fanfly.wingslog.core.ui.common.compose.SwipeAction
import dev.fanfly.wingslog.core.ui.common.compose.SwipeActionCard
import dev.fanfly.wingslog.core.ui.common.compose.SwipeActionTone
import dev.fanfly.wingslog.core.ui.common.compose.rememberSwipeRevealController
import dev.fanfly.wingslog.core.ui.common.compose.EmptyState
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.attachment.viewing.rememberFilePicker
import dev.fanfly.wingslog.feature.search.viewing.NoRecordsMatch
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.delete
import wingslog.core.sharedassets.generated.resources.delete_failed
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_delete_body
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_delete_title
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_empty_title
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_recent_uploads
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_supported_formats
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_upload
import wingslog.feature.search.sharedassets.generated.resources.search_placeholder
import wingslog.feature.search.sharedassets.generated.resources.Res as SearchRes

/**
 * The section body (design §10.2, PRD R2, R34, R35, R40): description and upload button on wide
 * layouts, the list newest first, in-flight imports inline, and the guest card where upload needs
 * an account. Phones get the upload control from [DataLogUploadFab] in the shell's FAB slot.
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
  var searching by remember { mutableStateOf(false) }
  val revealController = rememberSwipeRevealController()
  val snackbarHostState = LocalSnackbarHostState.current
  val deleteFailed = stringResource(CoreRes.string.delete_failed)
  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        DataLogListEvent.DeleteFailed -> snackbarHostState?.showSnackbar(deleteFailed)
      }
    }
  }

  Column(modifier = modifier.fillMaxSize()) {
    if (!compact) {
      Row(
        modifier = Modifier.fillMaxWidth()
          .padding(
            horizontal = Spacing.screenPadding,
            vertical = Spacing.large
          ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.large),
      ) {
        Text(
          text = lexicon.data_log_description,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )
        if (state.uploadGate == UploadGate.SignedIn) {
          Button(onClick = pick) {
            Icon(
              Icons.Filled.UploadFile,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Text(
              stringResource(
                Res.string.data_log_upload,
                LexiconFormatter.titleCase(lexicon.dataLogNoun)
              ),
              modifier = Modifier.padding(start = Spacing.small),
            )
          }
        }
      }
    } else if (state.rows.isNotEmpty()) {
      // PRD R2a: a search action on phones; the field appears in place when tapped.
      Row(
        modifier = Modifier.fillMaxWidth()
          .padding(
            horizontal = Spacing.screenPadding,
            vertical = Spacing.small
          ),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (searching) {
          OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text(stringResource(SearchRes.string.search_placeholder)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        } else {
          Text(
            text = lexicon.data_log_description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
          )
        }
        IconButton(onClick = {
          searching = !searching; if (!searching) viewModel.onQueryChange("")
        }) {
          Icon(
            Icons.Filled.Search,
            contentDescription = stringResource(SearchRes.string.search_placeholder)
          )
        }
      }
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
        modifier = Modifier.fillMaxSize().nestedScroll(revealController.closeOnScroll),
        contentPadding = PaddingValues(
          start = Spacing.screenPadding,
          end = Spacing.screenPadding,
          top = Spacing.small,
          bottom = Spacing.buttonHeight + Spacing.extraLarge + LocalNavPillClearance.current,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
      ) {
        if (!compact && state.uploadGate == UploadGate.Guest) {
          item { UploadGateCard(onLinkAccount = onLinkAccount) }
        }
        items(state.imports, key = { "import-${it.key}" }) { row ->
          ImportRowCard(
            row = row,
            onKeepBoth = { viewModel.confirmImport(row.key) },
            onDismiss = { viewModel.dismissImport(row.key) },
          )
        }
        if (!compact && state.rows.isNotEmpty()) {
          item {
            Text(
              text = "${stringResource(Res.string.data_log_recent_uploads)}  ${state.rows.size}",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = Spacing.small),
            )
          }
        }
        val visible = state.visibleRows
        if (visible.isEmpty() && state.query.isNotBlank()) {
          item {
            NoRecordsMatch(
              nounPlural = LexiconFormatter.plural(lexicon.dataLogNoun),
              onClearFilters = { viewModel.onQueryChange("") },
              modifier = Modifier.fillMaxWidth()
                .padding(top = Spacing.extraLarge),
            )
          }
        }
        items(visible, key = { it.id.value_ }) { row ->
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
            DataLogCard(row = row, onClick = { onOpen(row.id) }, showDetails = !compact)
          }
        }
      }
    }
  }

  state.deleting?.let {
    AlertDialog(
      onDismissRequest = viewModel::cancelDelete,
      title = { Text(stringResource(Res.string.data_log_delete_title, LexiconFormatter.titleCase(lexicon.dataLogNoun))) },
      text = { Text(stringResource(Res.string.data_log_delete_body)) },
      confirmButton = {
        TextButton(
          onClick = viewModel::confirmDelete,
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text(stringResource(CoreRes.string.delete)) }
      },
      dismissButton = {
        TextButton(onClick = viewModel::cancelDelete) { Text(stringResource(CoreRes.string.cancel)) }
      },
    )
  }
}

@Composable
private fun dataLogQuickActions(onDelete: (() -> Unit)?): List<SwipeAction> {
  val label = stringResource(CoreRes.string.delete)
  return if (onDelete == null) emptyList()
  else listOf(SwipeAction(icon = Icons.Filled.Delete, label = label, tone = SwipeActionTone.DESTRUCTIVE, onClick = onDelete))
}
