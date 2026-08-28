package dev.fanfly.wingslog.feature.ads.datamanager.impl

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.lifecycle.AppForegroundObserver
import dev.fanfly.wingslog.feature.ads.model.AdSlotKey
import dev.fanfly.wingslog.feature.ads.model.AdSurface
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
  private val foreground = AppForegroundObserver(clock)
  private val counter = AdSessionCounter(foreground)

  private var keySeq = 0

  /** A fresh slot each time, so the existing budget tests exercise distinct slots as they intend. */
  private fun nextKey() = AdSlotKey(AdSurface.SQUAWKS, keySeq++)

  private fun startSession() = foreground.onEnterForeground()

  private fun leaveFor(duration: Duration) {
    foreground.onEnterBackground()
    clock.advance(duration)
    foreground.onEnterForeground()
  }

  @Test
  fun `grants up to the cap and then nothing`() {
    startSession()
    repeat(AdSessionCounter.CAP) {
      assertThat(
        counter.reserve(
          nextKey(),
          1
        )
      ).isEqualTo(1)
    }

    assertThat(counter.reserve(nextKey(), 1)).isEqualTo(0)
    assertThat(counter.displayed.value).isEqualTo(AdSessionCounter.CAP)
    assertThat(counter.headroom).isEqualTo(0)
  }

  @Test
  fun `a two-up slot costs two units`() {
    startSession()
    assertThat(counter.reserve(nextKey(), 2)).isEqualTo(2)
    assertThat(counter.displayed.value).isEqualTo(2)
    assertThat(counter.headroom).isEqualTo(3)
  }

  @Test
  fun `a two-up slot with one unit of headroom is granted one, not two`() {
    // The "near the cap" rule: the slot renders one centred unit rather than overshooting the cap
    // or showing a half-empty band. CAP is 5, so four single reservations leave exactly one.
    startSession()
    repeat(AdSessionCounter.CAP - 1) { counter.reserve(nextKey(), 1) }

    assertThat(counter.headroom).isEqualTo(1)
    assertThat(counter.reserve(nextKey(), 2)).isEqualTo(1)
    assertThat(counter.displayed.value).isEqualTo(AdSessionCounter.CAP)
  }

  @Test
  fun `the cap is never overshot by a multi-unit reservation`() {
    startSession()
    repeat(10) { counter.reserve(nextKey(), 2) }
    assertThat(counter.displayed.value).isEqualTo(AdSessionCounter.CAP)
  }

  @Test
  fun `reserving zero or fewer units is a programming error`() {
    startSession()
    assertThat(runCatching { counter.reserve(nextKey(), 0) }.isFailure).isTrue()
    assertThat(runCatching {
      counter.reserve(
        nextKey(),
        -1
      )
    }.isFailure).isTrue()
  }

  // ------------------------------------------------------------ session boundaries

  @Test
  fun `a new session restores the full budget`() {
    startSession()
    repeat(AdSessionCounter.CAP) { counter.reserve(nextKey(), 1) }
    assertThat(counter.reserve(nextKey(), 1)).isEqualTo(0)

    leaveFor(30.minutes)

    assertThat(counter.headroom).isEqualTo(AdSessionCounter.CAP)
    assertThat(counter.reserve(nextKey(), 1)).isEqualTo(1)
  }

  @Test
  fun `a short background does not restore the budget`() {
    startSession()
    repeat(AdSessionCounter.CAP) { counter.reserve(nextKey(), 1) }

    leaveFor(29.minutes)

    // Backgrounding briefly must not hand a pilot another five ads.
    assertThat(counter.headroom).isEqualTo(0)
    assertThat(counter.reserve(nextKey(), 1)).isEqualTo(0)
  }

  @Test
  fun `the budget is spent globally, not per surface`() {
    // Three surfaces drawing from one budget: 2 + 2 + 2 is capped at 5, not 6.
    startSession()
    val granted = counter.reserve(nextKey(), 2) + counter.reserve(
      nextKey(),
      2
    ) + counter.reserve(nextKey(), 2)
    assertThat(granted).isEqualTo(AdSessionCounter.CAP)
  }

  @Test
  fun `a session that rolls over before any reservation still starts clean`() {
    // syncSession() is polled at use, so a boundary crossed while no slot was composing is still
    // observed the next time a slot asks - this is why the id is read rather than subscribed to.
    startSession()
    counter.reserve(nextKey(), 2)
    leaveFor(45.minutes)

    assertThat(counter.displayed.value).isEqualTo(2) // stale until something asks
    assertThat(counter.headroom).isEqualTo(AdSessionCounter.CAP) // asking re-syncs
    assertThat(counter.displayed.value).isEqualTo(0)
  }

  // ------------------------------------------------------------------ cap reached

  @Test
  fun `capReached emits once when the final unit is taken, and not again`() =
    runTest {
      startSession()
      val seen = mutableListOf<Unit>()
      // Unconfined so the collector is subscribed before the first reservation: capReached has no
      // replay, deliberately — a late subscriber must not receive an event that already fired.
      val job = launch(UnconfinedTestDispatcher(testScheduler)) {
        counter.capReached.collect { seen += it }
      }

      repeat(AdSessionCounter.CAP - 1) { counter.reserve(nextKey(), 1) }
      runCurrent()
      assertThat(seen).isEmpty()

      counter.reserve(nextKey(), 1)
      runCurrent()
      assertThat(seen).hasSize(1)

      // Reservations past the cap are refused before they can re-emit.
      repeat(3) { counter.reserve(nextKey(), 1) }
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

    repeat(AdSessionCounter.CAP) { counter.reserve(nextKey(), 1) }
    runCurrent()
    assertThat(seen).hasSize(1)

    leaveFor(30.minutes)
    repeat(AdSessionCounter.CAP) { counter.reserve(nextKey(), 1) }
    runCurrent()

    // Once per session, not once per process.
    assertThat(seen).hasSize(2)

    job.cancel()
  }

  // ------------------------------------------------- slot identity survives recomposition

  @Test
  fun `re-reserving the same slot returns the same grant and costs nothing`() {
    // The bug: AdSlot cached its grant in `remember`, which dies with the composable. Scrolling a
    // slot out of the lazy logs list and back re-ran the reservation and spent the budget again.
    startSession()
    val key = AdSlotKey(AdSurface.LOGS, slotIndex = 0)

    assertThat(counter.reserve(key, 1)).isEqualTo(1)
    assertThat(counter.displayed.value).isEqualTo(1)

    repeat(10) { assertThat(counter.reserve(key, 1)).isEqualTo(1) }

    assertThat(counter.displayed.value).isEqualTo(1)
  }

  @Test
  fun `a slot already granted still renders after the cap is reached`() {
    // The user-visible symptom: see all five ads, scroll back, and the ones already displayed had
    // vanished — the revisited slot was asking for budget that no longer existed.
    startSession()
    val first = AdSlotKey(AdSurface.SQUAWKS, slotIndex = 0)
    assertThat(counter.reserve(first, 1)).isEqualTo(1)

    // Exhaust the rest of the session on other slots.
    repeat(AdSessionCounter.CAP - 1) {
      counter.reserve(
        AdSlotKey(
          AdSurface.TASKS,
          it
        ), 1
      )
    }
    assertThat(counter.headroom).isEqualTo(0)

    // Scrolling back to the first slot must still show its ad.
    assertThat(counter.reserve(first, 1)).isEqualTo(1)
    assertThat(counter.displayed.value).isEqualTo(AdSessionCounter.CAP)
  }

  @Test
  fun `distinct slots across surfaces each get their own grant`() {
    startSession()
    val perSurface = listOf(AdSurface.SQUAWKS, AdSurface.TASKS, AdSurface.LOGS)
      .sumOf { counter.reserve(AdSlotKey(it, slotIndex = 0), 1) }
    assertThat(perSurface).isEqualTo(3)
    assertThat(counter.displayed.value).isEqualTo(3)
  }

  @Test
  fun `a released slot may be granted again later`() {
    // A no-fill is usually the network, not the slot: the pilot should not be permanently short an
    // ad slot because one request happened to fail.
    startSession()
    val key = AdSlotKey(AdSurface.LOGS, slotIndex = 3)

    assertThat(counter.reserve(key, 1)).isEqualTo(1)
    counter.release(key, 1)
    assertThat(counter.displayed.value).isEqualTo(0)

    assertThat(counter.reserve(key, 1)).isEqualTo(1)
    assertThat(counter.displayed.value).isEqualTo(1)
  }

  @Test
  fun `releasing a slot that holds nothing is harmless`() {
    startSession()
    counter.release(AdSlotKey(AdSurface.LOGS, slotIndex = 9), 2)
    assertThat(counter.displayed.value).isEqualTo(0)
    assertThat(counter.headroom).isEqualTo(AdSessionCounter.CAP)
  }

  @Test
  fun `an impression is counted once per slot, however often it is revisited`() {
    startSession()
    val key = AdSlotKey(AdSurface.SQUAWKS, slotIndex = 1)

    assertThat(counter.markImpressionLogged(key)).isTrue()
    repeat(5) { assertThat(counter.markImpressionLogged(key)).isFalse() }
  }

  @Test
  fun `a new session forgets grants and impressions`() {
    startSession()
    val key = AdSlotKey(AdSurface.SQUAWKS, slotIndex = 0)
    counter.reserve(key, 1)
    counter.markImpressionLogged(key)

    leaveFor(30.minutes)

    assertThat(counter.headroom).isEqualTo(AdSessionCounter.CAP)
    assertThat(counter.reserve(key, 1)).isEqualTo(1)
    assertThat(counter.markImpressionLogged(key)).isTrue()
  }
}
