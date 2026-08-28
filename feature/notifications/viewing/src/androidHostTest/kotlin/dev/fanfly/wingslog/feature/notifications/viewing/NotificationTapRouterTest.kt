package dev.fanfly.wingslog.feature.notifications.viewing

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.notifications.model.NotificationTapTarget
import org.junit.After
import org.junit.Test

class NotificationTapRouterTest {

  // A singleton object's state leaks between tests unless each one cleans up after itself.
  @After
  fun tearDown() {
    NotificationTapRouter.consume()
  }

  // encode/decode round-trip — every NotificationTapTarget variant.

  @Test
  fun encodeThenDeliver_squawk_roundTrips() {
    val target =
      NotificationTapTarget.Squawk(aircraftId = "ac-1", squawkId = "sq-1")

    val delivered =
      NotificationTapRouter.deliver(NotificationTapRouter.encode(target))

    assertThat(delivered).isTrue()
    assertThat(NotificationTapRouter.pending.value).isEqualTo(target)
  }

  @Test
  fun encodeThenDeliver_task_roundTrips() {
    val target =
      NotificationTapTarget.Task(aircraftId = "ac-1", taskId = "task-1")

    NotificationTapRouter.deliver(NotificationTapRouter.encode(target))

    assertThat(NotificationTapRouter.pending.value).isEqualTo(target)
  }

  @Test
  fun encodeThenDeliver_log_roundTrips() {
    val target = NotificationTapTarget.Log(aircraftId = "ac-1", logId = "log-1")

    NotificationTapRouter.deliver(NotificationTapRouter.encode(target))

    assertThat(NotificationTapRouter.pending.value).isEqualTo(target)
  }

  @Test
  fun encodeThenDeliver_aircraftWithTab_roundTrips() {
    val target =
      NotificationTapTarget.Aircraft(aircraftId = "ac-1", tab = "tasks")

    NotificationTapRouter.deliver(NotificationTapRouter.encode(target))

    assertThat(NotificationTapRouter.pending.value).isEqualTo(target)
  }

  @Test
  fun encodeThenDeliver_aircraftWithoutTab_roundTrips() {
    val target = NotificationTapTarget.Aircraft(aircraftId = "ac-1", tab = null)

    NotificationTapRouter.deliver(NotificationTapRouter.encode(target))

    assertThat(NotificationTapRouter.pending.value).isEqualTo(target)
  }

  // deliver() dispatch contract — false, and no delivery, for a URI this router doesn't own.

  @Test
  fun deliver_unrelatedUri_returnsFalse_andDeliversNothing() {
    val delivered =
      NotificationTapRouter.deliver("https://example.com/whatever")

    assertThat(delivered).isFalse()
    assertThat(NotificationTapRouter.pending.value).isNull()
  }

  @Test
  fun deliver_malformedNotificationUri_returnsFalse() {
    // Right scheme/host, but missing the record id a squawk/task/log target requires.
    val delivered =
      NotificationTapRouter.deliver("wingslog://notification-tap/squawk/ac-1")

    assertThat(delivered).isFalse()
    assertThat(NotificationTapRouter.pending.value).isNull()
  }

  // consume() clears pending.

  @Test
  fun consume_clearsPending() {
    NotificationTapRouter.deliver(
      NotificationTapRouter.encode(
        NotificationTapTarget.Aircraft("ac-1")
      )
    )

    NotificationTapRouter.consume()

    assertThat(NotificationTapRouter.pending.value).isNull()
  }
}
