package dev.fanfly.wingslog.feature.technician.manage.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.model.userprofile.LicenseType
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.technician.sharedassets.compose.displayResId
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.technician.sharedassets.generated.resources.Res
import wingslog.feature.technician.sharedassets.generated.resources.no_certificate

@Composable
fun TechnicianCard(
  technician: Technician,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val licenseType = try {
    if (technician.cert_type.isBlank()) LicenseType.NONE
    else LicenseType.valueOf(technician.cert_type)
  } catch (_: Exception) {
    LicenseType.NONE
  }
  val certNumber = technician.cert_number.takeIf { it.isNotBlank() }
  val expiration = technician.cert_expiration?.toLocalDate()?.toDisplayFormat()

  Card(
    onClick = onClick,
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(
      1.dp,
      MaterialTheme.colorScheme.outlineVariant
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(Spacing.extraLarge),
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      Text(
        text = technician.name,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
      )

      if (licenseType == LicenseType.NONE) {
        Text(
          text = stringResource(Res.string.no_certificate),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.outline,
        )
      } else {
        val certLine = listOfNotNull(
          stringResource(licenseType.displayResId()),
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
