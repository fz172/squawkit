package dev.fanfly.wingslog.feature.login.email

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.email_entry_hint

@Composable
internal fun EmailField(
  value: String,
  onValueChange: (String) -> Unit,
  isError: Boolean,
  enabled: Boolean,
  onImeAction: () -> Unit,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = Modifier.fillMaxWidth(),
    enabled = enabled,
    singleLine = true,
    isError = isError,
    placeholder = {
      Text(
        stringResource(Res.string.email_entry_hint),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
      )
    },
    keyboardOptions = KeyboardOptions(
      keyboardType = KeyboardType.Email,
      imeAction = ImeAction.Go
    ),
    keyboardActions = KeyboardActions(onGo = { onImeAction() }),
    shape = RoundedCornerShape(Spacing.buttonCornerRadius),
    colors = OutlinedTextFieldDefaults.colors(
      focusedTextColor = MaterialTheme.colorScheme.onSurface,
      unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
      cursorColor = MaterialTheme.colorScheme.onSurface,
      focusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
      unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
        alpha = 0.4f
      ),
    ),
  )
}
