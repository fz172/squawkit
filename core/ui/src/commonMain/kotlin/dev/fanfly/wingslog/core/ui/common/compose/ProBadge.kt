package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.Res
import wingslog.core.sharedassets.generated.resources.pro_badge

private val BadgeVerticalPadding = 3.dp
private val BadgeHorizontalPadding = 6.dp

/**
 * The "PRO" stamp beside anything the subscription unlocks. Advisory amber — the one brand accent,
 * spent here because a paid tier is the one thing worth pointing at — on a mono face so it reads
 * as a mark, not a word.
 */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
  Text(
    text = stringResource(Res.string.pro_badge),
    style = WingslogTypography.dataSmall,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.tertiary,
    modifier = modifier
      .background(
        MaterialTheme.colorScheme.tertiaryContainer,
        RoundedCornerShape(Spacing.badgeCornerRadius),
      )
      .padding(horizontal = BadgeHorizontalPadding, vertical = BadgeVerticalPadding),
  )
}
