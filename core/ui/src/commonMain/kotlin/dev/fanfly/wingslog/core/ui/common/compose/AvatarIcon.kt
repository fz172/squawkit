package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.DrawableResource

/**
 * Circular avatar that resolves what to show from a display name and optional photo:
 * a photo when present, otherwise the name's initials, otherwise a fallback drawable
 * (or "?" when none is supplied).
 */
@Composable
fun AvatarIcon(
  displayName: String?,
  photoUri: String?,
  modifier: Modifier = Modifier,
  size: Dp = Spacing.huge,
  contentDescription: String? = null,
  textStyle: TextStyle = MaterialTheme.typography.labelLarge,
  fallbackRes: DrawableResource? = null,
) {
  val initials = displayName.toInitials()
  val hasPhoto = !photoUri.isNullOrBlank()

  when {
    hasPhoto -> CircularImage(
      photoUri = photoUri,
      contentDescription = contentDescription,
      modifier = modifier,
      size = size,
      fallbackRes = fallbackRes,
    )

    initials != null -> InitialsCircle(initials, modifier, size, textStyle)

    fallbackRes != null -> CircularImage(
      photoUri = photoUri,
      contentDescription = contentDescription,
      modifier = modifier,
      size = size,
      fallbackRes = fallbackRes,
    )

    else -> InitialsCircle("?", modifier, size, textStyle)
  }
}

@Composable
private fun InitialsCircle(
  text: String,
  modifier: Modifier,
  size: Dp,
  textStyle: TextStyle,
) {
  Box(
    modifier = modifier
      .size(size)
      .clip(CircleShape)
      .background(MaterialTheme.colorScheme.primaryContainer),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      style = textStyle,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onPrimaryContainer,
    )
  }
}

private fun String?.toInitials(): String? =
  this
    ?.split(" ")
    ?.filter { it.isNotBlank() }
    ?.take(2)
    ?.map {
      it.first()
        .uppercaseChar()
    }
    ?.joinToString("")
    ?.takeIf { it.isNotBlank() }
