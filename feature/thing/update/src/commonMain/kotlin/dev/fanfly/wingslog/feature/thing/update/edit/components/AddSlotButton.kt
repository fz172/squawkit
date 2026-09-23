package dev.fanfly.wingslog.feature.thing.update.edit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.template.ComponentPath
import dev.fanfly.wingslog.core.template.ComponentRow
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.addableSlotsUnder
import dev.fanfly.wingslog.core.ui.common.compose.DashedButton
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.thing.update.edit.EditThingViewModel
import dev.fanfly.wingslog.thing.ComponentSlot
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.add
import wingslog.feature.thing.update.generated.resources.make
import wingslog.feature.thing.update.generated.resources.model
import wingslog.feature.thing.update.generated.resources.serial
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/** Add control for [slot] under [parentPath]; nothing when the slot cannot take another. */
@Composable
internal fun AddSlotButton(
  parentPath: ComponentPath,
  slot: ComponentSlot,
  existing: List<ComponentRow>,
  viewModel: EditThingViewModel
) {
  // `existing` is what caps a slot: a car's engine is repeatable so an EV can have none, and
  // `max_instances: 1` so a hatchback is not offered a second one.
  val addable =
    LocalThingTemplate.current.addableSlotsUnder(parentPath, existing)
  if (addable.none { it.slot_key == slot.slot_key }) return
  // Dashed, as every add control on this form has been: it reads as a placeholder for
  // something not there yet rather than as an action on what is.
  // Half width only for a slot whose instances pack into a group — Add Blade sits beside the
  // serials it adds to. Add Engine spans the form as it always has.
  //
  // Keyed on the packing, not on `compact_fields`: the engine sets that too, because its own
  // make, model and serial pair up. One flag was doing two jobs and shortened the wrong button.
  if (slot.inline_with_parent && slot.repeatable) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
      DashedButton(
        label = "${stringResource(CoreRes.string.add)} ${slot.label}",
        onClick = { viewModel.onAddComponent(parentPath, slot) },
        modifier = Modifier.weight(1f),
        height = 44.dp,
      )
      Spacer(Modifier.weight(1f))
    }
  } else {
    DashedButton(
      label = "${stringResource(CoreRes.string.add)} ${slot.label}",
      onClick = { viewModel.onAddComponent(parentPath, slot) },
      modifier = Modifier.fillMaxWidth(),
    )
  }
}
