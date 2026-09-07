package dev.fanfly.wingslog.feature.search.viewing

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import dev.fanfly.wingslog.core.ui.common.compose.highlightWords
import dev.fanfly.wingslog.core.ui.common.compose.searchHighlightStyle
import dev.fanfly.wingslog.feature.search.model.FieldMatch

/** The matched words in the given fields, for highlighting the text a card shows. */
fun List<FieldMatch>.wordsIn(vararg fields: String): Set<String> =
  filter { it.field in fields }.flatMapTo(LinkedHashSet()) { it.words }

/**
 * One line for a match the card cannot show: null while any [visibleFields] matched, otherwise the
 * first hidden field [noteFor] can describe, with its words highlighted.
 */
@Composable
fun hiddenMatchNote(
  matches: List<FieldMatch>,
  visibleFields: Set<String>,
  noteFor: @Composable (FieldMatch) -> String?,
): AnnotatedString? {
  if (matches.isEmpty() || matches.any { it.field in visibleFields }) return null
  val style = searchHighlightStyle()
  for (match in matches) {
    val note = noteFor(match) ?: continue
    return highlightWords(note, match.words, style)
  }
  return null
}
