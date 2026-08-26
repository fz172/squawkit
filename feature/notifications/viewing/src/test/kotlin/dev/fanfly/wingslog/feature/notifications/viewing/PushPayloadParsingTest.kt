package dev.fanfly.wingslog.feature.notifications.viewing

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.notifications.model.NotificationChannel
import dev.fanfly.wingslog.feature.notifications.model.NotificationTapTarget
import org.junit.Test

/**
 * The client half of the §7.6 wire contract. Every field name and value here is copied from what
 * `backend/firebase/functions/src/notifications/pushMessages.ts` actually sends — if those drift
 * apart, N1 arrives as a blank tray entry, and nothing else in either codebase would notice.
 */
class PushPayloadParsingTest {

  /** An activity summary, exactly as `activityPushData` builds it. */
  private fun activityData(vararg extra: Pair<String, String>): Map<String, String> =
    activityData(extra.toMap())

  private fun activityData(
    extra: Map<String, String> = emptyMap(),
  ): Map<String, String> = mapOf(
    "class" to "collaboration",
    "channel" to "COLLABORATION",
    "notificationId" to "n1:ac-1:task:actor-1:3",
    "highPriority" to "false",
    "aircraftId" to "ac-1",
    "recordType" to "task",
    "tapTarget" to "aircraft:ac-1:tasks",
    "titleKey" to "notification_n1_title",
    "bodyKey" to "notification_n1_body_plural",
    "tailNumber" to "N4589T",
    "actorName" to "Dave Chen",
    "changeCount" to "5",
  ) + extra

  @Test
  fun `parses an activity summary`() {
    val parsed = PushPayload.parse(activityData())!!

    assertThat(parsed.notificationId).isEqualTo("n1:ac-1:task:actor-1:3")
    assertThat(parsed.channel).isEqualTo(NotificationChannel.COLLABORATION)
    assertThat(parsed.highPriority).isFalse()
    assertThat(parsed.tailNumber).isEqualTo("N4589T")
    assertThat(parsed.actorName).isEqualTo("Dave Chen")
    assertThat(parsed.changeCount).isEqualTo(5)
    assertThat(parsed.tapTarget).isEqualTo(NotificationTapTarget.Aircraft("ac-1", tab = "tasks"))
  }

  @Test
  fun `parses an escalation, which lands on the record rather than the list`() {
    val parsed = PushPayload.parse(
      mapOf(
        "class" to "urgency",
        "channel" to "GROUNDED",
        "notificationId" to "n1esc:ac-1:sq-9",
        "highPriority" to "true",
        "recordType" to "squawk",
        "tapTarget" to "squawk:ac-1:sq-9",
        "titleKey" to "notification_title_grounded",
        "bodyKey" to "notification_n1_body_squawk_raised",
        "tailNumber" to "N4589T",
        "actorName" to "Dave Chen",
        "recordId" to "sq-9",
        "recordTitle" to "Left brake dragging",
      ),
    )!!

    assertThat(parsed.channel).isEqualTo(NotificationChannel.GROUNDED)
    assertThat(parsed.highPriority).isTrue()
    assertThat(parsed.recordTitle).isEqualTo("Left brake dragging")
    assertThat(parsed.tapTarget).isEqualTo(NotificationTapTarget.Squawk("ac-1", "sq-9"))
  }

  @Test
  fun `maps the server's URGENCY onto the client's URGENCY_UPDATE`() {
    // The one name the two sides spell differently. Getting this wrong routes a priority-raise
    // through the collaboration channel, which has the wrong importance and the wrong OS toggle.
    val parsed = PushPayload.parse(activityData(mapOf("channel" to "URGENCY")))!!

    assertThat(parsed.channel).isEqualTo(NotificationChannel.URGENCY_UPDATE)
  }

  @Test
  fun `falls back to COLLABORATION for an unknown channel rather than dropping the message`() {
    val parsed = PushPayload.parse(activityData(mapOf("channel" to "SOMETHING_NEW")))!!

    assertThat(parsed.channel).isEqualTo(NotificationChannel.COLLABORATION)
  }

  @Test
  fun `reads highPriority as the string it arrives as`() {
    // FCM data values are always strings. A Boolean-typed read would be false for every message.
    assertThat(PushPayload.parse(activityData(mapOf("highPriority" to "true")))!!.highPriority)
      .isTrue()
  }

  @Test
  fun `drops a message with no notificationId`() {
    // Without it nothing can replace or cancel the entry — §7.3's whole mechanism is that id.
    assertThat(PushPayload.parse(activityData() - "notificationId")).isNull()
    assertThat(PushPayload.parse(activityData(mapOf("notificationId" to "")))).isNull()
  }

  @Test
  fun `drops a message with no usable tap target`() {
    assertThat(PushPayload.parse(activityData() - "tapTarget")).isNull()
    assertThat(PushPayload.parse(activityData(mapOf("tapTarget" to "aircraft")))).isNull()
    assertThat(PushPayload.parse(activityData(mapOf("tapTarget" to "wat:ac-1:x")))).isNull()
  }

  @Test
  fun `degrades a record target with no id to that aircraft's list`() {
    // A tap that scrolls to nothing is survivable; losing the aircraft too is not.
    val parsed = PushPayload.parse(activityData(mapOf("tapTarget" to "squawk:ac-1")))!!

    assertThat(parsed.tapTarget).isEqualTo(NotificationTapTarget.Aircraft("ac-1", tab = "squawks"))
  }

  @Test
  fun `defaults changeCount to one when absent or unparseable`() {
    assertThat(PushPayload.parse(activityData() - "changeCount")!!.changeCount).isEqualTo(1)
    assertThat(PushPayload.parse(activityData(mapOf("changeCount" to "lots")))!!.changeCount)
      .isEqualTo(1)
  }

  @Test
  fun `keeps an empty actor name for the client to substitute`() {
    // The fallback ("A collaborator") is itself localized, so the server sends "" and the renderer
    // decides — this class must not invent a name of its own.
    assertThat(PushPayload.parse(activityData(mapOf("actorName" to "")))!!.actorName).isEmpty()
  }

  @Test
  fun `drops a message addressed to an account that is not signed in here`() {
    // The P4.13 case: a stale push_devices doc under a signed-out account keeps a live token, so its
    // notifications keep arriving at a device someone else is now using.
    val parsed = PushPayload.parse(activityData("recipientUid" to "user-a"))!!

    assertThat(parsed.isAddressedTo("user-b")).isFalse()
  }

  @Test
  fun `renders a message addressed to the account signed in here`() {
    val parsed = PushPayload.parse(activityData("recipientUid" to "user-a"))!!

    assertThat(parsed.isAddressedTo("user-a")).isTrue()
  }

  @Test
  fun `drops an addressed message when nobody is signed in`() {
    val parsed = PushPayload.parse(activityData("recipientUid" to "user-a"))!!

    assertThat(parsed.isAddressedTo(null)).isFalse()
  }

  @Test
  fun `still renders a message from a server that sends no recipient`() {
    // Rollout: a client newer than the server must not go silent. Absent means the server never
    // addressed the message, not that it addressed it to nobody.
    val parsed = PushPayload.parse(activityData())!!

    assertThat(parsed.recipientUid).isNull()
    assertThat(parsed.isAddressedTo("user-a")).isTrue()
    assertThat(parsed.isAddressedTo(null)).isTrue()
  }

  /** An empty value addresses nobody, so it reads as absent rather than as a reason to drop. */
  @Test
  fun `treats a blank recipient as absent`() {
    val parsed = PushPayload.parse(activityData("recipientUid" to ""))!!

    assertThat(parsed.recipientUid).isNull()
    assertThat(parsed.isAddressedTo("user-a")).isTrue()
  }

  @Test
  fun `parses the high-volume ceiling notice`() {
    val parsed = PushPayload.parse(
      activityData(
        mapOf(
          "notificationId" to "n1max:ac-1:2026082404",
          "tapTarget" to "aircraft:ac-1:overview",
          "titleKey" to "notification_n1_title_high_volume",
          "bodyKey" to "notification_n1_body_high_volume",
          "recordType" to "aircraft",
          "actorName" to "",
        ),
      ),
    )!!

    assertThat(parsed.notificationId).startsWith("n1max:")
    assertThat(parsed.tapTarget).isEqualTo(NotificationTapTarget.Aircraft("ac-1", tab = "overview"))
  }
}
