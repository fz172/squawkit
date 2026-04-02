package dev.fanfly.wingslog.feature.aircraft.overview.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.ComplianceType
import dev.fanfly.wingslog.aircraft.EngineHourRule
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.aircraft.InspectionComponentType
import dev.fanfly.wingslog.aircraft.InspectionRule
import dev.fanfly.wingslog.aircraft.LinkedRule
import dev.fanfly.wingslog.aircraft.TimeRule
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.common.datetime.createWireInstant
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.aircraft.maintenance.form.compose.InspectionPickerSheet
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.back
import wingslog.core.ui.generated.resources.ok
import wingslog.feature.aircraft.inspection.generated.resources.compliance_type
import wingslog.feature.aircraft.inspection.generated.resources.compliance_type_ad_short
import wingslog.feature.aircraft.inspection.generated.resources.compliance_type_routine_short
import wingslog.feature.aircraft.inspection.generated.resources.compliance_type_sb_short
import wingslog.feature.aircraft.inspection.generated.resources.component
import wingslog.feature.aircraft.inspection.generated.resources.component_airframe
import wingslog.feature.aircraft.inspection.generated.resources.component_avionics
import wingslog.feature.aircraft.inspection.generated.resources.component_engine
import wingslog.feature.aircraft.inspection.generated.resources.component_propeller
import wingslog.feature.aircraft.inspection.generated.resources.edit_inspection
import wingslog.feature.aircraft.inspection.generated.resources.force_due_engine_hours
import wingslog.feature.aircraft.inspection.generated.resources.force_overrides_safety
import wingslog.feature.aircraft.inspection.generated.resources.inspection_title
import wingslog.feature.aircraft.inspection.generated.resources.interval_hours
import wingslog.feature.aircraft.inspection.generated.resources.interval_months
import wingslog.feature.aircraft.inspection.generated.resources.intervals
import wingslog.feature.aircraft.inspection.generated.resources.link_to_inspection
import wingslog.feature.aircraft.inspection.generated.resources.compliance_authority
import wingslog.feature.aircraft.inspection.generated.resources.compliance_authority_hint
import wingslog.feature.aircraft.inspection.generated.resources.one_time_compliance
import wingslog.feature.aircraft.inspection.generated.resources.one_time_compliance_desc
import wingslog.feature.aircraft.inspection.generated.resources.override_next_due_date
import wingslog.feature.aircraft.inspection.generated.resources.override_next_due_engine
import wingslog.feature.aircraft.inspection.generated.resources.reference_number
import wingslog.feature.aircraft.inspection.generated.resources.remove_link
import wingslog.feature.aircraft.inspection.generated.resources.schedule_with_another_work
import wingslog.feature.aircraft.inspection.generated.resources.schedule_with_another_work_description
import wingslog.feature.aircraft.inspection.generated.resources.select_date
import wingslog.feature.aircraft.inspection.generated.resources.unknown
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.feature.aircraft.inspection.generated.resources.Res as InspectionRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditInspectionScreen(
  card: InspectionCard,
  availableInspections: List<InspectionCard>,
  onSave: (InspectionCard) -> Unit,
  onCancel: () -> Unit,
  onDeleteRequest: (String) -> Unit,
) {
  var title by remember { mutableStateOf(card.title) }
  val component = card.component
  val type = card.type
  var isOneTime by remember { mutableStateOf(card.is_one_time) }

  val initialIntervalMonths =
    card.rules.mapNotNull { it.time_rule?.interval_months }.firstOrNull()?.toString() ?: ""
  val initialIntervalHours =
    card.rules.mapNotNull { it.engine_hour_rule?.interval_hours }.firstOrNull()?.toString() ?: ""
  val initialLinkedId = card.rules.mapNotNull { it.linked_rule?.parent_inspection_id }.firstOrNull()

  var intervalMonths by remember { mutableStateOf(initialIntervalMonths) }
  var intervalHours by remember { mutableStateOf(initialIntervalHours) }
  var refNumber by remember { mutableStateOf(card.reference_number) }
  var complianceAuthority by remember { mutableStateOf(card.compliance_authority) }
  var linkedToId by remember { mutableStateOf(initialLinkedId) }
  var showLinkedPicker by remember { mutableStateOf(false) }

  // Force Override States
  var forceOverrideEngine by remember { mutableStateOf(card.force_due_engine_hour > 0f) }
  var forcedEngineHours by remember { mutableStateOf(if (card.force_due_engine_hour > 0f) card.force_due_engine_hour.toString() else "") }
  var forceOverrideDate by remember { mutableStateOf(card.force_due_date != null) }
  var forcedDateMillis by remember {
    mutableStateOf(card.force_due_date?.let { it.getEpochSecond() * 1000 })
  }

  var showDatePicker by remember { mutableStateOf(false) }
  var showDeleteConfirm by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            stringResource(InspectionRes.string.edit_inspection).uppercase(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
        },
        navigationIcon = {
          IconButton(onClick = onCancel) {
            Icon(
              Icons.AutoMirrored.Default.ArrowBack,
              contentDescription = stringResource(CoreRes.string.back)
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
        label = { Text(stringResource(InspectionRes.string.inspection_title)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
      )

      Spacer(modifier = Modifier.height(Spacing.medium))

      // Component Type (Static in Edit)
      Text(
        stringResource(InspectionRes.string.component),
        style = MaterialTheme.typography.labelLarge
      )
      Box(
        modifier = Modifier
          .background(
            MaterialTheme.colorScheme.surfaceVariant,
            RoundedCornerShape(Spacing.cardCornerRadius)
          )
          .padding(horizontal = Spacing.medium, vertical = Spacing.small)
      ) {
        Text(
          text = when (component) {
            InspectionComponentType.INSPECTION_COMPONENT_AIRFRAME -> stringResource(InspectionRes.string.component_airframe)
            InspectionComponentType.INSPECTION_COMPONENT_ENGINE -> stringResource(InspectionRes.string.component_engine)
            InspectionComponentType.INSPECTION_COMPONENT_PROPELLER -> stringResource(InspectionRes.string.component_propeller)
            InspectionComponentType.INSPECTION_COMPONENT_AVIONICS -> stringResource(InspectionRes.string.component_avionics)
            else -> component.name.removePrefix("INSPECTION_COMPONENT_")
          },
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Spacer(modifier = Modifier.height(Spacing.medium))

      // Compliance Type (Static in Edit)
      Text(
        stringResource(InspectionRes.string.compliance_type),
        style = MaterialTheme.typography.labelLarge
      )
      Box(
        modifier = Modifier
          .background(
            MaterialTheme.colorScheme.surfaceVariant,
            RoundedCornerShape(Spacing.cardCornerRadius)
          )
          .padding(horizontal = Spacing.medium, vertical = Spacing.small)
      ) {
        Text(
          text = when (type) {
            ComplianceType.COMPLIANCE_TYPE_AIRWORTHINESS_DIRECTIVE -> stringResource(InspectionRes.string.compliance_type_ad_short)
            ComplianceType.COMPLIANCE_TYPE_SERVICE_BULLETIN -> stringResource(InspectionRes.string.compliance_type_sb_short)
            ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION -> stringResource(InspectionRes.string.compliance_type_routine_short)
            else -> type.name.removePrefix("COMPLIANCE_TYPE_")
          },
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        Spacer(modifier = Modifier.height(Spacing.medium))
        OutlinedTextField(
          value = refNumber,
          onValueChange = { refNumber = it },
          label = { Text(stringResource(InspectionRes.string.reference_number)) },
          modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(Spacing.small))
        OutlinedTextField(
          value = complianceAuthority,
          onValueChange = { complianceAuthority = it },
          label = { Text(stringResource(InspectionRes.string.compliance_authority)) },
          placeholder = { Text(stringResource(InspectionRes.string.compliance_authority_hint)) },
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
          onClick = { showLinkedPicker = true },
          modifier = Modifier.fillMaxWidth()
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
              onClick = { linkedToId = null },
              modifier = Modifier.size(InputChipDefaults.IconSize)
            ) {
              Icon(
                Icons.Default.Close,
                contentDescription = stringResource(InspectionRes.string.remove_link),
                modifier = Modifier.size(InputChipDefaults.IconSize)
              )
            }
          }
        )
      }

      if (showLinkedPicker) {
        InspectionPickerSheet(
          availableCards = availableInspections.filter { it.id != card.id },
          selectedIds = listOfNotNull(linkedToId),
          onToggle = { id ->
            linkedToId = if (linkedToId == id) null else id
            showLinkedPicker = false
          },
          onDismiss = { showLinkedPicker = false },
          singleSelect = true
        )
      }

      Spacer(modifier = Modifier.height(Spacing.large))
      Divider()
      Spacer(modifier = Modifier.height(Spacing.large))

      // Overrides
      Text(
        stringResource(InspectionRes.string.force_overrides_safety),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.error
      )

      Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = forceOverrideEngine, onCheckedChange = { forceOverrideEngine = it })
        Text(stringResource(InspectionRes.string.override_next_due_engine))
      }
      if (forceOverrideEngine) {
        OutlinedTextField(
          value = forcedEngineHours,
          onValueChange = { forcedEngineHours = it.filter { c -> c.isDigit() || c == '.' } },
          label = { Text(stringResource(InspectionRes.string.force_due_engine_hours)) },
          modifier = Modifier.fillMaxWidth().padding(start = 32.dp)
        )
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = forceOverrideDate, onCheckedChange = { forceOverrideDate = it })
        Text(stringResource(InspectionRes.string.override_next_due_date))
      }
      if (forceOverrideDate) {
        OutlinedCard(
          onClick = { showDatePicker = true },
          modifier = Modifier.fillMaxWidth().padding(start = 32.dp)
        ) {
          Row(
            modifier = Modifier.padding(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.DateRange, contentDescription = null)
            Spacer(modifier = Modifier.width(Spacing.small))
            val dateText = forcedDateMillis?.let {
              Instant.fromEpochMilliseconds(it)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
            } ?: stringResource(InspectionRes.string.select_date)
            Text(dateText)
          }
        }
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
            intervalHours.toFloatOrNull()?.let {
              ruleList.add(InspectionRule(engine_hour_rule = EngineHourRule(interval_hours = it)))
            }
          }

          val updated = card.copy(
            title = title,
            component = component,
            type = type,
            rules = ruleList,
            is_one_time = isOneTime,
            reference_number = refNumber.takeIf { it.isNotBlank() } ?: "",
            compliance_authority = complianceAuthority.takeIf { it.isNotBlank() } ?: "",
            force_due_engine_hour = if (forceOverrideEngine) forcedEngineHours.toFloatOrNull()
              ?: 0f else 0f,
            force_due_date = if (forceOverrideDate) forcedDateMillis?.let {
              createWireInstant(
                it / 1000,
                0
              )
            } else null
          )
          onSave(updated)
        },
        onCancelClick = onCancel,
        onDeleteClick = { showDeleteConfirm = true },
        saveEnabled = title.isNotBlank()
      )
    }
  }

  if (showDeleteConfirm) {
    DeleteInspectionConfirmDialog(
      inspectionTitle = title,
      onConfirm = {
        showDeleteConfirm = false
        onDeleteRequest(card.id)
      },
      onDismiss = { showDeleteConfirm = false }
    )
  }

  if (showDatePicker) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(onClick = {
          forcedDateMillis = datePickerState.selectedDateMillis
          showDatePicker = false
        }) { Text(stringResource(CoreRes.string.ok)) }
      }
    ) {
      DatePicker(state = datePickerState)
    }
  }
}
