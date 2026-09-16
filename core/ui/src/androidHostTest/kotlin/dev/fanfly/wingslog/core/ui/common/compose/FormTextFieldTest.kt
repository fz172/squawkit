package dev.fanfly.wingslog.core.ui.common.compose

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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FormTextFieldTest {

  @get:Rule
  val rule = createComposeRule()

  private var current: String = ""

  @Composable
  private fun Field(selectAllOnFocus: Boolean, initial: String = "1234.5") {
    var value by remember { mutableStateOf(initial) }
    current = value
    MaterialTheme {
      FormTextField(
        label = "Engine time",
        value = value,
        modifier = Modifier.testTag("field"),
        selectAllOnFocus = selectAllOnFocus,
        onValueChange = { value = it },
      )
    }
  }

  @Test
  fun selectAllOnFocus_typingReplacesTheWholeValue() {
    rule.setContent { Field(selectAllOnFocus = true) }

    rule.onNodeWithTag("field").performClick()
    rule.waitForIdle()
    rule.onNodeWithTag("field").performTextInput("1250")
    rule.waitForIdle()

    // The point of the flag: a meter reading is a new number, not an edit of the old one, so the
    // user does not delete six characters before typing theirs.
    assertThat(current).isEqualTo("1250")
  }

  @Test
  fun withoutTheFlag_typingKeepsWhatWasThere() {
    rule.setContent { Field(selectAllOnFocus = false) }

    rule.onNodeWithTag("field").performClick()
    rule.waitForIdle()
    rule.onNodeWithTag("field").performTextInput("9")

    // Every other form field in the app still edits in place.
    assertThat(current).contains("1234.5")
  }

  @Test
  fun selectAllOnFocus_stillReportsEveryKeystroke() {
    rule.setContent { Field(selectAllOnFocus = true, initial = "") }

    rule.onNodeWithTag("field").performClick()
    rule.waitForIdle()
    rule.onNodeWithTag("field").performTextInput("80")
    rule.waitForIdle()
    rule.onNodeWithTag("field").performTextInput("1")
    rule.waitForIdle()

    // Selection is written once per focus, not on every recomposition — otherwise the second
    // keystroke would replace the first.
    assertThat(current).isEqualTo("801")
  }
}
