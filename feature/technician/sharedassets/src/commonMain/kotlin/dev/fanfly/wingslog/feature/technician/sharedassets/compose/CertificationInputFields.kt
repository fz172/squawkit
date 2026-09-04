package dev.fanfly.wingslog.feature.technician.sharedassets.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.template.CUSTOM_CERTIFICATION_PREFIX
import dev.fanfly.wingslog.core.template.OfferedCertification
import dev.fanfly.wingslog.core.ui.common.compose.DashedButton
import dev.fanfly.wingslog.core.ui.common.compose.FormSectionLabel
import dev.fanfly.wingslog.core.ui.common.compose.FormTextField
import dev.fanfly.wingslog.core.ui.common.compose.FormValueField
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.thing.CertExpireLimit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.ok
import wingslog.core.sharedassets.generated.resources.select_date
import wingslog.feature.technician.sharedassets.generated.resources.Res
import wingslog.feature.technician.sharedassets.generated.resources.add_certification
import wingslog.feature.technician.sharedassets.generated.resources.certificate_number
import wingslog.feature.technician.sharedassets.generated.resources.certification_name
import wingslog.feature.technician.sharedassets.generated.resources.certifications
import wingslog.feature.technician.sharedassets.generated.resources.custom_certification
import wingslog.feature.technician.sharedassets.generated.resources.expiration_date
import wingslog.feature.technician.sharedassets.generated.resources.never
import wingslog.feature.technician.sharedassets.generated.resources.remove_certification
import wingslog.feature.technician.sharedassets.generated.resources.unnamed_certification
import kotlin.time.Clock
import kotlin.time.Instant
import wingslog.core.sharedassets.generated.resources.Res as CoreUiRes

/**
 * One certification as the form holds it mid-edit — a [dev.fanfly.wingslog.thing.Certification]
 * with the expiry as an [Instant], which is what the date picker deals in.
 */
data class CertificationEntry(
  val type: String,
  val number: String = "",
  val expireLimit: CertExpireLimit = CertExpireLimit.CERT_EXPIRE_LIMIT_EXPIRES,
  val expiration: Instant? = null,
  /** The user's own word, on a `custom_N` entry only. Empty for anything a template names. */
  val label: String = "",
) {
  val isCustom: Boolean get() = type.startsWith(CUSTOM_CERTIFICATION_PREFIX)
}

/**
 * The certifications a person holds, added from what the account's templates declare (PRD §8.6).
 *
 * **No role question.** The credential says which domain the person works in, so the flow asks for
 * the credential and derives the rest. That is one question where the earlier draft had two, and it
 * is why a technician who is both an A&P and an ASE mechanic is one record with two rows rather
 * than two records the duplicate detector then has to reconcile.
 *
 * The section always renders, even when [offered] is empty: **Custom is always on the menu**, so an
 * account holding only Things whose templates declare no credentials can still record the licence
 * its handyman actually holds.
 */
@Composable
fun CertificationInputFields(
  entries: List<CertificationEntry>,
  offered: List<OfferedCertification>,
  onAdd: (String) -> Unit,
  onAddCustom: () -> Unit,
  onRemove: (Int) -> Unit,
  onNumberChanged: (Int, String) -> Unit,
  onLabelChanged: (Int, String) -> Unit,
  onExpireLimitChanged: (Int, CertExpireLimit) -> Unit,
  onExpirationChanged: (Int, Instant) -> Unit,
  modifier: Modifier = Modifier,
) {

  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(Spacing.columnGap),
  ) {
    FormSectionLabel(text = stringResource(Res.string.certifications))

    entries.forEachIndexed { index, entry ->
      CertificationRow(
        entry = entry,
        offered = offered.firstOrNull { it.key == entry.type },
        onRemove = { onRemove(index) },
        onNumberChanged = { onNumberChanged(index, it) },
        onLabelChanged = { onLabelChanged(index, it) },
        onExpireLimitChanged = { onExpireLimitChanged(index, it) },
        onExpirationChanged = { onExpirationChanged(index, it) },
      )
    }

    // Only what the person does not already hold: a second A&P is not a thing anyone has. Custom
    // is always last and always available — the whole point of it is a credential no list predicted.
    val addable =
      offered.filterNot { candidate -> entries.any { it.type == candidate.key } }
    var menuOpen by remember { mutableStateOf(false) }
    Box {
      DashedButton(
        label = stringResource(Res.string.add_certification),
        onClick = { menuOpen = true },
        modifier = Modifier.fillMaxWidth(),
      )
      DropdownMenu(
        expanded = menuOpen,
        onDismissRequest = { menuOpen = false }) {
        addable.forEach { candidate ->
          DropdownMenuItem(
            text = { Text(candidate.label) },
            onClick = {
              menuOpen = false
              onAdd(candidate.key)
            },
          )
        }
        DropdownMenuItem(
          text = { Text(stringResource(Res.string.custom_certification)) },
          onClick = {
            menuOpen = false
            onAddCustom()
          },
        )
      }
    }
  }
}

/**
 * @param offered null when nothing this build carries declares the key — a custom credential the
 *   user named, or a shared technician carrying one from a preset this build lacks. The row still
 *   renders: dropping it would delete the person’s credential on the next save.
 */
@Composable
private fun CertificationRow(
  entry: CertificationEntry,
  offered: OfferedCertification?,
  onRemove: () -> Unit,
  onNumberChanged: (String) -> Unit,
  onLabelChanged: (String) -> Unit,
  onExpireLimitChanged: (CertExpireLimit) -> Unit,
  onExpirationChanged: (Instant) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (entry.isCustom) {
        // A custom credential has no template word, so the header IS the input. Word capitalisation
        // rather than all-caps: this is a name — "Certified Welding Inspector", not a serial.
        FormTextField(
          value = entry.label,
          onValueChange = onLabelChanged,
          label = stringResource(Res.string.certification_name),
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
          modifier = Modifier.weight(1f),
        )
      } else {
        Text(
          text = offered?.label
            ?: entry.type.ifBlank { stringResource(Res.string.unnamed_certification) },
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.weight(1f),
        )
      }
      IconButton(onClick = onRemove) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = stringResource(Res.string.remove_certification),
        )
      }
    }

    // A certificate number is an identifier — matched exactly, printed on an export, and compared
    // between two records to decide they are one person. Same all-caps treatment `is_identifier`
    // gives a tail number or a VIN, and the ViewModel normalises to match.
    FormTextField(
      value = entry.number,
      onValueChange = onNumberChanged,
      label = offered?.def?.number_label?.takeIf { it.isNotBlank() }
        ?: stringResource(Res.string.certificate_number),
      placeholder = offered?.def?.number_placeholder?.takeIf { it.isNotBlank() },
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
      modifier = Modifier.fillMaxWidth(),
    )

    // Every credential gets the date and the "Never" checkbox. Whether one expires is the holder's
    // fact, not the template's: an FAA certificate does not expire and people still record dates
    // against it, so a template predicting the answer was wrong as often as right.
    ExpirationRow(
      entry = entry,
      onExpireLimitChanged = onExpireLimitChanged,
      onExpirationChanged = onExpirationChanged,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpirationRow(
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
