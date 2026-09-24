package dev.fanfly.wingslog.feature.login.email

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.login.chrome.LoginButtonLabelStyle

@Composable
internal fun PrimaryButton(
  label: String,
  enabled: Boolean,
  loading: Boolean,
  onClick: () -> Unit,
) {
  Button(
    modifier = Modifier
      .fillMaxWidth()
      .height(54.dp),
    enabled = enabled,
    shape = RoundedCornerShape(Spacing.buttonCornerRadius),
    colors = ButtonDefaults.buttonColors(
      containerColor = MaterialTheme.colorScheme.onSurface,
      contentColor = MaterialTheme.colorScheme.surface,
      disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
      disabledContentColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
    ),
    onClick = onClick,
  ) {
    if (loading) {
      CircularProgressIndicator(
        modifier = Modifier.size(Spacing.xLarge),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.surface,
      )
    } else {
      Text(text = label, style = LoginButtonLabelStyle)
    }
  }
}
