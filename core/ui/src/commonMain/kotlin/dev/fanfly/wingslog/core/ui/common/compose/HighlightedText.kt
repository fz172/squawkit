package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import dev.fanfly.wingslog.core.ui.common.text.highlightRanges

/** Advisory Amber wash behind a matched search word; text colour is left to the caller. */
@Composable
fun searchHighlightStyle(): SpanStyle =
  SpanStyle(background = MaterialTheme.colorScheme.tertiaryContainer, color = MaterialTheme.colorScheme.onTertiaryContainer)

/** [text] with [style] on every whole-word occurrence of [words]. Plain text when nothing matches. */
fun highlightWords(text: String, words: Set<String>, style: SpanStyle): AnnotatedString {
  val ranges = highlightRanges(text, words)
  if (ranges.isEmpty()) return AnnotatedString(text)
  return buildAnnotatedString {
    var cursor = 0
    for (range in ranges) {
      if (range.first < cursor) continue
      append(text, cursor, range.first)
      withStyle(style) { append(text, range.first, range.last + 1) }
      cursor = range.last + 1
    }
    append(text, cursor, text.length)
  }
}
