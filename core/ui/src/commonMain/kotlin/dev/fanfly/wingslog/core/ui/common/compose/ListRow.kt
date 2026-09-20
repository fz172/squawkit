package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.core.ui.theme.Spacing

/**
 * The list row: leading slot, title, metadata line, trailing slot. One implementation for every
 * list in the app.
 *
 * Both lines truncate to one at [Spacing.rowHeight], so a list of rows scans as a column rather
 * than as a stack of paragraphs. The row grows only for [supporting] — the extra line a search
 * result needs to say what it matched on, which the two lines above it cannot show.
 *
 * It draws [containerColor] and nothing else. The tonal ramp is what separates a row from the list
 * behind it (`DESIGN.md` §4), so no hairline is needed to make one exist. [accent] is the only
 * border a row ever gets, and it means emphasis: a down-state defect, an overdue task.
 */
@Composable
fun ListRow(
  title: AnnotatedString,
  modifier: Modifier = Modifier,
  metadata: AnnotatedString? = null,
  onClick: (() -> Unit)? = null,
  containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
  accent: Color? = null,
  metadataStyle: TextStyle = MaterialTheme.typography.bodySmall,
  leading: @Composable (() -> Unit)? = null,
  trailing: @Composable (() -> Unit)? = null,
  supporting: @Composable (() -> Unit)? = null,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    color = containerColor,
    border = accent?.let { BorderStroke(Spacing.hairline, it) },
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .heightIn(min = Spacing.rowHeight)
        .padding(horizontal = Spacing.large, vertical = Spacing.medium),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      leading?.invoke()
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (metadata != null) {
          Text(
            text = metadata,
            style = metadataStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        supporting?.invoke()
      }
      trailing?.invoke()
    }
  }
}

/** [ListRow] for a row whose text carries no highlighting. */
@Composable
fun ListRow(
  title: String,
  modifier: Modifier = Modifier,
  metadata: String? = null,
  onClick: (() -> Unit)? = null,
  containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
  accent: Color? = null,
  metadataStyle: TextStyle = MaterialTheme.typography.bodySmall,
  leading: @Composable (() -> Unit)? = null,
  trailing: @Composable (() -> Unit)? = null,
  supporting: @Composable (() -> Unit)? = null,
) = ListRow(
  title = AnnotatedString(title),
  modifier = modifier,
  metadata = metadata?.let { AnnotatedString(it) },
  onClick = onClick,
  containerColor = containerColor,
  accent = accent,
  metadataStyle = metadataStyle,
  leading = leading,
  trailing = trailing,
  supporting = supporting,
)
