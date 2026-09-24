package dev.fanfly.wingslog.feature.export.update.selection.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.core.ui.layout.ContentWidth
import dev.fanfly.wingslog.core.ui.layout.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.text.formatFileSize
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.export.update.selection.ExportUiState
import dev.fanfly.wingslog.feature.export.update.selection.joinFormats
import dev.fanfly.wingslog.feature.export.update.selection.rangeSummary
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_estimated_size
import wingslog.feature.export.sharedassets.generated.resources.export_footer_thing_count
import wingslog.feature.export.sharedassets.generated.resources.export_primary_action

@Composable
internal fun ExportBottomBar(
  state: ExportUiState.Configuring,
  onExport: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.background)
      .navigationBarsPadding(),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier
        .constrainedContentWidth(ContentWidth.Form)
        .padding(horizontal = Spacing.screenPadding)
        .padding(top = Spacing.medium, bottom = Spacing.large),
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = Spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        Icon(
          imageVector = Icons.Default.FolderZip,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(FormatTileIconSize),
        )
        Text(
          text = listOf(
            stringResource(
              Res.string.export_footer_thing_count,
              state.selectedThingIds.size
            ),
            rangeSummary(state),
            joinFormats(state.formats),
          ).joinToString(" · "),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        MetaDot()
        Text(
          text = stringResource(
            Res.string.export_estimated_size,
            state.estimatedSizeBytes.formatFileSize()
          ),
          style = WingslogTypography.dataMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
        )
      }

      Button(
        onClick = onExport,
        enabled = state.selectedThingIds.isNotEmpty() && state.formats.isNotEmpty(),
        modifier = Modifier
          .fillMaxWidth()
          .height(Spacing.buttonHeight),
        shape = RoundedCornerShape(Spacing.buttonCornerRadius),
      ) {
        Icon(
          imageVector = Icons.Default.Download,
          contentDescription = null,
          modifier = Modifier.size(Spacing.xLarge),
        )
        Spacer(Modifier.width(Spacing.small))
        Text(
          text = stringResource(Res.string.export_primary_action).uppercase(),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}

@Composable
private fun MetaDot() {
  Text(
    text = "·",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}
