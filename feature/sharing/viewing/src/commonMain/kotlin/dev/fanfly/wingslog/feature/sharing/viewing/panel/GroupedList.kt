package dev.fanfly.wingslog.feature.sharing.viewing.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.theme.Spacing

@Composable
internal fun GroupedList(content: @Composable ColumnScope.() -> Unit) {
  Surface(
    shape = RoundedCornerShape(Spacing.cardCornerRadius),
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.outlineVariant
    ),
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column(content = content)
  }
}
