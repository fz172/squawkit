package dev.fanfly.wingslog.feature.userprofile.userprofilecard.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.CertExpireLimit
import dev.fanfly.wingslog.aircraft.CertificateType
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.ui.common.compose.CircularImage
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.technician.sharedassets.compose.displayResId
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.userprofile.sharedassets.generated.resources.Res as SharedAssetsRes
import wingslog.feature.userprofile.sharedassets.generated.resources.anonymous_user
import wingslog.feature.userprofile.sharedassets.generated.resources.ic_anonymous_user
import wingslog.feature.userprofile.userprofilecard.generated.resources.Res as CardRes
import wingslog.feature.userprofile.userprofilecard.generated.resources.profile_picture

@Composable
fun UserProfileCard(
  self: Technician?,
  photoUri: String?,
) {
  Card(
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(Spacing.extraLarge),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      CircularImage(
        photoUri = photoUri,
        contentDescription = stringResource(CardRes.string.profile_picture),
        fallbackRes = SharedAssetsRes.drawable.ic_anonymous_user,
      )

      Spacer(modifier = Modifier.height(Spacing.medium))

      val displayName = self?.name?.takeIf { it.isNotBlank() }
      Text(
        text = displayName ?: stringResource(SharedAssetsRes.string.anonymous_user),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )

      if (self != null) {
        val certType = when {
          self.certificate_type != CertificateType.CERTIFICATE_TYPE_NONE -> self.certificate_type
          self.cert_type.isNotBlank() && self.cert_type != "NONE" -> try {
            CertificateType.valueOf(self.cert_type)
          } catch (_: Exception) {
            CertificateType.CERTIFICATE_TYPE_NONE
          }
          else -> CertificateType.CERTIFICATE_TYPE_NONE
        }

        if (certType != CertificateType.CERTIFICATE_TYPE_NONE) {
          Text(
            text = stringResource(certType.displayResId()),
            style = MaterialTheme.typography.bodyLarge,
          )
          if (self.cert_number.isNotBlank()) {
            Text(
              text = self.cert_number,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
          val showExpiration = when {
            self.cert_expire_limit == CertExpireLimit.CERT_EXPIRE_LIMIT_NEVER_EXPIRES -> false
            self.cert_expiration != null -> true
            else -> false
          }
          if (showExpiration) {
            Text(
              text = self.cert_expiration?.toLocalDate()?.toDisplayFormat() ?: "",
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      }
    }
  }
}
