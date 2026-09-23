package dev.fanfly.wingslog.core.ui.form

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.Res
import wingslog.core.sharedassets.generated.resources.required

/**
 * Text-entry field that locks when [editable] is false.
 *
 * Use this when the same form value may be editable during creation but fixed once a record has
 * been saved. A locked value keeps the field's box and label, so a form of mixed fields reads as
 * one form rather than as inputs scattered among captions; it simply takes no focus and no typing.
 * Its border never lights, which is what tells it from the field beside it that does.
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
  // Compact variant: tightens vertical content padding so the field reads shorter than the default.
  dense: Boolean = false,
  /**
   * Select the whole value when the field takes focus, so typing replaces it.
   *
   * For fields that open already holding a number the user is about to overwrite — a meter
   * reading, a count — where the alternative is deleting every digit by hand first.
   */
  selectAllOnFocus: Boolean = false,
  onValueChange: (String) -> Unit,
) {
  val errorText =
    supportingText ?: if (isError) stringResource(Res.string.required) else null
  val fieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.outline,
  )

  val field = rememberSelectAllOnFocus(
    value = value,
    enabled = selectAllOnFocus,
    onValueChange = onValueChange,
  )
  val fieldModifier = modifier
    .fillMaxWidth()
    .then(field.modifier)
    .then(if (editable) Modifier else Modifier.focusProperties {
      canFocus = false
    })

  if (dense) {
    // M3 OutlinedTextField has no contentPadding knob, so build it from the decoration box to
    // tighten the vertical padding (8dp vs the default 16dp) and shave the field height.
    val interactionSource = remember { MutableInteractionSource() }
    // The floating label rides the top border and overflows ~half a line above the field's content
    // box; with the tightened padding that overflow collided with the element above. Reserve a
    // vertical margin of ~0.75 character height around the field so the label (and supporting text)
    // have room. Derived from the text size so it scales with the user's font setting.
    val labelMargin = with(LocalDensity.current) {
      val charSp =
        textStyle.fontSize.takeIf { it != TextUnit.Unspecified }?.value ?: 14f
      (charSp * 0.75f).sp.toDp()
    }
    BasicTextField(
      value = field.value,
      onValueChange = field.onValueChange,
      modifier = fieldModifier.padding(vertical = labelMargin),
      readOnly = !editable,
      singleLine = singleLine,
      minLines = minLines,
      maxLines = maxLines,
      keyboardOptions = keyboardOptions,
      textStyle = textStyle.merge(TextStyle(color = MaterialTheme.colorScheme.onSurface)),
      cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
      interactionSource = interactionSource,
      decorationBox = { innerTextField ->
        OutlinedTextFieldDefaults.DecorationBox(
          value = value,
          innerTextField = innerTextField,
          enabled = true,
          singleLine = singleLine,
          visualTransformation = VisualTransformation.None,
          interactionSource = interactionSource,
          isError = isError,
          label = { Text(label) },
          placeholder = placeholder?.let { { Text(it) } },
          leadingIcon = leadingIcon,
          trailingIcon = trailingIcon,
          supportingText = errorText?.let { { Text(it) } },
          colors = fieldColors,
          contentPadding = OutlinedTextFieldDefaults.contentPadding(
            top = Spacing.small,
            bottom = Spacing.small,
          ),
          container = {
            OutlinedTextFieldDefaults.Container(
              enabled = true,
              isError = isError,
              interactionSource = interactionSource,
              colors = fieldColors,
              shape = shape,
            )
          },
        )
      },
    )
    return
  }

  OutlinedTextField(
    value = field.value,
    onValueChange = field.onValueChange,
    label = { Text(label) },
    modifier = fieldModifier,
    readOnly = !editable,
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
    colors = fieldColors,
  )
}
