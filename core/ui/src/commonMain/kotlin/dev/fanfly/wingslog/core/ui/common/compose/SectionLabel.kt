package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.Spacing

/** Tracking for uppercase section markers; wider than labelSmall's own so caps stay readable. */
val SectionLabelTracking = 0.9.sp

/**
 * An uppercase section marker above a grouped card — "Preferences", "Notification types".
 *
 * Uppercased at render rather than in the string resource so translations stay sentence case and
 * a language without letter case is unaffected. [action] is an optional trailing control on the
 * same baseline ("Clear all").
 */
@Composable
fun SectionLabel(
  text: String,
  modifier: Modifier = Modifier,
  action: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = Spacing.extraSmall),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = text.uppercase(),
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      letterSpacing = SectionLabelTracking,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    action?.invoke()
  }
}

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
