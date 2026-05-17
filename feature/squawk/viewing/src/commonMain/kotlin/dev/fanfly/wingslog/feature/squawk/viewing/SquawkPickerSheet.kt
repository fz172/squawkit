package dev.fanfly.wingslog.feature.squawk.viewing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.aircraft.Squawk
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
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = Spacing.screenPadding),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Text(
        text = stringResource(Res.string.squawks),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = Spacing.small),
      )

      LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
        items(openSquawks, key = { it.id }) { squawk ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
          ) {
            Checkbox(
              checked = squawk.id in selectedIds,
              onCheckedChange = { checked -> onToggle(squawk.id, checked) },
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = squawk.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
              )
              if (squawk.description.isNotBlank()) {
                Text(
                  text = squawk.description,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }
  }
}
