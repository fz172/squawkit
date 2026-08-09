package dev.fanfly.wingslog.feature.ads.model

/**
 * The three record lists that carry ads. No other surface does — not dashboards, detail sheets,
 * forms, wizards, pickers, settings, export flows, the technician list, search results, or the
 * AOG / critical-alert sections.
 *
 * [analyticsName] is the `surface` parameter on every ad event. Kept here rather than derived from
 * [name] so that renaming an enum constant cannot silently break a dashboard: GA4 params are a wire
 * format, and this is the only place they are spelled.
 */
enum class AdSurface(val analyticsName: String) {
  SQUAWKS("squawks"),
  TASKS("tasks"),
  LOGS("logs"),
}
