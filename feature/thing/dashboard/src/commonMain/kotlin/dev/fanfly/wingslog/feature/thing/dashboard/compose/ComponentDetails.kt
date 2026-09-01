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
 * Nesting is drawn by containment, never by indentation — but a slot the template marks
 * `inline_with_parent` **flows underneath its parent's own details instead of into a card**. A
 * propeller is part of how an owner describes the engine, not somewhere to navigate into, so the
 * engine's card reads: engine, then propeller, then its blades as chips.
 *
 * Was `EngineDetails`, which reached four levels into an engine for exactly this shape. The
 * template says it now.
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
          node.inlineBlockGroups.flatten().forEach { InlineComponentBlock(it) }
          node.chipChildren.groupBy { it.row.slot.slot_key }.forEach { (_, chips) ->
            ComponentChips(
              label = chips.first().row.slot.label,
              components = chips.mapNotNull { it.row.component },
            )
          }
          node.cardChildren.forEach { ComponentDetails(it) }
        }
      }
    },
  )
}

/** An inline component: the same three lines a card shows, without a card around them. */
@Composable
private fun InlineComponentBlock(node: ComponentNode) {
  val component = node.row.component ?: return
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
    ComponentSummary(
      category = node.row.label.uppercase(),
      name = stringResource(
        CoreRes.string.make_model_template,
        component.make,
        component.model,
      ),
      serial = component.serial,
    )
    node.inlineBlockGroups.flatten().forEach { InlineComponentBlock(it) }
    node.chipChildren.groupBy { it.row.slot.slot_key }.forEach { (_, chips) ->
      ComponentChips(
        label = chips.first().row.slot.label,
        components = chips.mapNotNull { it.row.component },
      )
    }
    node.cardChildren.forEach { ComponentDetails(it) }
  }
}
