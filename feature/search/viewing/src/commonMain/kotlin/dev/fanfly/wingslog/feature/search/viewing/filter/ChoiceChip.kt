package dev.fanfly.wingslog.feature.search.viewing.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography

/**
 * A chip, with what it would leave behind. [count] is the number of records carrying this option,
 * so a chip that would empty the list says so before it is tapped; null hides the number for a
 * caller that cannot compute one.
 */
@Composable
fun ChoiceChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  count: Int? = null,
) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = {
      Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(label)
        if (count != null) {
          Text(
            count.toString(),
            style = WingslogTypography.dataSmall,
            color = if (selected) {
              MaterialTheme.colorScheme.onSecondaryContainer
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
          )
        }
      }
    },
    leadingIcon = if (selected) {
      { Icon(Icons.Default.Check, contentDescription = null) }
    } else null,
    shape = RoundedCornerShape(Spacing.smallCornerRadius),
  )
}
