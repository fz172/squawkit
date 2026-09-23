package dev.fanfly.wingslog.feature.thing.update.edit.components

import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.template.ComponentField
import dev.fanfly.wingslog.core.template.ComponentNode
import dev.fanfly.wingslog.core.template.ComponentRow
import dev.fanfly.wingslog.core.template.LocalThingCapabilities
import dev.fanfly.wingslog.feature.thing.update.edit.EditThingUiState
import dev.fanfly.wingslog.feature.thing.update.edit.EditThingViewModel
import wingslog.feature.thing.update.generated.resources.make
import wingslog.feature.thing.update.generated.resources.model
import wingslog.feature.thing.update.generated.resources.serial

/**
 * The slot's fields: the make/model/serial triple, then whatever else the slot declares.
 *
 * Compact puts make on its own line and pairs model with serial beside it — the shape an owner
 * reads a plate in, rather than three stacked inputs each half empty.
 *
 * **The declared fields are drawn unconditionally**, after the triple and outside every branch
 * above. Hanging them off the end of the compact path meant a tyre — which packs nothing, so it
 * takes the plain branch — rendered neither its position nor its pressure, on a form where the
 * only evidence was their absence.
 */
@Composable
internal fun ComponentFields(
  node: ComponentNode,
  viewModel: EditThingViewModel,
  showValidationErrors: Boolean,
) {
  val row = node.row
  TripleFields(row, viewModel, showValidationErrors)
  SlotSpecFields(row, viewModel)
}

/**
 * A serial is hidden when the preset does not ask for one at creation, or the slot expects none.
 * Hidden means not required — `EditThingUiState` relaxes identically, or the form blocks on a
 * field that is not on screen.
 */
@Composable
internal fun ComponentField.isVisibleOn(row: ComponentRow): Boolean =
  this != ComponentField.SERIAL ||
    (row.slot.serial_expected && LocalThingCapabilities.current.component_serial_prompt)
