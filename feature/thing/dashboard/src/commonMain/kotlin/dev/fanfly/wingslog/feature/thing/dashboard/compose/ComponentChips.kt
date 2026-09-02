package dev.fanfly.wingslog.feature.thing.dashboard.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.template.ComponentChipLines
import dev.fanfly.wingslog.core.template.ComponentNode
import dev.fanfly.wingslog.core.ui.adaptive.compose.LayoutTier
import dev.fanfly.wingslog.core.ui.adaptive.compose.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.logs.viewing.generated.resources.s_n_empty
import wingslog.feature.logs.viewing.generated.resources.s_n_placeholder
import wingslog.feature.logs.viewing.generated.resources.Res as MaintenanceRes

/**
 * Two to a row on a phone: wide enough for a make and model, narrow enough that a set reads as a
 * set rather than as a list. A wide card fits more, and holding it at two there would waste most
 * of the row on whitespace.
 */
private val LayoutTier.chipsPerRow: Int
  get() = when (this) {
    LayoutTier.COMPACT, LayoutTier.MEDIUM -> 2
    LayoutTier.EXPANDED, LayoutTier.LARGE -> 4
  }

/**
 * One slot's components as chips — a matched set drawn as a set (#729).
 *
 * Was blades-only, then any repeating slot *nested* inside another, and both readings were too
 * narrow. Whether parts are a set has nothing to do with what they are bolted to: a car's four
 * tyres and a boat's two engines are top-level and every bit as much a set as an aeroplane's
 * blades, and a full-width card each is what made a car look like it tracked more than an
 * aeroplane. The template declares it now, as `compact_instances`.
 *
 * A chip carries the same three lines a card does, so compacting costs no information — only the
 * border and the full width. The serial-only case still reads as it always did: a blade names no
 * make or model, so its serial takes the headline rather than sitting under a blank one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComponentChips(nodes: List<ComponentNode>, modifier: Modifier = Modifier) {
  val chips = nodes.mapNotNull { it.row.chipLines }
  if (chips.isEmpty()) return
  val perRow = LocalLayoutTier.current.chipsPerRow
  FlowRow(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    verticalArrangement = Arrangement.spacedBy(Spacing.small),
    maxItemsInEachRow = perRow,
  ) {
    // weight, not wrap-content: chips sized to their own text leave a ragged right edge that
    // reads as a rendering fault, and a set is meant to be scanned down a column.
    chips.forEach { chip -> ComponentChip(chip, Modifier.weight(1f)) }
    // Three tyres would otherwise leave the third stretched across the whole card while the two
    // above it are half of it. The blanks hold the last row to the same grid.
    repeat((perRow - chips.size % perRow) % perRow) {
      Spacer(Modifier.weight(1f))
    }
  }
}

@Composable
private fun ComponentChip(chip: ComponentChipLines, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(Spacing.badgeCornerRadius),
    color = Color.Transparent,
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.outlineVariant
    ),
  ) {
    Column(
      modifier = Modifier.padding(
        horizontal = Spacing.medium,
        vertical = Spacing.small
      ),
    ) {
      Text(
        text = chip.label.uppercase(),
        style = TextStyle(
          fontFamily = FontFamily.SansSerif,
          fontWeight = FontWeight.Bold,
          fontSize = 10.sp,
          letterSpacing = 0.1.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
      )
      Text(
        text = chip.headline.ifBlank { stringResource(MaintenanceRes.string.s_n_empty) },
        modifier = Modifier.padding(top = Spacing.extraSmall),
        style = TextStyle(
          fontFamily = FontFamily.SansSerif,
          fontWeight = FontWeight.SemiBold,
          fontSize = 13.sp
        ),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      // Mono, per DESIGN.md: a serial is an identifier, and character alignment is what makes one
      // comparable to the one beside it — which is the whole point of drawing a set side by side.
      if (chip.serial.isNotBlank()) {
        Text(
          text = stringResource(MaintenanceRes.string.s_n_placeholder, chip.serial),
          modifier = Modifier.padding(top = Spacing.extraSmall),
          style = WingslogTypography.dataSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
