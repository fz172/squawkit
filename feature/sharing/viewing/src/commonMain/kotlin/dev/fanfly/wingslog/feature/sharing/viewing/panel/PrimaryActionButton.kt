package dev.fanfly.wingslog.feature.sharing.viewing.panel

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing

@Composable
internal fun PrimaryActionButton(
  label: String,
  onClick: () -> Unit,
  loading: Boolean = false
) {
  Button(
    onClick = onClick,
    enabled = !loading,
    modifier = Modifier.fillMaxWidth()
      .height(Spacing.buttonHeight),
  ) {
    if (loading) {
      CircularProgressIndicator(
        Modifier.padding(2.dp)
          .height(20.dp)
          .width(20.dp), strokeWidth = 2.dp
      )
    } else {
      Text(label)
    }
  }
}
