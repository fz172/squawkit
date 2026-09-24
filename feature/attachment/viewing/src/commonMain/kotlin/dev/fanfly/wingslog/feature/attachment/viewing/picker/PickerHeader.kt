package dev.fanfly.wingslog.feature.attachment.viewing.picker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.dismiss
import wingslog.feature.attachment.sharedassets.generated.resources.attachment_picker_title
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.attachment.sharedassets.generated.resources.Res as AttachRes

@Composable
internal fun PickerHeader(showClose: Boolean, onDismiss: () -> Unit) {
  Column {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = if (showClose) Spacing.none else Spacing.small),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(AttachRes.string.attachment_picker_title),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f),
      )
      if (showClose) {
        IconButton(onClick = onDismiss) {
          Icon(
            Icons.Default.Close,
            contentDescription = stringResource(CoreRes.string.dismiss),
            tint = MaterialTheme.colorScheme.secondary,
          )
        }
      }
    }
    if (showClose) {
      HorizontalDivider(modifier = Modifier.padding(top = Spacing.medium))
    }
  }
}
