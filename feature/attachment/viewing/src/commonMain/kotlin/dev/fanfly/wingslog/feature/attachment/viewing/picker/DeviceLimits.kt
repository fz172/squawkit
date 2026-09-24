package dev.fanfly.wingslog.feature.attachment.viewing.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.attachment.sharedassets.generated.resources.attachment_file_count
import wingslog.feature.attachment.sharedassets.generated.resources.attachment_size_hint
import wingslog.feature.attachment.sharedassets.generated.resources.Res as AttachRes

/** Size hint plus the used/allowed file badge, which also shows when the cap is reached. */
@Composable
internal fun DeviceLimits(fileCount: Int) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    Text(
      text = stringResource(AttachRes.string.attachment_size_hint),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = stringResource(AttachRes.string.attachment_file_count, fileCount),
      style = WingslogTypography.dataSmall,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier
        .background(
          MaterialTheme.colorScheme.primaryContainer,
          RoundedCornerShape(Spacing.badgeCornerRadius),
        )
        .padding(horizontal = Spacing.small),
    )
  }
}
