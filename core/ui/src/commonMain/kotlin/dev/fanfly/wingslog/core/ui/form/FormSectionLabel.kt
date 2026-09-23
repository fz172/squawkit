package dev.fanfly.wingslog.core.ui.form

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * Canonical heading for form sections and non-editable field labels.
 */
@Composable
fun FormSectionLabel(
  text: String,
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.primary,
) {
  Text(
    text = text,
    modifier = modifier,
    style = MaterialTheme.typography.labelSmall,
    fontWeight = FontWeight.Bold,
    color = color,
  )
}
