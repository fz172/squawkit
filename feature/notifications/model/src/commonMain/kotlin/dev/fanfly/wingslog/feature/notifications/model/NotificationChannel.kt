package dev.fanfly.wingslog.feature.notifications.model

/**
 * One notification channel per class (design §5.2, Q8), so OS-level controls (Android's
 * per-channel settings, iOS's per-category) mirror the in-app preference groups in §8.3 rather than
 * lumping everything under one importance level.
 */
enum class NotificationChannel {
  /** N1 — someone else changed a shared aircraft. */
  COLLABORATION,

  /** N2 — a squawk or inspection crossed into a more urgent tier, excluding AOG. */
  URGENCY_UPDATE,

  /** N2's top tier — an aircraft grounded by an AOG squawk. High priority; the one class that must pierce Focus once P5.3 lands the Time Sensitive entitlement. */
  GROUNDED,
}
