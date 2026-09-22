package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The helper as the task form's number input uses it — a bare [BasicTextField] whose caller
 * filters what it accepts, rather than through [FormTextField].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SelectAllOnFocusTest {

  @get:Rule
  val rule = createComposeRule()

  private var current: String = ""

  @Composable
  private fun DigitsField(initial: String) {
    var value by remember { mutableStateOf(initial) }
    current = value
    MaterialTheme {
      val field = rememberSelectAllOnFocus(value) { typed ->
        value = typed.filter { it.isDigit() || it == '.' }
      }
      BasicTextField(
        value = field.value,
        onValueChange = field.onValueChange,
        modifier = Modifier.testTag("field")
          .then(field.modifier),
      )
    }
  }

  @Test
  fun typingReplacesTheInterval() {
    rule.setContent { DigitsField("100") }

    rule.onNodeWithTag("field")
      .performClick()
    rule.waitForIdle()
    rule.onNodeWithTag("field")
      .performTextInput("50")
    rule.waitForIdle()

    assertThat(current).isEqualTo("50")
  }

  @Test
  fun aRejectedCharacterLeavesTheValueAlone() {
    rule.setContent { DigitsField("100") }

    rule.onNodeWithTag("field")
      .performClick()
    rule.waitForIdle()
    rule.onNodeWithTag("field")
      .performTextInput("2")
    rule.waitForIdle()
    // The caller's filter still governs what lands: the selection only decides what the keystroke
    // replaces.
    rule.onNodeWithTag("field")
      .performTextInput("x")
    rule.waitForIdle()

    assertThat(current).isEqualTo("2")
  }
}
