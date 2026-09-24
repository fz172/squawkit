package dev.fanfly.wingslog.feature.comments.viewing

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.comments.sharedassets.generated.resources.Res
import wingslog.feature.comments.sharedassets.generated.resources.comment_you

@Composable
internal fun MineBadge() {
  Surface(
    shape = RoundedCornerShape(Spacing.badgeCornerRadius),
    color = MaterialTheme.colorScheme.primaryContainer,
  ) {
    Text(
      text = stringResource(Res.string.comment_you).uppercase(),
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onPrimaryContainer,
      modifier = Modifier.padding(
        horizontal = Spacing.small,
        vertical = 1.dp,
      ),
    )
  }
}
