package dev.fanfly.wingslog.core.ui.theme

import androidx.compose.ui.unit.dp

object Spacing {
  val none = 0.dp
  val extraSmall = 4.dp
  val small = 8.dp
  val medium = 12.dp
  val large = 16.dp
  val xLarge = 20.dp
  val extraLarge = 24.dp
  val huge = 32.dp
  val massive = 48.dp

  val hairline = 1.dp             // Border strokes only

  // Common Layout Constants
  val screenPadding = extraLarge
  val columnGap = large
  val cardCornerRadius = 12.dp   // All card surfaces
  val smallCornerRadius = 8.dp   // Chips, badges, small surfaces
  val buttonHeight = 56.dp
  val buttonCornerRadius = 16.dp

  // Smaller surface radii
  val chipCornerRadius = 12.dp   // Form controls, dropdowns, outlined fields
  val badgeCornerRadius = 4.dp   // Inline status/type badges
}
