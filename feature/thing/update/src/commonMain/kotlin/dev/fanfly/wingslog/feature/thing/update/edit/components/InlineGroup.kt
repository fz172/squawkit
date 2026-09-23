package dev.fanfly.wingslog.feature.thing.update.edit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.ComponentNode
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.thing.update.edit.EditThingViewModel
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.remove
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * A repeating inline slot: one heading, then its instances' inputs packed together.
 *
 * Blade serials are the case — four of them belong under one "Blade" heading on two lines, not as
 * four headed blocks.
 */
@Composable
internal fun InlineGroup(
  group: List<ComponentNode>,
  viewModel: EditThingViewModel,
  showValidationErrors: Boolean,
) {
  val first = group.first().row
  if (!first.slot.repeatable) {
    // One block per component, and then whatever hangs off it. The recursion is the point: a
    // propeller is inline under its engine and its blades are inline under the propeller, so
    // rendering only this node's own fields dropped the blades entirely.
    group.forEach { node ->
      ComponentBlock(node, viewModel, showValidationErrors)
      // "Add Blade" belongs to the propeller, which has no card of its own to carry it.
      ChildSlots(node, viewModel, showValidationErrors)
    }
    return
  }
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    Text(
      text = first.slot.label,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // chunked(2) into Rows, which is how this form has always laid these out. A weighted child in
    // a FlowRow takes the whole line instead of half of it, so the pairing silently never happens
    // — the trailing Spacer is what keeps a lone last input at half width rather than stretching.
    group.chunked(2)
      .forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
          pair.forEach { node ->
            val row = node.row
            // The field and its own remove control, as one cell. The cross sits beside the input
            // rather than inside it: a dense field is shorter than a touch target, and two across
            // is what leaves room for a full-size one.
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              row.fields.filter { it.isVisibleOn(row) }
                .forEach { field ->
                  ComponentFieldInput(
                    row = row,
                    field = field,
                    viewModel = viewModel,
                    showValidationErrors = showValidationErrors,
                    modifier = Modifier.weight(1f),
                    // Numbered by instance rather than by field: the heading already said "Blade",
                    // so the input only has to say which one.
                    labelOverride = row.label,
                    dense = true,
                  )
                }
              if (row.canRemove) {
                IconButton(onClick = { viewModel.onRemoveComponent(row.path) }) {
                  Icon(
                    Icons.Default.Close,
                    // "Remove Blade 2" — a bare "Remove" four times over says nothing to a screen reader.
                    contentDescription = "${stringResource(CoreRes.string.remove)} ${row.label}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
          if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
      }
  }
}
