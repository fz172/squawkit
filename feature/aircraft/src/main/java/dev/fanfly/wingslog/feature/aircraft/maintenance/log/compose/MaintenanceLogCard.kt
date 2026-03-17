package dev.fanfly.wingslog.feature.aircraft.maintenance.log.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.feature.aircraft.R
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.feature.aircraft.maintenance.util.displayName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MaintenanceLogCard(
    log: MaintenanceLog,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val unknownDate = stringResource(R.string.unknown_date)
    val dateStr = if (log.hasTimestamp()) {
        dateFormat.format(Date(log.timestamp.seconds * 1000))
    } else {
        unknownDate
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_log_content_description))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_log_content_description),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (log.inspectionList.isNotEmpty()) {
                Text(
                    text = log.inspectionList.joinToString(", ") { it.displayName() },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = log.workDescription,
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (log.tachTime > 0.0) {
                    Text(
                        text = stringResource(R.string.tach_format, log.tachTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (log.componentType != MaintenanceLog.ComponentType.UNKNOWN) {
                    Text(
                        text = log.componentType.displayName(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MaintenanceLogCardPreview() {
    val log = dev.fanfly.wingslog.aircraft.MaintenanceLog.newBuilder()
        .setId("preview-id")
        .setWorkDescription("Performed annual inspection and oil change.")
        .setTachTime(1234.5)
        .setComponentType(dev.fanfly.wingslog.aircraft.MaintenanceLog.ComponentType.ENGINE)
        .addInspection(dev.fanfly.wingslog.aircraft.MaintenanceLog.InspectionType.ANNUAL)
        .addInspection(dev.fanfly.wingslog.aircraft.MaintenanceLog.InspectionType.OIL_CHANGE)
        .build()
    MaterialTheme {
        MaintenanceLogCard(log = log, onEdit = {}, onDelete = {})
    }
}
