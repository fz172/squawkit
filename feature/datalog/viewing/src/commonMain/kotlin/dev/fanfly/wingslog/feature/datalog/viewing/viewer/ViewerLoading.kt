package dev.fanfly.wingslog.feature.datalog.viewing.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_viewer_downloading
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_viewer_reading

/** The spinner, with the phase it is in when the load is slow enough to name one. */
@Composable
internal fun ViewerLoading(state: DataLogViewerUiState.Loading, modifier: Modifier = Modifier) {
  Box(
  modifier,
  contentAlignment = Alignment.Center
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Spacing.medium)
  ) {
    CircularProgressIndicator()
    val phase = when {
      state.reading -> Res.string.data_log_viewer_reading
      state.download != null -> Res.string.data_log_viewer_downloading
      else -> null
    }
    if (phase != null) {
      Text(
        stringResource(phase),
        style = MaterialTheme.typography.bodyMedium
      )
    }
  }
}
}
