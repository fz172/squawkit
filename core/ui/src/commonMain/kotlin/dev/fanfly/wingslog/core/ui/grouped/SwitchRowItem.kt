package dev.fanfly.wingslog.core.ui.grouped

/** One row of [SwitchRowCard]. */
data class SwitchRowItem(
  val title: String,
  val subtitle: String,
  val checked: Boolean,
  val enabled: Boolean,
  val onCheckedChange: (Boolean) -> Unit,
)
