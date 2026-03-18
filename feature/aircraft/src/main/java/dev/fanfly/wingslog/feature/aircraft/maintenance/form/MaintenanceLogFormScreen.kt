package dev.fanfly.wingslog.feature.aircraft.maintenance.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.feature.aircraft.R
import dev.fanfly.wingslog.feature.aircraft.maintenance.form.compose.InspectionPickerSheet
import dev.fanfly.wingslog.feature.aircraft.maintenance.form.data.MaintenanceLogFormEvent
import dev.fanfly.wingslog.feature.aircraft.maintenance.form.data.MaintenanceLogFormViewModel
import dev.fanfly.wingslog.feature.aircraft.maintenance.util.displayName
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceLogFormScreen(
    navController: NavController,
    viewModel: MaintenanceLogFormViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                MaintenanceLogFormEvent.SaveSuccess -> navController.popBackStack()
                MaintenanceLogFormEvent.DeleteSuccess -> navController.popBackStack()
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_log)) },
            text = { Text(stringResource(R.string.this_action_cannot_be_undone)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteLog()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditMode) stringResource(R.string.edit_log) else stringResource(R.string.add_log)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (viewModel.isEditMode) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete_log_content_description),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
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

                // Inspection Work section
                InspectionWorkSection(
                    selectedIds = uiState.selectedInspectionIds,
                    availableCards = uiState.availableInspectionCards,
                    onAddClick = viewModel::showInspectionPicker,
                    onRemove = viewModel::removeInspectionId,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Inspection picker bottom sheet
                if (uiState.showInspectionPicker) {
                    InspectionPickerSheet(
                        availableCards = uiState.availableInspectionCards,
                        selectedIds = uiState.selectedInspectionIds,
                        onToggle = viewModel::toggleInspectionSelection,
                        onDismiss = viewModel::hideInspectionPicker,
                    )
                }

                // Tach Time
                OutlinedTextField(
                    value = uiState.tachTime,
                    onValueChange = viewModel::onTachTimeChange,
                    label = { Text(stringResource(R.string.tach_time_hours)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                // Airframe Time
                OutlinedTextField(
                    value = uiState.airframeTime,
                    onValueChange = viewModel::onAirframeTimeChange,
                    label = { Text(stringResource(R.string.airframe_time_hours)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                // Prop Time
                OutlinedTextField(
                    value = uiState.propTime,
                    onValueChange = viewModel::onPropTimeChange,
                    label = { Text(stringResource(R.string.prop_time_hours)) },
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
                        Text(stringResource(if (viewModel.isEditMode) R.string.update else R.string.save))
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
                    val engines = aircraft.engine
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
                    aircraft.engine.forEach { engine ->
                        val prop = engine.propeller
                        val hub = prop?.hub
                        if (hub?.serial?.isNotEmpty() == true) {
                            val label = buildString {
                                append("Hub")
                                if (hub.make.isNotEmpty()) append(" - ${hub.make}")
                                if (hub.model.isNotEmpty()) append(" ${hub.model}")
                                append(" (${hub.serial})")
                            }
                            options.add(label to hub.serial)
                        }
                        prop?.blades?.forEach { blade ->
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

// InspectionTypeDropdown removed — replaced by InspectionPickerSheet in sub-task 6

@Composable
private fun InspectionWorkSection(
    selectedIds: List<String>,
    availableCards: List<dev.fanfly.wingslog.aircraft.InspectionCard>,
    onAddClick: () -> Unit,
    onRemove: (cardId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.inspection_work),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
            OutlinedButton(
                onClick = onAddClick,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp, vertical = 4.dp
                ),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.width(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.add), style = MaterialTheme.typography.labelMedium)
            }
        }

        if (selectedIds.isEmpty()) {
            Text(
                text = stringResource(R.string.no_inspection_work_recorded),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            selectedIds.forEach { cardId ->
                val card = availableCards.firstOrNull { it.id == cardId }
                val title = card?.title ?: stringResource(R.string.unknown_inspection, cardId)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRemove(cardId) }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.remove),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
