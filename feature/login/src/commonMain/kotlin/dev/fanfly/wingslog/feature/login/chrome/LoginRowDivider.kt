package dev.fanfly.wingslog.feature.login.chrome

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.theme.Spacing

/** The hairline between two rows, full-bleed like the rows themselves. */
@Composable
internal fun LoginRowDivider() {
  HorizontalDivider(
    thickness = Spacing.hairline,
    color = MaterialTheme.colorScheme.outlineVariant,
  )
}
