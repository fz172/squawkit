package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object ContentWidth {
  val Feed = 840.dp
  val Reading = 720.dp
  val Form = 680.dp
}

fun Modifier.constrainedContentWidth(
  maxWidth: Dp = ContentWidth.Reading,
): Modifier = widthIn(max = maxWidth).fillMaxWidth()
