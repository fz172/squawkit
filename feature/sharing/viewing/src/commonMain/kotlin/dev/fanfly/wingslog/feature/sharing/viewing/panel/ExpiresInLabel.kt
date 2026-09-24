package dev.fanfly.wingslog.feature.sharing.viewing.panel

import kotlin.time.Clock

/** "23h 41m" / "6d" style countdown to [expiresAtEpochMs]; "Expired" once past. */
internal fun expiresInLabel(expiresAtEpochMs: Long): String {
  val remainingMs = expiresAtEpochMs - Clock.System.now()
    .toEpochMilliseconds()
  if (remainingMs <= 0) return "0m"
  val totalMinutes = remainingMs / 60_000
  val days = totalMinutes / (24 * 60)
  val hours = (totalMinutes % (24 * 60)) / 60
  val minutes = totalMinutes % 60
  return when {
    days > 0 -> "${days}d ${hours}h"
    hours > 0 -> "${hours}h ${minutes}m"
    else -> "${minutes}m"
  }
}
