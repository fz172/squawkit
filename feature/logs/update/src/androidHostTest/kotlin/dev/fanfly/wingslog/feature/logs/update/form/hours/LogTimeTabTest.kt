package dev.fanfly.wingslog.feature.logs.update.form.hours

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.template.LocalThingCapabilities
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.core.template.canonical.AirplaneTemplate
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The "Use 3.9" offer beside a meter field — whether it is reachable and what tapping it reports.
 *
 * Which meter is offered what is the ViewModel's decision and is tested there; this covers the
 * half that only exists on screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LogTimeTabTest {

  @get:Rule
  val rule = createComposeRule()

  private val changes = mutableListOf<Pair<String, String>>()

  private fun renderTab(suggestions: Map<String, String>) {
    rule.setContent {
      WingslogTheme(darkTheme = false) {
        CompositionLocalProvider(
          LocalThingTemplate provides AirplaneTemplate.TEMPLATE,
          LocalThingCapabilities provides AirplaneTemplate.AIRPLANE_CAPABILITIES,
        ) {
          LogTimeTab(
            meterValues = mapOf(
              MeterKeys.AIRFRAME_HOURS to "3.0",
              MeterKeys.ENGINE_HOURS to "1.7",
              MeterKeys.PROP_HOURS to "2.0",
            ),
            meterSuggestions = suggestions,
            onMeterChange = { key, value -> changes += key to value },
          )
        }
      }
    }
    rule.waitForIdle()
  }

  @Test
  fun anOfferedMeter_showsItsButton_whole() {
    renderTab(mapOf(MeterKeys.PROP_HOURS to "3.9"))

    val button = rule.onNodeWithText("Use 3.9")
    button.assertIsDisplayed()
    button.assertHasClickAction()
    // It sits in the field's trailing slot, which is sized for an icon — so the one thing worth
    // pinning is that the label is not clipped down to "Use 3…".
    val clipped = button.getBoundsInRoot()
    val unclipped = button.getUnclippedBoundsInRoot()
    assertThat((clipped.right - clipped.left).value)
      .isWithin(0.5f)
      .of((unclipped.right - unclipped.left).value)
  }

  @Test
  fun tappingTheButton_reportsThatMetersValue() {
    renderTab(mapOf(MeterKeys.PROP_HOURS to "3.9"))

    rule.onNodeWithText("Use 3.9")
      .performClick()

    // The meter the button belongs to, not the one whose increment produced the number.
    assertThat(changes).containsExactly(MeterKeys.PROP_HOURS to "3.9")
  }

  @Test
  fun aMeterWithNothingToOffer_showsNoButton() {
    renderTab(emptyMap())

    rule.onNodeWithText("Use", substring = true)
      .assertDoesNotExist()
  }
}
