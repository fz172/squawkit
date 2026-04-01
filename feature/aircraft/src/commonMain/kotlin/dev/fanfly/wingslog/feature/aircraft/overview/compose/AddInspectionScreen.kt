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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
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
import wingslog.feature.aircraft.generated.resources.add_inspection
import wingslog.feature.aircraft.generated.resources.airframe
import wingslog.feature.aircraft.generated.resources.back
import wingslog.feature.aircraft.generated.resources.component
import wingslog.feature.aircraft.generated.resources.engine
import wingslog.feature.aircraft.generated.resources.engine_based_hours
import wingslog.feature.aircraft.generated.resources.inspection_notes
import wingslog.feature.aircraft.generated.resources.inspection_notes_hint
import wingslog.feature.aircraft.generated.resources.inspection_title
import wingslog.feature.aircraft.generated.resources.interval_hours
import wingslog.feature.aircraft.generated.resources.interval_months
import wingslog.feature.aircraft.generated.resources.on_condition
import wingslog.feature.aircraft.generated.resources.propeller
import wingslog.feature.aircraft.generated.resources.quick_add
import wingslog.feature.aircraft.generated.resources.save
import wingslog.feature.aircraft.generated.resources.time_based_months
import wingslog.feature.aircraft.generated.resources.title_required
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import wingslog.feature.aircraft.generated.resources.Res as AircraftRes

private data class QuickTemplate(
  val title: String,
  val component: InspectionComponentType,
  val rules: List<InspectionRule>,
)

private fun buildTimeRule(months: Int): InspectionRule =
  InspectionRule(time_rule = TimeRule(interval_months = months))

private fun buildEngineRule(hours: Float): InspectionRule =
  InspectionRule(engine_hour_rule = EngineHourRule(interval_hours = hours))

private fun buildOnConditionRule(): InspectionRule =
  InspectionRule(on_condition_rule = OnConditionRule())

private val quickTemplates = listOf(
  QuickTemplate(
    title = "Annual Condition",
    component = InspectionComponentType.INSPECTION_COMPONENT_AIRFRAME,
    rules = listOf(buildTimeRule(12)),
  ),
  QuickTemplate(
    title = "ELT Battery",
    component = InspectionComponentType.INSPECTION_COMPONENT_AIRFRAME,
    rules = listOf(buildTimeRule(12)),
  ),
  QuickTemplate(
    title = "Parachute Repack",
    component = InspectionComponentType.INSPECTION_COMPONENT_AIRFRAME,
    rules = listOf(buildTimeRule(6)),
  ),
  QuickTemplate(
    title = "100-Hour Inspection",
    component = InspectionComponentType.INSPECTION_COMPONENT_ENGINE,
    rules = listOf(buildEngineRule(100f)),
  ),
  QuickTemplate(
    title = "Oil Change",
    component = InspectionComponentType.INSPECTION_COMPONENT_ENGINE,
    rules = listOf(buildEngineRule(50f)),
  ),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddInspectionScreen(
  availableRecurringInspections: List<InspectionCard>,
  onBackClick: () -> Unit,
  onSave: (
    title: String,
    type: ComplianceType,
    component: InspectionComponentType,
    rules: List<InspectionRule>,
    referenceNumber: String,
    sbUrl: String,
    complianceDetails: String,
    isOneTime: Boolean,
    notes: String
  ) -> Unit,
  modifier: Modifier = Modifier,
) {
  var title by remember { mutableStateOf("") }
  var titleError by remember { mutableStateOf(false) }
  var selectedType by remember { mutableStateOf(ComplianceType.COMPLIANCE_TYPE_RECURRING_INSPECTION) }
  var selectedComponent by remember { mutableStateOf(InspectionComponentType.INSPECTION_COMPONENT_AIRFRAME) }
  var referenceNumber by remember { mutableStateOf("") }
  var sbUrl by remember { mutableStateOf("") }
  var complianceDetails by remember { mutableStateOf("") }
  var isOneTime by remember { mutableStateOf(false) }

  var timeRuleEnabled by remember { mutableStateOf(false) }
  var timeRuleMonths by remember { mutableStateOf("12") }
  var engineRuleEnabled by remember { mutableStateOf(false) }
  var engineRuleHours by remember { mutableStateOf("100") }
  var onConditionEnabled by remember { mutableStateOf(false) }
  var immediateRuleEnabled by remember { mutableStateOf(false) }
  var linkedRuleEnabled by remember { mutableStateOf(false) }
  var parentInspectionId by remember { mutableStateOf("") }
  var notes by remember { mutableStateOf("") }

  fun applyTemplate(template: QuickTemplate) {
    title = template.title
    selectedComponent = template.component
    selectedType = ComplianceType.COMPLIANCE_TYPE_RECURRING_INSPECTION
    referenceNumber = ""
    sbUrl = ""
    complianceDetails = ""
    isOneTime = false
    timeRuleEnabled = false
    engineRuleEnabled = false
    onConditionEnabled = false
    immediateRuleEnabled = false
    linkedRuleEnabled = false
    notes = ""
    template.rules.forEach { rule ->
      val timeRule = rule.time_rule
      val engineRule = rule.engine_hour_rule
      val onConditionRule = rule.on_condition_rule
      when {
        timeRule != null -> {
          timeRuleEnabled = true
          timeRuleMonths = timeRule.interval_months.toString()
        }

        engineRule != null -> {
          engineRuleEnabled = true
          engineRuleHours = engineRule.interval_hours.toString()
        }

        onConditionRule != null -> onConditionEnabled = true
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          val headerTitle = when (selectedType) {
            ComplianceType.COMPLIANCE_TYPE_RECURRING_INSPECTION -> "Add Inspection"
            ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN -> "Add Service Bulletin"
            ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE -> "Add AD"
            else -> "Add Compliance Item"
          }
          Text(headerTitle)
        },
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

        // Quick add chips (only for recurring)
        if (selectedType == ComplianceType.COMPLIANCE_TYPE_RECURRING_INSPECTION) {
          Text(
            text = cmpStringResource(AircraftRes.string.quick_add),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            quickTemplates.forEach { template ->
              SuggestionChip(
                onClick = { applyTemplate(template) },
                label = { Text(template.title, style = MaterialTheme.typography.labelMedium) },
              )
            }
          }
        }

        // Title input
        OutlinedTextField(
          value = title,
          onValueChange = {
            title = it
            titleError = false
          },
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

        // Component selection
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

        // Time-based rule toggle
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

        // Engine-based rule toggle
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

        // Linked rule toggle
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
              text = "No recurring inspections available to link to. Add an inspection (like Annual) first.",
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

        // On-condition toggle
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
      }

      BottomButtons(
        modifier = Modifier.align(Alignment.BottomCenter),
        onSaveClick = {
          if (title.isBlank()) {
            titleError = true
            return@BottomButtons
          }
          val rules = buildList {
            if (immediateRuleEnabled) {
              add(InspectionRule(immediate_rule = ImmediateRule()))
            } else {
              if (timeRuleEnabled) {
                val months = timeRuleMonths.toIntOrNull() ?: 12
                add(buildTimeRule(months))
              }
              if (engineRuleEnabled) {
                val hours = engineRuleHours.toFloatOrNull() ?: 100f
                add(buildEngineRule(hours))
              }
              if (onConditionEnabled) {
                add(buildOnConditionRule())
              }
              if (linkedRuleEnabled && parentInspectionId.isNotBlank()) {
                add(InspectionRule(linked_rule = LinkedRule(parent_inspection_id = parentInspectionId)))
              }
            }
          }
          onSave(
            title.trim(),
            selectedType,
            selectedComponent,
            rules,
            referenceNumber.trim(),
            sbUrl.trim(),
            complianceDetails.trim(),
            isOneTime,
            notes.trim()
          )
        },
        onCancelClick = onBackClick,
        saveLabel = cmpStringResource(AircraftRes.string.save)
      )
    }
  }
}
