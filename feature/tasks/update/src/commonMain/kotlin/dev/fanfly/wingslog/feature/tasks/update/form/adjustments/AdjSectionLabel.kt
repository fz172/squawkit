package dev.fanfly.wingslog.feature.tasks.update.form.adjustments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.common.compose.FormSectionLabel
import dev.fanfly.wingslog.core.ui.theme.Spacing

@Composable
internal fun AdjSectionLabel(
  label: String,
  complete: Boolean,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    if (complete) {
      Box(
        modifier = Modifier
          .size(Spacing.medium)
          .clip(RoundedCornerShape(Spacing.extraSmall))
          .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          "✓",
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onPrimary,
        )
      }
    }
    FormSectionLabel(text = label)
  }
}
