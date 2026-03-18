package dev.fanfly.wingslog.feature.aircraft.overview.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.InspectionComponentType
import dev.fanfly.wingslog.aircraft.InspectionRule
import dev.fanfly.wingslog.aircraft.OnConditionRule
import dev.fanfly.wingslog.aircraft.TachRule
import dev.fanfly.wingslog.aircraft.TimeRule
import dev.fanfly.wingslog.feature.aircraft.R

private data class QuickTemplate(
    val title: String,
    val component: InspectionComponentType,
    val rules: List<InspectionRule>,
)

private fun buildTimeRule(months: Int): InspectionRule =
    InspectionRule.newBuilder()
        .setTimeRule(TimeRule.newBuilder().setIntervalMonths(months).build())
        .build()

private fun buildTachRule(hours: Float): InspectionRule =
    InspectionRule.newBuilder()
        .setTachRule(TachRule.newBuilder().setIntervalHours(hours).build())
        .build()

private fun buildOnConditionRule(): InspectionRule =
    InspectionRule.newBuilder()
        .setOnConditionRule(OnConditionRule.newBuilder().build())
        .build()

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
        rules = listOf(buildTachRule(100f)),
    ),
    QuickTemplate(
        title = "Oil Change",
        component = InspectionComponentType.INSPECTION_COMPONENT_ENGINE,
        rules = listOf(buildTachRule(50f)),
    ),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddInspectionSheet(
    onDismiss: () -> Unit,
    onSave: (title: String, component: InspectionComponentType, rules: List<InspectionRule>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf("") }
    var titleError by remember { mutableStateOf(false) }
    var selectedComponent by remember { mutableStateOf(InspectionComponentType.INSPECTION_COMPONENT_AIRFRAME) }

    var timeRuleEnabled by remember { mutableStateOf(false) }
    var timeRuleMonths by remember { mutableStateOf("12") }
    var tachRuleEnabled by remember { mutableStateOf(false) }
    var tachRuleHours by remember { mutableStateOf("100") }
    var onConditionEnabled by remember { mutableStateOf(false) }

    fun applyTemplate(template: QuickTemplate) {
        title = template.title
        selectedComponent = template.component
        timeRuleEnabled = false
        tachRuleEnabled = false
        onConditionEnabled = false
        template.rules.forEach { rule ->
            when {
                rule.hasTimeRule() -> {
                    timeRuleEnabled = true
                    timeRuleMonths = rule.timeRule.intervalMonths.toString()
                }
                rule.hasTachRule() -> {
                    tachRuleEnabled = true
                    tachRuleHours = rule.tachRule.intervalHours.toString()
                }
                rule.hasOnConditionRule() -> onConditionEnabled = true
            }
        }
    }

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
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.add_inspection),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }

            // Quick add chips
            Text(
                text = stringResource(R.string.quick_add),
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

            // Title input
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    titleError = false
                },
                label = { Text(stringResource(R.string.inspection_title)) },
                isError = titleError,
                supportingText = if (titleError) {
                    { Text(stringResource(R.string.title_required)) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // Component dropdown
            Text(
                text = stringResource(R.string.component),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    InspectionComponentType.INSPECTION_COMPONENT_AIRFRAME to "Airframe",
                    InspectionComponentType.INSPECTION_COMPONENT_ENGINE to "Engine",
                    InspectionComponentType.INSPECTION_COMPONENT_PROPELLER to "Propeller",
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
                text = "Rules",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Time-based rule toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.time_based_months), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = timeRuleEnabled, onCheckedChange = { timeRuleEnabled = it })
            }
            if (timeRuleEnabled) {
                OutlinedTextField(
                    value = timeRuleMonths,
                    onValueChange = { timeRuleMonths = it },
                    label = { Text(stringResource(R.string.interval_months)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            // Tach-based rule toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.tach_based_hours), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = tachRuleEnabled, onCheckedChange = { tachRuleEnabled = it })
            }
            if (tachRuleEnabled) {
                OutlinedTextField(
                    value = tachRuleHours,
                    onValueChange = { tachRuleHours = it },
                    label = { Text(stringResource(R.string.interval_hours)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            // On-condition toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.on_condition), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = onConditionEnabled, onCheckedChange = { onConditionEnabled = it })
            }

            Spacer(Modifier.height(8.dp))

            // Save button
            Button(
                onClick = {
                    if (title.isBlank()) {
                        titleError = true
                        return@Button
                    }
                    val rules = buildList {
                        if (timeRuleEnabled) {
                            val months = timeRuleMonths.toIntOrNull() ?: 12
                            add(buildTimeRule(months))
                        }
                        if (tachRuleEnabled) {
                            val hours = tachRuleHours.toFloatOrNull() ?: 100f
                            add(buildTachRule(hours))
                        }
                        if (onConditionEnabled) {
                            add(buildOnConditionRule())
                        }
                    }
                    onSave(title.trim(), selectedComponent, rules)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}
