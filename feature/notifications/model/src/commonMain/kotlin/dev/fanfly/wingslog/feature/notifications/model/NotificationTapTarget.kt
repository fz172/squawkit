package dev.fanfly.wingslog.feature.notifications.model

/**
 * Where a tap on a [PendingNotification] should land. Carried over the wire by
 * `NotificationTapRouter` and applied to shell state by `AdaptiveShellViewModel` (design §5.3).
 *
 * Every variant names an [thingId], because every notification this app sends is about one
 * thing — so selecting it is the one thing every tap does, whatever else follows.
 *
 * The record variants land the pilot on the record *in its list*, scrolled to and briefly
 * highlighted, rather than in its edit form: a notification reports that something changed, and the
 * useful destination is the record in the context of everything around it.
 *
 * **`Aircraft.tab` is a plain nullable [String], not a typed tab enum.** Design §5.3 sketches it as
 * an `AircraftTab` — no such type exists anywhere in the tree today (checked; tab selection in
 * `feature/thing/dashboard` is per-composable, not a shared enum), and `:model` could not depend
 * on one even if it did, since `:model` may only depend on `core:model` (§3). It travels as a raw
 * string in the tap URI, so a `String` here matches the wire shape; resolving it to the shell's
 * `ShellSection` is the consumer's job.
 */
sealed interface NotificationTapTarget {
  val thingId: String

  data class Aircraft(override val thingId: String, val tab: String? = null) :
    NotificationTapTarget

  data class Squawk(override val thingId: String, val squawkId: String) :
    NotificationTapTarget

  data class Task(override val thingId: String, val taskId: String) :
    NotificationTapTarget

  data class Log(override val thingId: String, val logId: String) :
    NotificationTapTarget
}
