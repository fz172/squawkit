package dev.fanfly.wingslog.core.ui.grouped

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing

internal val IconChipSize = 40.dp

internal val IconChipRadius = 10.dp

internal val IconSize = 22.dp

internal const val ChevronAlpha = 0.6f

/**
 * Where a row's text starts when it leads with a [GroupedLeadingIconChip] — the divider between
 * two such rows is inset to this, so it underlines the text rather than cutting under the chip.
 * Rows without a chip inset only by the row padding ([Spacing.xLarge]).
 */
val GroupedDividerInset: Dp = Spacing.xLarge + IconChipSize + Spacing.large

/**
 * Bordered grouped surface for vertically stacked rows.
 */
@Composable
fun GroupedCard(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Card(
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.outlineVariant,
    ),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(content = content)
  }
}
