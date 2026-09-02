package dev.fanfly.wingslog.feature.thing.dashboard.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.template.ComponentGroup
import dev.fanfly.wingslog.core.template.ComponentNode
import dev.fanfly.wingslog.core.template.componentGroups
import dev.fanfly.wingslog.core.ui.theme.Spacing

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
    // Joined here rather than through the two-slot template string: a component with only a make
    // would otherwise render it followed by a dangling separator.
    name = listOf(component.make, component.model)
      .filter { it.isNotBlank() }
      .joinToString(" "),
    serial = component.serial,
    content = if (node.children.isEmpty()) {
      null
    } else {
      { ComponentChildren(node) }
    },
  )
}

/**
 * Everything under a component: its inline blocks first, then its remaining children in the order
 * the template declares them.
 *
 * Inline blocks lead because that is what `inline_with_parent` means — they are part of describing
 * this component, not things beside it. The rest keeps declaration order, chips and cards
 * interleaved as the template wrote them.
 */
@Composable
private fun ComponentChildren(node: ComponentNode) {
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
    node.inlineBlockGroups.flatten()
      .forEach { InlineComponentBlock(it) }
    ComponentGroups(node.groupedChildren)
  }
}

/**
 * A run of siblings, each drawn as whatever its slot asks for — a card, or a set of chips.
 *
 * The grouping is the template's, not this composable's: [componentGroups] merges a slot's
 * components into one chip block and leaves everything else standing alone, in order.
 */
@Composable
fun ComponentGroups(nodes: List<ComponentNode>) {
  nodes.componentGroups()
    .forEach { group ->
      when (group) {
        is ComponentGroup.Chips -> ComponentChips(group.nodes)
        is ComponentGroup.Card -> ComponentDetails(group.node)
      }
    }
}

/** An inline component: the same three lines a card shows, without a card around them. */
@Composable
private fun InlineComponentBlock(node: ComponentNode) {
  val component = node.row.component ?: return
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
    ComponentSummary(
      category = node.row.label.uppercase(),
      name = listOf(component.make, component.model)
        .filter { it.isNotBlank() }
        .joinToString(" "),
      serial = component.serial,
    )
    ComponentChildren(node)
  }
}
