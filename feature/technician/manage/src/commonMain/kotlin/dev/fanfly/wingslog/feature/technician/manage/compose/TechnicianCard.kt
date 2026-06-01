package dev.fanfly.wingslog.feature.technician.manage.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.aircraft.CertificateType
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.technician.sharedassets.compose.displayResId
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.technician.sharedassets.generated.resources.Res
import wingslog.feature.technician.sharedassets.generated.resources.no_certificate
import wingslog.feature.technician.sharedassets.generated.resources.you_badge

@Composable
fun TechnicianCard(
  technician: Technician,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  isSelf: Boolean = false,
) {
  val certType = when {
    technician.certificate_type != CertificateType.CERTIFICATE_TYPE_NONE -> technician.certificate_type
    technician.cert_type.isNotBlank() && technician.cert_type != "NONE" -> try {
      CertificateType.valueOf(technician.cert_type)
    } catch (_: Exception) {
      CertificateType.CERTIFICATE_TYPE_NONE
    }
    else -> CertificateType.CERTIFICATE_TYPE_NONE
  }
  val certNumber = technician.cert_number.takeIf { it.isNotBlank() }
  val expiration = technician.cert_expiration?.toLocalDate()?.toDisplayFormat()

  Card(
    onClick = onClick,
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(Spacing.extraLarge),
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = technician.name,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
        )
        if (isSelf) {
          SuggestionChip(
            onClick = {},
            label = { Text(stringResource(Res.string.you_badge), style = MaterialTheme.typography.labelSmall) },
            colors = SuggestionChipDefaults.suggestionChipColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
          )
        }
      }

      if (certType == CertificateType.CERTIFICATE_TYPE_NONE) {
        Text(
          text = stringResource(Res.string.no_certificate),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.outline,
        )
      } else {
        val certLine = listOfNotNull(
          stringResource(certType.displayResId()),
          certNumber,
        ).joinToString(" · ")
        Text(
          text = certLine,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (expiration != null) {
          Text(
            text = expiration,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
