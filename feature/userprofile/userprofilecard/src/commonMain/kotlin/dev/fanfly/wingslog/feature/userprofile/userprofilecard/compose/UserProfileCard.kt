package dev.fanfly.wingslog.feature.userprofile.userprofilecard.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.model.userprofile.LicenseExpireLimit
import dev.fanfly.wingslog.core.model.userprofile.LicenseInfo
import dev.fanfly.wingslog.core.model.userprofile.LicenseType
import dev.fanfly.wingslog.core.ui.common.compose.CircularImage
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.userprofile.userprofilecard.utils.displayResId
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res as CoreRes
import wingslog.core.ui.generated.resources.edit_profile
import wingslog.feature.userprofile.sharedassets.generated.resources.Res as SharedAssetsRes
import wingslog.feature.userprofile.sharedassets.generated.resources.anonymous_user
import wingslog.feature.userprofile.sharedassets.generated.resources.ic_anonymous_user
import wingslog.feature.userprofile.userprofilecard.generated.resources.Res as CardRes
import wingslog.feature.userprofile.userprofilecard.generated.resources.profile_picture

data class UserProfileCardData(
  val photoUri: String? = null,
  val displayName: String? = null,
  val licenceInfo: LicenseInfo? = null,
)

@Composable
fun UserProfileCard(
  data: UserProfileCardData,
  onOpenEditProfile: (() -> Unit)? = null,
) {
  Card(
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    modifier = Modifier.fillMaxWidth(),
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
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // --- Profile Image ---
      CircularImage(
        photoUri = data.photoUri,
        contentDescription = stringResource(CardRes.string.profile_picture),
        fallbackRes = SharedAssetsRes.drawable.ic_anonymous_user
      )

      Spacer(modifier = Modifier.height(Spacing.large))

      // --- User Info ---
      Text(
        text = if (data.displayName?.isEmpty()
            ?: true
        ) stringResource(SharedAssetsRes.string.anonymous_user) else data.displayName,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      if (data.licenceInfo != null) {
        if (data.licenceInfo.license_type != LicenseType.NONE) {
          Spacer(modifier = Modifier.height(Spacing.extraSmall))
          Text(
            text = stringResource(data.licenceInfo.license_type.displayResId()),
            style = MaterialTheme.typography.bodyLarge,
          )
          if (data.licenceInfo.license_number.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.extraSmall))
            Text(
              text = data.licenceInfo.license_number,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
          if (data.licenceInfo.expireLimit != LicenseExpireLimit.NEVER_EXPIRES) {
            Spacer(modifier = Modifier.height(Spacing.extraSmall))
            Text(
              text = data.licenceInfo.expiration_date?.toLocalDate()?.toDisplayFormat() ?: "",
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
        Spacer(modifier = Modifier.height(Spacing.rowGap))
      }
      // --- Edit Profile Button ---
      if (onOpenEditProfile != null) {
        Button(
          onClick = onOpenEditProfile,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(Spacing.buttonCornerRadius),
        ) {
          Text(
            text = stringResource(CoreRes.string.edit_profile),
            modifier = Modifier.padding(vertical = Spacing.small)
          )
        }
      }
    }
  }
}