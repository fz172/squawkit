package dev.fanfly.wingslog.core.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import dev.fanfly.wingslog.core.ui.theme.Spacing

// A breath, not a transition: slow enough to read as "waiting", never as something arriving.
internal const val PulseMillis = 900

internal const val PulseFloor = 0.45f

// Title and metadata widths, varied so the placeholder reads as a list and not as a grid.
private val RowWidths =
  listOf(0.62f to 0.34f, 0.48f to 0.26f, 0.7f to 0.4f, 0.55f to 0.3f)

/**
 * A list that has not arrived yet, in the shape it will arrive in: the filter bar, then flat rows at
 * [Spacing.rowHeight]. A centred spinner replaced the whole screen and the layout jumped twice per
 * load — once to the spinner, once back. One node for a screen reader, which hears "Loading".
 */
@Composable
fun SkeletonList(
  modifier: Modifier = Modifier,
  rows: Int = 8,
  showFilterBar: Boolean = true,
  horizontalPadding: Dp = Spacing.screenPadding,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .skeletonPulse()
      .padding(horizontal = horizontalPadding),
  ) {
    if (showFilterBar) {
      Row(
        modifier = Modifier.fillMaxWidth()
          .padding(vertical = Spacing.small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        SkeletonBlock(
          Modifier.weight(1f)
            .height(Spacing.buttonHeight)
        )
        SkeletonBlock(Modifier.size(Spacing.buttonHeight))
      }
    }
    repeat(rows) { index ->
      if (index > 0) ListRowDivider()
      val (title, metadata) = RowWidths[index % RowWidths.size]
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .height(Spacing.rowHeight)
          .padding(horizontal = Spacing.large),
        verticalArrangement = Arrangement.spacedBy(
          Spacing.small,
          Alignment.CenterVertically
        ),
      ) {
        SkeletonBlock(
          Modifier.fillMaxWidth(title)
            .height(Spacing.large)
        )
        SkeletonBlock(
          Modifier.fillMaxWidth(metadata)
            .height(Spacing.medium)
        )
      }
    }
  }
}
