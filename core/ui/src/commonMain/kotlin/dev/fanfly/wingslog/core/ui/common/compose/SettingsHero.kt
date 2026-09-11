package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing

private val HaloSize = 120.dp
private val DiscSize = 88.dp
private val HeroIconSize = 44.dp
private const val InactiveAlpha = 0.55f

/**
 * The head of a settings detail page: one subject in a ringed disc, a title and a sentence. Static
 * — motion here would be decoration, which the design system rules out.
 *
 * @param active false when the setting the page governs is off; the subject dims to say so.
 */
@Composable
fun SettingsHero(
  icon: ImageVector,
  title: String,
  body: String,
  modifier: Modifier = Modifier,
  active: Boolean = true,
) {
  val cs = MaterialTheme.colorScheme
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(top = Spacing.large),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Spacing.xLarge),
  ) {
    Box(
      modifier = Modifier
        .size(HaloSize)
        .clip(CircleShape)
        .background(cs.surfaceVariant),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier = Modifier
          .size(DiscSize)
          .clip(CircleShape)
          .background(if (active) cs.primaryContainer else cs.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier.size(HeroIconSize),
          tint = if (active) cs.primary else cs.onSurfaceVariant.copy(alpha = InactiveAlpha),
        )
      }
    }
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        color = cs.onSurface,
      )
      Text(
        text = body,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = cs.onSurfaceVariant,
      )
    }
  }
}
