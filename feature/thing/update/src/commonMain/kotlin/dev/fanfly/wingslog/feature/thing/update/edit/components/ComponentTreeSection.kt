package dev.fanfly.wingslog.feature.thing.update.edit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.template.LocalThingCapabilities
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.addableSlotsUnder
import dev.fanfly.wingslog.core.template.componentTree
import dev.fanfly.wingslog.core.template.slotsUnder
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.thing.update.edit.EditThingViewModel
import dev.fanfly.wingslog.thing.Thing
import wingslog.core.sharedassets.generated.resources.add

/**
 * The component tree, drawn from the slots the template declares (#729).
 *
 * Replaces the airframe/engine/propeller/hub/blade composables, which every template got — a bike
 * offered to add an engine, and that engine arrived with a propeller and numbered blades.
 *
 * **The widget is not template-driven; the slots, labels and nesting are** (`pivot_rollout_design.md`
 * §6). One row renderer handles every depth, so a preset adding a slot needs no code here.
 *
 * Renders nothing at all when the template declares no components — `custom` and `home` — which is
 * a real state rather than a defensive branch. The section header is the caller's business, so an
 * empty tree leaves no orphaned heading behind.
 */
@Composable
fun ComponentTreeSection(
  thing: Thing,
  viewModel: EditThingViewModel,
  showValidationErrors: Boolean,
) {
  val template = LocalThingTemplate.current
  if (!LocalThingCapabilities.current.components) return
  val nodes = template.componentTree(thing)
  // Not `nodes.isEmpty()`: removing the last engine emptied the tree and took the Add control with
  // it, leaving no way to add one back. The section is empty only when the template declares
  // nothing to add either — home and custom.
  if (nodes.isEmpty() && template.addableSlotsUnder(
      emptyList(),
      nodes.map { it.row })
      .isEmpty()
  ) return

  // A wide gap between top-level components and their add buttons: without cards it is the only
  // thing that says Add Blade belongs to the propeller above and Add Engine to the form.
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge)) {
    // Each slot's add button directly under that slot's components. All adds at the end put
    // "Add Propulsion" below Steering while the new card appeared above it.
    template.slotsUnder(emptyList())
      .forEach { slot ->
        nodes.filter { it.row.slot.slot_key == slot.slot_key }
          .forEach { node ->
            ComponentNodeCard(
              node = node,
              viewModel = viewModel,
              showValidationErrors = showValidationErrors,
            )
          }
        AddSlotButton(
          parentPath = emptyList(),
          slot = slot,
          existing = nodes.map { it.row },
          viewModel = viewModel,
        )
      }
  }
}
