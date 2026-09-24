package dev.fanfly.wingslog.feature.login.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.rememberBrandHeadlineFamily

/** The icon column, so labels line up whatever mark sits beside them. */
private val RowIconSize = 26.dp

internal val RowPadding = 24.dp

/**
 * One sign-in row: the provider's mark, its name, and a chevron.
 *
 * The accent fill belongs to interaction, not to a provider: a row takes it while it is *pressed*,
 * and keeps it while [inProgress] — where it also swaps the chevron for a spinner and grows a
 * progress line along its bottom edge. At rest every row looks the same, which is the point.
 *
 * A row the card has locked dims as a whole rather than by content colour: each mark sets its own
 * tint (the Google mark keeps its brand colours), so a disabled colour would never reach it and the
 * row would show faded text beside a full-strength icon.
 */
@Composable
internal fun LoginRow(
  label: String,
  enabled: Boolean,
  onClick: () -> Unit,
  icon: @Composable () -> Unit,
  inProgress: Boolean = false,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val accented = pressed || inProgress

  val background = if (accented) accentRowColor() else Color.Transparent
  val content =
    if (accented) Color.White else MaterialTheme.colorScheme.onSurface
  val chevron =
    if (accented) accentRowChevronColor() else MaterialTheme.colorScheme.onSurfaceVariant

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(LoginRowHeight)
      .background(background)
      .then(
        if (enabled) {
          Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
          )
        } else {
          Modifier
        },
      ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = RowPadding)
        .alpha(if (enabled || inProgress) 1f else DisabledAlpha),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Box(
        modifier = Modifier.size(RowIconSize),
        contentAlignment = Alignment.Center,
        content = { icon() },
      )
      Text(
        text = label,
        modifier = Modifier.weight(1f),
        style = TextStyle(
          fontFamily = rememberBrandHeadlineFamily(),
          fontWeight = FontWeight.SemiBold,
          fontSize = 16.sp,
        ),
        color = content,
        maxLines = 1,
      )
      LoginRowTrailing(inProgress = inProgress, chevron = chevron)
    }

    if (inProgress) {
      LinearProgressIndicator(
        modifier = Modifier
          .fillMaxWidth()
          .height(2.dp)
          .align(Alignment.BottomCenter),
        color = Color.White,
        trackColor = Color.White.copy(alpha = 0.22f),
      )
    }
  }
}
