package dev.fanfly.wingslog.feature.notifications.model

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.id.ThingId
import org.junit.Test

class OnScreenTapTargetsTest {

  private val suggestions = NotificationTapTarget.Suggestions(ThingId("thing-1"))

  @Test
  fun `a target is on screen only while shown`() {
    assertThat(OnScreenTapTargets.isOnScreen(suggestions)).isFalse()

    val hide = OnScreenTapTargets.show(suggestions)
    assertThat(OnScreenTapTargets.isOnScreen(suggestions)).isTrue()
    assertThat(OnScreenTapTargets.isOnScreen(NotificationTapTarget.Suggestions(ThingId("thing-2")))).isFalse()

    hide()
    assertThat(OnScreenTapTargets.isOnScreen(suggestions)).isFalse()
  }

  @Test
  fun `the same screen twice stays on screen until both copies leave`() {
    val first = OnScreenTapTargets.show(suggestions)
    val second = OnScreenTapTargets.show(suggestions)

    first()
    assertThat(OnScreenTapTargets.isOnScreen(suggestions)).isTrue()

    second()
    assertThat(OnScreenTapTargets.isOnScreen(suggestions)).isFalse()
  }
}
