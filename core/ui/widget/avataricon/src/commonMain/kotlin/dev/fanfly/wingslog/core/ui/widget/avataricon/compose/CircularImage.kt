package dev.fanfly.wingslog.core.ui.widget.avataricon.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private const val MAX_REMOTE_IMAGE_RETRIES = 1
private const val REMOTE_IMAGE_RETRY_DELAY_MS = 1_000L

@Composable
fun CircularImage(
  photoUri: String?,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  size: Dp = 100.dp,
  fallbackRes: DrawableResource? = null
) {
  Box(
    modifier = modifier
      .size(size)
      .clip(CircleShape),
    contentAlignment = Alignment.Center
  ) {
    val fallbackPainter = fallbackRes?.let { painterResource(it) }
    val remoteUri = photoUri?.takeIf { it.isNotBlank() }
    var retryCount by remember(remoteUri) { mutableIntStateOf(0) }
    var retryPending by remember(remoteUri) { mutableStateOf(false) }

    LaunchedEffect(retryPending) {
      if (retryPending && retryCount < MAX_REMOTE_IMAGE_RETRIES) {
        delay(REMOTE_IMAGE_RETRY_DELAY_MS)
        retryCount++
        retryPending = false
      }
    }

    // A fragment changes Coil's request identity without changing the remote HTTP URL.
    val imageModel = remoteUri?.let { uri ->
      if (retryCount == 0) uri else "$uri#retry-$retryCount"
    }
    val painter = rememberAsyncImagePainter(
      model = imageModel,
      placeholder = fallbackPainter,
      error = fallbackPainter,
      fallback = fallbackPainter,
      onError = {
        if (retryCount < MAX_REMOTE_IMAGE_RETRIES) {
          retryPending = true
        }
      },
    )
    val colorFilter = if (remoteUri == null) {
      ColorFilter.tint(LocalContentColor.current)
    } else {
      null
    }

    Image(
      painter = painter,
      contentDescription = contentDescription,
      contentScale = ContentScale.Crop,
      colorFilter = colorFilter,
      modifier = Modifier.fillMaxSize()
    )
  }
}
