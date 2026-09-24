package dev.fanfly.wingslog.feature.sharing.viewing.invitecode

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import dev.fanfly.wingslog.feature.sharing.viewing.text

/**
 * Renders the EFA2-GGTH grouping as display-only, over a raw (undashed) field value. The dash is
 * inserted after the 4th character once there's a 5th; the offset mapping shifts every caret
 * position past it by one so the cursor tracks the raw text instead of jumping when the dash appears.
 */
internal val InviteCodeGroupingTransformation = VisualTransformation { text ->
  val raw = text.text
  val formatted = if (raw.length > 4) "${raw.take(4)}-${raw.drop(4)}" else raw
  val mapping = object : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int =
      if (offset <= 4) offset else offset + 1

    override fun transformedToOriginal(offset: Int): Int =
      if (offset <= 4) offset else offset - 1
  }
  TransformedText(AnnotatedString(formatted), mapping)
}
