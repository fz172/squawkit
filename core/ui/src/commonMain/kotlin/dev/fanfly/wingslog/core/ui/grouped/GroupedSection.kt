package dev.fanfly.wingslog.core.ui.grouped

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.list.SectionLabel
import dev.fanfly.wingslog.core.ui.theme.Spacing

/** A [SectionLabel] and the content it introduces, with the standard gap between them. */
@Composable
fun GroupedSection(
  title: String,
  modifier: Modifier = Modifier,
  action: (@Composable () -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    SectionLabel(text = title, action = action)
    content()
  }
}
