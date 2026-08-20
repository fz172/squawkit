package dev.fanfly.wingslog.feature.notifications.model

/**
 * Where a tap on a [PendingNotification] should land. Converted to a nav route by
 * `NotificationTapRouter` (design §5.3, task P2.9) — that router, and the deep-link route it targets,
 * do not exist yet; this is only the data contract [PendingNotification] needs to compile, landing
 * ahead of its consumer because `:model` is where both it and `PendingNotification` live (§3).
 *
 * **`Aircraft.tab` is a plain nullable [String], not a typed tab enum.** Design §5.3 sketches it as
 * an `AircraftTab` — no such type exists anywhere in the tree today (checked; tab selection in
 * `feature/aircraft/dashboard` is per-composable, not a shared enum), and `:model` could not depend
 * on one even if it did, since `:model` may only depend on `core:model` (§3). `Screen.AircraftTabDeepLink`
 * (P2.9) is itself sketched as a raw string query parameter (`?tab={tab}`), so a `String` here matches
 * the wire shape it will actually travel as; resolving it to whatever typed selector the aircraft
 * dashboard uses is P2.9's problem when it defines the router, not this type's.
 */
sealed interface NotificationTapTarget {
  data class Aircraft(val aircraftId: String, val tab: String? = null) : NotificationTapTarget
  data class Squawk(val aircraftId: String, val squawkId: String) : NotificationTapTarget
  data class Task(val aircraftId: String, val taskId: String) : NotificationTapTarget
  data class Log(val aircraftId: String, val logId: String) : NotificationTapTarget
}
