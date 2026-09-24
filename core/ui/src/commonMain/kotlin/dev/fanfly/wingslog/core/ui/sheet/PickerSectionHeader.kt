package dev.fanfly.wingslog.core.ui.sheet

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing

@Composable
fun PickerSectionHeader(
  text: String,
  modifier: Modifier = Modifier,
) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = modifier.padding(
      top = Spacing.large,
      bottom = Spacing.small
    ),
  )
}
