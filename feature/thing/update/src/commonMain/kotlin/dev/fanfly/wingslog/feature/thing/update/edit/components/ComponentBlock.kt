package dev.fanfly.wingslog.feature.thing.update.edit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.ComponentNode
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.thing.update.edit.EditThingViewModel
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.remove
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/** One component's heading, remove control and fields — no card of its own. */
@Composable
internal fun ComponentBlock(
  node: ComponentNode,
  viewModel: EditThingViewModel,
  showValidationErrors: Boolean,
  showHeader: Boolean = true,
) {
  val row = node.row
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    if (showHeader) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = row.label,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (row.canRemove) {
          // Named, not a bare cross: beside a heading it has to say what it does.
          TextButton(onClick = { viewModel.onRemoveComponent(row.path) }) {
            Text(stringResource(CoreRes.string.remove))
          }
        }
      }
    }
    ComponentFields(node, viewModel, showValidationErrors)
  }
}
