package dev.fanfly.wingslog.feature.subscription.viewing.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing

private val ProLabelIconSize = 14.dp

/** The "Unlocked with Pro" marker: the section label in advisory amber with the Pro mark beside it. */
@Composable
internal fun ProSectionLabel(text: String) {
  Row(
    modifier = Modifier.padding(horizontal = Spacing.extraSmall),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall + Spacing.extraSmall / 2),
  ) {
    Icon(
      imageVector = Icons.Default.WorkspacePremium,
      contentDescription = null,
      modifier = Modifier.size(ProLabelIconSize),
      tint = MaterialTheme.colorScheme.tertiary,
    )
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.tertiary,
    )
  }
}
