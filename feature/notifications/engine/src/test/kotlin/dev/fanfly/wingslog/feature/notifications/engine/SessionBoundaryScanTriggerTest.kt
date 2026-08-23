package dev.fanfly.wingslog.feature.notifications.engine

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.lifecycle.AppForegroundObserver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Which foreground transitions become a scan request. Whether a request then does any work is
 * [UrgencyScanner]'s 2h debounce, covered in [UrgencyScannerSessionDebounceTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionBoundaryScanTriggerTest {

  private class FakeClock(private var current: Instant = Instant.fromEpochMilliseconds(0)) : Clock {
    override fun now(): Instant = current
    fun advance(by: Duration) {
      current += by
    }
  }

  private val clock = FakeClock()
  private val foreground = AppForegroundObserver(clock)
  private var requests = 0

  /**
   * The provider counts the request and then throws: a real [UrgencyScanner] needs six managers and
   * Firebase, and this class's contract ends at "asked for a scan". The trigger's own `runCatching`
   * swallows it, which is also what keeps a failing scan from killing the collector — asserted by
   * [aFailedScanDoesNotStopLaterBoundaries].
   */
  private fun TestScope.startedTrigger() = SessionBoundaryScanTrigger(
    foreground = foreground,
    scanner = {
      requests++
      throw RuntimeException("no scanner in this test")
    },
    // backgroundScope, not `this`: the collector never completes, and runTest waits on
    // children of the test scope itself.
    scope = backgroundScope,
  ).also { it.start() }

  @Test
  fun coldStart_requestsAScan() = runTest(UnconfinedTestDispatcher()) {
    startedTrigger()
    foreground.onEnterForeground()
    assertThat(requests).isEqualTo(1)
  }

  @Test
  fun briefBackgrounding_isTheSameSession_soNoScan() = runTest(UnconfinedTestDispatcher()) {
    startedTrigger()
    foreground.onEnterForeground()
    requests = 0

    foreground.onEnterBackground()
    clock.advance(5.minutes)
    foreground.onEnterForeground()

    assertThat(requests).isEqualTo(0)
  }

  @Test
  fun returningPastTheSessionThreshold_requestsAScan() = runTest(UnconfinedTestDispatcher()) {
    startedTrigger()
    foreground.onEnterForeground()
    requests = 0

    foreground.onEnterBackground()
    clock.advance(31.minutes)
    foreground.onEnterForeground()

    assertThat(requests).isEqualTo(1)
  }

  @Test
  fun start_calledTwice_doesNotAttachASecondCollector() = runTest(UnconfinedTestDispatcher()) {
    val trigger = startedTrigger()
    trigger.start()

    foreground.onEnterForeground()

    assertThat(requests).isEqualTo(1)
  }

  @Test
  fun aFailedScanDoesNotStopLaterBoundaries() = runTest(UnconfinedTestDispatcher()) {
    startedTrigger()
    foreground.onEnterForeground()

    foreground.onEnterBackground()
    clock.advance(31.minutes)
    foreground.onEnterForeground()

    // Both boundaries got through even though every scan threw.
    assertThat(requests).isEqualTo(2)
  }

  /**
   * The collector is launched at Koin init but subscribes asynchronously, so the cold-start
   * boundary can land before it attaches. Starting *after* the foreground edge must still scan —
   * a `drop(1)` on the StateFlow would discard exactly this case.
   */
  @Test
  fun startingAfterTheColdStartBoundary_stillScans() = runTest(UnconfinedTestDispatcher()) {
    foreground.onEnterForeground()
    assertThat(requests).isEqualTo(0)

    startedTrigger()

    assertThat(requests).isEqualTo(1)
  }

  /** ...but it must not then re-scan that same session on any later emission. */
  @Test
  fun anAlreadyHandledSession_isNotScannedTwice() = runTest(UnconfinedTestDispatcher()) {
    foreground.onEnterForeground()
    startedTrigger()

    foreground.onEnterBackground()
    clock.advance(5.minutes)
    foreground.onEnterForeground()

    assertThat(requests).isEqualTo(1)
  }
}
