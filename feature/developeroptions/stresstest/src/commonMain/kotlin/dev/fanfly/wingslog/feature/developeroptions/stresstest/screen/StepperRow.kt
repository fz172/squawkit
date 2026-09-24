package dev.fanfly.wingslog.feature.developeroptions.stresstest.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.developeroptions.stresstest.generated.resources.Res
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_decrement
import wingslog.feature.developeroptions.stresstest.generated.resources.stress_test_increment

@Composable
internal fun StepperRow(
  label: String,
  value: Int,
  range: IntRange,
  onDecrement: () -> Unit,
  onIncrement: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
    )
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      OutlinedButton(
        onClick = onDecrement,
        enabled = value > range.first,
        shape = RoundedCornerShape(Spacing.chipCornerRadius),
        contentPadding = PaddingValues(
          horizontal = Spacing.large,
          vertical = Spacing.small
        ),
      ) {
        Text(
          stringResource(Res.string.stress_test_decrement),
          style = MaterialTheme.typography.titleMedium
        )
      }
      Text(
        text = value.toString(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = Spacing.small),
      )
      OutlinedButton(
        onClick = onIncrement,
        enabled = value < range.last,
        shape = RoundedCornerShape(Spacing.chipCornerRadius),
        contentPadding = PaddingValues(
          horizontal = Spacing.large,
          vertical = Spacing.small
        ),
      ) {
        Text(
          stringResource(Res.string.stress_test_increment),
          style = MaterialTheme.typography.titleMedium
        )
      }
    }
  }
}
