package dev.fanfly.wingslog.feature.aircraft.overview.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.style.TextAlign
import dev.fanfly.wingslog.aircraft.ComplianceType
import dev.fanfly.wingslog.aircraft.EngineHourRule
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.aircraft.InspectionComponentType
import dev.fanfly.wingslog.aircraft.InspectionRule
import dev.fanfly.wingslog.aircraft.LinkedRule
import dev.fanfly.wingslog.aircraft.TimeRule
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.aircraft.maintenance.form.compose.InspectionPickerSheet
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.back
import wingslog.feature.aircraft.inspection.generated.resources.add_inspection
import wingslog.feature.aircraft.inspection.generated.resources.compliance_type
import wingslog.feature.aircraft.inspection.generated.resources.compliance_type_ad_short
import wingslog.feature.aircraft.inspection.generated.resources.compliance_type_routine_short
import wingslog.feature.aircraft.inspection.generated.resources.compliance_type_sb_short
import wingslog.feature.aircraft.inspection.generated.resources.component
import wingslog.feature.aircraft.inspection.generated.resources.component_airframe
import wingslog.feature.aircraft.inspection.generated.resources.component_avionics
import wingslog.feature.aircraft.inspection.generated.resources.component_engine
import wingslog.feature.aircraft.inspection.generated.resources.component_propeller
import wingslog.feature.aircraft.inspection.generated.resources.inspection_title
import wingslog.feature.aircraft.inspection.generated.resources.interval_hours
import wingslog.feature.aircraft.inspection.generated.resources.interval_months
import wingslog.feature.aircraft.inspection.generated.resources.intervals
import wingslog.feature.aircraft.inspection.generated.resources.link_to_inspection
import wingslog.feature.aircraft.inspection.generated.resources.manufacturer_url
import wingslog.feature.aircraft.inspection.generated.resources.one_time_compliance
import wingslog.feature.aircraft.inspection.generated.resources.one_time_compliance_desc
import wingslog.feature.aircraft.inspection.generated.resources.reference_number
import wingslog.feature.aircraft.inspection.generated.resources.remove_link
import wingslog.feature.aircraft.inspection.generated.resources.schedule_with_another_work
import wingslog.feature.aircraft.inspection.generated.resources.schedule_with_another_work_description
import wingslog.feature.aircraft.inspection.generated.resources.unknown
import wingslog.core.ui.generated.resources.Res as CoreRes
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
  var showLinkedPicker by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(title = {
        Text(
          stringResource(InspectionRes.string.add_inspection).uppercase(),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold
        )
      }, navigationIcon = {
        IconButton(onClick = onCancel) {
          Icon(
            Icons.AutoMirrored.Default.ArrowBack,
            contentDescription = stringResource(CoreRes.string.back)
          )
        }
      })
    }) { padding ->
    Column(
      modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
        .padding(Spacing.screenPadding)
    ) {
      // Title
      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text(stringResource(InspectionRes.string.inspection_title)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
      )

      Spacer(modifier = Modifier.height(Spacing.medium))

      // Component Type
      Text(
        stringResource(InspectionRes.string.component), style = MaterialTheme.typography.labelLarge
      )
      Spacer(modifier = Modifier.height(Spacing.small))
      val components =
        InspectionComponentType.entries.filter { it != InspectionComponentType.INSPECTION_COMPONENT_UNKNOWN }
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        components.forEachIndexed { index, entry ->
          SegmentedButton(
            selected = component == entry,
            onClick = { component = entry },
            shape = SegmentedButtonDefaults.itemShape(index = index, count = components.size),
            icon = {}, // Hide icon for cleaner alignment
            label = {
              val componentLabel = when (entry) {
                InspectionComponentType.INSPECTION_COMPONENT_AIRFRAME -> stringResource(
                  InspectionRes.string.component_airframe
                )

                InspectionComponentType.INSPECTION_COMPONENT_ENGINE -> stringResource(InspectionRes.string.component_engine)
                InspectionComponentType.INSPECTION_COMPONENT_PROPELLER -> stringResource(
                  InspectionRes.string.component_propeller
                )

                InspectionComponentType.INSPECTION_COMPONENT_AVIONICS -> stringResource(
                  InspectionRes.string.component_avionics
                )

                else -> entry.name.removePrefix("INSPECTION_COMPONENT_")
              }
              Text(
                text = componentLabel,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
              )
            })
        }
      }

      Spacer(modifier = Modifier.height(Spacing.medium))

      // Compliance Type
      Text(
        stringResource(InspectionRes.string.compliance_type),
        style = MaterialTheme.typography.labelLarge
      )
      Spacer(modifier = Modifier.height(Spacing.small))
      val types = ComplianceType.entries
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        types.forEachIndexed { index, entry ->
          SegmentedButton(
            selected = type == entry,
            onClick = { type = entry },
            shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
            icon = {}, // Hide icon for cleaner alignment
            label = {
              val labelText = when (entry) {
                ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE -> stringResource(
                  InspectionRes.string.compliance_type_ad_short
                )

                ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN -> stringResource(InspectionRes.string.compliance_type_sb_short)
                ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION -> stringResource(InspectionRes.string.compliance_type_routine_short)
                else -> entry.name.removePrefix("COMPLIANCE_TYPE_")
              }
              Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
              )
            })
        }
      }

      Spacer(modifier = Modifier.height(Spacing.medium))

      // One-time compliance toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            stringResource(InspectionRes.string.one_time_compliance),
            style = MaterialTheme.typography.bodyLarge
          )
          Text(
            stringResource(InspectionRes.string.one_time_compliance_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Switch(checked = isOneTime, onCheckedChange = { isOneTime = it })
      }
      Spacer(modifier = Modifier.height(Spacing.medium))

      // Regular Interval Inputs
      if (linkedToId == null) {
        Text(
          stringResource(InspectionRes.string.intervals),
          style = MaterialTheme.typography.labelLarge
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
          OutlinedTextField(
            value = intervalMonths,
            onValueChange = { intervalMonths = it.filter { c -> c.isDigit() } },
            label = { Text(stringResource(InspectionRes.string.interval_months)) },
            modifier = Modifier.weight(1f)
          )
          OutlinedTextField(
            value = intervalHours,
            onValueChange = { intervalHours = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text(stringResource(InspectionRes.string.interval_hours)) },
            modifier = Modifier.weight(1f)
          )
        }

        Spacer(modifier = Modifier.height(Spacing.small))
      }

      if (type == ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN || type == ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE) {
        if (linkedToId != null) {
          Spacer(modifier = Modifier.height(Spacing.medium))
        } else {
          Spacer(modifier = Modifier.height(Spacing.small))
        }
        OutlinedTextField(
          value = refNumber,
          onValueChange = { refNumber = it },
          label = { Text(stringResource(InspectionRes.string.reference_number)) },
          modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(Spacing.small))
        OutlinedTextField(
          value = manufacturerUrl,
          onValueChange = { manufacturerUrl = it },
          label = { Text(stringResource(InspectionRes.string.manufacturer_url)) },
          modifier = Modifier.fillMaxWidth()
        )
      }

      Spacer(modifier = Modifier.height(Spacing.medium))

      // Linked Inspection
      Text(
        stringResource(InspectionRes.string.schedule_with_another_work),
        style = MaterialTheme.typography.labelLarge
      )
      Text(
        stringResource(InspectionRes.string.schedule_with_another_work_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(modifier = Modifier.height(Spacing.small))

      if (linkedToId == null) {
        OutlinedButton(
          onClick = { showLinkedPicker = true }, modifier = Modifier.fillMaxWidth()
        ) {
          Icon(Icons.Default.Add, contentDescription = null)
          Spacer(modifier = Modifier.width(Spacing.small))
          Text(stringResource(InspectionRes.string.link_to_inspection))
        }
      } else {
        val linkedInsp = availableInspections.find { it.id == linkedToId }
        InputChip(
          selected = true,
          onClick = { showLinkedPicker = true },
          label = { Text(linkedInsp?.title ?: stringResource(InspectionRes.string.unknown)) },
          trailingIcon = {
            IconButton(
              onClick = { linkedToId = null }, modifier = Modifier.size(InputChipDefaults.IconSize)
            ) {
              Icon(
                Icons.Default.Close,
                contentDescription = stringResource(InspectionRes.string.remove_link),
                modifier = Modifier.size(InputChipDefaults.IconSize)
              )
            }
          })
      }

      if (showLinkedPicker) {
        InspectionPickerSheet(
          availableCards = availableInspections,
          selectedIds = listOfNotNull(linkedToId),
          onToggle = { id ->
            linkedToId = if (linkedToId == id) null else id
            showLinkedPicker = false
          },
          onDismiss = { showLinkedPicker = false },
          singleSelect = true
        )
      }

      Spacer(modifier = Modifier.weight(1f))
      Spacer(modifier = Modifier.height(Spacing.large))

      BottomButtons(
        onSaveClick = {
          val ruleList = mutableListOf<InspectionRule>()
          if (linkedToId != null) {
            ruleList.add(InspectionRule(linked_rule = LinkedRule(parent_inspection_id = linkedToId!!)))
          } else {
            intervalMonths.toIntOrNull()?.let {
              ruleList.add(InspectionRule(time_rule = TimeRule(interval_months = it)))
            }
            intervalHours.toDoubleOrNull()?.let {
              ruleList.add(InspectionRule(engine_hour_rule = EngineHourRule(interval_hours = it.toFloat())))
            }
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
            notes = "")
          onSave(card)
        }, onCancelClick = onCancel, saveEnabled = title.isNotBlank()
      )
    }
  }
}
