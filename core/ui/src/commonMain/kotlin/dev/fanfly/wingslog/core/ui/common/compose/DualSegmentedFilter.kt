package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DualSegmentedFilter(
  option1: String,
  option2: String,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  // Full width on a phone; capped where the content column is wide, so two words do not stretch
  // across the whole screen.
  SingleChoiceSegmentedButtonRow(
    modifier = modifier.fillMaxWidth()
      .widthIn(max = MAX_WIDTH)
  ) {
    SegmentedButton(
      selected = selectedIndex == 0,
      onClick = { onSelect(0) },
      shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
    ) { Text(option1) }
    SegmentedButton(
      selected = selectedIndex == 1,
      onClick = { onSelect(1) },
      shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
    ) { Text(option2) }
  }
}

private val MAX_WIDTH = 480.dp
