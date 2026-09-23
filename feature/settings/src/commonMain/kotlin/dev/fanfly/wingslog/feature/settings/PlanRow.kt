package dev.fanfly.wingslog.feature.settings

/** What the Subscription row says under its title. */
sealed interface PlanRow {
  data object Basic : PlanRow

  /** [periodEnd] is the display date the plan renews or ends on, or null when the store gave none. */
  data class Pro(val periodEnd: String?, val willRenew: Boolean) : PlanRow
}
