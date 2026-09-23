package dev.fanfly.wingslog.feature.logs.update.form

import androidx.compose.material3.DatePicker
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.popup.DatePickerDialog
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.ok
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/** The maintenance-date picker, seeded with the current date so re-opening lands on it. */
@Composable
internal fun LogDatePickerDialog(
  initialDate: LocalDate?,
  onConfirm: (LocalDate) -> Unit,
  onDismiss: () -> Unit,
) {
  val initialMs = initialDate?.let { date ->
    LocalDateTime(date.year, date.month, date.day, 12, 0, 0).let { ldt ->
      Instant.fromEpochSeconds(ldt.date.toEpochDays() * 86400L)
        .toEpochMilliseconds()
    }
  }
  val datePickerState =
    rememberDatePickerState(initialSelectedDateMillis = initialMs)
  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = {
        val selectedMs = datePickerState.selectedDateMillis
        if (selectedMs != null) {
          val selectedDate =
            Instant.fromEpochMilliseconds(selectedMs)
              .toLocalDateTime(TimeZone.UTC).date
          onConfirm(selectedDate)
        }
        onDismiss()
      }) {
        Text(stringResource(CoreRes.string.ok))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(CoreRes.string.cancel))
      }
    },
  ) {
    DatePicker(state = datePickerState)
  }
}
