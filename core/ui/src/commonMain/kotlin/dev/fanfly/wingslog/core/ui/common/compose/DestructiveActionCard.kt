package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors

/**
 * A critical-tinted row card that opens a destructive flow (delete a task, delete a squawk).
 * The caller owns the confirmation; this is only the entry point.
 */
@Composable
fun DestructiveActionCard(
  icon: ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val critical = MaterialTheme.statusColors.critical.accent

  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .border(
        Spacing.hairline,
        critical.copy(alpha = 0.4f),
        RoundedCornerShape(Spacing.cardCornerRadius)
      )
      .clickable(role = Role.Button, onClick = onClick)
      .padding(
        horizontal = Spacing.large,
        vertical = Spacing.medium
      ),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Box(
      modifier = Modifier
        .size(Spacing.huge)
        .clip(RoundedCornerShape(percent = 50))
        .background(critical.copy(alpha = 0.12f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.size(Spacing.large),
        tint = critical,
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = critical,
      )
      Text(
        subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Icon(
      Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
