package dev.fanfly.wingslog.feature.thing.update.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.template.ComponentField
import dev.fanfly.wingslog.core.template.ComponentPath
import dev.fanfly.wingslog.core.template.ComponentRow
import dev.fanfly.wingslog.core.template.LocalThingCapabilities
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.addableSlotsUnder
import dev.fanfly.wingslog.core.template.componentRows
import dev.fanfly.wingslog.core.template.valueOf
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
  val rows = template.componentRows(thing)
  if (rows.isEmpty()) return

  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    rows.forEach { row ->
      ComponentRowCard(
        row = row,
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
private fun ComponentRowCard(
  row: ComponentRow,
  viewModel: EditThingViewModel,
  showValidationErrors: Boolean,
) {
  // Indentation is the only thing depth changes. Nesting is already legible from the order and the
  // labels, and a card-in-a-card at four levels deep leaves no room to type on a phone.
  val indent = (row.depth * 12).dp
  val askForSerials = LocalThingCapabilities.current.component_serial_prompt

  Card(
    modifier = Modifier.fillMaxWidth().padding(start = indent),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
  ) {
    Column(modifier = Modifier.padding(Spacing.medium)) {
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

      ComponentFieldRow(
        row = row,
        field = ComponentField.MAKE,
        viewModel = viewModel,
      )
      ComponentFieldRow(
        row = row,
        field = ComponentField.MODEL,
        viewModel = viewModel,
      )
      // Two gates, and they mean different things. `serial_expected` is the template saying this
      // kind of component has a serial worth recording; the capability is the preset saying not to
      // ask at creation at all. Hidden either way means not required — EditThingUiState relaxes
      // identically, or the form blocks on a field that is not on screen.
      if (row.slot.serial_expected && askForSerials) {
        ComponentFieldRow(
          row = row,
          field = ComponentField.SERIAL,
          viewModel = viewModel,
          isError = showValidationErrors && row.component?.serial?.isBlank() == true,
        )
      }

      AddSlotButtons(parentPath = row.path, viewModel = viewModel)
    }
  }
}

@Composable
private fun ComponentFieldRow(
  row: ComponentRow,
  field: ComponentField,
  viewModel: EditThingViewModel,
  isError: Boolean = false,
) {
  FormTextField(
    value = row.component?.valueOf(field).orEmpty(),
    onValueChange = { viewModel.onComponentFieldChanged(row.path, field, it) },
    label = "${row.label} ${field.caption()}",
    isError = isError,
    keyboardOptions = if (field == ComponentField.SERIAL) {
      KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
    } else {
      KeyboardOptions.Default
    },
  )
}

@Composable
private fun AddSlotButtons(parentPath: ComponentPath, viewModel: EditThingViewModel) {
  val addable = LocalThingTemplate.current.addableSlotsUnder(parentPath)
  if (addable.isEmpty()) return
  Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
    addable.forEach { slot ->
      TextButton(onClick = { viewModel.onAddComponent(parentPath, slot) }) {
        Icon(Icons.Default.Add, contentDescription = null)
        Text(" ${stringResource(CoreRes.string.add)} ${slot.label}")
      }
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
