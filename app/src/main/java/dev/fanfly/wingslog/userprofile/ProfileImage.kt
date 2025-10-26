package dev.fanfly.wingslog.dev.fanfly.wingslog.userprofile

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.fanfly.wingslog.R

@Composable
fun ProfileImage(photoUrl: Uri?) {
  Box(
    modifier = Modifier
      .size(100.dp)
      .clip(CircleShape),
    contentAlignment = Alignment.Center
  ) {
    AsyncImage(
      model = photoUrl,
      contentDescription = stringResource(R.string.profile_picture),
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize()
    )
  }
}