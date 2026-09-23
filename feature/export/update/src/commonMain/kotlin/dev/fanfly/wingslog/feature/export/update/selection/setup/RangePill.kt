package dev.fanfly.wingslog.feature.export.update.selection.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing

@Composable
internal fun RangePill(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val accent = MaterialTheme.colorScheme.primary
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(Spacing.chipCornerRadius))
      .background(if (selected) accent else Color.Transparent)
      .border(
        width = 1.5.dp,
        color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
        shape = RoundedCornerShape(Spacing.chipCornerRadius),
      )
      .clickable(onClick = onClick)
      .padding(horizontal = Spacing.medium, vertical = Spacing.small),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    )
  }
}
