package dev.fanfly.wingslog.feature.aircraft.inspection.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.aircraft.inspection.generated.resources.Res as InspectionRes
import wingslog.feature.aircraft.inspection.generated.resources.one_time_compliance
import wingslog.feature.aircraft.inspection.generated.resources.one_time_compliance_desc

@Composable
fun OneTimeComplianceFields(
    isOneTime: Boolean,
    onOneTimeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onOneTimeChange(!isOneTime) },
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
        Switch(
            checked = isOneTime,
            onCheckedChange = null // Click handled by Row
        )
    }
}
