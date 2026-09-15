package dev.fanfly.wingslog.feature.ads.model

/**
 * The surfaces that carry ads: the three record lists, plus the data log viewer's one fixed slot.
 * No other surface does — not dashboards, detail sheets, forms, wizards, pickers, settings, export
 * flows, the technician list, search results, the data log *list*, or the AOG / critical-alert
 * sections.
 *
 * [analyticsName] is the `surface` parameter on every ad event. Kept here rather than derived from
 * [name] so that renaming an enum constant cannot silently break a dashboard: GA4 params are a wire
 * format, and this is the only place they are spelled.
 */
enum class AdSurface(val analyticsName: String) {
  SQUAWKS("squawks"),
  TASKS("tasks"),
  LOGS("logs"),

  /**
   * The data log viewer, and only the viewer. One slot at a fixed position, never interleaved into
   * a list and never inside or over a chart pane (data log PRD R44a).
   */
  DATA_LOGS("data_logs"),
}
