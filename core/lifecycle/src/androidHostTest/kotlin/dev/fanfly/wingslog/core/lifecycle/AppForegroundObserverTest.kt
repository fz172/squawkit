package dev.fanfly.wingslog.core.lifecycle

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AppForegroundObserverTest {

  /** A hand-wound clock. The whole point of keeping this class Compose-free is being able to do this. */
  private class FakeClock(
    private var current: Instant = Instant.fromEpochMilliseconds(
      0
    )
  ) : Clock {
    override fun now(): Instant = current
    fun advance(by: Duration) {
      current += by
    }
  }

  private val clock = FakeClock()
  private val observer = AppForegroundObserver(clock)

  @Test
  fun `starts at zero - no session until the app is actually foregrounded`() {
    assertThat(observer.sessionId.value).isEqualTo(0L)
  }

  @Test
  fun `cold start begins the first session`() {
    observer.onEnterForeground()
    assertThat(observer.sessionId.value).isEqualTo(1L)
  }

  @Test
  fun `a short background does not start a new session`() {
    observer.onEnterForeground()
    observer.onEnterBackground()
    clock.advance(29.minutes)
    observer.onEnterForeground()

    // Glancing at a notification must not reset a pilot's ad budget.
    assertThat(observer.sessionId.value).isEqualTo(1L)
  }

  @Test
  fun `a background of exactly the threshold starts a new session`() {
    observer.onEnterForeground()
    observer.onEnterBackground()
    clock.advance(30.minutes)
    observer.onEnterForeground()

    assertThat(observer.sessionId.value).isEqualTo(2L)
  }

  @Test
  fun `a long background starts a new session`() {
    observer.onEnterForeground()
    observer.onEnterBackground()
    clock.advance(4.hours)
    observer.onEnterForeground()

    assertThat(observer.sessionId.value).isEqualTo(2L)
  }

  @Test
  fun `repeated foregrounds without a background do not start sessions`() {
    observer.onEnterForeground()
    observer.onEnterForeground()
    observer.onEnterForeground()

    assertThat(observer.sessionId.value).isEqualTo(1L)
  }

  @Test
  fun `each long absence starts exactly one new session`() {
    observer.onEnterForeground()
    repeat(3) {
      observer.onEnterBackground()
      clock.advance(45.minutes)
      observer.onEnterForeground()
    }
    assertThat(observer.sessionId.value).isEqualTo(4L)
  }

  @Test
  fun `short absences accumulate without ever crossing the threshold`() {
    // Three 15-minute gaps are not one 45-minute gap: the clock restarts at each background.
    observer.onEnterForeground()
    repeat(3) {
      observer.onEnterBackground()
      clock.advance(15.minutes)
      observer.onEnterForeground()
    }
    assertThat(observer.sessionId.value).isEqualTo(1L)
  }

  @Test
  fun `the threshold is configurable`() {
    val quick = AppForegroundObserver(clock, threshold = 10.seconds)
    quick.onEnterForeground()
    quick.onEnterBackground()
    clock.advance(11.seconds)
    quick.onEnterForeground()

    assertThat(quick.sessionId.value).isEqualTo(2L)
  }
}
