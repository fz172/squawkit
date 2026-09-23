package dev.fanfly.wingslog.feature.thing.update.edit.components

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.template.ComponentNode
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.slotsUnder
import dev.fanfly.wingslog.feature.thing.update.edit.EditThingViewModel
import wingslog.core.sharedassets.generated.resources.add

/**
 * [node]'s children, slot by slot, each followed by its own add button.
 *
 * Inline slots come first — a propeller under its engine, its blades under that — then slots that
 * nest into cards of their own.
 */
@Composable
internal fun ChildSlots(
  node: ComponentNode,
  viewModel: EditThingViewModel,
  showValidationErrors: Boolean,
) {
  val existing = node.children.map { it.row }
  LocalThingTemplate.current.slotsUnder(node.row.path)
    .sortedBy { !it.inline_with_parent }
    .forEach { slot ->
      val filling =
        node.children.filter { it.row.slot.slot_key == slot.slot_key }
      if (filling.isNotEmpty()) {
        if (slot.inline_with_parent) {
          InlineGroup(filling, viewModel, showValidationErrors)
        } else {
          filling.forEach {
            ComponentNodeCard(
              it,
              viewModel,
              showValidationErrors
            )
          }
        }
      }
      AddSlotButton(node.row.path, slot, existing, viewModel)
    }
}
