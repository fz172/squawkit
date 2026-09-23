package dev.fanfly.wingslog.feature.thing.update.edit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.ComponentRow
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.thing.update.edit.EditThingViewModel
import wingslog.feature.thing.update.generated.resources.model
import wingslog.feature.thing.update.generated.resources.serial

/** Make, model and serial — the three every component carries, however the slot packs them. */
@Composable
internal fun TripleFields(
  row: ComponentRow,
  viewModel: EditThingViewModel,
  showValidationErrors: Boolean,
) {
  val visible = row.fields.filter { it.isVisibleOn(row) }
  if (visible.isEmpty()) return

  if (!row.slot.compact_fields) {
    visible.forEach { field ->
      ComponentFieldInput(
        row,
        field,
        viewModel,
        showValidationErrors,
        Modifier.fillMaxWidth()
      )
    }
    return
  }

  row.leadingFields.filter { it in visible }
    .forEach {
      ComponentFieldInput(
        row,
        it,
        viewModel,
        showValidationErrors,
        Modifier.fillMaxWidth()
      )
    }
  val paired = row.pairedFields.filter { it in visible }
  if (paired.isEmpty()) return
  Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
    paired.forEach { field ->
      ComponentFieldInput(
        row, field, viewModel, showValidationErrors, Modifier.weight(1f),
      )
    }
  }
}
