package dev.fanfly.wingslog.feature.search.viewing.filter

import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.popup.DatePickerDialog
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.ok
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(
  label: String,
  date: LocalDate,
  onDateChange: (LocalDate) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showPicker by remember { mutableStateOf(false) }
  OutlinedButton(onClick = { showPicker = true }, modifier = modifier) {
    Text("$label: ${date.toDisplayFormat()}")
  }
  if (showPicker) {
    val state = rememberDatePickerState(
      initialSelectedDateMillis = date.atStartOfDayIn(TimeZone.UTC)
        .toEpochMilliseconds(),
    )
    DatePickerDialog(
      onDismissRequest = { showPicker = false },
      confirmButton = {
        TextButton(onClick = {
          state.selectedDateMillis?.let {
            onDateChange(
              Instant.fromEpochMilliseconds(it)
                .toLocalDateTime(TimeZone.UTC).date
            )
          }
          showPicker = false
        }) { Text(stringResource(CoreRes.string.ok)) }
      },
      dismissButton = {
        TextButton(onClick = { showPicker = false }) {
          Text(
            stringResource(
              CoreRes.string.cancel
            )
          )
        }
      },
    ) {
      DatePicker(state = state)
    }
  }
}
