package dev.fanfly.wingslog.core.ui.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.Res
import wingslog.core.sharedassets.generated.resources.danger_zone

/**
 * Delete's one home: the last thing on an edit form, under a red heading so it cannot be read as
 * acting on whatever sits above it. The caller owns the confirmation.
 */
@Composable
fun DangerZone(
  title: String,
  subtitle: String,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(top = Spacing.small),
    verticalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    FormSectionLabel(
      text = stringResource(Res.string.danger_zone),
      color = MaterialTheme.statusColors.critical.accent,
    )
    DestructiveActionCard(
      icon = Icons.Default.Delete,
      title = title,
      subtitle = subtitle,
      onClick = onDelete,
    )
  }
}
