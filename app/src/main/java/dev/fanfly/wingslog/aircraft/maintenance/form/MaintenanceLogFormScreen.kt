package dev.fanfly.wingslog.aircraft.maintenance.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.R
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.maintenance.form.data.MaintenanceLogFormEvent
import dev.fanfly.wingslog.aircraft.maintenance.form.data.MaintenanceLogFormViewModel
import dev.fanfly.wingslog.aircraft.maintenance.util.displayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceLogFormScreen(
    navController: NavController,
    viewModel: MaintenanceLogFormViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                MaintenanceLogFormEvent.SaveSuccess -> navController.popBackStack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditMode) stringResource(R.string.edit_log) else stringResource(R.string.add_log)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Work Description (required)
                OutlinedTextField(
                    value = uiState.workDescription,
                    onValueChange = viewModel::onWorkDescriptionChange,
                    label = { Text(stringResource(R.string.work_description_required)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    isError = uiState.error == "Work description is required"
                )

                // Inspection Types
                InspectionTypeDropdown(
                    selected = uiState.inspections,
                    onToggle = { type ->
                        val current = uiState.inspections.toMutableList()
                        if (current.contains(type)) current.remove(type) else current.add(type)
                        viewModel.onInspectionsChange(current)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Tach Time
                OutlinedTextField(
                    value = uiState.tachTime,
                    onValueChange = viewModel::onTachTimeChange,
                    label = { Text(stringResource(R.string.tach_time_hours)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                // Component section
                ComponentSection(
                    aircraft = uiState.aircraft,
                    selectedComponentType = uiState.selectedComponentType,
                    selectedSubComponent = uiState.selectedSubComponent,
                    onComponentTypeChange = viewModel::onComponentTypeChange,
                    onSubComponentChange = viewModel::onSubComponentChange,
                    modifier = Modifier.fillMaxWidth()
                )

                // Error message
                if (uiState.error != null) {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Save button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = viewModel::save,
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Text(if (viewModel.isEditMode) "Update" else "Save")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComponentSection(
    aircraft: Aircraft?,
    selectedComponentType: MaintenanceLog.ComponentType,
    selectedSubComponent: String?,
    onComponentTypeChange: (MaintenanceLog.ComponentType) -> Unit,
    onSubComponentChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Component Type dropdown
        ComponentTypeDropdown(
            selected = selectedComponentType,
            onSelected = onComponentTypeChange,
            modifier = Modifier.fillMaxWidth()
        )

        when (selectedComponentType) {
            MaintenanceLog.ComponentType.AIRFRAME -> {
                // Display aircraft serial (read-only)
                val serial = aircraft?.serial ?: ""
                OutlinedTextField(
                    value = serial,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.airframe_serial)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            MaintenanceLog.ComponentType.ENGINE -> {
                if (aircraft == null) {
                    Text(
                        text = stringResource(R.string.loading_aircraft),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val engines = aircraft.engineList
                    if (engines.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_engines_found),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val options = engines.map { engine ->
                            val label = buildString {
                                if (engine.make.isNotEmpty()) append(engine.make)
                                if (engine.model.isNotEmpty()) {
                                    if (isNotEmpty()) append(" ")
                                    append(engine.model)
                                }
                                if (engine.serial.isNotEmpty()) append(" (${engine.serial})")
                            }
                            label to engine.serial
                        }
                        SubComponentDropdown(
                            label = stringResource(R.string.engine),
                            options = options,
                            selectedSerial = selectedSubComponent,
                            onSelected = onSubComponentChange,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            MaintenanceLog.ComponentType.PROPELLER -> {
                if (aircraft == null) {
                    Text(
                        text = stringResource(R.string.loading_aircraft),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // Collect all propeller components from all engines
                    val options = mutableListOf<Pair<String, String>>()
                    aircraft.engineList.forEach { engine ->
                        val prop = engine.propeller
                        val hub = prop.hub
                        if (hub.serial.isNotEmpty()) {
                            val label = buildString {
                                append("Hub")
                                if (hub.make.isNotEmpty()) append(" - ${hub.make}")
                                if (hub.model.isNotEmpty()) append(" ${hub.model}")
                                append(" (${hub.serial})")
                            }
                            options.add(label to hub.serial)
                        }
                        prop.bladesList.forEach { blade ->
                            if (blade.serial.isNotEmpty()) {
                                val label = buildString {
                                    append("Blade")
                                    if (blade.make.isNotEmpty()) append(" - ${blade.make}")
                                    if (blade.model.isNotEmpty()) append(" ${blade.model}")
                                    append(" (${blade.serial})")
                                }
                                options.add(label to blade.serial)
                            }
                        }
                    }

                    if (options.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_propeller_components_found),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        SubComponentDropdown(
                            label = stringResource(R.string.propeller_component),
                            options = options,
                            selectedSerial = selectedSubComponent,
                            onSelected = onSubComponentChange,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            else -> {
                // UNKNOWN — no sub-component
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubComponentDropdown(
    label: String,
    options: List<Pair<String, String>>, // display label to serial
    selectedSerial: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.second == selectedSerial }?.first ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (displayLabel, serial) ->
                DropdownMenuItem(
                    text = { Text(displayLabel) },
                    onClick = {
                        onSelected(serial)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComponentTypeDropdown(
    selected: MaintenanceLog.ComponentType,
    onSelected: (MaintenanceLog.ComponentType) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        MaintenanceLog.ComponentType.UNKNOWN,
        MaintenanceLog.ComponentType.AIRFRAME,
        MaintenanceLog.ComponentType.ENGINE,
        MaintenanceLog.ComponentType.PROPELLER
    )
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected.displayName(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.component_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName()) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InspectionTypeDropdown(
    selected: List<MaintenanceLog.InspectionType>,
    onToggle: (MaintenanceLog.InspectionType) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        MaintenanceLog.InspectionType.ANNUAL,
        MaintenanceLog.InspectionType.HUNDRED_HOUR,
        MaintenanceLog.InspectionType.ROUTINE,
        MaintenanceLog.InspectionType.TRANSPONDER_CHECK,
        MaintenanceLog.InspectionType.CONDITIONAL,
        MaintenanceLog.InspectionType.OIL_CHANGE,
        MaintenanceLog.InspectionType.ELT,
        MaintenanceLog.InspectionType.ALTIMETER_PITOT_STATIC,
    )
    var expanded by remember { mutableStateOf(false) }
    val noneLabel = stringResource(R.string.none)
    val displayText = if (selected.isEmpty()) noneLabel else selected.joinToString(", ") { it.displayName() }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.inspection_types)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selected.contains(option),
                                onCheckedChange = null
                            )
                            Text(option.displayName())
                        }
                    },
                    onClick = { onToggle(option) }
                )
            }
        }
    }
}
