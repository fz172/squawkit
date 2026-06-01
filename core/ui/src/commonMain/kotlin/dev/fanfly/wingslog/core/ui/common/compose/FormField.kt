package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.Res
import wingslog.core.sharedassets.generated.resources.required

/**
 * Text-entry field that switches to a non-input presentation when [editable] is false.
 *
 * Use this when the same form value may be editable during creation but fixed once a record has
 * been saved. A locked value should read as documented information, not as a disabled control.
 */
@Composable
fun FormTextField(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  editable: Boolean = true,
  placeholder: String? = null,
  isError: Boolean = false,
  supportingText: String? = null,
  singleLine: Boolean = true,
  minLines: Int = 1,
  maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  leadingIcon: (@Composable () -> Unit)? = null,
  trailingIcon: (@Composable () -> Unit)? = null,
  textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
  shape: Shape = RoundedCornerShape(Spacing.chipCornerRadius),
  onValueChange: (String) -> Unit,
) {
  val errorText =
    supportingText ?: if (isError) stringResource(Res.string.required) else null
  if (!editable) {
    FormValueField(
      label = label,
      value = value,
      modifier = modifier,
      placeholder = placeholder,
      supportingText = errorText,
      isError = isError,
      valueStyle = textStyle,
      leadingIcon = leadingIcon,
      trailingIcon = trailingIcon,
      maxLines = maxLines,
    )
    return
  }

  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(label.uppercase()) },
    modifier = modifier.fillMaxWidth(),
    placeholder = placeholder?.let { { Text(it) } },
    singleLine = singleLine,
    minLines = minLines,
    maxLines = maxLines,
    shape = shape,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
    isError = isError,
    supportingText = errorText?.let { { Text(it) } },
    keyboardOptions = keyboardOptions,
    textStyle = textStyle,
    colors = OutlinedTextFieldDefaults.colors(
      focusedBorderColor = MaterialTheme.colorScheme.primary,
      unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
      focusedLabelColor = MaterialTheme.colorScheme.primary,
      unfocusedLabelColor = MaterialTheme.colorScheme.outline,
    ),
  )
}

/**
 * Plain label-and-value presentation for values that cannot be edited in the current context.
 *
 * Supplying [onClick] makes the value an action row for picker-backed selection while retaining
 * a display treatment instead of presenting a false text-entry affordance.
 */
@Composable
fun FormValueField(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  showLabel: Boolean = true,
  placeholder: String? = null,
  supportingText: String? = null,
  isError: Boolean = false,
  onClick: (() -> Unit)? = null,
  accessibilityDescription: String = label,
  leadingIcon: (@Composable () -> Unit)? = null,
  trailingIcon: (@Composable () -> Unit)? = null,
  valueStyle: TextStyle = MaterialTheme.typography.bodyLarge,
  maxLines: Int = Int.MAX_VALUE,
) {
  val actionModifier = if (onClick == null) {
    Modifier
  } else {
    Modifier
      .semantics(mergeDescendants = true) {
        contentDescription = accessibilityDescription
        role = Role.Button
      }
      .clickable(onClick = onClick)
  }
  val shownValue = value.ifEmpty { placeholder ?: "-" }
  val valueColor = when {
    isError -> MaterialTheme.colorScheme.error
    value.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurface
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .then(actionModifier)
      .padding(vertical = Spacing.extraSmall),
    verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
  ) {
    if (showLabel) {
      FormSectionLabel(
        text = label,
        color = if (isError) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.primary,
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth()
        .padding(vertical = Spacing.extraSmall),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (leadingIcon != null) {
        leadingIcon()
        Spacer(Modifier.width(Spacing.medium))
      }
      Text(
        text = shownValue,
        modifier = Modifier.weight(1f),
        style = valueStyle,
        color = valueColor,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
      )
      if (trailingIcon != null) {
        Spacer(Modifier.width(Spacing.medium))
        trailingIcon()
      }
    }
    if (supportingText != null) {
      Text(
        text = supportingText,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

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
    text = text.uppercase(),
    modifier = modifier,
    style = MaterialTheme.typography.labelSmall,
    fontWeight = FontWeight.Bold,
    color = color,
    letterSpacing = 1.2.sp,
  )
}
