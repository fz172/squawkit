package dev.fanfly.wingslog.feature.aircraft.overview.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.*
import dev.fanfly.wingslog.core.ui.common.compose.BottomButtons
import dev.fanfly.wingslog.core.ui.common.datetime.createWireInstant
import dev.fanfly.wingslog.core.ui.theme.Spacing
import kotlinx.datetime.*
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.aircraft.generated.resources.Res as AircraftRes
import wingslog.feature.aircraft.generated.resources.*

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
    var type by remember { mutableStateOf(ComplianceType.COMPLIANCE_TYPE_RECURRING_INSPECTION) }
    var intervalMonths by remember { mutableStateOf("") }
    var intervalHours by remember { mutableStateOf("") }
    var refNumber by remember { mutableStateOf("") }
    var manufacturerUrl by remember { mutableStateOf("") }
    var linkedToId by remember { mutableStateOf<String?>(null) }
    
    // Force Override States
    var forceOverrideEngine by remember { mutableStateOf(false) }
    var forcedEngineHours by remember { mutableStateOf("") }
    var forceOverrideDate by remember { mutableStateOf(false) }
    var forcedDateMillis by remember { mutableStateOf<Long?>(null) }
    
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(AircraftRes.string.add_inspection).uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(AircraftRes.string.back))
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
            Text(stringResource(AircraftRes.string.component), style = MaterialTheme.typography.labelLarge)
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                InspectionComponentType.entries.filter { it != InspectionComponentType.INSPECTION_COMPONENT_UNKNOWN }.forEach { entry ->
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
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                ComplianceType.entries.forEach { entry ->
                    FilterChip(
                        selected = type == entry,
                        onClick = { type = entry },
                        label = { Text(entry.name.removePrefix("COMPLIANCE_TYPE_")) }
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

            Spacer(modifier = Modifier.height(Spacing.large))
            Divider()
            Spacer(modifier = Modifier.height(Spacing.large))

            // Overrides
            Text("FORCE OVERRIDES (SAFETY)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            
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
                    Row(modifier = Modifier.padding(Spacing.medium), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DateRange, contentDescription = null)
                        Spacer(modifier = Modifier.width(Spacing.small))
                        val dateText = forcedDateMillis?.let { 
                            Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
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
                        is_one_time = false,
                        force_due_engine_hour = if (forceOverrideEngine) forcedEngineHours.toFloatOrNull() ?: 0f else 0f,
                        force_due_date = if (forceOverrideDate) forcedDateMillis?.let { createWireInstant(it / 1000, 0) } else null,
                        notes = ""
                    )
                    onSave(card)
                },
                onCancelClick = onCancel,
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
