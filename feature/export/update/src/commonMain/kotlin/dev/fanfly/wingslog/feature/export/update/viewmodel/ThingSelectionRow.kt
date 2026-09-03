package dev.fanfly.wingslog.feature.export.update.viewmodel

/**
 * Display-ready thing row used by the export picker.
 */
data class ThingSelectionRow(
  val thingId: String,
  /**
   * What to call this Thing, and what to say underneath.
   *
   * Resolved here rather than in the composable: the export list spans the whole account, so a
   * mixed fleet has no single lexicon to render from — every row read "Untitled aircraft" because
   * the screen asked `LocalThingLexicon`, which describes the *selected* Thing.
   */
  val label: String,
  val subtitle: String,
  val logCount: Int,
  val attachmentSizeBytes: Long,
)
