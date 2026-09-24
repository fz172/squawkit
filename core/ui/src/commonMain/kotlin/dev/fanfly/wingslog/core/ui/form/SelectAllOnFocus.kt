package dev.fanfly.wingslog.core.ui.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * What a text field needs to select its whole value when it takes focus, over a hoisted [String].
 *
 * Pass all three to the field: [value] and [onValueChange] in place of the String pair, and
 * [modifier] anywhere in its modifier chain.
 */
class SelectAllOnFocusField internal constructor(
  val value: TextFieldValue,
  val modifier: Modifier,
  val onValueChange: (TextFieldValue) -> Unit,
)

/**
 * A [TextFieldValue] mirror over [value] that selects the whole value when the field takes focus,
 * so typing replaces it.
 *
 * For fields that open already holding a number the user is about to overwrite — a meter reading,
 * an interval — where the alternative is deleting every digit by hand first.
 *
 * Form fields take a plain String everywhere in this app, and a String cannot carry a selection,
 * so a field that wants one keeps this mirror instead: the text stays the caller's, the selection
 * is the field's own. That is exactly the mirror the String-valued text-field overloads keep
 * internally, which is why a field reading through this one with [enabled] false behaves as it
 * always did.
 */
@Composable
fun rememberSelectAllOnFocus(
  value: String,
  enabled: Boolean = true,
  onValueChange: (String) -> Unit,
): SelectAllOnFocusField {
  var fieldState by remember { mutableStateOf(TextFieldValue(value)) }
  val fieldValue = fieldState.copy(text = value)
  SideEffect { fieldState = fieldValue }

  var focused by remember { mutableStateOf(false) }
  LaunchedEffect(focused, enabled) {
    if (!focused || !enabled) return@LaunchedEffect
    // After the frame that focused the field, not during it: a tap asks for focus and then drops
    // the caret where the finger landed, which would overwrite a selection written any earlier.
    withFrameNanos { }
    fieldState =
      fieldState.copy(selection = TextRange(0, fieldState.text.length))
  }

  return SelectAllOnFocusField(
    value = fieldValue,
    modifier = Modifier.onFocusChanged { focused = it.isFocused },
    onValueChange = { next ->
      fieldState = next
      if (next.text != value) onValueChange(next.text)
    },
  )
}
