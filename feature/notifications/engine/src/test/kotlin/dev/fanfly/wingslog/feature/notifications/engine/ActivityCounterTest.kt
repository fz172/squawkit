package dev.fanfly.wingslog.feature.notifications.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Design §7.4's two rules, as applied locally by web (§8.4). */
class ActivityCounterTest {

  private class FakeClock(var current: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)) :
    Clock {
    override fun now(): Instant = current
    fun advance(by: Duration) {
      current += by
    }
  }

  private val clock = FakeClock()
  private val counter = ActivityCounter(clock)
  private val key = ActivityKey("ac-1", "task", "dave")

  @Test
  fun firstWrite_posts() {
    val post = counter.record(key)
    assertThat(post).isNotNull()
    assertThat(post!!.changeCount).isEqualTo(1)
  }

  // --- MIN_REPOST_INTERVAL ---

  @Test
  fun secondWriteWithinThrottle_isCountedButNotPosted() {
    counter.record(key)
    clock.advance(5.seconds)
    assertThat(counter.record(key)).isNull()
  }

  /** The suppressed writes still counted — the next post reports the true total, not 2. */
  @Test
  fun postAfterThrottle_reportsEveryWriteIncludingSuppressedOnes() {
    counter.record(key)
    repeat(3) {
      clock.advance(5.seconds)
      counter.record(key)
    }
    clock.advance(30.seconds)

    val post = counter.record(key)
    assertThat(post).isNotNull()
    assertThat(post!!.changeCount).isEqualTo(5)
  }

  /** §7.4's example: a 200-record bulk import must not produce 200 notifications. */
  @Test
  fun aBurstPostsTwicePerMinute_notOncePerWrite() {
    var posts = 0
    repeat(200) {
      if (counter.record(key) != null) posts++
      clock.advance(1.seconds)
    }
    // 200 writes over 200s at one post per 30s.
    assertThat(posts).isAtMost(8)
    assertThat(posts).isAtLeast(6)
  }

  // --- ACTIVITY_WINDOW ---

  @Test
  fun writeAfterTheWindow_startsANewSessionAndResetsTheCount() {
    counter.record(key)
    clock.advance(31.seconds)
    counter.record(key)   // count 2

    clock.advance(31.minutes)
    val post = counter.record(key)

    assertThat(post).isNotNull()
    assertThat(post!!.changeCount).isEqualTo(1)
  }

  /** The tag embeds sessionStart, so a new session must roll it or it overwrites the old entry. */
  @Test
  fun aNewSessionRollsTheSessionStart() {
    val first = counter.record(key)!!
    clock.advance(31.minutes)
    val second = counter.record(key)!!

    assertThat(second.sessionStart).isGreaterThan(first.sessionStart)
  }

  /** Within the window, the tag must stay put so the entry replaces rather than stacks. */
  @Test
  fun withinTheWindow_theSessionStartIsStable() {
    val first = counter.record(key)!!
    clock.advance(31.seconds)
    val second = counter.record(key)!!

    assertThat(second.sessionStart).isEqualTo(first.sessionStart)
  }

  /** A new session posts immediately — it has its own tray entry to fill, so the throttle from the previous session must not carry over. */
  @Test
  fun aNewSessionIsNotThrottledByThePreviousSessionsSend() {
    counter.record(key)
    clock.advance(31.minutes)
    assertThat(counter.record(key)).isNotNull()
  }

  // --- key isolation ---

  @Test
  fun differentActors_areCountedSeparately() {
    counter.record(key)
    val other = counter.record(key.copy(actorUid = "sarah"))

    assertThat(other).isNotNull()
    assertThat(other!!.changeCount).isEqualTo(1)
  }

  @Test
  fun differentRecordTypes_areCountedSeparately() {
    counter.record(key)
    assertThat(counter.record(key.copy(recordType = "squawk"))).isNotNull()
  }

  @Test
  fun differentAircraft_areCountedSeparately() {
    counter.record(key)
    assertThat(counter.record(key.copy(aircraftId = "ac-2"))).isNotNull()
  }

  /** Sign-out must not let one account's session bleed into the next. */
  @Test
  fun clear_startsFresh() {
    counter.record(key)
    clock.advance(5.seconds)
    counter.clear()

    val post = counter.record(key)
    assertThat(post).isNotNull()
    assertThat(post!!.changeCount).isEqualTo(1)
  }
}
