package dev.fanfly.wingslog.core.ui.adaptive

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The vector for a template's `icon` key.
 *
 * The key is template data and the vector is not: a template published after this build ships can
 * name an icon this build has never heard of, so an unknown key falls back rather than failing.
 * That fallback is the same shape as [dev.fanfly.wingslog.core.template.TemplateResolution] — a
 * Thing is never hidden because we could not draw it.
 */
fun thingIcon(key: String): ImageVector = when (key) {
  "airplane" -> Icons.Filled.Flight
  "automotive" -> Icons.Filled.DirectionsCar
  "boat" -> Icons.Filled.Sailing
  "bike" -> Icons.Filled.PedalBike
  "home" -> Icons.Filled.Home
  else -> Icons.Filled.Category
}
