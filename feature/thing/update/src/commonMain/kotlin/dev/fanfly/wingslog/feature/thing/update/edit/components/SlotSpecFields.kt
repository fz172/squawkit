package dev.fanfly.wingslog.feature.thing.update.edit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.ComponentRow
import dev.fanfly.wingslog.core.template.specValue
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.thing.update.edit.EditThingViewModel
import dev.fanfly.wingslog.feature.thing.update.edit.SpecFieldInput
import dev.fanfly.wingslog.thing.SpecField
import wingslog.feature.thing.update.generated.resources.make

/**
 * The slot's own declared fields — a tyre's position and its normal pressure.
 *
 * Paired two to a row like the triple above, because these are short: a position picked from a
 * list and a two-digit pressure each read fine at half width, and stacking them would make a set
 * of four tyres four times as tall for no gain.
 */
@Composable
internal fun SlotSpecFields(row: ComponentRow, viewModel: EditThingViewModel) {
  if (row.slot.spec_fields.isEmpty()) return
  row.slot.spec_fields.chunked(2)
    .forEach { pair ->
      Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        pair.forEach { field ->
          SlotSpecFieldInput(row, field, viewModel, Modifier.weight(1f))
        }
        if (pair.size == 1) Spacer(Modifier.weight(1f))
      }
    }
}

/** Binds one of a slot's declared fields to the component at [row]; [SpecFieldInput] draws it. */
@Composable
private fun SlotSpecFieldInput(
  row: ComponentRow,
  field: SpecField,
  viewModel: EditThingViewModel,
  modifier: Modifier = Modifier,
) {
  SpecFieldInput(
    field = field,
    value = row.component?.specValue(field.key)
      .orEmpty(),
    onValueChange = { viewModel.onComponentSpecChanged(row.path, field, it) },
    modifier = modifier,
  )
}
