package dev.fanfly.wingslog.feature.search.viewing.filter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import dev.fanfly.wingslog.core.ui.theme.Spacing

@Composable
internal fun RecordFilterPanel(
  horizontalPadding: Dp,
  content: @Composable ColumnScope.() -> Unit
) {
  Surface(
    shape = RoundedCornerShape(Spacing.smallCornerRadius),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.outlineVariant
    ),
    modifier = Modifier
      .fillMaxWidth()
      .padding(
        start = horizontalPadding,
        end = horizontalPadding,
        bottom = Spacing.small
      ),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth()
        .padding(Spacing.large),
      verticalArrangement = Arrangement.spacedBy(Spacing.large),
      content = content,
    )
  }
}
