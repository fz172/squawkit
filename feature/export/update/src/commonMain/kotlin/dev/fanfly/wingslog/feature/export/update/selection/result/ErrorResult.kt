package dev.fanfly.wingslog.feature.export.update.selection.result

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.retry
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_back_to_setup
import wingslog.feature.export.sharedassets.generated.resources.export_error_details
import wingslog.feature.export.sharedassets.generated.resources.export_error_subtitle
import wingslog.feature.export.sharedassets.generated.resources.export_error_title
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

@Composable
internal fun ErrorResult(
  modifier: Modifier,
  onRetry: () -> Unit,
  onBack: () -> Unit,
) {
  ResultShell(
    modifier = modifier,
    heroIcon = Icons.Default.ErrorOutline,
    heroColor = MaterialTheme.statusColors.critical.accent,
    heroContainer = MaterialTheme.statusColors.critical.container,
    title = stringResource(Res.string.export_error_title),
    subtitle = stringResource(Res.string.export_error_subtitle),
    body = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(Spacing.cardCornerRadius))
          .background(MaterialTheme.colorScheme.surfaceContainer)
          .border(
            width = Spacing.hairline,
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = RoundedCornerShape(Spacing.cardCornerRadius),
          )
          .padding(Spacing.large),
      ) {
        Text(
          text = stringResource(Res.string.export_error_details),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    actions = {
      ResultPrimaryButton(
        label = stringResource(CoreRes.string.retry),
        icon = null,
        onClick = onRetry,
      )
      ResultSecondaryButton(
        label = stringResource(Res.string.export_back_to_setup),
        icon = Icons.Default.Tune,
        onClick = onBack,
      )
    },
  )
}
