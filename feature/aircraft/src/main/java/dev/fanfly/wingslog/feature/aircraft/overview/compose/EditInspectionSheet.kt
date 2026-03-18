package dev.fanfly.wingslog.feature.aircraft.overview.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.protobuf.Timestamp
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.aircraft.InspectionComponentType
import dev.fanfly.wingslog.aircraft.InspectionRule
import dev.fanfly.wingslog.aircraft.InspectionRule.RuleCase
import dev.fanfly.wingslog.aircraft.OnConditionRule
import dev.fanfly.wingslog.aircraft.TachRule
import dev.fanfly.wingslog.aircraft.TimeRule
import dev.fanfly.wingslog.feature.aircraft.overview.data.InspectionCardWithStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditInspectionSheet(
    cardWithStatus: InspectionCardWithStatus,
    onDismiss: () -> Unit,
    onSave: (
        cardId: String,
        title: String,
        component: InspectionComponentType,
        rules: List<InspectionRule>,
        forceDueDate: Timestamp?,
        forceDueTach: Float,
    ) -> Unit,
    onDeleteRequest: (cardId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val card = cardWithStatus.card

    // Pre-populate from card
    var title by remember { mutableStateOf(card.title) }
    var titleError by remember { mutableStateOf(false) }
    var selectedComponent by remember { mutableStateOf(card.component) }

    // Parse existing rules
    var timeRuleEnabled by remember {
        mutableStateOf(card.rulesList.any { it.ruleCase == RuleCase.TIME_RULE })
    }
    var timeRuleMonths by remember {
        mutableStateOf(
            card.rulesList.firstOrNull { it.ruleCase == RuleCase.TIME_RULE }
                ?.timeRule?.intervalMonths?.toString() ?: "12"
        )
    }
    var tachRuleEnabled by remember {
        mutableStateOf(card.rulesList.any { it.ruleCase == RuleCase.TACH_RULE })
    }
    var tachRuleHours by remember {
        mutableStateOf(
            card.rulesList.firstOrNull { it.ruleCase == RuleCase.TACH_RULE }
                ?.tachRule?.intervalHours?.toString() ?: "100"
        )
    }
    var onConditionEnabled by remember {
        mutableStateOf(card.rulesList.any { it.ruleCase == RuleCase.ON_CONDITION_RULE })
    }

    // Force-override toggles
    val hasForcedDate = card.hasForceDueDate() &&
            (card.forceDueDate.seconds > 0 || card.forceDueDate.nanos > 0)
    val hasForcedTach = card.forceDueTach > 0f
    var forceOverrideEnabled by remember { mutableStateOf(hasForcedDate || hasForcedTach) }
    var forceTachHours by remember {
        mutableStateOf(if (hasForcedTach) card.forceDueTach.toString() else "")
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
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Edit Inspection",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }

            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; titleError = false },
                label = { Text("Inspection Title") },
                isError = titleError,
                supportingText = if (titleError) {
                    { Text("Title is required") }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // Component chips
            Text(
                text = "Component",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Time-based (months)", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = timeRuleEnabled, onCheckedChange = { timeRuleEnabled = it })
            }
            if (timeRuleEnabled) {
                OutlinedTextField(
                    value = timeRuleMonths,
                    onValueChange = { timeRuleMonths = it },
                    label = { Text("Interval (months)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Tach-based (hours)", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = tachRuleEnabled, onCheckedChange = { tachRuleEnabled = it })
            }
            if (tachRuleEnabled) {
                OutlinedTextField(
                    value = tachRuleHours,
                    onValueChange = { tachRuleHours = it },
                    label = { Text("Interval (hours)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("On Condition", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = onConditionEnabled, onCheckedChange = { onConditionEnabled = it })
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
                    Text("Override next due tach", style = MaterialTheme.typography.bodyMedium)
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
                    value = forceTachHours,
                    onValueChange = { forceTachHours = it },
                    label = { Text("Force Due Tach (hours)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Save
            Button(
                onClick = {
                    if (title.isBlank()) {
                        titleError = true
                        return@Button
                    }
                    val rules = buildList {
                        if (timeRuleEnabled) {
                            val months = timeRuleMonths.toIntOrNull() ?: 12
                            add(
                                InspectionRule.newBuilder()
                                    .setTimeRule(TimeRule.newBuilder().setIntervalMonths(months).build())
                                    .build()
                            )
                        }
                        if (tachRuleEnabled) {
                            val hours = tachRuleHours.toFloatOrNull() ?: 100f
                            add(
                                InspectionRule.newBuilder()
                                    .setTachRule(TachRule.newBuilder().setIntervalHours(hours).build())
                                    .build()
                            )
                        }
                        if (onConditionEnabled) {
                            add(
                                InspectionRule.newBuilder()
                                    .setOnConditionRule(OnConditionRule.newBuilder().build())
                                    .build()
                            )
                        }
                    }
                    val forcedTach = if (forceOverrideEnabled) forceTachHours.toFloatOrNull() ?: 0f else 0f
                    onSave(card.id, title.trim(), selectedComponent, rules, null, forcedTach)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }

            // Delete
            OutlinedButton(
                onClick = { onDeleteRequest(card.id) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Delete Inspection")
            }
        }
    }
}
