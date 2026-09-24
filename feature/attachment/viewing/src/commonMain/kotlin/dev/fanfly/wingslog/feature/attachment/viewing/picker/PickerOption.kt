package dev.fanfly.wingslog.feature.attachment.viewing.picker

import androidx.compose.ui.graphics.vector.ImageVector

internal class PickerOption(
  val icon: ImageVector,
  val title: String,
  val description: String,
  val enabled: Boolean,
  val onClick: () -> Unit,
)

internal const val DISABLED_ALPHA = 0.38f
