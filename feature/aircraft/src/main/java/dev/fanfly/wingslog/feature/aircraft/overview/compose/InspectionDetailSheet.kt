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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.InspectionRule.RuleCase
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.core.ui.theme.StatusOk
import dev.fanfly.wingslog.feature.aircraft.database.DueStatus
import dev.fanfly.wingslog.feature.aircraft.overview.data.InspectionCardWithStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionDetailSheet(
    cardWithStatus: InspectionCardWithStatus,
    logs: List<MaintenanceLog>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val card = cardWithStatus.card
    val dueStatus = cardWithStatus.dueStatus

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
                    text = card.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }

            // Component badge
            val componentLabel = when (card.component.name) {
                "INSPECTION_COMPONENT_ENGINE" -> "Engine"
                "INSPECTION_COMPONENT_PROPELLER" -> "Propeller"
                else -> "Airframe"
            }
            SuggestionChip(
                onClick = {},
                label = { Text(componentLabel, style = MaterialTheme.typography.labelMedium) },
            )

            // Rules summary
            if (card.rulesList.isNotEmpty()) {
                Text(
                    text = "Rules",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                card.rulesList.forEach { rule ->
                    val ruleText = when (rule.ruleCase) {
                        RuleCase.TIME_RULE -> "Every ${rule.timeRule.intervalMonths} months"
                        RuleCase.TACH_RULE -> "Every ${rule.tachRule.intervalHours.toInt()} tach hours"
                        RuleCase.ON_CONDITION_RULE -> {
                            val desc = rule.onConditionRule.description
                            if (desc.isBlank()) "On condition" else "On condition: $desc"
                        }
                        else -> "Unknown rule"
                    }
                    Text(
                        text = "• $ruleText",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            HorizontalDivider()

            // Next due status
            Text(
                text = "Next Due",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DueStatusChip(dueStatus = dueStatus)

            HorizontalDivider()

            // Maintenance log history
            Text(
                text = "Maintenance History",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (logs.isEmpty()) {
                Text(
                    text = "No maintenance logs for this inspection yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Reverse-chrono (logs are already sorted by the ViewModel)
                logs.forEach { log ->
                    LogHistoryItem(log = log)
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DueStatusChip(dueStatus: DueStatus) {
    val (label, color) = when {
        dueStatus.isOnCondition -> "On Condition" to MaterialTheme.colorScheme.onSurfaceVariant
        dueStatus.isOverdue -> {
            val dateStr = dueStatus.nextDueDate?.format(dateFormatter) ?: ""
            "Overdue${if (dateStr.isNotBlank()) " (was $dateStr)" else ""}" to MaterialTheme.colorScheme.error
        }
        dueStatus.nextDueDate != null -> "Due ${dueStatus.nextDueDate?.format(dateFormatter)}" to StatusOk
        dueStatus.nextDueTach != null -> "Due @ ${"%.1f".format(dueStatus.nextDueTach ?: 0f)} tach hrs" to StatusOk
        else -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    AssistChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color),
    )
}

@Composable
private fun LogHistoryItem(log: MaintenanceLog) {
    val dateStr = if (log.timestamp.seconds > 0) {
        Instant.ofEpochSecond(log.timestamp.seconds)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(dateFormatter)
    } else {
        "Unknown date"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = dateStr,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (log.tachTime > 0.0) {
                Text(
                    text = "${"%.1f".format(log.tachTime)} tach",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (log.workDescription.isNotBlank()) {
            Text(
                text = log.workDescription,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
