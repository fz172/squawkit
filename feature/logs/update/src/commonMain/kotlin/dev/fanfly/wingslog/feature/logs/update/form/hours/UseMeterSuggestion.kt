package dev.fanfly.wingslog.feature.logs.update.form.hours

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.logs.update.generated.resources.Res
import wingslog.feature.logs.update.generated.resources.meter_use_suggestion

/**
 * "Use 3.9" — the reading this meter would show if it had moved with the one it follows.
 *
 * An offer, not a correction: it fills the field the user would otherwise work out by hand, and
 * disappears once the field says what it suggests.
 */
@Composable
internal fun UseMeterSuggestion(
  suggested: String,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(Spacing.chipCornerRadius),
    color = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.primary,
    border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.primary),
    modifier = Modifier.padding(end = Spacing.small),
  ) {
    Text(
      text = stringResource(Res.string.meter_use_suggestion, suggested),
      style = MaterialTheme.typography.labelMedium,
      maxLines = 1,
      modifier = Modifier.padding(
        horizontal = Spacing.small,
        vertical = Spacing.extraSmall,
      ),
    )
  }
}
