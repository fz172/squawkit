package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.core.ui.theme.Spacing

/**
 * The list row: leading slot, title, metadata line, trailing slot. One implementation for every
 * list in the app.
 *
 * **A row is not a card.** It draws `surface` — the colour of the list behind it — with no corner
 * radius and no border, so a list reads as a column of records rather than a tray of tiles.
 * `ListRowDivider` separates one from the next. It is filled rather than transparent only because
 * `SwipeActionCard` reveals its controls *underneath* the row, and they must not show through.
 *
 * Both text lines truncate to one at [Spacing.rowHeight]. The row grows for exactly one thing —
 * [supporting], the line a search result needs to say what it matched on. Anything else a record
 * cannot fit on two lines belongs in its detail sheet.
 *
 * [accent] is the exception, and it is rare: a record the list must not let you scroll past — the
 * down-state defect — becomes a contained block, filled a step above the list and bordered in the
 * accent colour. Status that merely needs *noticing* belongs in the leading icon and the
 * [StatusChip], not in a container.
 */
@Composable
fun ListRow(
  title: AnnotatedString,
  modifier: Modifier = Modifier,
  metadata: AnnotatedString? = null,
  onClick: (() -> Unit)? = null,
  accent: Color? = null,
  metadataStyle: TextStyle = MaterialTheme.typography.bodySmall,
  leading: @Composable (() -> Unit)? = null,
  trailing: @Composable (() -> Unit)? = null,
  supporting: @Composable (() -> Unit)? = null,
) {
  val contained = accent != null
  val shape = if (contained) RoundedCornerShape(Spacing.cardCornerRadius) else RectangleShape
  // The row IS the Row. A `Surface` here would wrap every one of them in a second layout node, a
  // clip of a rectangle, a semantics traversal group and two composition-local writes, to reach
  // the one modifier a flat row actually needs. Slots are given explicit colours below rather than
  // inheriting a `LocalContentColor` that a Surface would have set.
  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(
        color = if (contained) MaterialTheme.colorScheme.surfaceContainer
        else MaterialTheme.colorScheme.surface,
        shape = shape,
      )
      .then(if (accent != null) Modifier.border(Spacing.hairline, accent, shape) else Modifier)
      // Clip before clickable, so the ripple stops at the corners of a contained row.
      .then(if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier)
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

/** [ListRow] for a row whose text carries no highlighting. */
@Composable
fun ListRow(
  title: String,
  modifier: Modifier = Modifier,
  metadata: String? = null,
  onClick: (() -> Unit)? = null,
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
  accent = accent,
  metadataStyle = metadataStyle,
  leading = leading,
  trailing = trailing,
  supporting = supporting,
)

/**
 * The hairline between two adjacent rows. Inset past the leading slot, so it reads as a list going
 * on rather than a table drawing a rule across it — and never above the first row or below the
 * last, where it would fence the list off from the screen.
 */
@Composable
fun ListRowDivider(modifier: Modifier = Modifier) {
  HorizontalDivider(
    modifier = modifier.padding(start = Spacing.large),
    color = MaterialTheme.colorScheme.outlineVariant,
  )
}
