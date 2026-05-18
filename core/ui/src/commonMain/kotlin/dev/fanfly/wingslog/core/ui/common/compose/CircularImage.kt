package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun CircularImage(
  photoUri: String?,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  size: androidx.compose.ui.unit.Dp = 100.dp,
  fallbackRes: DrawableResource? = null
) {
  Box(
    modifier = modifier
      .size(size)
      .clip(CircleShape),
    contentAlignment = Alignment.Center
  ) {
    val fallbackPainter = fallbackRes?.let { painterResource(it) }
    val painter = rememberAsyncImagePainter(
      model = photoUri?.takeIf { it.isNotBlank() },
      placeholder = fallbackPainter,
      error = fallbackPainter,
      fallback = fallbackPainter,
    )
    val colorFilter = if (photoUri.isNullOrBlank()) {
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
