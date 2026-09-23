package dev.fanfly.wingslog.feature.export.update.selection.running

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.export.datamanager.ExportProgressStep
import dev.fanfly.wingslog.feature.export.update.selection.ExportUiState
import dev.fanfly.wingslog.feature.export.update.selection.result.ResultSecondaryButton
import dev.fanfly.wingslog.feature.export.update.selection.result.ResultShell
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_running_stage_counter
import wingslog.feature.export.sharedassets.generated.resources.export_running_title
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

@Composable
internal fun RunningContent(
  state: ExportUiState.Running,
  modifier: Modifier,
  onCancel: () -> Unit,
) {
  val phases = exportRunningPhases()
  val currentIndex = phases.indexOf(state.step)
    .coerceAtLeast(0)
  ResultShell(
    modifier = modifier,
    heroIcon = Icons.Default.FolderZip,
    heroColor = MaterialTheme.colorScheme.primary,
    heroContainer = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
    title = stringResource(Res.string.export_running_title),
    subtitle = state.step.label(),
    body = {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
          LinearProgressIndicator(
            progress = { state.percent / 100f },
            modifier = Modifier.fillMaxWidth(),
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "${state.percent}%",
              style = WingslogTypography.dataMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = stringResource(
                Res.string.export_running_stage_counter,
                currentIndex + 1,
                phases.size,
              ),
              style = WingslogTypography.dataMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Spacing.cardCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
              width = Spacing.hairline,
              color = MaterialTheme.colorScheme.outlineVariant,
              shape = RoundedCornerShape(Spacing.cardCornerRadius),
            )
            .padding(Spacing.large),
          verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
          phases.forEachIndexed { index, step ->
            ProgressStepRow(
              label = step.label(),
              active = index == currentIndex,
              complete = index < currentIndex,
            )
          }
        }
      }
    },
    actions = {
      ResultSecondaryButton(
        label = stringResource(CoreRes.string.cancel),
        icon = null,
        onClick = onCancel,
      )
    },
  )
}

private fun exportRunningPhases(): List<ExportProgressStep> = listOf(
  ExportProgressStep.COLLECTING_DATA,
  ExportProgressStep.BUILDING_ARCHIVE,
  ExportProgressStep.COMPRESSING_ARCHIVE,
  ExportProgressStep.SAVING_FILE,
  ExportProgressStep.UPLOADING_ARCHIVE,
)
