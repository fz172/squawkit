package dev.fanfly.wingslog.feature.export.update.selection.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.grouped.GroupedSection
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.feature.export.datamanager.ExportFormat
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_format_pick_one
import wingslog.feature.export.sharedassets.generated.resources.export_formats_section

@Composable
internal fun FormatSection(
  formats: Set<ExportFormat>,
  onToggleFormat: (ExportFormat) -> Unit,
) {
  GroupedSection(title = stringResource(Res.string.export_formats_section)) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
      FORMAT_CHOICES.forEach { choice ->
        val selected = choice.format in formats
        val isLastSelected = selected && formats.size == 1
        FormatTile(
          label = choice.format.name,
          icon = choice.icon,
          selected = selected,
          onClick = {
            if (!isLastSelected) {
              onToggleFormat(choice.format)
            }
          },
          modifier = Modifier.weight(1f),
        )
      }
    }
    // The picker enforces at least one format; the advisory only surfaces in the edge case.
    if (formats.isEmpty()) {
      Spacer(Modifier.height(Spacing.small))
      Text(
        text = stringResource(Res.string.export_format_pick_one),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.statusColors.caution.accent,
        modifier = Modifier.padding(start = Spacing.extraSmall),
      )
    }
  }
}

private val FORMAT_CHOICES = listOf(
  FormatChoice(ExportFormat.PDF, Icons.Default.PictureAsPdf),
  FormatChoice(ExportFormat.CSV, Icons.Default.Description),
  FormatChoice(ExportFormat.XLSX, Icons.Default.TableView),
)
