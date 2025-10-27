package dev.fanfly.wingslog.settings

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
import com.google.firebase.auth.FirebaseUser
import dev.fanfly.wingslog.R
import dev.fanfly.wingslog.userprofile.ProfileImage

@Composable
fun UserProfileCard(currentUser: FirebaseUser?, onOpenEditProfile: () -> Unit) {
  Card(
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    if (currentUser == null) {
      return@Card
    }
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // --- Profile Image ---
      ProfileImage(currentUser.photoUrl)

      Spacer(modifier = Modifier.height(16.dp))

      // --- User Info ---
      Text(
        text = currentUser.displayName ?: "",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "qualifications",
        fontSize = 16.sp,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "licenseInfo",
        fontSize = 14.sp,
      )

      Spacer(modifier = Modifier.height(20.dp))

      // --- Edit Profile Button ---
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