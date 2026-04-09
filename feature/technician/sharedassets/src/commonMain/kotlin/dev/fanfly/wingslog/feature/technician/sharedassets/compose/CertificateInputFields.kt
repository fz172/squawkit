package dev.fanfly.wingslog.feature.technician.sharedassets.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import dev.fanfly.wingslog.core.model.userprofile.LicenseExpireLimit
import dev.fanfly.wingslog.core.model.userprofile.LicenseType
import dev.fanfly.wingslog.core.ui.common.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.theme.Spacing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.cancel
import wingslog.core.ui.generated.resources.ok
import wingslog.core.ui.generated.resources.select_date
import wingslog.feature.technician.sharedassets.generated.resources.Res
import wingslog.feature.technician.sharedassets.generated.resources.certificate_number
import wingslog.feature.technician.sharedassets.generated.resources.certificate_type
import wingslog.feature.technician.sharedassets.generated.resources.certificate_type_amt
import wingslog.feature.technician.sharedassets.generated.resources.certificate_type_none
import wingslog.feature.technician.sharedassets.generated.resources.certificate_type_repairman
import wingslog.feature.technician.sharedassets.generated.resources.expiration_date
import wingslog.feature.technician.sharedassets.generated.resources.never
import kotlin.time.Instant
import wingslog.core.ui.generated.resources.Res as CoreUiRes

fun LicenseType.displayResId(): StringResource {
  return when (this) {
    LicenseType.NONE -> Res.string.certificate_type_none
    LicenseType.REPAIRMAN -> Res.string.certificate_type_repairman
    LicenseType.AMT -> Res.string.certificate_type_amt
    else -> Res.string.certificate_type_none
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertificateInputFields(
  licenseType: LicenseType,
  onLicenseTypeChanged: (LicenseType) -> Unit,
  licenseNumber: String,
  onLicenseNumberChanged: (String) -> Unit,
  expireLimit: LicenseExpireLimit,
  onExpireLimitChanged: (LicenseExpireLimit) -> Unit,
  expirationDate: Instant?,
  onExpirationDateChanged: (Instant) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(Spacing.columnGap)
  ) {
    // --- License Type (Dropdown) ---
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
      expanded = expanded, onExpandedChange = { expanded = !expanded }) {
      OutlinedTextField(
        value = stringResource(licenseType.displayResId()),
        onValueChange = {},
        readOnly = true,
        label = { Text(text = stringResource(Res.string.certificate_type)) },
        trailingIcon = {
          ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        },
        modifier = Modifier
          .fillMaxWidth()
          .menuAnchor(),
        shape = RoundedCornerShape(Spacing.buttonCornerRadius)
      )
      ExposedDropdownMenu(
        expanded = expanded, onDismissRequest = { expanded = false }) {
        LicenseType.entries.forEach { type ->
          DropdownMenuItem(text = { Text(stringResource(type.displayResId())) }, onClick = {
            onLicenseTypeChanged(type)
            expanded = false
          })
        }
      }
    }

    // --- License Number ---
    OutlinedTextField(
      value = licenseNumber,
      onValueChange = onLicenseNumberChanged,
      label = { Text(stringResource(Res.string.certificate_number)) },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
      shape = RoundedCornerShape(Spacing.buttonCornerRadius),
      enabled = licenseType != LicenseType.NONE
    )

    var showDatePicker by remember { mutableStateOf(false) }

    // --- Expiration Date ---
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      val expirationDateEnabled =
        expireLimit != LicenseExpireLimit.NEVER_EXPIRES && licenseType != LicenseType.NONE
      OutlinedTextField(
        value = if (expireLimit != LicenseExpireLimit.NEVER_EXPIRES)
          expirationDate?.toLocalDateTime(TimeZone.UTC)?.date?.toDisplayFormat() ?: "" else "",
        onValueChange = { },
        readOnly = true,
        label = { Text(stringResource(Res.string.expiration_date)) },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.CalendarToday,
            contentDescription = stringResource(CoreUiRes.string.select_date)
          )
        },
        enabled = false,
        singleLine = true,
        shape = RoundedCornerShape(Spacing.buttonCornerRadius),
        modifier = Modifier
          .weight(1f)
          .clickable {
            if (expirationDateEnabled) {
              showDatePicker = true
            }
          },
        colors = if (expirationDateEnabled) {
          OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
          )
        } else {
          OutlinedTextFieldDefaults.colors()
        }
      )
      Spacer(modifier = Modifier.width(Spacing.large))
      Text(text = stringResource(Res.string.never))
      Checkbox(
        checked = expireLimit == LicenseExpireLimit.NEVER_EXPIRES,
        onCheckedChange = { never ->
          onExpireLimitChanged(if (never) LicenseExpireLimit.NEVER_EXPIRES else LicenseExpireLimit.EXPIRES)
        },
        enabled = licenseType != LicenseType.NONE
      )
    }
    if (showDatePicker) {
      val datePickerState = rememberDatePickerState()
      DatePickerDialog(
        onDismissRequest = { showDatePicker = false },
        confirmButton = {
          TextButton(
            onClick = {
              val selectedDate = datePickerState.selectedDateMillis?.let {
                Instant.fromEpochMilliseconds(it)
              } ?: kotlin.time.Clock.System.now()
              onExpirationDateChanged(selectedDate)
              showDatePicker = false
            }) {
            Text(text = stringResource(CoreUiRes.string.ok))
          }
        },
        dismissButton = {
          TextButton(
            onClick = { showDatePicker = false }) {
            Text(text = stringResource(CoreUiRes.string.cancel))
          }
        }) {
        DatePicker(state = datePickerState)
      }
    }
  }
}