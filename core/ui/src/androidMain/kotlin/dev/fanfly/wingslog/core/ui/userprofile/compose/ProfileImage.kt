package dev.fanfly.wingslog.core.ui.userprofile.compose

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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res
import wingslog.core.ui.generated.resources.profile_picture

@Composable
fun ProfileImage(photoUri: Uri?) {
  Box(
    modifier = Modifier
      .size(100.dp)
      .clip(CircleShape),
    contentAlignment = Alignment.Center
  ) {
    AsyncImage(
      model = photoUri,
      contentDescription = stringResource(Res.string.profile_picture),
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize()
    )
  }
}