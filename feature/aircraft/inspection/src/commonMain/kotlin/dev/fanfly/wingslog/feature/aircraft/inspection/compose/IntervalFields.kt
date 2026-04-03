package dev.fanfly.wingslog.feature.aircraft.inspection.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.aircraft.inspection.generated.resources.interval_hours
import wingslog.feature.aircraft.inspection.generated.resources.interval_months
import wingslog.feature.aircraft.inspection.generated.resources.intervals
import wingslog.feature.aircraft.inspection.generated.resources.Res as InspectionRes

@Composable
fun IntervalFields(
    intervalMonths: String,
    onMonthsChange: (String) -> Unit,
    intervalHours: String,
    onHoursChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            stringResource(InspectionRes.string.intervals),
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(Spacing.small))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = intervalMonths,
                onValueChange = { onMonthsChange(it.filter { c -> c.isDigit() }) },
                label = { Text(stringResource(InspectionRes.string.interval_months)) },
                placeholder = { Text("e.g. 12") },
                modifier = Modifier.weight(1f),
                prefix = {
                    Text(
                        "Every ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
            Text(
                "OR",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline
            )
            OutlinedTextField(
                value = intervalHours,
                onValueChange = { onHoursChange(it.filter { c -> c.isDigit() || c == '.' }) },
                label = { Text(stringResource(InspectionRes.string.interval_hours)) },
                placeholder = { Text("e.g. 100") },
                modifier = Modifier.weight(1f),
                prefix = {
                    Text(
                        "Every ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        if (intervalMonths.isNotBlank() || intervalHours.isNotBlank()) {
            Spacer(modifier = Modifier.height(Spacing.small))
            Text(
                "Note: This inspection will be due on whichever comes first.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        } else {
            Spacer(modifier = Modifier.height(Spacing.small))
            Text(
                "Set a recurring interval based on time, engine hours, or both.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}
