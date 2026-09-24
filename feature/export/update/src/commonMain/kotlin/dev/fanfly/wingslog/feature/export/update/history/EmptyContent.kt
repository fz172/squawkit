package dev.fanfly.wingslog.feature.export.update.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.layout.ContentWidth
import dev.fanfly.wingslog.core.ui.layout.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_history_empty_body
import wingslog.feature.export.sharedassets.generated.resources.export_history_empty_title
import wingslog.feature.export.sharedassets.generated.resources.export_history_new

@Composable
internal fun EmptyContent(modifier: Modifier, onNew: () -> Unit) {
  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier
        .constrainedContentWidth(ContentWidth.Form)
        .padding(horizontal = Spacing.huge),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
      Icon(
        imageVector = Icons.Default.Inbox,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(56.dp),
      )
      Text(
        text = stringResource(Res.string.export_history_empty_title),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
      )
      Text(
        text = stringResource(Res.string.export_history_empty_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Button(
        onClick = onNew,
        shape = RoundedCornerShape(Spacing.chipCornerRadius),
      ) {
        Text(stringResource(Res.string.export_history_new))
      }
    }
  }
}
