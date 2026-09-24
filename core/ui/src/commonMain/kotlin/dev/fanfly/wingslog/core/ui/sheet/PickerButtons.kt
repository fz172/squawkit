package dev.fanfly.wingslog.core.ui.sheet

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.fanfly.wingslog.core.ui.theme.Spacing

@Composable
fun PickerDoneButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Button(
    onClick = onClick,
    modifier = modifier
      .fillMaxWidth()
      .padding(top = Spacing.large),
  ) {
    Text(text)
  }
}

@Composable
fun PickerActionButton(
  text: String,
  onClick: () -> Unit,
  icon: ImageVector,
  modifier: Modifier = Modifier,
) {
  TextButton(
    onClick = onClick,
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = Spacing.small),
  ) {
    Icon(imageVector = icon, contentDescription = null)
    Spacer(Modifier.width(Spacing.small))
    Text(text)
  }
}
