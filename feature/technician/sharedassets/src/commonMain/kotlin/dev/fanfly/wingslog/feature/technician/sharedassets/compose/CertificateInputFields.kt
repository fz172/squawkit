package dev.fanfly.wingslog.feature.technician.sharedassets.compose

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
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import dev.fanfly.wingslog.aircraft.CertExpireLimit
import dev.fanfly.wingslog.aircraft.CertificateType
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.compose.FormSectionLabel
import dev.fanfly.wingslog.core.ui.common.compose.FormTextField
import dev.fanfly.wingslog.core.ui.common.compose.FormValueField
import dev.fanfly.wingslog.core.ui.theme.Spacing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.cancel
import wingslog.core.ui.generated.resources.none
import wingslog.core.ui.generated.resources.ok
import wingslog.core.ui.generated.resources.select_date
import wingslog.feature.technician.sharedassets.generated.resources.Res
import wingslog.feature.technician.sharedassets.generated.resources.certificate_number
import wingslog.feature.technician.sharedassets.generated.resources.certificate_type
import wingslog.feature.technician.sharedassets.generated.resources.certificate_type_amt
import wingslog.feature.technician.sharedassets.generated.resources.certificate_type_repairman
import wingslog.feature.technician.sharedassets.generated.resources.expiration_date
import wingslog.feature.technician.sharedassets.generated.resources.never
import kotlin.time.Clock
import kotlin.time.Instant
import wingslog.core.ui.generated.resources.Res as CoreUiRes

fun CertificateType.displayResId(): StringResource {
  return when (this) {
    CertificateType.CERTIFICATE_TYPE_NONE -> CoreUiRes.string.none
    CertificateType.CERTIFICATE_TYPE_REPAIRMAN -> Res.string.certificate_type_repairman
    CertificateType.CERTIFICATE_TYPE_AMT -> Res.string.certificate_type_amt
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertificateInputFields(
  certType: CertificateType,
  onCertTypeChanged: (CertificateType) -> Unit,
  certNumber: String,
  onCertNumberChanged: (String) -> Unit,
  expireLimit: CertExpireLimit,
  onExpireLimitChanged: (CertExpireLimit) -> Unit,
  expirationDate: Instant?,
  onExpirationDateChanged: (Instant) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(Spacing.columnGap)
  ) {
    // --- Certificate Type ---
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
      FormSectionLabel(text = stringResource(Res.string.certificate_type))
      val types = CertificateType.entries
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        types.forEachIndexed { index, type ->
          SegmentedButton(
            selected = certType == type,
            onClick = { onCertTypeChanged(type) },
            shape = SegmentedButtonDefaults.itemShape(
              index = index,
              count = types.size
            ),
            icon = {},
            label = { Text(stringResource(type.displayResId())) },
          )
        }
      }
    }

    FormTextField(
      value = certNumber,
      onValueChange = onCertNumberChanged,
      label = stringResource(Res.string.certificate_number),
      modifier = Modifier.fillMaxWidth(),
      editable = certType != CertificateType.CERTIFICATE_TYPE_NONE,
    )

    var showDatePicker by remember { mutableStateOf(false) }

    if (certType == CertificateType.CERTIFICATE_TYPE_NONE) {
      FormValueField(
        value = "",
        label = stringResource(Res.string.expiration_date),
        modifier = Modifier.fillMaxWidth(),
      )
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
        FormSectionLabel(text = stringResource(Res.string.expiration_date))
        val expirationDateEnabled =
          expireLimit != CertExpireLimit.CERT_EXPIRE_LIMIT_NEVER_EXPIRES
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FormValueField(
            value = if (expirationDateEnabled)
              expirationDate?.toLocalDateTime(TimeZone.UTC)?.date?.toDisplayFormat()
                ?: "" else "",
            label = stringResource(Res.string.expiration_date),
            showLabel = false,
            trailingIcon = if (expirationDateEnabled) {
              {
                Icon(
                  imageVector = Icons.Default.CalendarToday,
                  contentDescription = stringResource(CoreUiRes.string.select_date),
                )
              }
            } else null,
            onClick = if (expirationDateEnabled) {
              { showDatePicker = true }
            } else null,
            accessibilityDescription = stringResource(CoreUiRes.string.select_date),
            modifier = Modifier.weight(1f),
          )
          Spacer(modifier = Modifier.width(Spacing.large))
          Text(text = stringResource(Res.string.never))
          Checkbox(
            checked = expireLimit == CertExpireLimit.CERT_EXPIRE_LIMIT_NEVER_EXPIRES,
            onCheckedChange = { never ->
              onExpireLimitChanged(
                if (never) CertExpireLimit.CERT_EXPIRE_LIMIT_NEVER_EXPIRES
                else CertExpireLimit.CERT_EXPIRE_LIMIT_EXPIRES
              )
            },
          )
        }
      }
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
              } ?: Clock.System.now()
              onExpirationDateChanged(selectedDate)
              showDatePicker = false
            }) {
            Text(text = stringResource(CoreUiRes.string.ok))
          }
        },
        dismissButton = {
          TextButton(onClick = { showDatePicker = false }) {
            Text(text = stringResource(CoreUiRes.string.cancel))
          }
        }) {
        DatePicker(state = datePickerState)
      }
    }
  }
}
