package dev.fanfly.wingslog.feature.datalog.update.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.core.datetime.formatDuration
import dev.fanfly.wingslog.core.datetime.toClockText
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.model.id.value
import dev.fanfly.wingslog.core.nav.Screen.Companion.CROSS_SCREEN_SUCCESS_MESSAGE
import dev.fanfly.wingslog.core.template.LexiconFormatter
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.dataLogNoun
import dev.fanfly.wingslog.core.ui.common.compose.AlertDialog
import dev.fanfly.wingslog.core.ui.common.compose.EmptyState
import dev.fanfly.wingslog.core.ui.common.compose.StatusChip
import dev.fanfly.wingslog.core.ui.common.compose.WingsLogTopAppBar
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusTier
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.datalog.viewing.list.toDataLogRow
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.delete
import wingslog.core.sharedassets.generated.resources.delete_failed
import wingslog.core.sharedassets.generated.resources.retry
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_delete_body
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_delete_title
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_deleted
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_tail_mismatch
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_viewer_downloading
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_viewer_load_failed
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_viewer_missing
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_viewer_utc_offset
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * The full-screen viewer route (design §10.4). Until the panes land (T28), the body lists the
 * catalogue so the load path can be exercised end to end on a developer build.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataLogViewerScreen(
  thingId: ThingId,
  dataLogId: DataLogId,
  navController: NavController,
  viewModel: DataLogViewerViewModel = koinViewModel(
    key = "viewer-${dataLogId.value}",
    parameters = { parametersOf(thingId.value, dataLogId.value) },
  ),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val lexicon = LocalThingLexicon.current
  val snackbarHostState = remember { SnackbarHostState() }
  val deletedMessage = stringResource(
    Res.string.data_log_deleted,
    LexiconFormatter.sentenceCase(lexicon.dataLogNoun)
  )
  val deleteFailedMessage = stringResource(CoreRes.string.delete_failed)

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        DataLogViewerEvent.Deleted -> {
          navController.previousBackStackEntry?.savedStateHandle?.set(
            CROSS_SCREEN_SUCCESS_MESSAGE,
            deletedMessage
          )
          navController.popBackStack()
        }

        DataLogViewerEvent.DeleteFailed -> snackbarHostState.showSnackbar(
          deleteFailedMessage
        )
      }
    }
  }

  val ready = state as? DataLogViewerUiState.Ready
  val row = ready?.record?.toDataLogRow()
  Scaffold(
    topBar = {
      WingsLogTopAppBar(
        title = row?.startLocal?.date?.toDisplayFormat(numberOnly = false)
          ?: LexiconFormatter.titleCase(lexicon.dataLogNoun),
        onBackClick = { navController.popBackStack() },
        actions = {
          if (ready != null) {
            IconButton(onClick = viewModel::requestDelete) {
              Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(CoreRes.string.delete)
              )
            }
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { innerPadding ->
    val content = Modifier.padding(innerPadding)
      .fillMaxSize()
    when (val s = state) {
      is DataLogViewerUiState.Loading -> Box(
        content,
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
          CircularProgressIndicator()
          if (s.download != null) {
            Text(
              stringResource(Res.string.data_log_viewer_downloading),
              style = MaterialTheme.typography.bodyMedium
            )
          }
        }
      }

      is DataLogViewerUiState.Failed -> EmptyState(
        title = if (s.reason == LoadFailure.NOT_FOUND) stringResource(
          Res.string.data_log_viewer_missing,
          lexicon.dataLogNoun.singular
        )
        else stringResource(Res.string.data_log_viewer_load_failed),
        description = "",
        icon = Icons.Filled.ShowChart,
        actionText = if (s.reason == LoadFailure.NOT_FOUND) null else stringResource(
          CoreRes.string.retry
        ),
        onActionClick = if (s.reason == LoadFailure.NOT_FOUND) null else viewModel::retry,
        modifier = content,
      )

      is DataLogViewerUiState.Ready -> {
        val r = checkNotNull(row)
        LazyColumn(
          modifier = content,
          contentPadding = PaddingValues(
            horizontal = Spacing.screenPadding,
            vertical = Spacing.large
          ),
          verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
          item {
            if (r.identity.isNotBlank()) Text(
              r.identity,
              style = WingslogTypography.dataMedium
            )
            if (r.identityMismatch) StatusChip(
              label = stringResource(Res.string.data_log_tail_mismatch),
              tier = StatusTier.CAUTION
            )
            Text(
              text = listOf(
                stringResource(
                  Res.string.data_log_viewer_utc_offset,
                  r.startLocal.time.toClockText(),
                  offsetText(s.record.utc_offset_minutes)
                ),
                formatDuration(r.durationSeconds),
                r.product,
              ).filter { it.isNotBlank() }
                .joinToString(" · "),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(bottom = Spacing.medium),
            )
          }
          items(s.record.series, key = { it.column }) { series ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(series.name, style = MaterialTheme.typography.bodyMedium)
              Text(
                text = if (series.unit.isBlank()) series.short_name else "${series.short_name} · ${series.unit}",
                style = WingslogTypography.dataSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        if (s.deleting) {
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
                  stringResource(CoreRes.string.cancel)
                )
              }
            },
          )
        }
      }
    }
  }
}

/** `UTC-07:00` from the record's offset minutes. */
private fun offsetText(minutes: Int): String {
  val sign = if (minutes < 0) "-" else "+"
  val abs = kotlin.math.abs(minutes)
  return "UTC$sign${
    (abs / 60).toString()
      .padStart(2, '0')
  }:${
    (abs % 60).toString()
      .padStart(2, '0')
  }"
}
