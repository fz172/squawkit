package dev.fanfly.wingslog.feature.login.email

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.Spacing

@Composable
internal fun StepHeading(title: String, subtitle: String? = null) {
  Text(
    text = title,
    style = TextStyle(
      fontWeight = FontWeight.SemiBold,
      fontSize = 20.sp,
      color = MaterialTheme.colorScheme.onSurface
    ),
  )
  if (subtitle != null) {
    Spacer(Modifier.height(Spacing.small))
    Text(
      text = subtitle,
      style = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      ),
    )
  }
}
