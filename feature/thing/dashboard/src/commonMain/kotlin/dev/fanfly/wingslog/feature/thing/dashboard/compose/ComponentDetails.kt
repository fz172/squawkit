package dev.fanfly.wingslog.feature.thing.dashboard.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.core.template.ComponentGroup
import dev.fanfly.wingslog.core.template.ComponentNode
import dev.fanfly.wingslog.core.template.componentGroups
import dev.fanfly.wingslog.core.template.joinAsPhrase
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography

/**
 * The component tree as flat lines — slot, make and model, serial — in the order the template
 * declares them (#729). No boxes: an engine's propeller and its blades simply follow it, and a
 * hairline separates one top-level component from the next, so two engines read as two blocks.
 */
@Composable
fun ComponentTree(nodes: List<ComponentNode>, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    nodes.componentGroups()
      .forEachIndexed { index, group ->
        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ComponentGroupLines(group)
      }
  }
}

@Composable
private fun ComponentGroupLines(group: ComponentGroup) {
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    when (group) {
      is ComponentGroup.Card -> ComponentLines(group.node)
      is ComponentGroup.Chips -> SetLines(group.nodes)
    }
  }
}

/** One component, then everything attached to it: inline slots first, as the template means. */
@Composable
private fun ComponentLines(node: ComponentNode) {
  val component = node.row.component ?: return
  ComponentLine(
    label = node.row.label,
    // Joined here rather than through a two-slot string: a component with only a make would
    // otherwise render it followed by a dangling separator.
    name = listOf(component.make, component.model).joinAsPhrase(),
    serial = component.serial,
  )
  node.inlineBlockGroups.flatten()
    .forEach { ComponentLines(it) }
  node.groupedChildren.componentGroups()
    .forEach { ComponentGroupLines(it) }
}

/**
 * A matched set — blades, tyres. Parts known only by a serial share one line, "88228 · 88279";
 * anything that names a make, a position or a declared value gets a line each.
 */
@Composable
private fun SetLines(nodes: List<ComponentNode>) {
  val chips = nodes.mapNotNull { it.row.chipLines }
  if (chips.isEmpty()) return
  if (chips.all { it.serial.isBlank() && it.specs.isEmpty() }) {
    ComponentLine(
      label = nodes.first().row.slot.label,
      name = chips.map { it.headline }
        .filter { it.isNotBlank() }
        .joinToString(SEPARATOR),
      serial = "",
      nameIsIdentifier = true,
    )
    return
  }
  chips.forEach { chip ->
    val specs = chip.specs.map { "${it.label} ${it.value}" }
    ComponentLine(
      label = chip.label,
      name = (listOf(chip.headline) + specs).filter { it.isNotBlank() }
        .joinToString(SEPARATOR),
      serial = chip.serial,
    )
  }
}

@Composable
private fun ComponentLine(
  label: String,
  name: String,
  serial: String,
  /** The name is itself a serial, so it takes the mono face a serial gets. */
  nameIsIdentifier: Boolean = false,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.width(LABEL_WIDTH)
        .alignByBaseline(),
    )
    Text(
      text = name,
      style = if (nameIsIdentifier) WingslogTypography.dataSmall else MaterialTheme.typography.bodyMedium,
      color = if (nameIsIdentifier) {
        MaterialTheme.colorScheme.onSurfaceVariant
      } else {
        MaterialTheme.colorScheme.onSurface
      },
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f)
        .alignByBaseline(),
    )
    // Omitted when there is none: a home has no serial to give.
    if (serial.isNotBlank()) {
      Text(
        text = serial,
        style = WingslogTypography.dataSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.alignByBaseline(),
      )
    }
  }
}

private const val SEPARATOR = " · "

/** One column for every line, so names start on the same edge. Fits "Front Left" and "Propeller". */
private val LABEL_WIDTH = Spacing.huge * 3
