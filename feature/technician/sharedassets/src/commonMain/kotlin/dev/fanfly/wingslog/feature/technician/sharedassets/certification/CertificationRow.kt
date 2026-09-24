package dev.fanfly.wingslog.feature.technician.sharedassets.certification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.fanfly.wingslog.core.template.OfferedCertification
import dev.fanfly.wingslog.core.ui.form.FormTextField
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.thing.CertExpireLimit
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.technician.sharedassets.generated.resources.Res
import wingslog.feature.technician.sharedassets.generated.resources.certificate_number
import wingslog.feature.technician.sharedassets.generated.resources.certification_name
import wingslog.feature.technician.sharedassets.generated.resources.remove_certification
import wingslog.feature.technician.sharedassets.generated.resources.unnamed_certification

/**
 * @param offered null when nothing this build carries declares the key — a custom credential the
 *   user named, or a shared technician carrying one from a preset this build lacks. The row still
 *   renders: dropping it would delete the person’s credential on the next save.
 */
@Composable
internal fun CertificationRow(
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
