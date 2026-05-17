package dev.fanfly.wingslog.feature.logs.update.logs.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.Spacing

@Composable
internal fun LogSection(
  header: String,
  description: String? = null,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Text(
      text = header.uppercase(),
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary,
      letterSpacing = 1.2.sp,
    )
    if (description != null) {
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    content()
  }
}

/**
 * A read-only text field that is tappable and accessible to screen readers.
 * Avoids the `enabled = false` accessibility trap by placing a transparent clickable overlay.
 */
@Composable
internal fun TappableReadOnlyField(
  value: String,
  label: @Composable () -> Unit,
  onClick: () -> Unit,
  accessibilityDescription: String,
  modifier: Modifier = Modifier,
  leadingIcon: (@Composable () -> Unit)? = null,
) {
  Box(modifier = modifier) {
    OutlinedTextField(
      value = value,
      onValueChange = {},
      enabled = false,
      modifier = Modifier.fillMaxWidth(),
      label = label,
      leadingIcon = leadingIcon,
      singleLine = true,
      colors = OutlinedTextFieldDefaults.colors(
        disabledTextColor = MaterialTheme.colorScheme.onSurface,
        disabledBorderColor = MaterialTheme.colorScheme.outline,
        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
      ),
    )
    Box(
      modifier = Modifier
        .matchParentSize()
        .semantics(mergeDescendants = true) {
          this.contentDescription = accessibilityDescription
          this.role = Role.Button
        }
        .clickable(onClick = onClick),
    )
  }
}
