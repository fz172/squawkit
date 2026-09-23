package dev.fanfly.wingslog.feature.export.update.selection.setup

import androidx.compose.ui.graphics.vector.ImageVector
import dev.fanfly.wingslog.feature.export.datamanager.ExportFormat

internal data class FormatChoice(
  val format: ExportFormat,
  val icon: ImageVector,
)
