package dev.fanfly.wingslog.feature.notifications.model

/**
 * What caused an urgency scan to run.
 *
 * In `model` rather than beside the scanner because it is shared vocabulary: `engine` produces it,
 * `analytics` reports it, and `analytics` must not depend on `engine`. Only [SESSION_BOUNDARY] is
 * debounced — an app opened six times an hour should not scan six times, while the periodic
 * background job is already spaced at the cadence the debounce would enforce and [MANUAL] is
 * someone explicitly asking (design §6.6).
 */
enum class ScanTrigger {
  MANUAL,
  SCHEDULED,
  SESSION_BOUNDARY,
}
