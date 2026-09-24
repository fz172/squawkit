package dev.fanfly.wingslog.feature.developeroptions.stresstest.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun SliderRow(
  label: String,
  value: Int,
  range: IntRange,
  onValueChange: (Int) -> Unit,
) {
  Column {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
      )
      Text(
        text = value.toString(),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
      )
    }
    Slider(
      value = value.toFloat(),
      onValueChange = { onValueChange(it.toInt()) },
      valueRange = range.first.toFloat()..range.last.toFloat(),
      modifier = Modifier.fillMaxWidth(),
    )
  }
}
