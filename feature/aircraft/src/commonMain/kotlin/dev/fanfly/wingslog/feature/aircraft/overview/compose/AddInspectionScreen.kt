package dev.fanfly.wingslog.feature.aircraft.overview.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import dev.fanfly.wingslog.aircraft.ComplianceType
import dev.fanfly.wingslog.aircraft.EngineHourRule
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.aircraft.InspectionComponentType
import dev.fanfly.wingslog.aircraft.InspectionRule
import dev.fanfly.wingslog.aircraft.LinkedRule
import dev.fanfly.wingslog.aircraft.TimeRule
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.aircraft.generated.resources.add_inspection
import wingslog.feature.aircraft.generated.resources.back
import wingslog.feature.aircraft.generated.resources.component
import wingslog.feature.aircraft.generated.resources.inspection_title
import wingslog.feature.aircraft.generated.resources.interval_hours
import wingslog.feature.aircraft.generated.resources.interval_months
import wingslog.feature.aircraft.inspection.generated.resources.compliance_type_ad
import wingslog.feature.aircraft.inspection.generated.resources.compliance_type_routine
import wingslog.feature.aircraft.inspection.generated.resources.compliance_type_sb
import wingslog.feature.aircraft.generated.resources.Res as AircraftRes
import wingslog.feature.aircraft.inspection.generated.resources.Res as InspectionRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInspectionScreen(
  aircraftId: String,
  availableInspections: List<InspectionCard>,
  onSave: (InspectionCard) -> Unit,
  onCancel: () -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var component by remember { mutableStateOf(InspectionComponentType.INSPECTION_COMPONENT_AIRFRAME) }
  var type by remember { mutableStateOf(ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION) }
  var intervalMonths by remember { mutableStateOf("") }
  var intervalHours by remember { mutableStateOf("") }
  var isOneTime by remember { mutableStateOf(false) }
  var refNumber by remember { mutableStateOf("") }
  var manufacturerUrl by remember { mutableStateOf("") }
  var linkedToId by remember { mutableStateOf<String?>(null) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            stringResource(AircraftRes.string.add_inspection).uppercase(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
        },
        navigationIcon = {
          IconButton(onClick = onCancel) {
            Icon(
              Icons.Default.ArrowBack,
              contentDescription = stringResource(AircraftRes.string.back)
            )
          }
        }
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .padding(padding)
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(Spacing.screenPadding)
    ) {
      // Title
      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text(stringResource(AircraftRes.string.inspection_title)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
      )

      Spacer(modifier = Modifier.height(Spacing.medium))

      // Component Type
      Text(
        stringResource(AircraftRes.string.component),
        style = MaterialTheme.typography.labelLarge
      )
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small)
      ) {
        InspectionComponentType.entries.filter { it != InspectionComponentType.INSPECTION_COMPONENT_UNKNOWN }
          .forEach { entry ->
            FilterChip(
              selected = component == entry,
              onClick = { component = entry },
              label = { Text(entry.name.removePrefix("INSPECTION_COMPONENT_")) }
            )
          }
      }

      Spacer(modifier = Modifier.height(Spacing.medium))

      // Compliance Type
      Text("COMPLIANCE TYPE", style = MaterialTheme.typography.labelLarge)
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small)
      ) {
        ComplianceType.entries.forEach { entry ->
          FilterChip(
            selected = type == entry,
            onClick = { type = entry },
            label = {
              val labelText = when (entry) {
                ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE -> stringResource(
                  InspectionRes.string.compliance_type_ad
                )

                ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN -> stringResource(InspectionRes.string.compliance_type_sb)
                ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION -> stringResource(InspectionRes.string.compliance_type_routine)
                else -> entry.name.removePrefix("COMPLIANCE_TYPE_")
              }
              Text(labelText)
            }
          )
        }
      }

      Spacer(modifier = Modifier.height(Spacing.medium))

      // Regular Interval Inputs (Always visible)
      Text("INTERVALS", style = MaterialTheme.typography.labelLarge)
      Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        OutlinedTextField(
          value = intervalMonths,
          onValueChange = { intervalMonths = it.filter { c -> c.isDigit() } },
          label = { Text(stringResource(AircraftRes.string.interval_months)) },
          modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
          value = intervalHours,
          onValueChange = { intervalHours = it.filter { c -> c.isDigit() || c == '.' } },
          label = { Text(stringResource(AircraftRes.string.interval_hours)) },
          modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.height(Spacing.small))

      // One-time compliance toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("One-Time Compliance", style = MaterialTheme.typography.bodyLarge)
          Text(
            "Moves to history after first log",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Switch(checked = isOneTime, onCheckedChange = { isOneTime = it })
      }

      if (type == ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN || type == ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE) {
        Spacer(modifier = Modifier.height(Spacing.medium))
        OutlinedTextField(
          value = refNumber,
          onValueChange = { refNumber = it },
          label = { Text("Reference Number") },
          modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(Spacing.small))
        OutlinedTextField(
          value = manufacturerUrl,
          onValueChange = { manufacturerUrl = it },
          label = { Text("Manufacturer URL") },
          modifier = Modifier.fillMaxWidth()
        )
      }

      Spacer(modifier = Modifier.height(Spacing.medium))

      // Linked Inspection
      Text("LINKED TO (OPTIONAL)", style = MaterialTheme.typography.labelLarge)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        FilterChip(
          selected = linkedToId == null,
          onClick = { linkedToId = null },
          label = { Text("None") }
        )
        availableInspections.forEach { insp ->
          FilterChip(
            selected = linkedToId == insp.id,
            onClick = { linkedToId = insp.id },
            label = { Text(insp.title) }
          )
        }
      }

      Spacer(modifier = Modifier.weight(1f))
      Spacer(modifier = Modifier.height(Spacing.large))

      BottomButtons(
        onSaveClick = {
          val ruleList = mutableListOf<InspectionRule>()
          intervalMonths.toIntOrNull()?.let {
            ruleList.add(InspectionRule(time_rule = TimeRule(interval_months = it)))
          }
          intervalHours.toDoubleOrNull()?.let {
            ruleList.add(InspectionRule(engine_hour_rule = EngineHourRule(interval_hours = it.toFloat())))
          }
          linkedToId?.let {
            ruleList.add(InspectionRule(linked_rule = LinkedRule(parent_inspection_id = it)))
          }

          val card = InspectionCard(
            id = "",
            title = title,
            component = component,
            type = type,
            rules = ruleList,
            reference_number = refNumber.takeIf { it.isNotBlank() } ?: "",
            sb_url = manufacturerUrl.takeIf { it.isNotBlank() } ?: "",
            compliance_details = "",
            is_one_time = isOneTime,
            force_due_engine_hour = 0f,
            force_due_date = null,
            notes = ""
          )
          onSave(card)
        },
        onCancelClick = onCancel,
        saveEnabled = title.isNotBlank()
      )
    }
  }
}
