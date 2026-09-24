package dev.fanfly.wingslog.feature.attachment.viewing.picker

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.attachment.sharedassets.generated.resources.file_upload_coming_soon
import wingslog.feature.attachment.sharedassets.generated.resources.Res as AttachRes

@Composable
internal fun UpsellHint() {
  Text(
    text = stringResource(AttachRes.string.file_upload_coming_soon),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = Spacing.small),
  )
}
