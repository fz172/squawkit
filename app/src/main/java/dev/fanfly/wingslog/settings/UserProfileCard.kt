package dev.fanfly.wingslog.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dev.fanfly.wingslog.R

@Composable
fun UserProfileCard(credential: GoogleIdTokenCredential?) {
  Card(
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    if (credential == null) {
      return@Card
    }
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // --- Profile Image ---
      // Replace this Box with your Image composable
      Box(
        modifier = Modifier
          .size(100.dp)
          .clip(CircleShape),
        contentAlignment = Alignment.Center
      ) {
        AsyncImage(
          model = credential.profilePictureUri,
          contentDescription = "Profile Picture",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      // --- User Info ---
      Text(
        text = credential.displayName ?: "",
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
        onClick = { },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
      ) {
        Text(
          text = stringResource(R.string.edit_qualification),
          modifier = Modifier.padding(vertical = 8.dp)
        )
      }
    }
  }
}