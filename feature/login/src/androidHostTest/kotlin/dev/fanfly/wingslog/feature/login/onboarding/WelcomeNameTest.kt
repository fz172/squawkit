package dev.fanfly.wingslog.feature.login.onboarding

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The welcome greeting used to read its name off the live local-record flow, which starts null and
 * can arrive after the screen has already revealed its text — and never arrives at all on a fresh
 * Google/Apple signup, where nothing has written a local technician record yet. Both cases showed
 * the no-name fallback instead of the user's name, so the name is resolved from both sources up
 * front, on the way into the step.
 */
class WelcomeNameTest {

  @Test
  fun localNameWins() {
    assertThat(resolveWelcomeName("Amelia", "A. Earhart")).isEqualTo("Amelia")
  }

  @Test
  fun fallsBackToTheAuthProfileWhenNoLocalRecordExistsYet() {
    assertThat(resolveWelcomeName(null, "Amelia Earhart")).isEqualTo("Amelia Earhart")
    assertThat(resolveWelcomeName("", "Amelia Earhart")).isEqualTo("Amelia Earhart")
    assertThat(resolveWelcomeName("   ", "Amelia Earhart")).isEqualTo("Amelia Earhart")
  }

  @Test
  fun trimsWhitespace() {
    assertThat(resolveWelcomeName("  Amelia  ", null)).isEqualTo("Amelia")
    assertThat(resolveWelcomeName(null, "  Amelia  ")).isEqualTo("Amelia")
  }

  @Test
  fun blankWhenNeitherSourceHasAName() {
    assertThat(resolveWelcomeName(null, null)).isEmpty()
    assertThat(resolveWelcomeName("", "  ")).isEmpty()
  }
}
