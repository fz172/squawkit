package dev.fanfly.wingslog.feature.thing.dashboard.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.template.ComponentNode
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.make_model_template
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * One component and everything attached to it (#729).
 *
 * **Nesting is drawn by containment, not by indentation** — a propeller renders *inside* its
 * engine's card, its blades as chips directly beneath it. That is how this card read before the
 * tree became template-driven, and the card border already says what an indent would.
 *
 * Was `EngineDetails`, which reached four levels into an engine for a propeller, a hub and blades.
 * The recursion replaces the reaching; the shape on screen is the same.
 */
@Composable
fun ComponentDetails(node: ComponentNode) {
  val component = node.row.component ?: return
  ComponentCard(
    category = node.row.label.uppercase(),
    name = stringResource(
      CoreRes.string.make_model_template,
      component.make,
      component.model,
    ),
    serial = component.serial,
    content = if (node.children.isEmpty()) {
      null
    } else {
      {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
          node.cardChildren.forEach { ComponentDetails(it) }
          // A matched set — blades, told apart by serial — as chips under the part they attach to.
          node.chipChildren.groupBy { it.row.slot.slot_key }
            .forEach { (_, chips) ->
              ComponentChips(
                label = chips.first().row.slot.label,
                components = chips.mapNotNull { it.row.component },
              )
            }
        }
      }
    },
  )
}
