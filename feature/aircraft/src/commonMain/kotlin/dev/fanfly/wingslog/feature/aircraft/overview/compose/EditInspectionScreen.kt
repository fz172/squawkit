package dev.fanfly.wingslog.feature.aircraft.overview.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import dev.fanfly.wingslog.aircraft.ComplianceType
import dev.fanfly.wingslog.aircraft.EngineHourRule
import dev.fanfly.wingslog.aircraft.ImmediateRule
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.aircraft.InspectionComponentType
import dev.fanfly.wingslog.aircraft.InspectionRule
import dev.fanfly.wingslog.aircraft.LinkedRule
import dev.fanfly.wingslog.aircraft.OnConditionRule
import dev.fanfly.wingslog.aircraft.TimeRule
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.aircraft.overview.data.InspectionCardWithStatus
import wingslog.feature.aircraft.generated.resources.airframe
import wingslog.feature.aircraft.generated.resources.back
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
import wingslog.feature.aircraft.generated.resources.save
import wingslog.feature.aircraft.generated.resources.time_based_months
import wingslog.feature.aircraft.generated.resources.title_required
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.feature.aircraft.generated.resources.Res as AircraftRes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditInspectionScreen(
  cardWithStatus: InspectionCardWithStatus,
  availableRecurringInspections: List<InspectionCard>,
  onBackClick: () -> Unit,
  onSave: (
    cardId: String,
    title: String,
    type: ComplianceType,
    component: InspectionComponentType,
    rules: List<InspectionRule>,
    referenceNumber: String,
    sbUrl: String,
    complianceDetails: String,
    isOneTime: Boolean,
    forceDueDate: com.squareup.wire.Instant?,
    forceDueEngine: Float,
    notes: String,
  ) -> Unit,
  onDeleteRequest: (cardId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val card = cardWithStatus.card

  // Pre-populate from card
  var title by remember { mutableStateOf(card.title) }
  var titleError by remember { mutableStateOf(false) }
  var selectedType by remember { mutableStateOf(card.type) }
  var selectedComponent by remember { mutableStateOf(card.component) }
  var referenceNumber by remember { mutableStateOf(card.reference_number) }
  var sbUrl by remember { mutableStateOf(card.sb_url) }
  var complianceDetails by remember { mutableStateOf(card.compliance_details) }
  var isOneTime by remember { mutableStateOf(card.is_one_time) }
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
  var immediateRuleEnabled by remember {
    mutableStateOf(card.rules.any { it.immediate_rule != null })
  }
  var linkedRuleEnabled by remember {
    mutableStateOf(card.rules.any { it.linked_rule != null })
  }
  var parentInspectionId by remember {
    mutableStateOf(
      card.rules.firstOrNull { it.linked_rule != null }
        ?.linked_rule?.parent_inspection_id ?: ""
    )
  }

  // Force-override toggles
  val hasForcedDate = card.force_due_date != null &&
      ((card.force_due_date?.getEpochSecond() ?: 0L) > 0L || (card.force_due_date?.getNano() ?: 0) > 0)
  val hasForcedEngine = card.force_due_engine_hour > 0f
  var forceOverrideEnabled by remember { mutableStateOf(hasForcedDate || hasForcedEngine) }
  var forceEngineHours by remember {
    mutableStateOf(if (hasForcedEngine) card.force_due_engine_hour.toString() else "")
  }
  var forceEngineError by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(cmpStringResource(AircraftRes.string.edit_inspection)) },
        navigationIcon = {
          IconButton(onClick = onBackClick) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = cmpStringResource(AircraftRes.string.back))
          }
        }
      )
    },
    modifier = modifier.fillMaxSize(),
  ) { paddingValues ->
    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = Spacing.screenPadding)
          .padding(bottom = 120.dp), // Clearance for bottom buttons
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
      ) {
        // 1. Compliance Type
        Text(
          text = "Type",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf(
            ComplianceType.COMPLIANCE_TYPE_RECURRING_INSPECTION to "Inspection",
            ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN to "SB",
            ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE to "AD",
          ).forEach { (type, label) ->
            FilterChip(
              selected = selectedType == type,
              onClick = { selectedType = type },
              label = { Text(label) },
            )
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

        if (selectedType != ComplianceType.COMPLIANCE_TYPE_RECURRING_INSPECTION) {
          OutlinedTextField(
            value = referenceNumber,
            onValueChange = { referenceNumber = it },
            label = { Text("Reference Number (e.g. SB-2024-01)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
          )
        }

        // Component chips
        Text(
          text = cmpStringResource(AircraftRes.string.component),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf(
            InspectionComponentType.INSPECTION_COMPONENT_AIRFRAME to cmpStringResource(AircraftRes.string.airframe),
            InspectionComponentType.INSPECTION_COMPONENT_ENGINE to cmpStringResource(AircraftRes.string.engine),
            InspectionComponentType.INSPECTION_COMPONENT_PROPELLER to cmpStringResource(AircraftRes.string.propeller),
            InspectionComponentType.INSPECTION_COMPONENT_AVIONICS to "Avionics",
          ).forEach { (type, label) ->
            FilterChip(
              selected = selectedComponent == type,
              onClick = { selectedComponent = type },
              label = { Text(label) },
            )
          }
        }

        // One-time toggle
        if (selectedType != ComplianceType.COMPLIANCE_TYPE_RECURRING_INSPECTION) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                "One-Time Compliance",
                style = MaterialTheme.typography.bodyMedium
              )
              Text(
                "Moves to history after first log",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(checked = isOneTime, onCheckedChange = { isOneTime = it })
          }
        }

        // Rules
        Text(
          text = "Due Strategy",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Immediate rule toggle
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            "Immediate Compliance",
            style = MaterialTheme.typography.bodyMedium
          )
          Switch(checked = immediateRuleEnabled, onCheckedChange = {
            immediateRuleEnabled = it
            if (it) {
              timeRuleEnabled = false
              engineRuleEnabled = false
              onConditionEnabled = false
              linkedRuleEnabled = false
            }
          })
        }

        // Time Rule
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            cmpStringResource(AircraftRes.string.time_based_months),
            style = MaterialTheme.typography.bodyMedium
          )
          Switch(checked = timeRuleEnabled, onCheckedChange = {
            timeRuleEnabled = it
            if (it) immediateRuleEnabled = false
          })
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

        // Engine Rule
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            cmpStringResource(AircraftRes.string.engine_based_hours),
            style = MaterialTheme.typography.bodyMedium
          )
          Switch(checked = engineRuleEnabled, onCheckedChange = {
            engineRuleEnabled = it
            if (it) immediateRuleEnabled = false
          })
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

        // Linked Rule
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            "Linked to Inspection",
            style = MaterialTheme.typography.bodyMedium
          )
          Switch(checked = linkedRuleEnabled, onCheckedChange = {
            linkedRuleEnabled = it
            if (it) immediateRuleEnabled = false
          })
        }
        if (linkedRuleEnabled) {
          if (availableRecurringInspections.isEmpty()) {
            Text(
              text = "No other recurring inspections available to link to. Add a parent inspection (like Annual) first.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(vertical = Spacing.small)
            )
          } else {
            Text(
              text = "Select an existing inspection to sync due dates with:",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              availableRecurringInspections.forEach { inspection ->
                FilterChip(
                  selected = parentInspectionId == inspection.id,
                  onClick = { parentInspectionId = inspection.id },
                  label = { Text(inspection.title) },
                )
              }
            }
          }
        }

        // On Condition
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            cmpStringResource(AircraftRes.string.on_condition),
            style = MaterialTheme.typography.bodyMedium
          )
          Switch(checked = onConditionEnabled, onCheckedChange = {
            onConditionEnabled = it
            if (it) immediateRuleEnabled = false
          })
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

        if (selectedType != ComplianceType.COMPLIANCE_TYPE_RECURRING_INSPECTION) {
          OutlinedTextField(
            value = sbUrl,
            onValueChange = { sbUrl = it },
            label = { Text("SB URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
          )
          OutlinedTextField(
            value = complianceDetails,
            onValueChange = { complianceDetails = it },
            label = { Text("Compliance Details") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5,
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

        Spacer(Modifier.height(88.dp))
      }

      BottomButtons(
        modifier = Modifier.align(Alignment.BottomCenter),
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
            if (immediateRuleEnabled) {
              add(InspectionRule(immediate_rule = ImmediateRule()))
            } else {
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
              if (linkedRuleEnabled && parentInspectionId.isNotBlank()) {
                add(InspectionRule(linked_rule = LinkedRule(parent_inspection_id = parentInspectionId)))
              }
            }
          }
          val forcedEngine = if (forceOverrideEnabled) forceEngineHours.toFloatOrNull() ?: 0f else 0f
          onSave(
            card.id,
            title.trim(),
            selectedType,
            selectedComponent,
            rules,
            referenceNumber.trim(),
            sbUrl.trim(),
            complianceDetails.trim(),
            isOneTime,
            null, // forceDueDate
            forcedEngine,
            notes.trim()
          )
        },
        onCancelClick = onBackClick,
        onDeleteClick = { onDeleteRequest(card.id) },
        deleteLabel = cmpStringResource(AircraftRes.string.delete_inspection),
        saveLabel = cmpStringResource(AircraftRes.string.save)
      )
    }
  }
}
