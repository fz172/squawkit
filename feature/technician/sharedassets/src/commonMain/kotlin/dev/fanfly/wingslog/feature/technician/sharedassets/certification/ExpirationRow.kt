package dev.fanfly.wingslog.feature.technician.sharedassets.certification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.form.FormSectionLabel
import dev.fanfly.wingslog.core.ui.form.FormValueField
import dev.fanfly.wingslog.core.ui.popup.DatePickerDialog
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.thing.CertExpireLimit
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.ok
import wingslog.core.sharedassets.generated.resources.select_date
import wingslog.feature.technician.sharedassets.generated.resources.Res
import wingslog.feature.technician.sharedassets.generated.resources.expiration_date
import wingslog.feature.technician.sharedassets.generated.resources.never
import wingslog.core.sharedassets.generated.resources.Res as CoreUiRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpirationRow(
  entry: CertificationEntry,
  onExpireLimitChanged: (CertExpireLimit) -> Unit,
  onExpirationChanged: (Instant) -> Unit,
) {
  var showDatePicker by remember { mutableStateOf(false) }
  val dated =
    entry.expireLimit != CertExpireLimit.CERT_EXPIRE_LIMIT_NEVER_EXPIRES

  Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
    FormSectionLabel(text = stringResource(Res.string.expiration_date))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FormValueField(
        value = if (dated) {
          entry.expiration?.toLocalDateTime(TimeZone.UTC)?.date?.toDisplayFormat()
            .orEmpty()
        } else {
          ""
        },
        label = stringResource(Res.string.expiration_date),
        showLabel = false,
        trailingIcon = if (dated) {
          {
            Icon(
              imageVector = Icons.Default.CalendarToday,
              contentDescription = stringResource(CoreUiRes.string.select_date),
            )
          }
        } else {
          null
        },
        onClick = if (dated) ({ showDatePicker = true }) else null,
        accessibilityDescription = stringResource(CoreUiRes.string.select_date),
        modifier = Modifier.weight(1f),
      )
      Spacer(modifier = Modifier.width(Spacing.large))
      Text(text = stringResource(Res.string.never))
      Checkbox(
        checked = !dated,
        onCheckedChange = { never ->
          onExpireLimitChanged(
            if (never) {
              CertExpireLimit.CERT_EXPIRE_LIMIT_NEVER_EXPIRES
            } else {
              CertExpireLimit.CERT_EXPIRE_LIMIT_EXPIRES
            }
          )
        },
      )
    }
  }

  if (showDatePicker) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(onClick = {
          val selected = datePickerState.selectedDateMillis
            ?.let { Instant.fromEpochMilliseconds(it) }
            ?: Clock.System.now()
          onExpirationChanged(selected)
          showDatePicker = false
        }) {
          Text(text = stringResource(CoreUiRes.string.ok))
        }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) {
          Text(text = stringResource(CoreUiRes.string.cancel))
        }
      },
    ) {
      DatePicker(state = datePickerState)
    }
  }
}
