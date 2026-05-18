package dev.fanfly.wingslog.feature.userprofile.userprofilecard.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.aircraft.CertExpireLimit
import dev.fanfly.wingslog.aircraft.CertificateType
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.ui.common.compose.CircularImage
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusWarning
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
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

private val AvatarSize = Spacing.massive * 2
private val AvatarRingWidth = Spacing.extraSmall

// Spacer height = half avatar circle + ring, so card top edge cuts avatar at its center
private val CardTopOffset = AvatarSize / 2 + AvatarRingWidth

// Avatar left edge aligns with card's inner horizontal padding; ring extends 4dp to the left
private val AvatarOffsetX = Spacing.large - AvatarRingWidth

@Composable
fun UserProfileCard(
  self: Technician?,
  photoUri: String?,
) {
  Box(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Spacer(modifier = Modifier.height(CardTopOffset))
      val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
      val background = MaterialTheme.colorScheme.background
      Card(
        shape = RoundedCornerShape(Spacing.large),
        modifier = Modifier
            .fillMaxWidth()
            .background(
              brush = Brush.verticalGradient(
                0f to background,
                0.7f to surfaceContainer,
                1f to surfaceContainer,
              ),
              shape = RoundedCornerShape(Spacing.large),
            ),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
      ) {
        Row(
          modifier = Modifier
              .fillMaxWidth()
              .padding(
                start = Spacing.large,
                end = Spacing.large,
                top = Spacing.large,
                bottom = Spacing.xLarge,
              ),
          verticalAlignment = Alignment.Top,
        ) {
          // Reserve horizontal space for the overlapping avatar + gap to text
          Spacer(modifier = Modifier.width(AvatarSize + Spacing.large))
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
          ) {
            ProfileNameAndCreds(self)
          }
        }
      }
    }

    // Avatar overflows the card's top edge by half its height
    ProfileAvatar(
      self = self,
      photoUri = photoUri,
      modifier = Modifier
          .align(Alignment.TopStart)
          .offset(x = AvatarOffsetX),
    )
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

  val showExpiration =
    self.cert_expire_limit != CertExpireLimit.CERT_EXPIRE_LIMIT_NEVER_EXPIRES
        && self.cert_expiration != null

  CredRow(
    label = stringResource(certType.displayResId()),
    value = if (showExpiration) {
      "exp ${
        self.cert_expiration!!.toLocalDate()
            .toDisplayFormat()
      }"
    } else null,
    valueColor = if (showExpiration) certExpiryColor(self.cert_expiration!!) else null,
  )

  if (self.cert_number.isNotBlank()) {
    CredRow(label = "Cert #", value = self.cert_number)
  }
}

@Composable
private fun ProfileAvatar(
  self: Technician?,
  photoUri: String?,
  modifier: Modifier = Modifier,
) {
  val hasPhoto = !photoUri.isNullOrBlank()
  val initials = self?.name
      ?.split(" ")
      ?.filter { it.isNotBlank() }
      ?.take(2)
      ?.map {
        it.first()
            .uppercaseChar()
      }
      ?.joinToString("")
      ?.takeIf { it.isNotBlank() }

  Box(
    modifier = modifier
        .size(AvatarSize + AvatarRingWidth * 2)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.background)
        .padding(AvatarRingWidth)
        .clip(CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    if (hasPhoto || initials == null) {
      // Photo or anonymous icon fallback via CircularImage
      CircularImage(
        photoUri = photoUri,
        contentDescription = stringResource(CardRes.string.profile_picture),
        size = AvatarSize,
        fallbackRes = SharedAssetsRes.drawable.ic_anonymous_user,
      )
    } else {
      // Initials avatar
      Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = initials,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
    }
  }
}

@Composable
private fun CredRow(
  label: String,
  value: String?,
  valueColor: Color? = null,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontWeight = FontWeight.SemiBold,
    )
    if (value != null) {
      Text(
        text = value,
        style = WingslogTypography.dataSmall,
        color = valueColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun certExpiryColor(expiration: WireInstant): Color {
  val expiryDate = expiration.toLocalDate()
  val today = Clock.System.now()
      .toLocalDateTime(TimeZone.currentSystemDefault())
      .date
  val daysUntil = expiryDate.toEpochDays() - today.toEpochDays()
  return when {
    daysUntil < 0 -> MaterialTheme.colorScheme.error
    daysUntil <= 90 -> StatusWarning
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
