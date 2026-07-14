package dev.fanfly.wingslog.feature.userprofile.userprofilecard.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.aircraft.CertExpireLimit
import dev.fanfly.wingslog.aircraft.CertificateType
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors
import dev.fanfly.wingslog.core.ui.widget.avataricon.compose.CircularImage
import dev.fanfly.wingslog.core.ui.widget.avataricon.compose.toInitials
import dev.fanfly.wingslog.feature.technician.sharedassets.compose.displayResId
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.userprofile.sharedassets.generated.resources.anonymous_user
import wingslog.feature.userprofile.sharedassets.generated.resources.ic_anonymous_user
import wingslog.feature.userprofile.userprofilecard.generated.resources.profile_picture
import kotlin.time.Clock
import com.squareup.wire.Instant as WireInstant
import wingslog.feature.userprofile.sharedassets.generated.resources.Res as SharedAssetsRes
import wingslog.feature.userprofile.userprofilecard.generated.resources.Res as CardRes

// Flat profile header from the Settings design handoff: a 60dp rounded-square avatar (initials or
// photo) beside the name + certificate line, inside a bordered card.
private val AvatarSize = 60.dp
private val AvatarShape = RoundedCornerShape(18.dp)
private val CardPadding = 22.dp

@Composable
fun UserProfileCard(
  self: Technician?,
  photoUri: String?,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = RoundedCornerShape(Spacing.large),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.outlineVariant
    ),
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(CardPadding),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ProfileAvatar(self = self, photoUri = photoUri)
      Spacer(modifier = Modifier.width(Spacing.large))
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
      ) {
        ProfileNameAndCreds(self)
      }
    }
  }
}

@Composable
private fun ProfileNameAndCreds(self: Technician?) {
  val displayName = self?.name?.takeIf { it.isNotBlank() }
  Text(
    text = displayName ?: stringResource(SharedAssetsRes.string.anonymous_user),
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.onSurface,
  )

  val certType = self?.let { resolveCertType(it) } ?: return
  if (certType == CertificateType.CERTIFICATE_TYPE_NONE) return

  val certNumber = self.cert_number.takeIf { it.isNotBlank() }
  val certLine = listOfNotNull(
    stringResource(certType.displayResId()),
    certNumber,
  ).joinToString(" · ")
  Text(
    text = certLine,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )

  val showExpiration =
    self.cert_expire_limit != CertExpireLimit.CERT_EXPIRE_LIMIT_NEVER_EXPIRES
      && self.cert_expiration != null
  if (showExpiration) {
    Text(
      text = self.cert_expiration!!.toLocalDate(TimeZone.UTC)
        .toDisplayFormat(),
      style = MaterialTheme.typography.bodySmall,
      color = certExpiryColor(self.cert_expiration!!),
    )
  }
}

@Composable
private fun ProfileAvatar(
  self: Technician?,
  photoUri: String?,
  modifier: Modifier = Modifier,
) {
  val initials = self?.name.toInitials()
  val hasPhoto = !photoUri.isNullOrBlank()
  Box(
    modifier = modifier
      .size(AvatarSize)
      .clip(AvatarShape)
      .background(MaterialTheme.colorScheme.surfaceVariant),
    contentAlignment = Alignment.Center,
  ) {
    when {
      // A photo or the anonymous fallback drawable fills the rounded square; initials sit on the
      // neutral chip when only a name is known.
      hasPhoto || initials == null -> CircularImage(
        photoUri = photoUri,
        contentDescription = stringResource(CardRes.string.profile_picture),
        size = AvatarSize,
        shape = AvatarShape,
        fallbackRes = SharedAssetsRes.drawable.ic_anonymous_user,
      )

      else -> Text(
        text = initials,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun certExpiryColor(expiration: WireInstant): Color {
  // The expiration is a picked wall date stored as UTC midnight; read it back in UTC so the
  // comparison is date-vs-date rather than shifting a day off the device's offset.
  val expiryDate = expiration.toLocalDate(TimeZone.UTC)
  val today = Clock.System.now()
    .toLocalDateTime(TimeZone.currentSystemDefault())
    .date
  val daysUntil = expiryDate.toEpochDays() - today.toEpochDays()
  val colors = MaterialTheme.statusColors
  return when {
    daysUntil < 0 -> colors.critical.accent
    daysUntil <= 90 -> colors.caution.accent
    else -> MaterialTheme.colorScheme.onSurfaceVariant
  }
}

private fun resolveCertType(technician: Technician): CertificateType {
  return when {
    technician.certificate_type != CertificateType.CERTIFICATE_TYPE_NONE -> technician.certificate_type
    technician.cert_type.isNotBlank() && technician.cert_type != "NONE" -> try {
      CertificateType.valueOf(technician.cert_type)
    } catch (_: Exception) {
      CertificateType.CERTIFICATE_TYPE_NONE
    }

    else -> CertificateType.CERTIFICATE_TYPE_NONE
  }
}
