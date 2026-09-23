package dev.fanfly.wingslog.feature.tasks.update.form.schedule

import androidx.compose.ui.graphics.vector.ImageVector

/** One segment: what picking it means, its words, and an optional glyph ahead of them. */
internal data class SegmentOption<T>(
  val value: T,
  val label: String,
  val icon: ImageVector? = null,
)
