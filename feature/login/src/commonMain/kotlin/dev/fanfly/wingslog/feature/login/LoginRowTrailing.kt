package dev.fanfly.wingslog.feature.login

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * What sits at the end of a sign-in row: a chevron at rest, a spinner while its request runs.
 *
 * Kept beside [LoginRow] rather than passed in as a slot because the two states have to stay the
 * same size — swapping a 20dp chevron for a differently sized spinner shifts the label every time a
 * request starts.
 */
@Composable
internal fun LoginRowTrailing(inProgress: Boolean, chevron: Color) {
  if (inProgress) {
    CircularProgressIndicator(
      modifier = Modifier.size(20.dp),
      strokeWidth = 2.dp,
      color = Color.White,
      trackColor = Color.White.copy(alpha = 0.28f),
    )
  } else {
    Icon(
      imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      modifier = Modifier.size(20.dp),
      tint = chevron,
    )
  }
}
