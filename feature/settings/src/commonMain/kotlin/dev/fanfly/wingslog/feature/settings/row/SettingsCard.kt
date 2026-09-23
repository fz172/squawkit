package dev.fanfly.wingslog.feature.settings.row

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.grouped.GroupedCard

/**
 * A grouped settings surface: a bordered, 16dp-radius card that stacks its rows top to bottom.
 * Matches the `.card` group in the Settings design handoff — flat (no elevation), a hairline
 * `outlineVariant` border, and the standard card container tone.
 */
@Composable
fun SettingsCard(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  GroupedCard(modifier = modifier, content = content)
}
