package dev.fanfly.wingslog.feature.thing.update.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.fanfly.wingslog.core.template.ComponentField
import dev.fanfly.wingslog.core.template.ComponentNode
import dev.fanfly.wingslog.core.template.ComponentPath
import dev.fanfly.wingslog.core.template.ComponentRow
import dev.fanfly.wingslog.core.template.LocalThingCapabilities
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.addableSlotsUnder
import dev.fanfly.wingslog.core.template.componentTree
import dev.fanfly.wingslog.core.template.valueOf
import dev.fanfly.wingslog.core.ui.common.compose.DashedButton
import dev.fanfly.wingslog.core.ui.common.compose.FormTextField
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.thing.update.viewmodel.EditThingViewModel
import dev.fanfly.wingslog.thing.Thing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.add
import wingslog.core.sharedassets.generated.resources.remove
import wingslog.feature.thing.update.generated.resources.make
import wingslog.feature.thing.update.generated.resources.model
import wingslog.feature.thing.update.generated.resources.serial
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.thing.update.generated.resources.Res as UpdateRes

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
  if (nodes.isEmpty() && template.addableSlotsUnder(emptyList())
      .isEmpty()
  ) return

  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    nodes.forEach { node ->
      ComponentNodeCard(
        node = node,
        viewModel = viewModel,
        showValidationErrors = showValidationErrors,
      )
    }
    // Root-level adds. A slot nested under a component is offered on that component's own card,
    // where the thing being added to is unambiguous.
    AddSlotButtons(parentPath = emptyList(), viewModel = viewModel)
  }
}

@Composable
private fun ComponentNodeCard(
  node: ComponentNode,
  viewModel: EditThingViewModel,
  showValidationErrors: Boolean,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.outlineVariant
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
  ) {
    Column(
      modifier = Modifier.padding(Spacing.medium),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      ComponentBlock(node, viewModel, showValidationErrors)

      // Slots the template marks inline flow underneath this card's own fields rather than into a
      // card of their own — a propeller under its engine, its blades under that.
      node.inlineGroups.forEach { group ->
        InlineGroup(group, viewModel, showValidationErrors)
      }

      node.cardChildren.forEach { child ->
        ComponentNodeCard(child, viewModel, showValidationErrors)
      }

      AddSlotButtons(parentPath = node.row.path, viewModel = viewModel)
    }
  }
}

/** One component's heading, remove control and fields — no card of its own. */
@Composable
private fun ComponentBlock(
  node: ComponentNode,
  viewModel: EditThingViewModel,
  showValidationErrors: Boolean,
  showHeader: Boolean = true,
) {
  val row = node.row
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    if (showHeader) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = row.label,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
        if (row.canRemove) {
          IconButton(onClick = { viewModel.onRemoveComponent(row.path) }) {
            Icon(
              Icons.Default.Close,
              contentDescription = stringResource(CoreRes.string.remove),
            )
          }
        }
      }
    }
    ComponentFields(node, viewModel, showValidationErrors)
  }
}

/**
 * The slot's fields, on one line each or packed when the template asks for it.
 *
 * Compact puts make on its own line and pairs model with serial beside it — the shape an owner
 * reads a plate in, rather than three stacked inputs each half empty.
 */
@Composable
private fun ComponentFields(
  node: ComponentNode,
  viewModel: EditThingViewModel,
  showValidationErrors: Boolean,
) {
  val row = node.row
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

/**
 * A repeating inline slot: one heading, then its instances' inputs packed together.
 *
 * Blade serials are the case — four of them belong under one "Blade" heading on two lines, not as
 * four headed blocks.
 */
@Composable
private fun InlineGroup(
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
      node.inlineGroups.forEach {
        InlineGroup(
          it,
          viewModel,
          showValidationErrors
        )
      }
      node.cardChildren.forEach {
        ComponentNodeCard(it, viewModel, showValidationErrors)
      }
      // "Add Blade" belongs to the propeller, which has no card of its own to carry it.
      AddSlotButtons(parentPath = node.row.path, viewModel = viewModel)
    }
    return
  }
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    Text(
      text = first.slot.label,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
    )
    // Two to a line, with room between the rows. `maxItemsInEachRow` rather than a width fraction:
    // a weighted child in a FlowRow otherwise takes the whole line and the pairing never happens.
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
      maxItemsInEachRow = 2,
    ) {
      group.forEach { node ->
        val row = node.row
        row.fields.filter { it.isVisibleOn(row) }
          .forEach { field ->
            ComponentFieldInput(
              row = row,
              field = field,
              viewModel = viewModel,
              showValidationErrors = showValidationErrors,
              modifier = Modifier.weight(1f),
              // Numbered by instance rather than by field: the heading already said "Blade", so the
              // input only has to say which one.
              labelOverride = row.label,
              // Removing is a cross on the field itself, where the old form put it. A button below
              // the group would not say which one it drops.
              onRemove = { viewModel.onRemoveComponent(row.path) }.takeIf { row.canRemove },
            )
          }
      }
    }
  }
}

@Composable
private fun ComponentFieldInput(
  row: ComponentRow,
  field: ComponentField,
  viewModel: EditThingViewModel,
  showValidationErrors: Boolean,
  modifier: Modifier = Modifier,
  labelOverride: String? = null,
  onRemove: (() -> Unit)? = null,
) {
  FormTextField(
    value = row.component?.valueOf(field)
      .orEmpty(),
    onValueChange = { viewModel.onComponentFieldChanged(row.path, field, it) },
    // Just "Make" — the heading above already said which component this is, and "Engine 2 Make"
    // on every input reads as noise once there are three of them.
    label = labelOverride ?: field.caption(),
    modifier = modifier,
    isError = field == ComponentField.SERIAL &&
      showValidationErrors &&
      row.component?.serial?.isBlank() == true,
    keyboardOptions = if (field == ComponentField.SERIAL) {
      KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
    } else {
      KeyboardOptions.Default
    },
    trailingIcon = onRemove?.let {
      {
        IconButton(onClick = it) {
          Icon(
            Icons.Default.Close,
            contentDescription = stringResource(CoreRes.string.remove),
          )
        }
      }
    },
  )
}

/**
 * A serial is hidden when the preset does not ask for one at creation, or the slot expects none.
 * Hidden means not required — `EditThingUiState` relaxes identically, or the form blocks on a
 * field that is not on screen.
 */
@Composable
private fun ComponentField.isVisibleOn(row: ComponentRow): Boolean =
  this != ComponentField.SERIAL ||
    (row.slot.serial_expected && LocalThingCapabilities.current.component_serial_prompt)

@Composable
private fun AddSlotButtons(
  parentPath: ComponentPath,
  viewModel: EditThingViewModel
) {
  val addable = LocalThingTemplate.current.addableSlotsUnder(parentPath)
  if (addable.isEmpty()) return
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    addable.forEach { slot ->
      // Dashed, as every add control on this form has been: it reads as a placeholder for
      // something not there yet rather than as an action on what is.
      DashedButton(
        label = "${stringResource(CoreRes.string.add)} ${slot.label}",
        onClick = { viewModel.onAddComponent(parentPath, slot) },
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

/**
 * The three field captions, from `strings.xml` rather than the template.
 *
 * Make, model and serial are the same three words for every component of every kind — they name
 * the field, not the domain, so a template declaring them would be six presets repeating one
 * vocabulary. The domain word is the slot label this is appended to.
 */
@Composable
private fun ComponentField.caption(): String = when (this) {
  ComponentField.MAKE -> stringResource(UpdateRes.string.make)
  ComponentField.MODEL -> stringResource(UpdateRes.string.model)
  ComponentField.SERIAL -> stringResource(UpdateRes.string.serial)
}
