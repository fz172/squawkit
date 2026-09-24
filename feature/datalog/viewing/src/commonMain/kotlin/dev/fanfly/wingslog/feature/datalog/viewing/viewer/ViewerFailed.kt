package dev.fanfly.wingslog.feature.datalog.viewing.viewer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.dataLogNoun
import dev.fanfly.wingslog.core.ui.list.EmptyState
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.retry
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_viewer_load_failed
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_viewer_missing
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/** Missing or unreadable: the one offers no retry, the other does. */
@Composable
internal fun ViewerFailed(
  state: DataLogViewerUiState.Failed,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val lexicon = LocalThingLexicon.current
  EmptyState(
    title = if (state.reason == LoadFailure.NOT_FOUND) stringResource(
      Res.string.data_log_viewer_missing,
      lexicon.dataLogNoun.singular
    )
    else stringResource(Res.string.data_log_viewer_load_failed),
    description = "",
    icon = Icons.AutoMirrored.Filled.ShowChart,
    actionText = if (state.reason == LoadFailure.NOT_FOUND) null else stringResource(
      CoreRes.string.retry
    ),
    onActionClick = if (state.reason == LoadFailure.NOT_FOUND) null else onRetry,
    modifier = modifier,
  )
}
