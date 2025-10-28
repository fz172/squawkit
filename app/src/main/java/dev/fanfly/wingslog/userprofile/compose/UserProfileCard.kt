package dev.fanfly.wingslog.userprofile.compose

import android.net.Uri
import android.text.TextUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.R
import dev.fanfly.wingslog.common.datetime.toDisplayFormat
import dev.fanfly.wingslog.common.datetime.toLocalDate
import dev.fanfly.wingslog.userprofile.data.LicenseExpireLimit
import dev.fanfly.wingslog.userprofile.data.LicenseInfo
import dev.fanfly.wingslog.userprofile.data.LicenseType
import dev.fanfly.wingslog.userprofile.data.displayResId

data class UserProfileCardData(
  val photoUri: Uri? = null,
  val displayName: String? = null,
  val licenceInfo: LicenseInfo? = null
)

@Composable
fun UserProfileCard(
  data: UserProfileCardData,
  onOpenEditProfile: (() -> Unit)? = null,
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // --- Profile Image ---
      ProfileImage(data.photoUri)

      Spacer(modifier = Modifier.height(16.dp))

      // --- User Info ---
      Text(
        text = data.displayName ?: "",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
      )
      if (data.licenceInfo != null) {
        if (data.licenceInfo.licenseType != LicenseType.NONE) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = stringResource(data.licenceInfo.licenseType.displayResId()),
            fontSize = 16.sp,
          )
          if (!TextUtils.isEmpty(data.licenceInfo.licenseNumber)) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = data.licenceInfo.licenseNumber,
              fontSize = 14.sp,
            )
          }
          if (data.licenceInfo.expireLimit != LicenseExpireLimit.NEVER_EXPIRES) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = data.licenceInfo.expirationDate.toLocalDate().toDisplayFormat(),
              fontSize = 14.sp,
            )
          }
        }
        Spacer(modifier = Modifier.height(20.dp))
      }
      // --- Edit Profile Button ---
      if (onOpenEditProfile != null) {
        Button(
          onClick = onOpenEditProfile,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text(
            text = stringResource(R.string.edit_profile),
            modifier = Modifier.padding(vertical = 8.dp)
          )
        }
      }
    }
  }
}