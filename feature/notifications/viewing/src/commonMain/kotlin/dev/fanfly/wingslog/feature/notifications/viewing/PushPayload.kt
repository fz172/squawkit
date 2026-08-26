package dev.fanfly.wingslog.feature.notifications.viewing

import dev.fanfly.wingslog.feature.notifications.model.NotificationChannel
import dev.fanfly.wingslog.feature.notifications.model.NotificationTapTarget

/**
 * A decoded N1 push payload (design §7.6) — everything the server sent, resolved into the app's own
 * types, with the localized strings still to be rendered by the caller.
 *
 * **In `commonMain`, not `androidMain`, on purpose.** The wire format is one contract with two
 * readers: Android's `FirebaseMessagingService` today, and P5's iOS notification service extension
 * next. Parsing it twice would be two places for the server's field names to drift out of.
 *
 * The server sends **named values, never rendered text** ([titleKey]/[bodyKey] plus the variables),
 * because it does not know the recipient's locale and cannot render a localized section label at
 * all. Which argument goes where is the client's knowledge, and it lives in `renderTitle`/
 * `renderBody` beside the string resources rather than here.
 */
data class PushPayload(
  val notificationId: String,
  val channel: NotificationChannel,
  val highPriority: Boolean,
  val titleKey: String,
  val bodyKey: String,
  val tailNumber: String,
  /** Empty means "the server had no display name" — render `notification_n1_actor_fallback`. */
  val actorName: String,
  /** `squawk` / `task` / `log` / `aircraft`, which selects the section labels. */
  val recordType: String,
  val changeCount: Int,
  val recordTitle: String,
  val tapTarget: NotificationTapTarget,
  /**
   * Which account the server addressed this copy to, or `null` from a server older than issue
   * P4.13. See [isAddressedTo] for why the two are not the same thing.
   */
  val recipientUid: String?,
) {

  /**
   * Whether this message should be shown on a device signed in as [signedInUid] (`null` when nobody
   * is signed in here).
   *
   * **Why the check is needed at all:** an FCM token belongs to the app *install*, not to an
   * account, while `push_devices` is keyed by install id under `users/{uid}/`. Sign out without the
   * registry delete landing — offline, or through a path that never calls it — and the next account
   * to sign in registers the same token under its own uid while the previous one keeps a document
   * holding a perfectly live address. Nothing prunes it: `pruneDeadTokens` only fires on a token FCM
   * reports as gone, and this one is not gone. So the previous account's collaboration text keeps
   * arriving at a device it no longer controls.
   *
   * The three cases, and each one is a deliberate answer rather than a fallthrough:
   * - **No [recipientUid]** — a server that predates the field. Render. A client newer than the
   *   server must not go silent during a rollout, and "absent" means the server never addressed the
   *   message, not that it addressed it to nobody.
   * - **Addressed, nobody signed in** — drop. There is no one here to show it to, and the account it
   *   names is not using this device.
   * - **Addressed to someone else** — drop. This is the case the field exists for.
   */
  fun isAddressedTo(signedInUid: String?): Boolean = when {
    recipientUid == null -> true
    signedInUid == null -> false
    else -> recipientUid == signedInUid
  }

  companion object {
    /**
     * Returns `null` for anything that is not a well-formed N1 message, so an unrecognised push —
     * a future message type, a truncated payload — is dropped rather than posted as a half-rendered
     * tray entry. [notificationId] and [tapTarget] are the two fields with no sane default: without
     * the first nothing can replace in the tray, and without the second a tap goes nowhere.
     */
    fun parse(data: Map<String, String>): PushPayload? {
      val notificationId = data["notificationId"]?.takeIf { it.isNotBlank() } ?: return null
      val tapTarget = parseTapTarget(data["tapTarget"]) ?: return null
      return PushPayload(
        notificationId = notificationId,
        channel = parseChannel(data["channel"]),
        // FCM data values are always strings — `highPriority` arrives as "true"/"false", never a
        // JSON boolean, so this is a string comparison rather than a toBoolean() on a real Boolean.
        highPriority = data["highPriority"] == "true",
        titleKey = data["titleKey"].orEmpty(),
        bodyKey = data["bodyKey"].orEmpty(),
        tailNumber = data["tailNumber"].orEmpty(),
        actorName = data["actorName"].orEmpty(),
        recordType = data["recordType"].orEmpty(),
        changeCount = data["changeCount"]?.toIntOrNull() ?: 1,
        recordTitle = data["recordTitle"].orEmpty(),
        tapTarget = tapTarget,
        // Blank is treated as absent: an empty string addresses nobody, and dropping every message
        // over a server that sent `recipientUid=""` would be a silent outage.
        recipientUid = data["recipientUid"]?.takeIf { it.isNotBlank() },
      )
    }

    /**
     * The server's tap target is **colon-delimited** (`squawk:{aircraftId}:{squawkId}`,
     * `aircraft:{aircraftId}:{tab}`) — deliberately not the slash-and-query URI
     * [NotificationTapRouter] encodes, which is this app's internal deep-link format and not
     * something the server should have to know. `NotificationTapRouter.decode` will not read this;
     * that asymmetry is the reason this function exists.
     */
    internal fun parseTapTarget(raw: String?): NotificationTapTarget? {
      val parts = raw?.split(":") ?: return null
      if (parts.size < 2) return null
      val aircraftId = parts[1].takeIf { it.isNotBlank() } ?: return null
      val recordId = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
      return when (parts[0]) {
        "aircraft" -> NotificationTapTarget.Aircraft(aircraftId, tab = recordId)
        // The record variants are useless without an id — a tap would scroll to nothing. Falling
        // back to the aircraft still lands the pilot on the right aircraft, which is the part every
        // variant shares.
        "squawk" -> recordId?.let { NotificationTapTarget.Squawk(aircraftId, it) }
          ?: NotificationTapTarget.Aircraft(aircraftId, tab = "squawks")
        "task" -> recordId?.let { NotificationTapTarget.Task(aircraftId, it) }
          ?: NotificationTapTarget.Aircraft(aircraftId, tab = "tasks")
        "log" -> recordId?.let { NotificationTapTarget.Log(aircraftId, it) }
          ?: NotificationTapTarget.Aircraft(aircraftId, tab = "logs")
        else -> null
      }
    }

    /**
     * `URGENCY` is the server's name for what the client enum calls `URGENCY_UPDATE`. Unknown
     * channels fall back to `COLLABORATION` rather than dropping the message: a notification on the
     * wrong channel is a routing annoyance, a dropped one is lost news.
     */
    internal fun parseChannel(raw: String?): NotificationChannel = when (raw) {
      "URGENCY" -> NotificationChannel.URGENCY_UPDATE
      else -> NotificationChannel.COLLABORATION
    }
  }
}
