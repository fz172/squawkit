package dev.fanfly.wingslog.feature.datalog.model

/**
 * What a pane gesture asks of the shared time domain (PRD R23, design §11.4). Positions are
 * fractions of the pane width so the ViewModel needs no pixels; the pane converts a brush's pixels
 * itself because the minimum-span rule lives with the brush.
 */
sealed interface GestureIntent {
  /** Zoom to the span between two x positions, in pixels across [widthPx]. */
  data class Brush(val x0Px: Float, val x1Px: Float, val widthPx: Int) :
    GestureIntent

  /** Move the view by a fraction of the visible span; positive moves later in time. */
  data class Pan(val spanFraction: Double) : GestureIntent

  /** Scale the span by `1 / factor` around a fraction of the width; factor > 1 zooms in. */
  data class Zoom(val anchorFraction: Double, val factor: Double) :
    GestureIntent

  /** Place the cursor at a fraction of the width, or clear it. */
  data class Cursor(val fraction: Double?) : GestureIntent

  /** Return to the whole log. */
  data object Reset : GestureIntent
}
