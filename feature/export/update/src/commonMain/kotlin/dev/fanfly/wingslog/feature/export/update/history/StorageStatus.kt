package dev.fanfly.wingslog.feature.export.update.history

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource

internal data class StorageStatus(
  val icon: ImageVector,
  val color: Color,
  val label: StringResource,
)
