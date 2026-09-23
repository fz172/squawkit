package dev.fanfly.wingslog.feature.thing.update.edit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.ComponentNode
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.thing.update.edit.EditThingViewModel

/**
 * One component and what hangs off it, as a labelled run of fields. No card at any depth: the
 * heading says where a component starts, and the fields are the only boxes on the form.
 */
@Composable
internal fun ComponentNodeCard(
  node: ComponentNode,
  viewModel: EditThingViewModel,
  showValidationErrors: Boolean,
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    ComponentBlock(node, viewModel, showValidationErrors)
    ChildSlots(node, viewModel, showValidationErrors)
  }
}
