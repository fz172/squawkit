package dev.fanfly.wingslog.feature.attachment.viewing.picker

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.feature.attachment.model.PickedDataLog

/**
 * The data log picker a form screen supplies: the option's label and the body that replaces the
 * options once it is chosen, exactly as *Add link* swaps to the URL field (design §9.2).
 */
class DataLogPickerSlot(
  val label: String,
  val body: @Composable (onAttach: (List<PickedDataLog>) -> Unit, onCancel: () -> Unit) -> Unit,
)
