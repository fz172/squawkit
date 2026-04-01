package dev.fanfly.wingslog.feature.aircraft.overview.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.EngineHourRule
import dev.fanfly.wingslog.aircraft.InspectionComponentType
import dev.fanfly.wingslog.aircraft.InspectionRule
import dev.fanfly.wingslog.aircraft.OnConditionRule
import dev.fanfly.wingslog.aircraft.TimeRule
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.aircraft.overview.data.InspectionCardWithStatus
import wingslog.feature.aircraft.generated.resources.airframe
import wingslog.feature.aircraft.generated.resources.component
import wingslog.feature.aircraft.generated.resources.delete_inspection
import wingslog.feature.aircraft.generated.resources.edit_inspection
import wingslog.feature.aircraft.generated.resources.engine
import wingslog.feature.aircraft.generated.resources.engine_based_hours
import wingslog.feature.aircraft.generated.resources.force_due_engine_hours
import wingslog.feature.aircraft.generated.resources.inspection_notes
import wingslog.feature.aircraft.generated.resources.inspection_notes_hint
import wingslog.feature.aircraft.generated.resources.inspection_title
import wingslog.feature.aircraft.generated.resources.interval_hours
import wingslog.feature.aircraft.generated.resources.interval_months
import wingslog.feature.aircraft.generated.resources.on_condition
import wingslog.feature.aircraft.generated.resources.override_next_due_engine
import wingslog.feature.aircraft.generated.resources.propeller
import wingslog.feature.aircraft.generated.resources.rules
import wingslog.feature.aircraft.generated.resources.save
import wingslog.feature.aircraft.generated.resources.time_based_months
import wingslog.feature.aircraft.generated.resources.title_required
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.feature.aircraft.generated.resources.Res as AircraftRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditInspectionSheet(
  cardWithStatus: InspectionCardWithStatus,
  onDismiss: () -> Unit,
  onSave: (
    cardId: String,
    title: String,
    component: InspectionComponentType,
    rules: List<InspectionRule>,
    forceDueDate: com.squareup.wire.Instant?,
    forceDueEngine: Float,
    notes: String,
  ) -> Unit,
  onDeleteRequest: (cardId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val card = cardWithStatus.card

  // Pre-populate from card
  var title by remember { mutableStateOf(card.title) }
  var titleError by remember { mutableStateOf(false) }
  var selectedComponent by remember { mutableStateOf(card.component) }
  var notes by remember { mutableStateOf(card.notes) }

  // Parse existing rules
  var timeRuleEnabled by remember {
    mutableStateOf(card.rules.any { it.time_rule != null })
  }
  var timeRuleMonths by remember {
    mutableStateOf(
      card.rules.firstOrNull { it.time_rule != null }
        ?.time_rule?.interval_months?.toString() ?: "12"
    )
  }
  var engineRuleEnabled by remember {
    mutableStateOf(card.rules.any { it.engine_hour_rule != null })
  }
  var engineRuleHours by remember {
    mutableStateOf(
      card.rules.firstOrNull { it.engine_hour_rule != null }
        ?.engine_hour_rule?.interval_hours?.toString() ?: "100"
    )
  }
  var onConditionEnabled by remember {
    mutableStateOf(card.rules.any { it.on_condition_rule != null })
  }

  // Force-override toggles
  val hasForcedDate = card.force_due_date != null &&
      ((card.force_due_date?.getEpochSecond() ?: 0L) > 0L || (card.force_due_date?.getNano()
        ?: 0) > 0)
  val hasForcedTach = card.force_due_engine_hour > 0f
  var forceOverrideEnabled by remember { mutableStateOf(hasForcedDate || hasForcedTach) }
  var forceEngineHours by remember {
    mutableStateOf(if (hasForcedTach) card.force_due_engine_hour.toString() else "")
  }
  var forceEngineError by remember { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp)
        .padding(bottom = 48.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = cmpStringResource(AircraftRes.string.edit_inspection),
          style = MaterialTheme.typography.titleLarge
        )
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = null)
        }
      }

      // Title
      OutlinedTextField(
        value = title,
        onValueChange = { title = it; titleError = false },
        label = { Text(cmpStringResource(AircraftRes.string.inspection_title)) },
        isError = titleError,
        supportingText = if (titleError) {
          { Text(cmpStringResource(AircraftRes.string.title_required)) }
        } else null,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
      )

      // Component chips
      Text(
        text = cmpStringResource(AircraftRes.string.component),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        listOf(
          InspectionComponentType.INSPECTION_COMPONENT_AIRFRAME to cmpStringResource(AircraftRes.string.airframe),
          InspectionComponentType.INSPECTION_COMPONENT_ENGINE to cmpStringResource(AircraftRes.string.engine),
          InspectionComponentType.INSPECTION_COMPONENT_PROPELLER to cmpStringResource(AircraftRes.string.propeller),
        ).forEach { (type, label) ->
          FilterChip(
            selected = selectedComponent == type,
            onClick = { selectedComponent = type },
            label = { Text(label) },
          )
        }
      }

      // Rules
      Text(
        text = cmpStringResource(AircraftRes.string.rules),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          cmpStringResource(AircraftRes.string.time_based_months),
          style = MaterialTheme.typography.bodyMedium
        )
        Switch(checked = timeRuleEnabled, onCheckedChange = { timeRuleEnabled = it })
      }
      if (timeRuleEnabled) {
        OutlinedTextField(
          value = timeRuleMonths,
          onValueChange = { timeRuleMonths = it },
          label = { Text(cmpStringResource(AircraftRes.string.interval_months)) },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          cmpStringResource(AircraftRes.string.engine_based_hours),
          style = MaterialTheme.typography.bodyMedium
        )
        Switch(checked = engineRuleEnabled, onCheckedChange = { engineRuleEnabled = it })
      }
      if (engineRuleEnabled) {
        OutlinedTextField(
          value = engineRuleHours,
          onValueChange = { engineRuleHours = it },
          label = { Text(cmpStringResource(AircraftRes.string.interval_hours)) },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          cmpStringResource(AircraftRes.string.on_condition),
          style = MaterialTheme.typography.bodyMedium
        )
        Switch(checked = onConditionEnabled, onCheckedChange = { onConditionEnabled = it })
      }

      // Force override
      Text(
        text = "Force Override",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            cmpStringResource(AircraftRes.string.override_next_due_engine),
            style = MaterialTheme.typography.bodyMedium
          )
          Text(
            text = "Skips computed calculation",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(checked = forceOverrideEnabled, onCheckedChange = { forceOverrideEnabled = it })
      }
      if (forceOverrideEnabled) {
        OutlinedTextField(
          value = forceEngineHours,
          onValueChange = { forceEngineHours = it; forceEngineError = false },
          label = { Text(cmpStringResource(AircraftRes.string.force_due_engine_hours)) },
          isError = forceEngineError,
          supportingText = if (forceEngineError) {
            { Text("Enter a valid engine value (e.g. 1250.5)") }
          } else null,

          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
      }

      // Notes

      OutlinedTextField(
        value = notes,
        onValueChange = { notes = it },
        label = { Text(cmpStringResource(AircraftRes.string.inspection_notes)) },
        placeholder = { Text(cmpStringResource(AircraftRes.string.inspection_notes_hint)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 5,
      )

      Spacer(Modifier.height(Spacing.small))

      BottomButtons(
        onSaveClick = {
          if (title.isBlank()) {
            titleError = true
            return@BottomButtons
          }
          if (forceOverrideEnabled) {
            val parsedEngine = forceEngineHours.toFloatOrNull()
            if (parsedEngine == null || parsedEngine <= 0f) {
              forceEngineError = true
              return@BottomButtons
            }
          }
          val rules = buildList {
            if (timeRuleEnabled) {
              val months = timeRuleMonths.toIntOrNull() ?: 12
              add(InspectionRule(time_rule = TimeRule(interval_months = months)))
            }
            if (engineRuleEnabled) {
              val hours = engineRuleHours.toFloatOrNull() ?: 100f
              add(InspectionRule(engine_hour_rule = EngineHourRule(interval_hours = hours)))
            }
            if (onConditionEnabled) {
              add(InspectionRule(on_condition_rule = OnConditionRule()))
            }
          }
          val forcedEngine =
            if (forceOverrideEnabled) forceEngineHours.toFloatOrNull() ?: 0f else 0f
          onSave(card.id, title.trim(), selectedComponent, rules, null, forcedEngine, notes.trim())
        },
        onCancelClick = onDismiss,
        onDeleteClick = { onDeleteRequest(card.id) },
        deleteLabel = cmpStringResource(AircraftRes.string.delete_inspection),
        saveLabel = cmpStringResource(AircraftRes.string.save)
      )
    }
  }
}