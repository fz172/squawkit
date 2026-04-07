package dev.fanfly.wingslog.feature.maintenance.update.aircraft.compose

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import wingslog.core.ui.generated.resources.Res
import wingslog.core.ui.generated.resources.required

@Composable
fun InputField(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  isError: Boolean = false,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  trailingIcon: @Composable (() -> Unit)? = null,
  onValueChange: (String) -> Unit,
) = OutlinedTextField(
  value = value,
  onValueChange = onValueChange,
  label = { Text(label, fontSize = 10.sp) },
  modifier = modifier.padding(vertical = 4.dp),
  singleLine = true,
  shape = RoundedCornerShape(12.dp),
  enabled = enabled,
  trailingIcon = trailingIcon,
  isError = isError,
  supportingText = { if (isError) Text(stringResource(Res.string.required)) },
  keyboardOptions = keyboardOptions
)