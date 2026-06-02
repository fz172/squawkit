package dev.fanfly.wingslog.feature.squawk.viewing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.core.ui.common.compose.PickerSelectableRow
import dev.fanfly.wingslog.core.ui.common.compose.PickerSelectionMode
import dev.fanfly.wingslog.core.ui.common.compose.PickerSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.squawks

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquawkPickerSheet(
  openSquawks: List<Squawk>,
  selectedIds: Set<String>,
  onToggle: (String, Boolean) -> Unit,
  onDismiss: () -> Unit,
) =
  PickerSheet(
    onDismiss = onDismiss,
    headerSlot = {
      Text(
        text = stringResource(Res.string.squawks),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
    },
  ) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
      items(openSquawks, key = { it.id }) { squawk ->
        val checked = squawk.id in selectedIds
        PickerSelectableRow(
          title = squawk.title,
          subtitle = squawk.description,
          selected = checked,
          selectionMode = PickerSelectionMode.CHECKBOX,
          titleStyle = MaterialTheme.typography.bodyMedium,
          titleWeight = FontWeight.Medium,
          onClick = { onToggle(squawk.id, !checked) },
        )
      }
    }
  }
