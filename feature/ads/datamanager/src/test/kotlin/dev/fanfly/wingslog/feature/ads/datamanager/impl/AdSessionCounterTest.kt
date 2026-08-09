package dev.fanfly.wingslog.feature.ads.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.lifecycle.AppForegroundObserver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AdSessionCounterTest {

  private class FakeClock(private var current: Instant = Instant.fromEpochMilliseconds(0)) : Clock {
    override fun now(): Instant = current
    fun advance(by: Duration) {
      current += by
    }
  }

  private val clock = FakeClock()
  private val foreground = AppForegroundObserver(clock)
  private val counter = AdSessionCounter(foreground)

  private fun startSession() = foreground.onEnterForeground()

  private fun leaveFor(duration: Duration) {
    foreground.onEnterBackground()
    clock.advance(duration)
    foreground.onEnterForeground()
  }

  @Test
  fun `grants up to the cap and then nothing`() {
    startSession()
    repeat(AdSessionCounter.CAP) { assertThat(counter.reserve(1)).isEqualTo(1) }

    assertThat(counter.reserve(1)).isEqualTo(0)
    assertThat(counter.displayed.value).isEqualTo(AdSessionCounter.CAP)
    assertThat(counter.headroom).isEqualTo(0)
  }

  @Test
  fun `a two-up slot costs two units`() {
    startSession()
    assertThat(counter.reserve(2)).isEqualTo(2)
    assertThat(counter.displayed.value).isEqualTo(2)
    assertThat(counter.headroom).isEqualTo(3)
  }

  @Test
  fun `a two-up slot with one unit of headroom is granted one, not two`() {
    // The "near the cap" rule: the slot renders one centred unit rather than overshooting the cap
    // or showing a half-empty band. CAP is 5, so four single reservations leave exactly one.
    startSession()
    repeat(AdSessionCounter.CAP - 1) { counter.reserve(1) }

    assertThat(counter.headroom).isEqualTo(1)
    assertThat(counter.reserve(2)).isEqualTo(1)
    assertThat(counter.displayed.value).isEqualTo(AdSessionCounter.CAP)
  }

  @Test
  fun `the cap is never overshot by a multi-unit reservation`() {
    startSession()
    repeat(10) { counter.reserve(2) }
    assertThat(counter.displayed.value).isEqualTo(AdSessionCounter.CAP)
  }

  @Test
  fun `reserving zero or fewer units is a programming error`() {
    startSession()
    assertThat(runCatching { counter.reserve(0) }.isFailure).isTrue()
    assertThat(runCatching { counter.reserve(-1) }.isFailure).isTrue()
  }

  // ------------------------------------------------------------ session boundaries

  @Test
  fun `a new session restores the full budget`() {
    startSession()
    repeat(AdSessionCounter.CAP) { counter.reserve(1) }
    assertThat(counter.reserve(1)).isEqualTo(0)

    leaveFor(30.minutes)

    assertThat(counter.headroom).isEqualTo(AdSessionCounter.CAP)
    assertThat(counter.reserve(1)).isEqualTo(1)
  }

  @Test
  fun `a short background does not restore the budget`() {
    startSession()
    repeat(AdSessionCounter.CAP) { counter.reserve(1) }

    leaveFor(29.minutes)

    // Backgrounding briefly must not hand a pilot another five ads.
    assertThat(counter.headroom).isEqualTo(0)
    assertThat(counter.reserve(1)).isEqualTo(0)
  }

  @Test
  fun `the budget is spent globally, not per surface`() {
    // Three surfaces drawing from one budget: 2 + 2 + 2 is capped at 5, not 6.
    startSession()
    val granted = counter.reserve(2) + counter.reserve(2) + counter.reserve(2)
    assertThat(granted).isEqualTo(AdSessionCounter.CAP)
  }

  @Test
  fun `a session that rolls over before any reservation still starts clean`() {
    // syncSession() is polled at use, so a boundary crossed while no slot was composing is still
    // observed the next time a slot asks - this is why the id is read rather than subscribed to.
    startSession()
    counter.reserve(2)
    leaveFor(45.minutes)

    assertThat(counter.displayed.value).isEqualTo(2) // stale until something asks
    assertThat(counter.headroom).isEqualTo(AdSessionCounter.CAP) // asking re-syncs
    assertThat(counter.displayed.value).isEqualTo(0)
  }

  // ------------------------------------------------------------------ cap reached

  @Test
  fun `capReached emits once when the final unit is taken, and not again`() = runTest {
    startSession()
    val seen = mutableListOf<Unit>()
    // Unconfined so the collector is subscribed before the first reservation: capReached has no
    // replay, deliberately — a late subscriber must not receive an event that already fired.
    val job = launch(UnconfinedTestDispatcher(testScheduler)) {
      counter.capReached.collect { seen += it }
    }

    repeat(AdSessionCounter.CAP - 1) { counter.reserve(1) }
    runCurrent()
    assertThat(seen).isEmpty()

    counter.reserve(1)
    runCurrent()
    assertThat(seen).hasSize(1)

    // Reservations past the cap are refused before they can re-emit.
    repeat(3) { counter.reserve(1) }
    runCurrent()
    assertThat(seen).hasSize(1)

    job.cancel()
  }

  @Test
  fun `capReached emits again in a new session`() = runTest {
    startSession()
    val seen = mutableListOf<Unit>()
    val job = launch(UnconfinedTestDispatcher(testScheduler)) {
      counter.capReached.collect { seen += it }
    }

    repeat(AdSessionCounter.CAP) { counter.reserve(1) }
    runCurrent()
    assertThat(seen).hasSize(1)

    leaveFor(30.minutes)
    repeat(AdSessionCounter.CAP) { counter.reserve(1) }
    runCurrent()

    // Once per session, not once per process.
    assertThat(seen).hasSize(2)

    job.cancel()
  }
}
