package dev.fanfly.wingslog.core.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.fanfly.wingslog.core.ui.badge.StatusChip
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusTier
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography

/**
 * The name of a group inside a list — a month, a priority tier — with how many rows it holds.
 *
 * Opaque, on the same ground as the rows ([LocalListRowGround]), because it is built to be pinned:
 * rows scroll underneath it, and a transparent header would let them show through its text.
 * [color] is for a header that carries meaning of its own, such as a priority tier's tone.
 * [tier] draws the title as that tier's [StatusChip] — the badge a row of the group would wear, so
 * a header and its rows can never disagree about what a colour means.
 * [trailing] sits at the far edge — the link to the full list this group previews.
 */
@Composable
fun SectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  count: Int? = null,
  color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  tier: StatusTier? = null,
  trailing: @Composable (() -> Unit)? = null,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(LocalListRowGround.current.takeOrElse { MaterialTheme.colorScheme.surface })
      .padding(top = Spacing.medium, bottom = Spacing.small)
      .semantics { heading() },
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (tier != null) {
      StatusChip(label = title, tier = tier)
    } else {
      Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = color,
      )
    }
    if (count != null) {
      Text(
        text = count.toString(),
        style = WingslogTypography.dataSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (trailing != null) {
      Spacer(Modifier.weight(1f))
      trailing()
    }
  }
}

/**
 * A [SectionHeader] that stays pinned while its group scrolls under it, so the rows on screen never
 * lose the name of the group they belong to. [key] must be unique in the list and stable.
 */
fun LazyListScope.stickySectionHeader(
  key: Any,
  title: String,
  count: Int? = null,
  color: Color = Color.Unspecified,
) {
  stickyHeader(key = key, contentType = "section-header") {
    SectionHeader(
      title = title,
      count = count,
      color = color.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant },
    )
  }
}
