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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
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
import wingslog.feature.aircraft.generated.resources.back
import wingslog.feature.aircraft.generated.resources.component
import wingslog.feature.aircraft.generated.resources.edit_inspection
import wingslog.feature.aircraft.generated.resources.force_due_engine_hours
import wingslog.feature.aircraft.generated.resources.inspection_title
import wingslog.feature.aircraft.generated.resources.interval_hours
import wingslog.feature.aircraft.generated.resources.interval_months
import wingslog.feature.aircraft.generated.resources.ok
import wingslog.feature.aircraft.generated.resources.override_next_due_date
import wingslog.feature.aircraft.generated.resources.override_next_due_engine
import wingslog.feature.aircraft.generated.resources.schedule_with_another_work
import wingslog.feature.aircraft.generated.resources.schedule_with_another_work_description
import wingslog.feature.aircraft.generated.resources.select_date
import wingslog.feature.aircraft.generated.resources.Res as AircraftRes

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
  var manufacturerUrl by remember { mutableStateOf(card.sb_url) }
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

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            stringResource(AircraftRes.string.edit_inspection).uppercase(),
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

      // Component Type (Static in Edit)
      Text(
        stringResource(AircraftRes.string.component),
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
          text = component.name.removePrefix("INSPECTION_COMPONENT_"),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Spacer(modifier = Modifier.height(Spacing.medium))

      // Compliance Type (Static in Edit)
      Text("COMPLIANCE TYPE", style = MaterialTheme.typography.labelLarge)
      Box(
        modifier = Modifier
          .background(
            MaterialTheme.colorScheme.surfaceVariant,
            RoundedCornerShape(Spacing.cardCornerRadius)
          )
          .padding(horizontal = Spacing.medium, vertical = Spacing.small)
      ) {
        Text(
          text = type.name.removePrefix("COMPLIANCE_TYPE_"),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Spacer(modifier = Modifier.height(Spacing.medium))

      // Regular Interval Inputs
      if (linkedToId == null) {
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
      }

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
      Text(
        stringResource(AircraftRes.string.schedule_with_another_work).uppercase(),
        style = MaterialTheme.typography.labelLarge
      )
      Text(
        stringResource(AircraftRes.string.schedule_with_another_work_description),
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
          Text("Link to inspection")
        }
      } else {
        val linkedInsp = availableInspections.find { it.id == linkedToId }
        InputChip(
          selected = true,
          onClick = { showLinkedPicker = true },
          label = { Text(linkedInsp?.title ?: "Unknown") },
          trailingIcon = {
            IconButton(
              onClick = { linkedToId = null },
              modifier = Modifier.size(InputChipDefaults.IconSize)
            ) {
              Icon(
                Icons.Default.Close,
                contentDescription = "Remove link",
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
        "FORCE OVERRIDES (SAFETY)",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.error
      )

      Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = forceOverrideEngine, onCheckedChange = { forceOverrideEngine = it })
        Text(stringResource(AircraftRes.string.override_next_due_engine))
      }
      if (forceOverrideEngine) {
        OutlinedTextField(
          value = forcedEngineHours,
          onValueChange = { forcedEngineHours = it.filter { c -> c.isDigit() || c == '.' } },
          label = { Text(stringResource(AircraftRes.string.force_due_engine_hours)) },
          modifier = Modifier.fillMaxWidth().padding(start = 32.dp)
        )
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = forceOverrideDate, onCheckedChange = { forceOverrideDate = it })
        Text(stringResource(AircraftRes.string.override_next_due_date))
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
            } ?: stringResource(AircraftRes.string.select_date)
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
            sb_url = manufacturerUrl.takeIf { it.isNotBlank() } ?: "",
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
        onDeleteClick = { onDeleteRequest(card.id) },
        saveEnabled = title.isNotBlank()
      )
    }
  }

  if (showDatePicker) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(onClick = {
          forcedDateMillis = datePickerState.selectedDateMillis
          showDatePicker = false
        }) { Text(stringResource(AircraftRes.string.ok)) }
      }
    ) {
      DatePicker(state = datePickerState)
    }
  }
}
