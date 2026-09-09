package dev.fanfly.wingslog.core.ui.common

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

sealed class UiText {
  data class DynamicString(val value: String) : UiText()

  /**
   * A resource plus the arguments that fill it. [args] exists because most record-naming copy is a
   * lexicon frame ("%1$s deleted"), and the noun is resolved where the record is — in the
   * ViewModel, which knows the thing's template — not in whichever composable happens to show it.
   */
  data class StringRes(
    val res: StringResource,
    val args: List<String> = emptyList(),
  ) : UiText()

  @Composable
  fun asString(): String {
    return when (this) {
      is DynamicString -> value
      is StringRes ->
        if (args.isEmpty()) stringResource(res) else stringResource(
          res,
          *args.toTypedArray()
        )
    }
  }
}
