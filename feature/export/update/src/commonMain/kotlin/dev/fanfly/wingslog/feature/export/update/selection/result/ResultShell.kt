package dev.fanfly.wingslog.feature.export.update.selection.result

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.layout.ContentWidth
import dev.fanfly.wingslog.core.ui.layout.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.theme.Spacing

@Composable
internal fun ResultShell(
  modifier: Modifier,
  heroIcon: ImageVector,
  heroColor: Color,
  heroContainer: Color,
  title: String,
  subtitle: String,
  body: @Composable () -> Unit,
  actions: @Composable ColumnScope.() -> Unit,
  subtitleContent: (@Composable () -> Unit)? = null,
) {
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier
        .fillMaxHeight()
        .constrainedContentWidth(ContentWidth.Form)
        .padding(horizontal = Spacing.screenPadding)
        .padding(top = Spacing.large, bottom = Spacing.extraLarge),
    ) {
      Column(
        // Tight icon/title/subtitle cluster, then a generous gap before the content below.
        modifier = Modifier.fillMaxWidth()
          .padding(top = Spacing.large, bottom = Spacing.huge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        Box(
          modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(heroContainer)
            .border(
              width = 1.5.dp,
              color = heroColor.copy(alpha = 0.35f),
              shape = RoundedCornerShape(percent = 50),
            ),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = heroIcon,
            contentDescription = null,
            tint = heroColor,
            modifier = Modifier.size(36.dp),
          )
        }
        Text(
          text = title,
          style = MaterialTheme.typography.headlineSmall,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitleContent != null) {
          subtitleContent()
        } else if (subtitle.isNotBlank()) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
      }
      body()
      Spacer(Modifier.weight(1f))
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        actions()
      }
    }
  }
}
